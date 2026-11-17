package org.fossifymigration.baizhu.gallery.helpers

import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getParentPath
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossifymigration.baizhu.gallery.extensions.addPathToDB
import org.fossifymigration.baizhu.gallery.extensions.updateDirectoryPath
import org.fossifymigration.baizhu.gallery.interfaces.DirectoryOperationsListener
import org.fossifymigration.baizhu.gallery.interfaces.MediaOperationsListener
import java.lang.ref.WeakReference

/** Coalesces MediaStore bursts instead of rescanning an album after every renamed photo. */
class MediaChangeObserver(activity: BaseSimpleActivity) {
    companion object {
        private const val DELAY_MS = 250L
        private const val MAX_PENDING_URIS = 256
    }

    private val owner = WeakReference(activity)
    private val context = activity.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val pending = LinkedHashSet<Uri>()
    private var bulkChanged = false
    private var closed = false
    private val flush = Runnable { flushChanges() }
    val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            GalleryPresentationChangeWatch.markChanged()
            synchronized(pending) {
                if (closed) return
                if (BulkOperationProgress.isRunning()) bulkChanged = true
                else if (uri != null) {
                    if (pending.size < MAX_PENDING_URIS) pending.add(uri) else bulkChanged = true
                }
            }
            handler.removeCallbacks(flush)
            handler.postDelayed(flush, DELAY_MS)
        }
    }

    init {
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> handler.post(flush)
                Lifecycle.Event.ON_DESTROY -> synchronized(pending) {
                    closed = true
                    pending.clear()
                    handler.removeCallbacks(flush)
                }
                else -> Unit
            }
        })
    }

    private fun flushChanges() {
        val activity = owner.get() ?: return
        if (closed || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (BulkOperationProgress.isRunning()) {
            handler.postDelayed(flush, DELAY_MS)
            return
        }
        val (uris, refresh) = synchronized(pending) {
            val result = pending.toList() to bulkChanged
            pending.clear()
            bulkChanged = false
            result
        }
        if (uris.isNotEmpty()) GalleryExecutors.database.execute {
            val paths = uris.mapNotNull { uri ->
                try { context.getRealPathFromURI(uri) } catch (ignored: Exception) { null }
            }.toSet()
            paths.map { it.getParentPath() }.toSet().forEach { context.updateDirectoryPath(it) }
            paths.forEach { context.addPathToDB(it) }
        }
        if (refresh) {
            when (activity) {
                is MediaOperationsListener -> activity.refreshItems()
                is DirectoryOperationsListener -> activity.refreshItems()
            }
        }
    }
}
