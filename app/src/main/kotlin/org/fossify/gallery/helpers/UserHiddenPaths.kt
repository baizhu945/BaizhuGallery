package org.fossify.gallery.helpers

internal fun normalizeUserHiddenPath(path: String): String = path.trimEnd('/').ifEmpty {
    if (path.startsWith('/')) "/" else ""
}

internal fun isUserHiddenPathWithin(path: String, root: String): Boolean =
    path == root || (root == "/" && path.startsWith('/')) || path.startsWith("$root/")

/** Explicit user intent only; no discovery, migration, or filesystem heuristics. */
class UserHiddenPaths(files: Set<String>, folders: Set<String>) {
    val files: Set<String> = files.map(::normalizeUserHiddenPath).filter { it.isNotEmpty() }.toSet()
    val folders: Set<String> = folders.map(::normalizeUserHiddenPath).filter { it.isNotEmpty() }.toSet()

    /** [hasNoMedia] receives a directory path, not the marker's path. */
    fun matches(path: String, hasNoMedia: (String) -> Boolean): Boolean {
        val normalized = normalizeUserHiddenPath(path)
        if (normalized in files) return true
        val root = folders.filter { isUserHiddenPathWithin(normalized, it) }.maxByOrNull { it.length } ?: return false
        return matchesFolderScope(normalized, root, hasNoMedia)
    }

    private fun matchesFolderScope(path: String, root: String, hasNoMedia: (String) -> Boolean): Boolean {
        if (path == root) return true

        val relative = path.removePrefix(root).trimStart('/')
        val segments = relative.split('/')
        var directory = root.trimEnd('/')
        for (segment in segments) {
            // A claimed parent must not implicitly claim independently hidden children.
            if (segment.startsWith('.')) return false
            directory += "/$segment"
            // For a file this probe simply returns false; it also handles directory queries.
            if (hasNoMedia(directory)) return false
        }
        return true
    }
}
