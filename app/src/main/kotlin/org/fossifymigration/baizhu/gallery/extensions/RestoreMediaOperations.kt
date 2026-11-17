package org.fossifymigration.baizhu.gallery.extensions

import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.getDocumentSdk30
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getPicturesDirectoryPath
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.getSomeDocumentFile
import org.fossify.commons.extensions.hasProperStoredFirstParentUri
import org.fossify.commons.extensions.humanizePath
import org.fossify.commons.extensions.isInDownloadDir
import org.fossify.commons.extensions.isRestrictedWithSAFSdk30
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.updateLastModified
import org.fossifymigration.baizhu.gallery.R
import org.fossifymigration.baizhu.gallery.activities.SimpleActivity
import org.fossifymigration.baizhu.gallery.helpers.GalleryPresentationChangeWatch
import org.fossifymigration.baizhu.gallery.helpers.RECYCLE_BIN
import org.fossifymigration.baizhu.gallery.models.DateTaken
import org.fossifymigration.baizhu.gallery.models.Medium
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val RESTORE_COPY_BUFFER_SIZE = 128 * 1024
private const val RESTORE_SCAN_TIMEOUT_SECONDS = 60L
private const val RESTORE_SCAN_WAIT_MS = 200L
private const val RESTORE_PERMISSION_TIMEOUT_MS = 300_000L

/** Owns permission continuation, stream closure, scanner completion and final publication. */
internal fun BaseSimpleActivity.restoreMediaWithProgress(paths: List<String>, onFinished: () -> Unit) {
    runOnUiThread {
        val host = this as? SimpleActivity
        if (host == null) {
            onFinished()
            return@runOnUiThread
        }
        val progress = beginOperation(org.fossify.commons.R.string.restore_selected_files, paths.size)
        if (progress == null) {
            if (!isDestroyed) onFinished()
            return@runOnUiThread
        }
        val session = OwnedBulkSession(
            closed = { progress.isClosed }, report = { done, failed -> progress.update(done, failed) },
            phase = { progress.phase(it) }, bytes = { done, total -> progress.bytes(done, total) },
            finish = { progress.finish() }, prompt = { progress.showPrompt(it) }
        )
        ownedMediaMutations.execute { host.runRestore(paths.distinct(), session, onFinished) }
    }
}

private fun SimpleActivity.runRestore(paths: List<String>, session: OwnedBulkSession, onFinished: () -> Unit) {
    try {
        runCatching { restoreSelection(paths, session) }.onFailure { error ->
            session.failed++
            if (!session.closed()) showErrorToast(error.toString())
        }
    } finally {
        // Do NOT updateDirectoryPath or launch another scanner here: both can overwrite settled dates.
        GalleryPresentationChangeWatch.markChanged()
        session.finish()
        runOnUiThread {
            if (!isDestroyed && !isFinishing) {
                if (session.failed > 0 || session.processed < paths.size) toast(R.string.bulk_operation_partial)
                onFinished()
            }
        }
    }
}

private fun SimpleActivity.restoreSelection(paths: List<String>, session: OwnedBulkSession) {
    session.phase(R.string.bulk_preparing)
    // Snapshot bin metadata BEFORE any copy/scanner can invalidate a selected row.
    val snapshot = mediaDB.getDeletedMedia().associateBy { it.path }
    val keepDates = config.keepLastModified
    for (source in paths) {
        if (session.stopped()) break
        val result = runCatching {
            val original = source.removePrefix(recycleBinPath)
            val row = checkNotNull(snapshot["$RECYCLE_BIN$original"]) {
                "Missing recycle-bin metadata: $original"
            }.copy()
            restoreOne(File(source), original, row, keepDates, session)
        }
        result.onFailure { if (!session.closed()) showErrorToast(it.toString()) }
        session.counted(result.isSuccess)
    }
}

