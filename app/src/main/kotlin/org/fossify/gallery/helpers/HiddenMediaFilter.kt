package org.fossify.gallery.helpers

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
        if (!isHidden(path)) {
            return !onlyHidden
        }

        return hiddenAccessAllowed && (includeOtherHidden || isUserHidden(pathToCheck))
    }

    fun isHidden(path: String): Boolean {
        val pathToCheck = originalPath(path)
        return pathToCheck.startsWith('.') || pathToCheck.contains("/.") || isDirectoryHidden(File(pathToCheck).parent)
    }

    private fun isDirectoryHidden(path: String?): Boolean {
        if (path == null) {
            return false
        }

        return hiddenDirectories.getOrPut(path) {
            hasNoMedia(path) || isDirectoryHidden(File(path).parent)
        }
    }
}
