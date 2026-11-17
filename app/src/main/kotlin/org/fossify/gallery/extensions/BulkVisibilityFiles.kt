package org.fossify.gallery.extensions

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS

/** A result describes observed disk state, never a proposed rename. */
internal data class VisibilityFileResult(
    val source: String,
    val destination: String = source,
    val verified: Boolean = false,
    val failed: Boolean = false,
    val unsupported: Boolean = false
) {
    val renamed: Boolean get() = verified && source != destination
}

internal fun visibilityDestination(path: String, hide: Boolean): String? {
    if (path.isBlank() || !path.startsWith('/') || path.contains("://")) return null
    val file = File(path)
    val name = file.name
    if (name.isBlank() || name == "." || name == "..") return null
    val newName = if (hide) {
        if (name.startsWith('.')) name else ".$name"
    } else name.trimStart('.')
    if (newName.isEmpty()) return null
    return File(file.parentFile ?: return null, newName).path
}

internal fun isWritableVisibilityDirectory(directory: File): Boolean =
    directory.isDirectory && directory.canRead() && directory.canWrite()

/** No REPLACE_EXISTING, including for dotfiles and dangling destination symlinks. */
internal fun moveVisibilityFile(path: String, hide: Boolean, keepLastModified: Boolean): VisibilityFileResult {
    val target = visibilityDestination(path, hide) ?: return VisibilityFileResult(path)
    val source = File(path)
    val destination = File(target)
    return try {
        visibilityPreflight(source, destination, hide) ?: performVisibilityMove(source, destination, keepLastModified)
    } catch (_: Exception) {
        VisibilityFileResult(path, failed = true)
    }
}

private fun visibilityPreflight(source: File, destination: File, hide: Boolean): VisibilityFileResult? {
    val path = source.path
    val parent = source.parentFile ?: return VisibilityFileResult(path, failed = true)
    val unchanged = source == destination
    val missingOrdinaryName = unchanged && !hide && !Files.exists(source.toPath(), NOFOLLOW_LINKS)
    return when {
        !isWritableVisibilityDirectory(parent) -> VisibilityFileResult(path, failed = true, unsupported = true)
        // An ordinary name needs no rename; remove a stale exact user claim.
        missingOrdinaryName -> VisibilityFileResult(path, verified = true)
        !Files.isRegularFile(source.toPath(), NOFOLLOW_LINKS) -> VisibilityFileResult(path, failed = true)
        unchanged -> VisibilityFileResult(path, verified = true)
        Files.exists(destination.toPath(), NOFOLLOW_LINKS) -> VisibilityFileResult(path, failed = true)
        else -> null
    }
}

private fun performVisibilityMove(source: File, destination: File, keepLastModified: Boolean): VisibilityFileResult {
    val modified = source.lastModified()
    Files.move(source.toPath(), destination.toPath())
    val verified = !Files.exists(source.toPath(), NOFOLLOW_LINKS) &&
        Files.isRegularFile(destination.toPath(), NOFOLLOW_LINKS)
    return if (verified) {
        val timestampUpdated = updateVisibilityTimestamp(destination, modified, keepLastModified)
        VisibilityFileResult(source.path, destination.path, verified = true, failed = !timestampUpdated)
    } else {
        VisibilityFileResult(source.path, failed = true)
    }
}

private fun updateVisibilityTimestamp(destination: File, modified: Long, keepLastModified: Boolean): Boolean =
    try {
        // Timestamp failure must not discard a verified rename or roll back the move.
        val desired = if (keepLastModified) modified else System.currentTimeMillis()
        (keepLastModified && desired == 0L) || destination.lastModified() == desired ||
            destination.setLastModified(desired)
    } catch (_: Exception) {
        false
    }

/** Completed means settled, not succeeded. Unknown/unsupported work stops the batch. */
internal class VisibilityBatchCounts {
    var completed = 0
        private set
    var failed = 0
        private set

    fun settle(failure: Boolean) {
        completed++
        if (failure) failed++
    }
}