private fun SimpleActivity.restoreOne(
    source: File, original: String, row: Medium, keepDates: Boolean, session: OwnedBulkSession
) {
    require(source.isFile && source.length() == row.size) { "Recycle-bin bytes unavailable: $original" }
    val hidden = config.userHiddenPaths.matches(original) { directory ->
        getDoesFilePathExist("${directory.trimEnd('/')}/.nomedia", config.OTGPath)
    }
    val destination = restoreDestination(original)
    session.phase(R.string.bulk_waiting_permission)
    check(awaitRestorePermission(session) { result -> handleSAFDialog(destination.path, result) }) {
        "Storage approval denied or incomplete; recycle-bin bytes and metadata retained"
    }
    check(awaitRestorePermission(session) { result ->
        // Go straight to DocumentsUI: Commons' dismissible rationale has no cancellation callback.
        handleSAFDialogSdk30(destination.path, showRationale = false, callback = result)
    }) {
        "Storage approval denied or incomplete; recycle-bin bytes and metadata retained"
    }
    session.phase(R.string.bulk_copying)
    val restored = RestoreCopy(this, session).copy(source, destination)
    if (hidden) config.addUserHiddenFiles(listOf(restored.path))
    session.phase(R.string.bulk_refreshing)
    scanRestoredFile(restored.path, session)
    if (keepDates) settleRestoreDates(restored, row, session)
    val modified = if (keepDates) row.modified else File(restored.path).lastModified()
    val taken = if (keepDates) row.taken else modified
    dateTakensDB.insertAll(listOf(DateTaken(
        null, restored.path, File(restored.path).name, File(restored.path).parent.orEmpty(), taken,
        (System.currentTimeMillis() / 1000).toInt(), modified
    )))
    if (row.isFavorite) favoritesDB.insert(getFavoriteFromPath(restored.path))
    // Insert all original fields, with the ACTUAL created identity. Never rename a live original row.
    val live = row.copy(
        id = null, name = File(restored.path).name, path = restored.path,
        parentPath = File(restored.path).parent.orEmpty(), modified = modified, taken = taken,
        deletedTS = 0, mediaStoreId = 0
    )
    mediaDB.insert(live)
    RestoreCommit(this, session).complete(source, restored, live, row.path, keepDates)
}

private fun SimpleActivity.awaitRestorePermission(
    session: OwnedBulkSession, request: ((Boolean) -> Unit) -> Unit
): Boolean {
    val handler = Handler(Looper.getMainLooper())
    var expiry: Runnable? = null
    return try {
        awaitOwnedUi(session, false) { result ->
            val answered = AtomicBoolean(false)
            val complete: (Boolean) -> Unit = { granted ->
                if (answered.compareAndSet(false, true)) result(granted)
            }
            val timeout = Runnable { complete(false) }
            expiry = timeout
            handler.postDelayed(timeout, RESTORE_PERMISSION_TIMEOUT_MS)
            request { granted ->
                handler.removeCallbacks(timeout)
                complete(granted)
            }
        }
    } finally {
        expiry?.let { handler.removeCallbacks(it) }
    }
}

private fun SimpleActivity.restoreDestination(original: String): File {
    val parent = File(original).parent.orEmpty()
    return if (isRestrictedWithSAFSdk30(parent) && !isInDownloadDir(parent)) {
        val pictures = getPicturesDirectoryPath(original)
        toast(getString(R.string.restore_to_path, humanizePath(pictures)))
        File(pictures, File(original).name)
    } else {
        File(original)
    }
}

private data class RestoredIdentity(val path: String, val document: DocumentFile?)

private class RestoreCommit(private val host: SimpleActivity, private val session: OwnedBulkSession) {
    fun complete(source: File, restored: RestoredIdentity, live: Medium, binKey: String, keepDates: Boolean) {
        val root = File(host.recycleBinPath).canonicalPath.trimEnd('/') + "/"
        val expected = File(host.recycleBinPath, binKey.removePrefix(RECYCLE_BIN).trimStart('/'))
        require(source.canonicalPath.startsWith(root) && source.canonicalPath == expected.canonicalPath)
        require(!Files.isSymbolicLink(source.toPath()))
        val identity = checkNotNull(Files.readAttributes(source.toPath(), BasicFileAttributes::class.java).fileKey())
        verifyLive(live)
        // Metadata repair/scanning has finished. Re-prove byte identity immediately before retirement.
        val document = restored.document ?: DocumentFile.fromFile(File(restored.path))
        val hash = host.hashOwnedDocument(DocumentFile.fromFile(source), session)
        host.verifyOwnedDocumentCopy(DocumentFile.fromFile(source), document, hash, session)
        if (keepDates) check(File(restored.path).lastModified() == live.modified) {
            "Restored filesystem date changed; recycle-bin bytes and metadata retained"
        }
        verifyLive(live)
        check(!session.stopped())
        check(Files.readAttributes(source.toPath(), BasicFileAttributes::class.java).fileKey() == identity)
        // Never remove the authoritative bin row first: failure to delete must remain retryable.
        check(source.delete()) { "Restored copy verified, but private backup cleanup failed; bin entry retained" }
        host.mediaDB.deleteMediumPath(binKey)
    }

