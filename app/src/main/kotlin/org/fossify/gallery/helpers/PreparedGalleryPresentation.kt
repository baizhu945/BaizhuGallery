package org.fossify.gallery.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.MediaStore
import java.util.concurrent.atomic.AtomicLong
import org.fossify.gallery.extensions.config
import org.fossify.gallery.extensions.getHiddenMediaFilter
import org.fossify.gallery.extensions.prepareMediaSignatures
import org.fossify.gallery.models.Medium
import org.fossify.gallery.models.ThumbnailItem
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future

/** MediaStore changes invalidate reuse without a directory scan on every viewer return. */
class GalleryPresentationChangeWatch(context: Context) {
    private val resolver = context.applicationContext.contentResolver
    private val stopProtectionWatch = context.config.watchFolderProtectionChanges { markChanged() }
    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) { markChanged() }
    }

    init {
        try {
            resolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            resolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        } catch (_: SecurityException) {
            // Permission policy is independently checked before any snapshot publication.
        }
    }

    fun close() {
        resolver.unregisterContentObserver(observer)
        stopProtectionWatch()
    }

    companion object {
        private val changes = AtomicLong()
        val revision: Long get() = changes.get()
        fun markChanged() { changes.incrementAndGet() }
    }
}

/** Owns both the debounce and the interruptible worker, not merely its publication. */
class LatestGalleryPresentationWork {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    private var future: Future<*>? = null

    fun cancel() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
        future?.cancel(true)
        future = null
    }

    fun submit(executor: ExecutorService, delay: Long = 0, work: () -> Unit) {
        cancel()
        val request = Runnable { future = executor.submit { if (!Thread.currentThread().isInterrupted) work() } }
        pending = request
        handler.postDelayed(request, delay)
    }
}

const val GALLERY_QUERY_DEBOUNCE_MS = 150L

fun captureGalleryPresentationPolicy(context: Context, path: String): List<Any?> = with(context.config) {
    listOf(
        filterMedia, getFolderSorting(path), getFolderGrouping(path), includedFolders.toSet(), excludedFolders.toSet(),
        temporarilyShowExcluded, isFolderProtected(path), getFolderProtectionType(path), getFolderProtectionHash(path)
    )
}

fun Medium.copyGalleryPresentationMedium(gridPosition: Int = this.gridPosition): Medium =
    copy(gridPosition = gridPosition).also { it.thumbnailModified = thumbnailModified }

/** Only call on a worker. Grouping mutates gridPosition, so never group shared media. */
fun prepareGalleryPresentation(
    context: Context, items: List<ThumbnailItem>, path: String, query: String? = null
): ArrayList<ThumbnailItem>? {
    val filter = context.getHiddenMediaFilter()
    val detached = ArrayList<Medium>()
    for (item in items) {
        if (Thread.currentThread().isInterrupted) return null
        if (item is Medium && filter.matches(item.path) && matchesPresentationQuery(item, query)) {
            detached.add(item.copyGalleryPresentationMedium())
        }
    }
    if (query != null) detached.sortBy { !it.name.startsWith(query, true) }
    context.prepareMediaSignatures(detached)
    if (Thread.currentThread().isInterrupted) return null
    return MediaFetcher(context).groupMedia(detached, path)
}

private fun matchesPresentationQuery(item: Medium, query: String?) = query == null || item.name.contains(query, true)
