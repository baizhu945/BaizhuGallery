package org.fossifymigration.baizhu.gallery.extensions

import android.os.SystemClock
import org.fossifymigration.baizhu.gallery.activities.SimpleActivity
import java.io.File
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val OWNED_COPY_BUFFER_SIZE = 128 * 1024
private const val OWNED_BYTE_UPDATE_MS = 100L
private const val OWNED_UI_WAIT_MS = 200L

/** UI callbacks are supplied by the coordinator; mutable state belongs to its single worker. */
internal class OwnedBulkSession(
    val closed: () -> Boolean,
    val report: (Int, Int) -> Unit,
    val phase: (Int) -> Unit,
    val bytes: (Long, Long) -> Unit,
    val finish: () -> Unit,
    val prompt: (Boolean) -> Unit = {}
) {
    var processed = 0
    var failed = 0
    var cancelled = false
    private var lastByteUpdate = 0L
    val changedPaths = linkedSetOf<String>()
    val changedDirectories = linkedSetOf<String>()

    fun stopped() = cancelled || closed()

    fun copiedBytes(completed: Long, total: Long) {
        val now = SystemClock.elapsedRealtime()
        if (completed == total || now - lastByteUpdate >= OWNED_BYTE_UPDATE_MS) {
            lastByteUpdate = now
            bytes(completed, total)
        }
    }

    fun counted(success: Boolean) {
        processed++
        if (!success) failed++
        report(processed, failed)
    }
}

/** CREATE_NEW protects older bin images and concurrent conflict winners. */
internal fun copyOwnedFile(source: File, target: File, session: OwnedBulkSession) {
    check(!session.stopped())
    require(source.isFile)
    val expected = source.length()
    val modified = source.lastModified()
    target.parentFile?.let { check(it.isDirectory || it.mkdirs()) }
    var created = false
    var verified = false
    try {
        Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
            created = true
            val copied = transferOwnedBytes(source, output, expected, session)
            check(copied == expected && source.length() == expected && source.lastModified() == modified)
        }
        check(target.length() == expected)
        verified = true
        target.setLastModified(modified)
    } finally {
        // Never remove a completed verified copy on cancellation or uncertain source outcomes.
        if (created && !verified) target.delete()
    }
}

private fun transferOwnedBytes(
    source: File,
    output: OutputStream,
    expected: Long,
    session: OwnedBulkSession
): Long = source.inputStream().use { input ->
    val buffer = ByteArray(OWNED_COPY_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        check(!session.stopped())
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        copied += count
        session.copiedBytes(copied, expected)
    }
    output.flush()
    copied
}

internal fun ownedFilesEqual(first: File, second: File, session: OwnedBulkSession): Boolean {
    if (!first.isFile || !second.isFile || first.length() != second.length()) return false
    return ownedFileHash(first, session).contentEquals(ownedFileHash(second, session))
}

private fun ownedFileHash(file: File, session: OwnedBulkSession): ByteArray {
    val expected = file.length()
    val modified = file.lastModified()
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(OWNED_COPY_BUFFER_SIZE)
        while (true) {
            check(!session.stopped())
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    check(file.length() == expected && file.lastModified() == modified)
    return digest.digest()
}

internal fun discardRetainedBackup(source: File, backup: File?, session: OwnedBulkSession) {
    // Only this run's verified backup, with a positively retained identical original.
    if (backup != null && !session.stopped()) {
        val retained = runCatching { ownedFilesEqual(source, backup, session) }.getOrDefault(false)
        if (retained) backup.delete()
    }
}

internal enum class OwnedConflict { KEEP_BOTH, SKIP, CANCEL }

internal fun resolveOwnedTarget(target: File, choice: OwnedConflict, session: OwnedBulkSession): File? = when (choice) {
    OwnedConflict.KEEP_BOTH -> uniqueOwnedTarget(target, session)
    OwnedConflict.SKIP -> null.also { session.counted(false) }
    OwnedConflict.CANCEL -> null.also { session.cancelled = true }
}

private fun uniqueOwnedTarget(target: File, session: OwnedBulkSession): File {
    val extension = target.extension.let { if (it.isEmpty()) "" else ".$it" }
    val stem = target.name.removeSuffix(extension)
    var index = 1
    var candidate = File(target.parentFile, "$stem ($index)$extension")
    while (candidate.exists()) {
        check(!session.stopped())
        index++
        candidate = File(target.parentFile, "$stem ($index)$extension")
    }
    // This is only a proposed name; CREATE_NEW still arbitrates concurrent writers.
    return candidate
}

/** Worker-only wait; destruction releases it even if the platform loses a callback. */
internal fun <T> SimpleActivity.awaitOwnedUi(
    session: OwnedBulkSession,
    fallback: T,
    request: ((T) -> Unit) -> Unit
): T {
    val latch = CountDownLatch(1)
    var result = fallback
    runOnUiThread {
        if (!session.stopped()) {
            try {
                session.prompt(true)
                request { value ->
                    result = value
                    latch.countDown()
                }
            } catch (_: Exception) {
                latch.countDown()
            }
        } else {
            latch.countDown()
        }
    }
    return try {
        while (!latch.await(OWNED_UI_WAIT_MS, TimeUnit.MILLISECONDS)) {
            if (session.stopped()) return fallback
        }
        result
    } finally {
        session.prompt(false)
    }
}