    private fun verifyLive(expected: Medium) {
        val actual = host.mediaDB.getMediaFromPath(expected.parentPath).singleOrNull { it.path == expected.path }
        check(actual?.copy(id = null, gridPosition = 0) == expected.copy(id = null, gridPosition = 0)) {
            "Restored library metadata was not retained; recycle-bin bytes and metadata retained"
        }
    }
}

private class RestoreCopy(private val host: SimpleActivity, private val session: OwnedBulkSession) {
    fun copy(source: File, proposed: File): RestoredIdentity {
        val originalHash = host.hashOwnedDocument(DocumentFile.fromFile(source), session)
        val target = nextTarget(proposed)
        check(!session.stopped())
        val output = try {
            target.parentFile?.mkdirs()
            Files.newOutputStream(target.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)
        } catch (error: FileAlreadyExistsException) {
            // A concurrent winner is NOT a permission failure; never retry that target via a provider.
            throw IOException("Destination appeared during restore: ${target.path}", error)
        } catch (_: IOException) {
            null
        }
        if (output == null) return createDocument(source, proposed, originalHash)
        // Capture our inode while the exclusive-create descriptor is still open. Unknown identity
        // means retaining a partial destination rather than risking deletion of a replacement.
        val identity = runCatching { Files.readAttributes(target.toPath(), BasicFileAttributes::class.java).fileKey() }
            .getOrNull()
        var verified = false
        try {
            output.use { stream(source, it) }
            check(ownedFilesEqual(source, target, session)) { "Restored bytes differ: ${target.path}" }
            check(retainedSource(source, originalHash)) { "Recycle-bin source changed during restore" }
            verified = true
            return RestoredIdentity(target.path, null)
        } finally {
            if (!verified) discardPartialFile(source, target, identity, originalHash)
        }
    }

    private fun discardPartialFile(source: File, target: File, identity: Any?, hash: ByteArray) {
        runCatching {
            val current = Files.readAttributes(target.toPath(), BasicFileAttributes::class.java).fileKey()
            if (identity != null && identity == current && retainedSource(source, hash)) target.delete()
        }
    }

    private fun nextTarget(proposed: File): File {
        val extension = proposed.extension.let { if (it.isEmpty()) "" else ".$it" }
        val stem = proposed.name.removeSuffix(extension)
        var target = proposed
        var index = 1
        while (host.getDoesFilePathExist(target.path)) {
            check(!session.stopped())
            target = File(proposed.parentFile, "$stem (${index++})$extension")
        }
        return target
    }

    private fun createDocument(source: File, proposed: File, hash: ByteArray): RestoredIdentity {
        val parent = checkNotNull(resolveDocument(proposed.parent.orEmpty())) { "Destination grant unavailable" }
        require(parent.isDirectory && parent.canWrite())
        val previous = parent.listFiles().mapTo(HashSet()) { it.uri }
        val target = nextTarget(proposed)
        check(!session.stopped())
        val created = checkNotNull(parent.createFile(source.path.getMimeType(), target.name)) {
            "Document creation failed"
        }
        require(created.uri !in previous) { "Provider returned an existing document; not overwritten" }
        var verified = false
        try {
            val actual = checkNotNull(host.getRealPathFromURI(created.uri)) { "Cannot identify restored document" }
            require(actual.startsWith('/') && resolveDocument(actual)?.uri == created.uri) {
                "Cannot verify restored document identity: ${created.uri}"
            }
            val output = checkNotNull(host.contentResolver.openOutputStream(created.uri, "w")) { "Output unavailable" }
            output.use { stream(source, it) }
            // ALL streams close before verification, scanning, or timestamps.
            host.verifyOwnedDocumentCopy(DocumentFile.fromFile(source), created, hash, session)
            verified = true
            return RestoredIdentity(actual, created)
        } finally {
            // Never clean an existing URI or an uncertain source. Completed copies survive date failures.
            if (!verified) runCatching { if (retainedSource(source, hash)) created.delete() }
        }
    }

    private fun resolveDocument(path: String): DocumentFile? = with(host) {
        if (hasProperStoredFirstParentUri(path)) getDocumentSdk30(path) else getSomeDocumentFile(path)
    }

