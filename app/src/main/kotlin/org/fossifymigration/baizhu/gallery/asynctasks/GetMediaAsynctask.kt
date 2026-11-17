package org.fossifymigration.baizhu.gallery.asynctasks

import android.content.Context
import android.os.AsyncTask
import org.fossify.commons.helpers.FAVORITES
import org.fossify.commons.helpers.SORT_BY_DATE_MODIFIED
import org.fossify.commons.helpers.SORT_BY_DATE_TAKEN
import org.fossify.commons.helpers.SORT_BY_SIZE
import org.fossifymigration.baizhu.gallery.extensions.config
import org.fossifymigration.baizhu.gallery.extensions.getFavoritePaths
import org.fossifymigration.baizhu.gallery.extensions.getHiddenMediaState
import org.fossifymigration.baizhu.gallery.extensions.prepareMediaSignatures
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossifymigration.baizhu.gallery.helpers.*
import org.fossifymigration.baizhu.gallery.models.Medium
import org.fossifymigration.baizhu.gallery.models.ThumbnailItem

class GetMediaAsynctask(
    val context: Context, val mPath: String, val isPickImage: Boolean = false, val isPickVideo: Boolean = false,
    val showAll: Boolean, val applyHiddenFilter: Boolean = true, val callback: (media: ArrayList<ThumbnailItem>) -> Unit
) :
    AsyncTask<Void, Void, ArrayList<ThumbnailItem>>() {
    val requestedHiddenMediaState = context.getHiddenMediaState()
    private val requestPath = if (showAll) SHOW_ALL else mPath
    private fun presentationPolicy() = captureGalleryPresentationPolicy(context, requestPath)
    private val requestedPolicy = presentationPolicy()
    private val mediaFetcher = MediaFetcher(context, applyHiddenFilter)

    override fun doInBackground(vararg params: Void): ArrayList<ThumbnailItem> {
        val pathToUse = if (showAll) SHOW_ALL else mPath
        val folderGrouping = context.config.getFolderGrouping(pathToUse)
        val folderSorting = context.config.getFolderSorting(pathToUse)
        val getProperDateTaken = needsDateTaken(folderSorting, folderGrouping)

        val getProperLastModified = needsLastModified(folderSorting, folderGrouping)

        val getProperFileSize = folderSorting and SORT_BY_SIZE != 0
        val favoritePaths = context.getFavoritePaths()
        val getVideoDurations = context.config.showThumbnailVideoDuration
        val lastModifieds = if (getProperLastModified) mediaFetcher.getLastModifieds() else HashMap()
        val dateTakens = if (getProperDateTaken) mediaFetcher.getDateTakens() else HashMap()

        val media = if (showAll) {
            val foldersToScan = mediaFetcher.getFoldersToScan().filter {
                it != RECYCLE_BIN && it != FAVORITES && !context.config.isFolderProtected(it)
            }
            val media = ArrayList<Medium>()
            val android11Files = if (isRPlus() && !isExternalStorageManager()) {
                mediaFetcher.getAndroid11FolderMedia(
                    isPickImage, isPickVideo, favoritePaths, false, getProperDateTaken, dateTakens
                )
            } else null
            foldersToScan.forEach {
                if (isCancelled || Thread.currentThread().isInterrupted) return arrayListOf()
                val newMedia = mediaFetcher.getFilesFrom(
                    it, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize,
                    favoritePaths, getVideoDurations, lastModifieds, dateTakens, android11Files
                )
                media.addAll(newMedia)
            }

            mediaFetcher.sortMedia(media, context.config.getFolderSorting(SHOW_ALL))
            media
        } else {
            mediaFetcher.getFilesFrom(
                mPath, isPickImage, isPickVideo, getProperDateTaken, getProperLastModified, getProperFileSize, favoritePaths,
                getVideoDurations, lastModifieds, dateTakens, null
            )
        }

        if (isCancelled || !isRequestCurrent()) return arrayListOf()
        val detached = media.mapTo(ArrayList()) { it.copyGalleryPresentationMedium() }
        context.prepareMediaSignatures(detached)
        return mediaFetcher.groupMedia(detached, pathToUse)
    }

    override fun onPostExecute(media: ArrayList<ThumbnailItem>) {
        super.onPostExecute(media)
        // Fail closed; callers retain their request tags and can restart stale requests.
        callback(if (isRequestCurrent()) media else arrayListOf())
    }

    private fun isRequestCurrent() = requestedHiddenMediaState == context.getHiddenMediaState() &&
        requestedPolicy == presentationPolicy()

    private fun needsDateTaken(sorting: Int, grouping: Int) = sorting and SORT_BY_DATE_TAKEN != 0 ||
        grouping and GROUP_BY_DATE_TAKEN_DAILY != 0 || grouping and GROUP_BY_DATE_TAKEN_MONTHLY != 0

    private fun needsLastModified(sorting: Int, grouping: Int) = sorting and SORT_BY_DATE_MODIFIED != 0 ||
        grouping and GROUP_BY_LAST_MODIFIED_DAILY != 0 || grouping and GROUP_BY_LAST_MODIFIED_MONTHLY != 0

    fun stopFetching() {
        mediaFetcher.shouldStop = true
        cancel(true)
    }
}
