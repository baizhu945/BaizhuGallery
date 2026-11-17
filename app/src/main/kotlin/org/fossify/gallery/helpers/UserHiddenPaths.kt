package org.fossify.gallery.helpers

import java.util.Collections

internal fun normalizeUserHiddenPath(path: String): String = path.trimEnd('/').ifEmpty {
    if (path.startsWith('/')) "/" else ""
}

internal fun isUserHiddenPathWithin(path: String, root: String): Boolean =
    path == root || (root == "/" && path.startsWith('/')) || path.startsWith("$root/")

/** Explicit user intent only; no discovery, migration, or filesystem heuristics. */
class UserHiddenPaths(files: Set<String>, folders: Set<String>) {
    val files: Set<String> = immutablePaths(files)
    val folders: Set<String> = immutablePaths(folders)

    private fun immutablePaths(paths: Set<String>): Set<String> = Collections.unmodifiableSet(
        paths.mapTo(HashSet()) { normalizeUserHiddenPath(it) }.apply { remove("") }
    )

    /** [hasNoMedia] receives a directory path, not the marker's path. */
    fun matches(path: String, hasNoMedia: (String) -> Boolean): Boolean =
        matches(path, hasNoMedia, media = false)

    /** File-only query: a filename cannot contain a .nomedia child. */
    fun matchesMedia(path: String, hasNoMedia: (String) -> Boolean): Boolean =
        matches(path, hasNoMedia, media = true)

    private fun matches(path: String, hasNoMedia: (String) -> Boolean, media: Boolean): Boolean {
        val normalized = normalizeUserHiddenPath(path)
        if (normalized in files) return true
        // Walk deepest-first: work depends on path depth, not registry size.
        var ancestor = normalized
        while (true) {
            if (ancestor in folders) return matchesFolderScope(normalized, ancestor, hasNoMedia, media)
            val separator = ancestor.lastIndexOf('/')
            if (separator < 0 || ancestor == "/") return false
            ancestor = if (separator == 0) "/" else ancestor.substring(0, separator)
        }
    }

    private fun matchesFolderScope(
        path: String,
        root: String,
        hasNoMedia: (String) -> Boolean,
        media: Boolean
    ): Boolean {
        if (path == root) return true

        val relative = path.removePrefix(root).trimStart('/')
        val segments = relative.split('/')
        var directory = root.trimEnd('/')
        for ((index, segment) in segments.withIndex()) {
            // A claimed parent must not implicitly claim independently hidden children.
            if (segment.startsWith('.')) return false
            directory += "/$segment"
            // General queries can be directories; media queries never probe the filename.
            if ((!media || index < segments.lastIndex) && hasNoMedia(directory)) return false
        }
        return true
    }
}
