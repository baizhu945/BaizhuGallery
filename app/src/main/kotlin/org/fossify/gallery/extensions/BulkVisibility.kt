package org.fossify.gallery.extensions

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.deleteFromMediaStore
import org.fossify.commons.extensions.handleLockedFolderOpening
import org.fossify.commons.extensions.needsStupidWritePermissions
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.NOMEDIA
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.databases.GalleryDatabase
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

private const val CALLBACK_TIMEOUT_MINUTES = 2L
private const val CALLBACK_POLL_MILLIS = 100L

// One owner at a time, including permission callbacks and the final scan. Never fan out renames.
private val visibilityMutations = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "gallery-visibility").apply { isDaemon = true }
}

fun BaseSimpleActivity.runVisibilityBatch(paths: List<String>, hide: Boolean, onFinished: () -> Unit) {
    startVisibilityBatch(paths, hide, folders = false, onFinished = onFinished)
}

fun BaseSimpleActivity.runFolderVisibilityBatch(paths: List<String>, hide: Boolean, onFinished: () -> Unit) {
    startVisibilityBatch(paths, hide, folders = true, onFinished = onFinished)
}

private fun BaseSimpleActivity.startVisibilityBatch(
    paths: List<String>,
    hide: Boolean,
    folders: Boolean,
    onFinished: () -> Unit
) {
    // Also safe for callers that are not already on the UI thread.
    runOnUiThread {
        val host = this as? SimpleActivity ?: return@runOnUiThread
        if (isFinishing || isDestroyed) return@runOnUiThread
        val progress = host.beginOperation(if (hide) R.string.bulk_hiding else R.string.bulk_unhiding, paths.size)
            ?: return@runOnUiThread
        val owner = VisibilityOwner(
            activity = this,
            closed = { progress.isClosed || isFinishing || isDestroyed },
            update = { completed, failed -> progress.update(completed, failed) },
            phase = { progress.phase(R.string.bulk_refreshing) },
            finish = { progress.finish() },
            prompt = { visible -> progress.showPrompt(visible) },
            onFinished = onFinished
        )
        try {
            visibilityMutations.execute { owner.run(paths, hide, folders) }
        } catch (_: Exception) {
            progress.finish()
            if (!isFinishing && !isDestroyed) toast(R.string.bulk_operation_failed)
        }
    }
}

private fun visibilitySourceParent(path: String, hide: Boolean, folders: Boolean): String? =
    if (visibilityDestination(path, hide) == null) null else if (folders) path else File(path).parent

