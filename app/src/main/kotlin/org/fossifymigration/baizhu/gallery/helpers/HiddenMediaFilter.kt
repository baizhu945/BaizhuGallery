package org.fossifymigration.baizhu.gallery.helpers

import java.io.File

class HiddenMediaFilter(
    private val onlyHidden: Boolean,
    private val hiddenAccessAllowed: Boolean,
    private val hasNoMedia: (directory: String) -> Boolean,
    private val originalPath: (path: String) -> String = { it },
    private val includeOtherHidden: Boolean = false,
    private val isUserHidden: (String) -> Boolean = { false }
) {
    private val hiddenDirectories = HashMap<String, Boolean>()

    fun matches(path: String): Boolean {
        val pathToCheck = originalPath(path)
        if (!isPhysicallyHidden(pathToCheck)) {
            return !onlyHidden
        }

        return hiddenAccessAllowed && (includeOtherHidden || isUserHidden(pathToCheck))
    }

    fun isHidden(path: String): Boolean = isPhysicallyHidden(originalPath(path))

    private fun isPhysicallyHidden(path: String): Boolean =
        path.startsWith('.') || path.contains("/.") || isDirectoryHidden(File(path).parent)

    private fun isDirectoryHidden(path: String?): Boolean {
        if (path == null) {
            return false
        }

        return hiddenDirectories.getOrPut(path) {
            hasNoMedia(path) || isDirectoryHidden(File(path).parent)
        }
    }
}
