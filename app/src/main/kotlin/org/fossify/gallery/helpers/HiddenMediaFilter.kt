package org.fossify.gallery.helpers

import java.io.File

class HiddenMediaFilter(
    private val onlyHidden: Boolean,
    private val hiddenAccessAllowed: Boolean,
    private val hasNoMedia: (directory: String) -> Boolean,
    private val originalPath: (path: String) -> String = { it }
) {
    private val hiddenDirectories = HashMap<String, Boolean>()

    fun matches(path: String): Boolean {
        if (!onlyHidden) {
            return true
        }

        if (!hiddenAccessAllowed) {
            return false
        }

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
