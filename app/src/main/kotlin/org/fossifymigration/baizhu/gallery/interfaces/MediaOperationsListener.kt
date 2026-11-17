package org.fossifymigration.baizhu.gallery.interfaces

import org.fossify.commons.models.FileDirItem
import org.fossifymigration.baizhu.gallery.models.ThumbnailItem

interface MediaOperationsListener {
    fun refreshItems()

    fun tryDeleteFiles(fileDirItems: ArrayList<FileDirItem>, skipRecycleBin: Boolean)

    fun selectedPaths(paths: ArrayList<String>)

    fun updateMediaGridDecoration(media: ArrayList<ThumbnailItem>)
}
