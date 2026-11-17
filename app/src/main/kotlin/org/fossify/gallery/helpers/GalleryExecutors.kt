package org.fossify.gallery.helpers

import android.os.Process
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/** Shared pools bound active work, rather than creating a thread for every album. */
object GalleryExecutors {
    private fun threads(pool: String): ThreadFactory {
        val nextId = AtomicInteger()
        return ThreadFactory { work ->
            Thread({
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                } catch (ignored: SecurityException) {
                    // Still run on the bounded worker if the platform disallows priority changes.
                }
                work.run()
            }, "gallery-$pool-${nextId.incrementAndGet()}")
        }
    }

    val media: ExecutorService = Executors.newFixedThreadPool(2, threads("media"))
    val database: ExecutorService = Executors.newSingleThreadExecutor(threads("database"))
    val search: ExecutorService = Executors.newSingleThreadExecutor(threads("search"))
}
