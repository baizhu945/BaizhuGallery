package org.fossifymigration.baizhu.gallery.helpers

import android.content.pm.ActivityInfo
import android.content.DialogInterface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.formatSize
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossifymigration.baizhu.gallery.R
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/** Activity-owned work with independent notification feedback while the window is unavailable. */
class BulkOperationProgress private constructor(
    activity: BaseSimpleActivity,
    private val title: Int,
    private val total: Int?
) {
    companion object {
        private const val SHOW_DELAY_MS = 150L
        private const val UPDATE_INTERVAL_MS = 80L
        private var current = WeakReference<BulkOperationProgress>(null)

        @Synchronized
        fun begin(activity: BaseSimpleActivity, title: Int, total: Int?): BulkOperationProgress? {
            if (activity.isFinishing || activity.isDestroyed) return null
            current.get()?.takeUnless { it.isClosed }?.let { operation ->
                operation.handler.post {
                    operation.dismissed = false
                    operation.enqueueRender()
                }
                return null
            }
            return BulkOperationProgress(activity, title, total).also { current = WeakReference(it) }
        }

        @Synchronized
        fun isRunning() = current.get()?.isClosed == false
    }

    private val owner = WeakReference(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val notification = BulkProgressNotification(activity.applicationContext, handler, title)
    private val closed = AtomicBoolean(false)
    private val originalOrientation = activity.requestedOrientation
    private var dialog: AlertDialog? = null
    private var bar: ProgressBar? = null
    private var description: TextView? = null
    private var phaseText = activity.getString(R.string.bulk_preparing)
    private var completed = 0
    private var failed = 0
    private var determinate = false
    private var preparing = true
    private var bytesDone: Long? = null
    private var bytesTotal: Long? = null
    private var dismissed = false
    @Volatile
    private var promptVisible = false
    private var lastRender = 0L
    private val earliestShow = SystemClock.uptimeMillis() + SHOW_DELAY_MS
    private val render = Runnable { renderState() }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> enqueueRender()
            Lifecycle.Event.ON_STOP -> detachDialog()
            Lifecycle.Event.ON_DESTROY -> finish()
            else -> Unit
        }
    }

    val isClosed: Boolean get() = closed.get() || owner.get() == null

    init {
        activity.lifecycle.addObserver(lifecycleObserver)
        // Legacy storage permission callbacks belong to this activity. Restore its prior policy afterwards.
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
        GalleryPresentationChangeWatch.markChanged()
        enqueueRender()
    }

    @Synchronized
    fun update(completed: Int, failed: Int = 0) {
        this.completed = completed.coerceAtLeast(0).let { value -> total?.let(value::coerceAtMost) ?: value }
        if (preparing) {
            phaseText = owner.get()?.getString(title).orEmpty()
            preparing = false
        }
        this.failed = failed.coerceIn(0, this.completed)
        determinate = total != null && total > 0
        bytesDone = null
        bytesTotal = null
        enqueueRender()
    }

    @Synchronized
    fun phase(message: Int, determinate: Boolean = false) {
        phaseText = owner.get()?.getString(message).orEmpty()
        preparing = message == R.string.bulk_preparing
        this.determinate = determinate && total != null && total > 0
        bytesDone = null
        bytesTotal = null
        enqueueRender()
    }

    @Synchronized
    fun bytes(completed: Long, total: Long) {
        if (preparing) {
            phaseText = owner.get()?.getString(title).orEmpty()
            preparing = false
        }
        bytesDone = completed.coerceAtLeast(0).coerceAtMost(total.coerceAtLeast(0))
        bytesTotal = total.coerceAtLeast(0)
        determinate = total > 0
        enqueueRender()
    }

    fun finish() {
        if (!closed.compareAndSet(false, true)) return
        val cleanup = Runnable {
            handler.removeCallbacks(render)
            notification.clear()
            detachDialog()
            owner.get()?.let {
                it.lifecycle.removeObserver(lifecycleObserver)
                if (!it.isDestroyed) it.requestedOrientation = originalOrientation
            }
            synchronized(Companion) {
                if (current.get() === this) current.clear()
            }
            GalleryPresentationChangeWatch.markChanged()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run() else handler.post(cleanup)
    }

    fun showPrompt(visible: Boolean) {
        val update = Runnable {
            promptVisible = visible
            if (visible) detachDialog()
            enqueueRender()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) update.run() else handler.post(update)
    }

    private fun enqueueRender() {
        if (isClosed) return
        handler.removeCallbacks(render)
        val nextRender = maxOf(earliestShow, lastRender + UPDATE_INTERVAL_MS)
        handler.postDelayed(render, (nextRender - SystemClock.uptimeMillis()).coerceAtLeast(0L))
    }

    @Synchronized
    private fun renderState() {
        val activity = owner.get() ?: return
        if (isClosed || activity.isFinishing || activity.isDestroyed) return
        lastRender = SystemClock.uptimeMillis()
        val display = display(activity)
        val backgroundDisplay = if (promptVisible) {
            display.waitingForInput(activity.getString(R.string.bulk_waiting_input))
        } else display
        notification.update(backgroundDisplay)
        val started = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (!needsBulkProgressDialog(started, dismissed, promptVisible)) return
        if (dialog == null) createDialog(activity)
        bar?.isIndeterminate = display.value == null
        display.value?.let { bar?.progress = it }
        val note = activity.getString(R.string.bulk_progress_note)
        val hint = if (notification.canShow()) {
            R.string.bulk_progress_notification_hint
        } else R.string.bulk_progress_notification_disabled
        val minimizedHint = activity.getString(hint)
        description?.text = listOf(display.phase, display.counts, display.bytes, note, minimizedHint)
            .filter(String::isNotEmpty).joinToString("\n")
    }

    private fun display(activity: BaseSimpleActivity): BulkProgressDisplay {
        val count = total?.let { activity.getString(R.string.bulk_items_processed, completed, it, failed) }.orEmpty()
        val byteText = bytesTotal?.let {
            activity.getString(R.string.bulk_current_file_bytes, (bytesDone ?: 0L).formatSize(), it.formatSize())
        }.orEmpty()
        val value = bulkProgressValue(determinate, bytesDone ?: completed.toLong(), bytesTotal ?: total?.toLong() ?: 0L)
        return BulkProgressDisplay(phaseText, count, byteText, value)
    }

    private fun detachDialog() {
        dialog?.dismiss()
        dialog = null
        bar = null
        description = null
    }

    private fun createDialog(activity: BaseSimpleActivity) {
        val builder = activity.getAlertDialogBuilder()
            .setNeutralButton(R.string.bulk_hide_progress) { _, _ ->
                dismissed = true
                detachDialog()
            }
        val themedContext = builder.context
        val palette = progressPalette(activity, builder)
        val spacing = activity.resources.getDimensionPixelSize(org.fossify.commons.R.dimen.activity_margin)
        val content = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(spacing, spacing, spacing, spacing)
        }
        description = TextView(themedContext).also { content.addView(it) }
        bar = ProgressBar(themedContext, null, android.R.attr.progressBarStyleHorizontal).also {
            it.max = BULK_PROGRESS_STEPS
            content.addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = spacing })
        }
        val scroll = ScrollView(themedContext).apply { addView(content) }
        activity.setupDialogStuff(scroll, builder, titleId = title, cancelOnTouchOutside = false) {
            description?.setTextColor(palette.text)
            bar?.let(palette::apply)
            it.getButton(DialogInterface.BUTTON_NEUTRAL)?.setTextColor(palette.text)
            dialog = it
            it.setCancelable(false)
            it.setCanceledOnTouchOutside(false)
        }
    }
}