/** Lifecycle closure stops only at item boundaries; it never interrupts a write or its metadata commit. */
private class VisibilityOwner(
    private val activity: BaseSimpleActivity,
    private val closed: () -> Boolean,
    private val update: (Int, Int) -> Unit,
    private val phase: () -> Unit,
    private val finish: () -> Unit,
    prompt: (Boolean) -> Unit,
    private val onFinished: () -> Unit
) {
    private val counts = VisibilityBatchCounts()
    private val changes = VisibilityChanges(activity)
    private val callbacks = VisibilityCallbacks(activity, closed, prompt)
    private val folderMutations = VisibilityFolders(activity, callbacks, changes.scanPaths)
    private var incomplete = false

    fun run(paths: List<String>, hide: Boolean, folders: Boolean) {
        try {
            val gates = collectPermissions(paths, hide, folders)
            if (!incomplete) mutateItems(paths, hide, folders, gates)
        } catch (_: Exception) {
            incomplete = true
        } finally {
            complete()
        }
    }

    private fun collectPermissions(paths: List<String>, hide: Boolean, folders: Boolean): Map<String, Boolean> {
        // All security prompts precede writes and are serialized, including denied parents.
        val gates = HashMap<String, Boolean>()
        for (path in paths) {
            if (closed() || incomplete) break
            val parent = visibilitySourceParent(path, hide, folders)
            if (parent != null && !gates.containsKey(parent)) requestParentPermission(parent, gates)
        }
        return gates
    }

    private fun requestParentPermission(parent: String, gates: MutableMap<String, Boolean>) {
        val allowed = callbacks.requestOnUi<Boolean> { reply ->
            activity.handleLockedFolderOpening(parent) { reply(it) }
        }
        if (allowed == null) incomplete = true else gates[parent] = allowed
    }

    private fun mutateItems(paths: List<String>, hide: Boolean, folders: Boolean, gates: Map<String, Boolean>) {
        val keepLastModified = activity.config.keepLastModified
        for (path in paths) {
            if (closed() || incomplete) break
            val parent = visibilitySourceParent(path, hide, folders)
            when {
                parent == null -> settle(false) // Blank and virtual rows are intentionally skipped.
                gates[parent] != true -> settle(true)
                else -> mutateItem(path, hide, folders, keepLastModified)
            }
        }
    }

    private fun mutateItem(path: String, hide: Boolean, folders: Boolean, keepLastModified: Boolean) {
        val result = try {
            if (folders) folderMutations.mutate(path, hide) else moveVisibilityFile(path, hide, keepLastModified)
        } catch (_: Exception) {
            VisibilityFileResult(path, failed = true)
        }
        if (result.verified) changes.recordVerified(result, hide, folders)
        settle(result.failed)
        // Commons' SAF/rename helpers can omit callbacks or replace a racing target. An
        // unsupported/unknown result stops remaining work; a proposed path is never success.
        if (result.unsupported) incomplete = true
    }

    private fun complete() {
        // Even after destruction, commit verified writes. Closing the owner must not leave
        // an already moved file with metadata pointing at its old name.
        try {
            changes.commitMetadata()
        } catch (_: Exception) {
            incomplete = true
        }
        try {
            refreshBatch()
        } catch (_: Exception) {
            incomplete = true
        } finally {
            finishOnUi()
        }
    }

    private fun refreshBatch() {
        if (changes.scanPaths.isEmpty()) return
        activity.runOnUiThread { if (!closed()) phase() }
        val scanned = callbacks.awaitCallback<Boolean>(stopOnClose = false) { reply ->
            activity.applicationContext.rescanPaths(changes.scanPaths.toList()) { reply(true) }
        }
        if (scanned != true) incomplete = true
    }

    private fun finishOnUi() {
        activity.runOnUiThread {
            val alive = !closed()
            try {
                // The controller owns orientation restoration and lifecycle cleanup.
                finish()
            } finally {
                if (alive) notifyCompletion()
            }
        }
    }

    private fun notifyCompletion() {
        if (counts.failed > 0 || incomplete) activity.toast(R.string.bulk_operation_partial)
        onFinished()
    }

    private fun settle(failure: Boolean) {
        counts.settle(failure)
        val completed = counts.completed
        val failed = counts.failed
        if (!closed()) update(completed, failed)
    }
}

private class VisibilityChanges(private val activity: BaseSimpleActivity) {
    private val renames = ArrayList<Pair<String, String>>()
    private val added = LinkedHashSet<String>()
    private val removed = LinkedHashSet<String>()
    val scanPaths = LinkedHashSet<String>()

    fun recordVerified(result: VisibilityFileResult, hide: Boolean, folders: Boolean) {
        if (!folders) recordFileClaims(result, hide)
        scanPaths.add(result.source)
        scanPaths.add(result.destination)
    }

    private fun recordFileClaims(result: VisibilityFileResult, hide: Boolean) {
        removed.add(result.source)
        if (hide) added.add(result.destination) else removed.add(result.destination)
        if (result.renamed) renames.add(result.source to result.destination)
    }

    fun commitMetadata() {
        val context = activity.applicationContext
        if (added.isNotEmpty() || removed.isNotEmpty()) {
            context.config.updateUserHiddenFiles(added, removed)
        }
        if (renames.isNotEmpty()) {
            val database = GalleryDatabase.getInstance(context)
            database.runInTransaction {
                for ((old, new) in renames) {
                    val file = File(new)
                    database.MediumDao().updateMedium(
                        oldPath = old, newParentPath = file.parent.orEmpty(), newFilename = file.name, newFullPath = new
                    )
                    database.FavoritesDao().updateFavorite(file.name, new, file.parent.orEmpty(), old)
                }
            }
        }
    }
}

