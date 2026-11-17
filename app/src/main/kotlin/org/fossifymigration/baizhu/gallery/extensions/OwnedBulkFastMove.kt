package org.fossifymigration.baizhu.gallery.extensions

import android.system.Os
import org.fossifymigration.baizhu.gallery.activities.SimpleActivity
import java.io.File
import java.io.IOException
import java.nio.file.Files

/** Returns false only when the original is retained and buffered-copy fallback is safe. */
internal fun SimpleActivity.tryOwnedFastMove(
    source: File,
    target: File,
    hidden: Boolean,
    session: OwnedBulkSession
): Boolean {
    if (!source.isFile || session.stopped()) return false
    val sameDevice = runCatching {
        Os.stat(source.path).st_dev == Os.stat(target.parentFile!!.path).st_dev
    }.getOrDefault(false)
    if (!sameDevice) return false
    val expected = source.length()
    try {
        // ATOMIC_MOVE is intentionally absent: it permits replacement on some providers.
        Files.move(source.toPath(), target.toPath())
    } catch (_: IOException) {
        if (source.isFile && !target.exists()) return false
        session.counted(false)
        session.changedPaths.add(target.path)
        return true
    }
    session.changedPaths.add(target.path)
    val verified = target.isFile && target.length() == expected && !source.exists()
    if (verified) recordOwnedMove(source, target, hidden)
    session.counted(verified)
    return true
}
