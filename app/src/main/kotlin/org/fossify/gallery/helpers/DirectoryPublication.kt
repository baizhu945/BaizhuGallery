package org.fossify.gallery.helpers

import android.os.Handler
import android.os.Looper

/** At most one projection is running and one (latest) projection is waiting. */
class DirectoryPublication {
    companion object {
        private const val PUBLICATION_DELAY_MILLIS = 100L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var pending: (() -> Unit)? = null
    private var running = false
    private var scheduled = false
    private var pendingImmediate = false
    private val launch: Runnable = Runnable {
        val work = synchronized(this) {
            scheduled = false
            if (running) null else pending?.also {
                pending = null
                pendingImmediate = false
                running = true
            }
        }
        if (work != null) {
            GalleryExecutors.media.execute {
                try {
                    work()
                } finally {
                    synchronized(this) {
                        running = false
                        if (pending != null && !scheduled) {
                            scheduled = true
                            handler.postDelayed(launchPending, if (pendingImmediate) 0L else PUBLICATION_DELAY_MILLIS)
                        }
                    }
                }
            }
        }
    }
    private val launchPending: Runnable = Runnable { launch.run() }

    @Synchronized
    fun submit(immediate: Boolean = false, work: () -> Unit) {
        pending = work
        pendingImmediate = pendingImmediate || immediate
        if (immediate && scheduled) {
            handler.removeCallbacks(launch)
            handler.removeCallbacks(launchPending)
            scheduled = false
        }
        if (!running && !scheduled) {
            scheduled = true
            handler.postDelayed(launch, if (immediate) 0L else PUBLICATION_DELAY_MILLIS)
        }
    }

    @Synchronized
    fun clear() {
        pending = null
        pendingImmediate = false
        handler.removeCallbacks(launch)
        handler.removeCallbacks(launchPending)
        scheduled = false
    }
}