private class VisibilityFolders(
    private val activity: BaseSimpleActivity,
    private val callbacks: VisibilityCallbacks,
    private val scanPaths: MutableSet<String>
) {
    fun mutate(path: String, hide: Boolean): VisibilityFileResult {
        val directory = File(path)
        val directAccess = isWritableVisibilityDirectory(directory) && !activity.needsStupidWritePermissions(path)
        if (!directAccess) return VisibilityFileResult(path, failed = true, unsupported = true)
        val marker = File(directory, NOMEDIA)
        return try {
            if (hide) hideFolder(path, marker) else unhideFolder(path, marker)
        } catch (_: Exception) {
            VisibilityFileResult(path, failed = true)
        }
    }

    private fun hideFolder(path: String, marker: File): VisibilityFileResult {
        // Unlike addNoMedia, this owns MediaStore work serially rather than spawning a worker.
        if (!Files.exists(marker.toPath(), NOFOLLOW_LINKS)) Files.createFile(marker.toPath())
        if (!Files.isRegularFile(marker.toPath(), NOFOLLOW_LINKS)) return VisibilityFileResult(path, failed = true)
        scanPaths.add(marker.path)
        activity.config.addUserHiddenFolders(listOf(path))
        activity.addNoMediaIntoMediaStore(marker.path)
        return VisibilityFileResult(path, verified = true)
    }

    private fun unhideFolder(path: String, marker: File): VisibilityFileResult {
        // Unlike removeNoMedia, do not rescan each folder or invoke legacy SAF deletion.
        if (Files.exists(marker.toPath(), NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(marker.toPath(), NOFOLLOW_LINKS)) return VisibilityFileResult(path, failed = true)
            Files.delete(marker.toPath())
        }
        if (Files.exists(marker.toPath(), NOFOLLOW_LINKS)) return VisibilityFileResult(path, failed = true)
        scanPaths.add(marker.path)
        activity.config.removeUserHiddenFolders(listOf(path))
        val deletionCompleted = deleteMarkerRow(marker.path)
        // Disk deletion is verified; unknown provider completion is partial and stops the batch.
        // Never undo the deletion or remove explicit child-file claims.
        return VisibilityFileResult(
            path, verified = true, failed = !deletionCompleted, unsupported = !deletionCompleted
        )
    }

    private fun deleteMarkerRow(path: String): Boolean {
        // Without this, getNoMediaFoldersSync can retain the stale row. Any requested scan is
        // folded into the single final batch scan, including the now-missing marker itself.
        val completed = try {
            callbacks.awaitCallback<Boolean>(stopOnClose = false) { reply ->
                activity.applicationContext.deleteFromMediaStore(path) { reply(true) }
            }
        } catch (_: Exception) {
            null
        }
        return completed == true
    }
}

private class VisibilityCallbacks(
    private val activity: BaseSimpleActivity,
    private val closed: () -> Boolean,
    private val prompt: (Boolean) -> Unit
) {
    fun <T : Any> requestOnUi(request: ((T) -> Unit) -> Unit): T? = try {
        awaitCallback(stopOnClose = true) { reply ->
            activity.runOnUiThread { dispatchRequest(request, reply) }
        }
    } finally {
        prompt(false)
    }

    private fun <T : Any> dispatchRequest(request: ((T) -> Unit) -> Unit, reply: (T) -> Unit) {
        if (closed()) return
        try {
            prompt(true)
            request(reply)
        } catch (_: Exception) {
            // Await's deadline also handles providers that silently omit callbacks.
        }
    }

    /** A missing callback is unknown, not success. No subsequent mutations are started. */
    fun <T : Any> awaitCallback(stopOnClose: Boolean, request: ((T) -> Unit) -> Unit): T? {
        val latch = CountDownLatch(1)
        val value = AtomicReference<T?>()
        val accepting = AtomicBoolean(true)
        val deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(CALLBACK_TIMEOUT_MINUTES)
        try {
            request { result ->
                if (accepting.compareAndSet(true, false)) {
                    value.set(result)
                    latch.countDown()
                }
            }
            while (!latch.await(CALLBACK_POLL_MILLIS, TimeUnit.MILLISECONDS)) {
                if ((stopOnClose && closed()) || System.nanoTime() >= deadline) return null
            }
            return value.get()
        } finally {
            accepting.set(false)
        }
    }
}
