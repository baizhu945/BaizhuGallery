package org.fossifymigration.baizhu.gallery.helpers

fun main() {
    check(bulkProgressValue(true, 17L, 100L) == 170)
    check(bulkProgressValue(true, 5_000_000_000L, 10_000_000_000L) == 500)
    check(bulkProgressValue(true, -1L, 50L) == 0)
    check(bulkProgressValue(true, 51L, 50L) == BULK_PROGRESS_STEPS)
    check(bulkProgressValue(false, 50L, 50L) == null)
    check(bulkProgressValue(true, 0L, 0L) == null)

    val running = BulkProgressDisplay("Copying", "Processed 1/3 · Failed: 0", "4 MB / 8 MB", 500)
    val waiting = running.waitingForInput("Waiting for confirmation")
    check(waiting == BulkProgressDisplay("Waiting for confirmation", running.counts, "", null))
    check(running.value == 500 && running.bytes.isNotEmpty())

    // Stop/resume must not reset the active snapshot, resurrect a dismissed window,
    // or let an operation dialog replace an outstanding conflict/permission prompt.
    for (started in listOf(false, true)) {
        for (dismissed in listOf(false, true)) {
            for (prompt in listOf(false, true)) {
                check(needsBulkProgressDialog(started, dismissed, prompt) == (started && !dismissed && !prompt))
            }
        }
    }
    println("Bulk progress display: 16 checks passed")
}
