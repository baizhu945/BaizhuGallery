package org.fossifymigration.baizhu.gallery.extensions

import android.content.Context
import android.os.Process
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.rescanPaths
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.toast
import org.fossify.commons.models.FileDirItem
import org.fossifymigration.baizhu.gallery.R
import org.fossifymigration.baizhu.gallery.activities.SimpleActivity
import org.fossifymigration.baizhu.gallery.helpers.GalleryPresentationChangeWatch
import java.io.File
import java.util.concurrent.Executors

// Serialization includes permission results: mutations cannot race a pending native delete.
internal val ownedMediaMutations = Executors.newSingleThreadExecutor { task ->
    Thread({
        runCatching { Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND) }
        task.run()
    }, "gallery-owned-media-mutations")
}

private fun SimpleActivity.startOwnedBulk(
    title: Int,
    paths: List<String>,
    onFinished: () -> Unit,
    work: (OwnedBulkSession) -> Unit
) {
    runOnUiThread {
        val progress = beginOperation(title, paths.size)
        if (progress == null) {
            if (!isDestroyed) onFinished()
            return@runOnUiThread
        }
        val session = OwnedBulkSession(
            closed = { progress.isClosed },
            report = { completed, failed -> progress.update(completed, failed) },
            phase = { message -> progress.phase(message) },
            bytes = { completed, total -> progress.bytes(completed, total) },
            finish = { progress.finish() },
            prompt = { visible -> progress.showPrompt(visible) }
        )
        GalleryPresentationChangeWatch.markChanged()
        ownedMediaMutations.execute {
            try {
                session.phase(R.string.bulk_preparing)
                work(session)
            } catch (_: Exception) {
                session.failed++
            } finally {
                finishOwnedBulk(session, paths, onFinished)
            }
        }
    }
}

private fun SimpleActivity.finishOwnedBulk(
    session: OwnedBulkSession,
    sources: List<String>,
    onFinished: () -> Unit
) {
    try {
        session.phase(R.string.bulk_refreshing)
        val paths = (sources + session.changedPaths).distinct()
        rescanPaths(ArrayList(paths))
        (paths.mapNotNull { File(it).parent } + session.changedDirectories).distinct().forEach { parent ->
            runCatching { updateDirectoryPath(parent) }
        }
    } finally {
        GalleryPresentationChangeWatch.markChanged()
        session.finish()
        runOnUiThread {
            if (!isDestroyed) {
                if (session.failed > 0 || session.processed < sources.size) {
                    toast(R.string.bulk_operation_partial)
                }
                onFinished()
            }
        }
    }
}

/** No optimistic adapter removals. The callback requests a reload of actual state. */
fun BaseSimpleActivity.deleteMediaWithProgress(
    items: List<FileDirItem>,
    skipRecycleBin: Boolean,
    onFinished: () -> Unit
) {
    val activity = this as? SimpleActivity
    if (activity == null) {
        runOnUiThread {
            if (!isDestroyed) {
                toast(R.string.bulk_operation_failed)
                onFinished()
            }
        }
        return
    }
    val selected = items.distinctBy { it.path }.toList()
    val recycle = config.useRecycleBin && !skipRecycleBin
    activity.startOwnedBulk(R.string.bulk_deleting, selected.map { it.path }, onFinished) { session ->
        val pending = ArrayList<OwnedDelete>()
        for (item in selected) {
            if (session.stopped()) break
            activity.prepareOwnedDelete(item, recycle, session, pending)
        }
        activity.resolveOwnedDeletes(pending, session)
    }
}

/** Verified mappings only. Opaque Commons transfers are deliberately not a fallback. */
fun BaseSimpleActivity.transferMediaWithProgress(
    items: List<FileDirItem>,
    destination: String,
    copy: Boolean,
    onFinished: (String) -> Unit
) {
    val activity = this as? SimpleActivity
    if (activity == null) {
        runOnUiThread {
            if (!isDestroyed) {
                toast(R.string.bulk_operation_failed)
                onFinished(destination)
            }
        }
        return
    }
    activity.startOwnedTransfer(items, destination, copy, onFinished)
}

private data class OwnedTransfer(val source: File, val hidden: Boolean, val directory: Boolean)

private fun SimpleActivity.startOwnedTransfer(
    items: List<FileDirItem>,
    destination: String,
    copy: Boolean,
    onFinished: (String) -> Unit
) {
    val selected = items.distinctBy { it.path }.toList()
    val title = if (copy) R.string.bulk_copying else R.string.bulk_moving
    startOwnedBulk(title, selected.map { it.path }, { onFinished(destination) }) { session ->
        val registry = config.userHiddenPaths
        // Snapshot all origins before the first mutation, including independent marker boundaries.
        val origins = selected.map { item ->
            check(!session.stopped())
            OwnedTransfer(File(item.path), registry.matchesMedia(item.path) { directory ->
                getDoesFilePathExist("${directory.trimEnd('/')}/.nomedia", config.OTGPath)
            }, item.isDirectory)
        }
        transferOwnedFiles(origins, File(destination), copy, session)
    }
}

