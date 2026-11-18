package org.fossifymigration.baizhu.gallery.helpers

internal const val BULK_PROGRESS_STEPS = 1000

/** Byte progress is for the current file only. Unknown stages must not reuse its percentage. */
internal data class BulkProgressDisplay(
    val phase: String,
    val counts: String,
    val bytes: String,
    val value: Int?
) {
    fun waitingForInput(message: String) = copy(phase = message, bytes = "", value = null)
}

internal fun bulkProgressValue(determinate: Boolean, completed: Long, total: Long): Int? {
    if (!determinate || total <= 0) return null
    return (completed.coerceIn(0L, total).toDouble() / total * BULK_PROGRESS_STEPS).toInt()
}

internal fun needsBulkProgressDialog(started: Boolean, dismissed: Boolean, promptVisible: Boolean) =
    started && !dismissed && !promptVisible