    private fun stream(source: File, output: OutputStream) {
        val expected = source.length()
        source.inputStream().use { input ->
            val buffer = ByteArray(RESTORE_COPY_BUFFER_SIZE)
            var written = 0L
            while (true) {
                check(!session.stopped())
                val count = input.read(buffer)
                if (count < 0) break
                check(!session.stopped())
                output.write(buffer, 0, count)
                written += count
                session.copiedBytes(written, expected)
            }
            output.flush()
            check(written == expected && source.length() == expected) { "Recycle-bin source size changed" }
        }
    }

    private fun retainedSource(source: File, hash: ByteArray): Boolean {
        if (!source.isFile) return false
        val size = source.length()
        val modified = source.lastModified()
        val digest = MessageDigest.getInstance("SHA-256")
        // Cleanup must positively re-read the retained source even if the owner has just closed.
        source.inputStream().use { input ->
            val buffer = ByteArray(RESTORE_COPY_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return source.isFile && source.length() == size && source.lastModified() == modified &&
            hash.contentEquals(digest.digest())
    }
}

private fun SimpleActivity.scanRestoredFile(path: String, session: OwnedBulkSession): Uri {
    val latch = CountDownLatch(1)
    var scanned: Uri? = null
    MediaScannerConnection.scanFile(applicationContext, arrayOf(path), null) { _, uri ->
        scanned = uri
        latch.countDown()
    }
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(RESTORE_SCAN_TIMEOUT_SECONDS)
    while (!latch.await(RESTORE_SCAN_WAIT_MS, TimeUnit.MILLISECONDS)) {
        check(!session.stopped() && System.nanoTime() < deadline) {
            "Restore scan did not finish: $path; backup retained"
        }
    }
    val uri = checkNotNull(scanned) { "Scanner did not identify restored file: $path" }
    contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
        check(cursor.moveToFirst() && cursor.getString(0) == path) { "Scanner identity mismatch: $path" }
    } ?: error("Scanner identity unavailable: $path")
    return uri
}

private fun SimpleActivity.settleRestoreDates(
    restored: RestoredIdentity, row: Medium, session: OwnedBulkSession
) {
    // Commons first tries the filesystem and then MediaStore DATE_MODIFIED. This MUST be after
    // provider close AND the explicit scan callback, never inside an output-stream use/finally.
    updateLastModified(restored.path, row.modified)
    if (File(restored.path).lastModified() != row.modified && restored.document != null) {
        // A SAF descriptor can permit utimens even where traversal via the raw path is denied.
        // Reopen read-only: no second write-close/rescan is introduced by metadata repair.
        contentResolver.openFileDescriptor(restored.document.uri, "r")?.use { descriptor ->
            File("/proc/self/fd/${descriptor.fd}").setLastModified(row.modified)
        }
    }
    // DATE_MODIFIED is derived from the file and may ignore direct writes. Re-index only
    // after all writers closed and the actual filesystem timestamp has been restored.
    val indexed = scanRestoredFile(restored.path, session)
    val values = ContentValues().apply {
        put(MediaStore.Images.ImageColumns.DATE_TAKEN, row.taken)
    }
    // A provider can return zero for an unchanged or derived field. Only the read-back
    // postconditions below establish success, not the update count (or lack of an exception).
    runCatching { contentResolver.update(indexed, values, null, null) }
    // Do not claim preserved filesystem timestamps solely because a Room/MediaStore write succeeded.
    check(File(restored.path).lastModified() == row.modified) {
        "Provider cannot preserve filesystem modification time: ${restored.path}; bytes copied, bin metadata retained"
    }
    contentResolver.query(
        indexed, arrayOf(MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.Images.ImageColumns.DATE_TAKEN),
        null, null, null
    )?.use { cursor ->
        check(cursor.moveToFirst()) { "Cannot identify restored dates: ${restored.path}" }
        val taken = cursor.getLong(1)
        // MediaStore keeps DATE_TAKEN null for images without capture metadata. Gallery's
        // taken == modified value is then a display fallback, not an original provider date.
        val fallbackSecondMatches = row.taken / 1000 == row.modified / 1000
        val takenMatches = taken == row.taken || (taken == 0L && fallbackSecondMatches)
        check(cursor.getLong(0) == row.modified / 1000 && takenMatches) {
            "Provider did not retain original dates: ${restored.path}; bin metadata retained"
        }
    } ?: error("Cannot verify restored dates: ${restored.path}; bin metadata retained")
}