private fun SimpleActivity.transferOwnedFiles(
    origins: List<OwnedTransfer>,
    directory: File,
    copy: Boolean,
    session: OwnedBulkSession
) {
    val pending = ArrayList<OwnedDelete>()
    session.changedDirectories.add(directory.path)
    var conflict: OwnedConflict? = null
    for (origin in origins) {
        if (session.stopped()) break
        val sameFolderMove = !copy && origin.source.parentFile?.canonicalFile == directory.canonicalFile
        val proposed = File(directory, origin.source.name)
        val target = if (sameFolderMove) {
            session.counted(false)
            null
        } else if (proposed.exists()) {
            val choice = conflict ?: chooseOwnedConflict(session).also { conflict = it }
            resolveOwnedTarget(proposed, choice, session)
        } else {
            proposed
        }
        if (target != null) transferOwnedFile(origin, target, copy, session, pending)
    }
    resolveOwnedDeletes(pending, session)
}

private fun SimpleActivity.chooseOwnedConflict(session: OwnedBulkSession): OwnedConflict =
    awaitOwnedUi(session, OwnedConflict.CANCEL) { result ->
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.bulk_conflict_title)
            .setMessage(R.string.bulk_conflict_description)
            .setPositiveButton(R.string.bulk_keep_both) { _, _ -> result(OwnedConflict.KEEP_BOTH) }
            .setNeutralButton(R.string.bulk_skip) { _, _ -> result(OwnedConflict.SKIP) }
            .setNegativeButton(R.string.bulk_cancel_remaining) { _, _ -> result(OwnedConflict.CANCEL) }
            .setOnCancelListener { result(OwnedConflict.CANCEL) }
            .create()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_DESTROY) dialog.dismiss()
        }
        lifecycle.addObserver(observer)
        dialog.setOnDismissListener { lifecycle.removeObserver(observer) }
        dialog.show()
    }

private fun SimpleActivity.transferOwnedFile(
    origin: OwnedTransfer,
    target: File,
    copy: Boolean,
    session: OwnedBulkSession,
    pending: MutableList<OwnedDelete>
) {
    try {
        // Tree-only/virtual destinations and directory traversal need a separate engine.
        // Reject visibly rather than inventing Commons' conflict-resolved target paths.
        require(!origin.directory)
        if (!origin.source.isFile || target.parentFile?.isDirectory != true) {
            transferOwnedDocument(origin.source, target, origin.hidden, copy, session, pending) {
                chooseOwnedConflict(session)
            }
            return
        }
        if (!copy && tryOwnedFastMove(origin.source, target, origin.hidden, session)) return
        copyOwnedFile(origin.source, target, session)
        session.changedPaths.add(target.path)
        if (origin.hidden) config.updateUserHiddenFiles(added = listOf(target.path), removed = emptyList())
        if (copy) {
            // Copies do not inherit favorites or move the original DB row.
            session.counted(true)
        } else {
            finishOwnedTransfer(origin, target, session, pending)
        }
    } catch (_: Exception) {
        session.counted(false)
    }
}

private fun SimpleActivity.finishOwnedTransfer(
    origin: OwnedTransfer,
    target: File,
    session: OwnedBulkSession,
    pending: MutableList<OwnedDelete>
) {
    if (session.stopped()) return // A completed destination is retained as a copy.
    check(ownedFilesEqual(origin.source, target, session))
    if (deleteOwnedSource(origin.source)) {
        recordOwnedMove(origin.source, target, origin.hidden)
        session.counted(true)
    } else {
        val uri = findOwnedMediaUri(origin.source.path)
        if (uri == null) session.counted(false)
        else pending.add(OwnedDelete(origin.source, uri, destination = target, hidden = origin.hidden))
    }
}

internal fun Context.recordOwnedMove(source: File, target: File, hidden: Boolean) {
    mediaDB.updateMedium(
        oldPath = source.path, newParentPath = target.parent.orEmpty(),
        newFilename = target.name, newFullPath = target.path
    )
    favoritesDB.updateFavorite(target.name, target.path, target.parent.orEmpty(), source.path)
    if (hidden) {
        config.updateUserHiddenFiles(added = listOf(target.path), removed = listOf(source.path))
    }
}
