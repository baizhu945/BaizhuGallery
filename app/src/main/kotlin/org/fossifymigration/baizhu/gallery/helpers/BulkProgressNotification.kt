package org.fossifymigration.baizhu.gallery.helpers

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.SystemClock
import org.fossifymigration.baizhu.gallery.R

/** No paths, filenames, thumbnails, intents into hidden media, or lock-screen content. */
internal class BulkProgressNotification(
    private val context: Context,
    private val handler: Handler,
    private val title: Int
) {
    private val manager = context.getSystemService(NotificationManager::class.java)
    private var latest: BulkProgressDisplay? = null
    private var posted: BulkProgressDisplay? = null
    private var lastPost = 0L
    private val render = Runnable { postLatest() }

    private fun postLatest() {
        val display = latest ?: return
        if (!canShow()) {
            clear()
            return
        }
        val details = listOf(display.phase, display.counts, display.bytes).filter(String::isNotEmpty).joinToString("\n")
        val notification = Notification.Builder(context, BULK_PROGRESS_CHANNEL)
            .setSmallIcon(R.drawable.ic_bulk_progress_vector)
            .setContentTitle(context.getString(title))
            .setContentText(details.replace('\n', ' '))
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setProgress(BULK_PROGRESS_STEPS, display.value ?: 0, display.value == null)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            // A process killed by Android cannot clear its notification. Age it out instead
            // of leaving a stale claim that an operation is still running.
            .setTimeoutAfter(NOTIFICATION_EXPIRY_MS)
            .build()
        // A user can revoke permission between checking it and posting an update.
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            ) {
                manager.notify(BULK_PROGRESS_NOTIFICATION_ID, notification)
                posted = display
                lastPost = SystemClock.uptimeMillis()
                handler.postDelayed(render, NOTIFICATION_HEARTBEAT_MS)
            } else clear()
        } catch (_: SecurityException) {
            clear()
        }
    }

    fun canShow() = manager.areNotificationsEnabled() &&
        manager.getNotificationChannel(BULK_PROGRESS_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE

    fun update(display: BulkProgressDisplay) {
        if (!canShow()) {
            clear()
            return
        }
        latest = display
        handler.removeCallbacks(render)
        val interval = if (posted == display) NOTIFICATION_HEARTBEAT_MS else NOTIFICATION_INTERVAL_MS
        val delay = (lastPost + interval - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        handler.postDelayed(render, delay)
    }

    fun clear() {
        handler.removeCallbacks(render)
        latest = null
        if (posted != null) manager.cancel(BULK_PROGRESS_NOTIFICATION_ID)
        posted = null
    }

    init {
        ensureBulkProgressChannel(context)
    }
}

internal const val BULK_PROGRESS_CHANNEL = "bulk_operation_progress"
private const val BULK_PROGRESS_NOTIFICATION_ID = 1051
private const val NOTIFICATION_INTERVAL_MS = 750L
private const val NOTIFICATION_HEARTBEAT_MS = 15_000L
private const val NOTIFICATION_EXPIRY_MS = 45_000L

internal fun ensureBulkProgressChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(
        BULK_PROGRESS_CHANNEL,
        context.getString(R.string.bulk_progress_notifications),
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = context.getString(R.string.bulk_progress_notification_description)
        setShowBadge(false)
    })
}
