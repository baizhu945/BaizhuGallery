package org.fossify.gallery.extensions

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import org.fossify.commons.extensions.getSomeDocumentFile
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.isVideoFast
import org.fossify.commons.extensions.isGif
import org.fossify.commons.extensions.isRawFast
import org.fossify.commons.extensions.isSvg
import org.fossify.commons.extensions.recycleBinPath
import org.fossify.commons.models.FileDirItem
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.helpers.RECYCLE_BIN
import org.fossify.gallery.helpers.PendingNativeJournal
import org.fossify.gallery.helpers.TYPE_VIDEOS
import org.fossify.gallery.helpers.TYPE_GIFS
import org.fossify.gallery.helpers.TYPE_RAWS
import org.fossify.gallery.helpers.TYPE_SVGS
import org.fossify.gallery.helpers.TYPE_IMAGES
import org.fossify.gallery.models.Medium
import java.io.File

internal data class OwnedDelete(
    val source: File,
    val uri: Uri,
    val backup: File? = null,
    val destination: File? = null,
    val hidden: Boolean = false
)

internal fun SimpleActivity.prepareOwnedDelete(
    item: FileDirItem,
    recycle: Boolean,
    session: OwnedBulkSession,
    pending: MutableList<OwnedDelete>
) {
    val source = File(item.path)
    val hidden = config.userHiddenPaths.matchesMedia(source.path) { directory ->
        getDoesFilePathExist("${directory.trimEnd('/')}/.nomedia", config.OTGPath)
    }
    var backup: File? = null
    try {
        require(!item.isDirectory) { "Bulk deletion accepts media files only" }
        if (recycle && !source.path.startsWith("${recycleBinPath.trimEnd('/')}/")) {
            session.phase(R.string.bulk_recycling)
            val candidate = File(recycleBinPath, source.path.trimStart('/'))
            RecycleBackup(this, session).prepare(source, candidate)
            // Assign only after exclusive creation or verified unreferenced-byte reuse succeeds.
            backup = candidate
        }
        if (session.stopped()) return
        if (deleteOwnedPreparedSource(source, backup, hidden, session)) {
            session.counted(true)
        } else {
            val uri = findOwnedMediaUri(source.path)
            if (uri == null) {
                discardRetainedBackup(source, backup, session)
                session.counted(false)
            } else {
                pending.add(OwnedDelete(source, uri, backup, hidden = hidden))
            }
        }
    } catch (_: Exception) {
        // Keep verified backups: a durable local record may now be their only restoration metadata.
        session.counted(false)
    }
}

private class RecycleBackup(private val host: SimpleActivity, private val session: OwnedBulkSession) {
    fun prepare(source: File, candidate: File) {
        val root = File(host.recycleBinPath).canonicalPath.trimEnd('/') + "/"
        require(candidate.canonicalPath.startsWith(root) && !java.nio.file.Files.isSymbolicLink(candidate.toPath()))
        if (!candidate.exists()) {
            copyOwnedFile(source, candidate, session)
            return
        }
        // This is an authoritative worker-side Room query, never a UI/cache provenance guess.
        require(host.mediaDB.getDeletedMedia().none { it.path.equals("$RECYCLE_BIN${source.path}", true) }) {
            "An active recycle-bin entry already owns this backup"
        }
        reuseIdentical(source, candidate)
    }

    private fun reuseIdentical(source: File, candidate: File) {
        val modified = source.lastModified()
        val size = source.length()
        require(source.isFile && source.canRead() && candidate.isFile)
        check(ownedFilesEqual(source, candidate, session)) { "Existing backup differs; both files retained" }
        check(source.length() == size && source.lastModified() == modified && !session.stopped())
        // Only the private, unreferenced IDENTICAL backup may inherit the current source's mtime.
        // Its bytes are never truncated, replaced or deleted by migration.
        if (candidate.lastModified() != modified) check(candidate.setLastModified(modified))
        check(candidate.lastModified() == modified)
        check(ownedFilesEqual(source, candidate, session))
        check(source.length() == size && source.lastModified() == modified)
    }
}

private fun SimpleActivity.deleteOwnedPreparedSource(
    source: File,
    backup: File?,
    hidden: Boolean,
    session: OwnedBulkSession
): Boolean {
    val token = if (backup != null) {
        check(ownedFilesEqual(source, backup, session))
        checkNotNull(PendingNativeJournal.prepare(
            applicationContext, listOf(OwnedDelete(source, Uri.EMPTY, backup, hidden = hidden)), session, local = true
        ))
    } else {
        null
    }
    check(!session.stopped())
    val deleted = deleteOwnedSource(source)
    if (token != null) check(PendingNativeJournal.completeLocal(applicationContext, token, deleted))
    else if (deleted) recordOwnedDeletion(source, null)
    return deleted
}

internal fun Context.recordOwnedDeletion(source: File, backup: File?) {
    if (backup != null) {
        require(backup.isFile)
        val modified = backup.lastModified()
        val medium = Medium(
            id = null, name = source.name, path = "$RECYCLE_BIN${source.path}", parentPath = source.parent.orEmpty(),
            modified = modified, taken = recycledTaken(source, backup, modified),
            size = backup.length(), type = ownedDeletedMediaType(source.path),
            videoDuration = 0, isFavorite = favoritesDB.isFavorite(source.path),
            deletedTS = System.currentTimeMillis().coerceAtLeast(1L), mediaStoreId = 0L
        )
        // Insert the restoration row independently; never rename/clobber a replacement's source cache row.
        mediaDB.insert(medium)
        // The verified deletion must not leave a live cache ghost; never remove a replacement row.
        if (isOriginalPathVerifiedAbsent(source.path)) mediaDB.deleteMediumPath(source.path)
    } else {
        deleteDBPath(source.path, forgetUserHidden = true)
    }
}

private fun ownedDeletedMediaType(path: String): Int = when {
    path.isVideoFast() -> TYPE_VIDEOS
    path.isGif() -> TYPE_GIFS
    path.isRawFast() -> TYPE_RAWS
    path.isSvg() -> TYPE_SVGS
    else -> TYPE_IMAGES
}

internal fun SimpleActivity.deleteOwnedSource(source: File): Boolean {
    if (source.delete()) return true
    // Existing tree grants only. No worker-thread permission dialogs or native per-file prompts.
    return runCatching { getSomeDocumentFile(source.path)?.delete() == true }.getOrDefault(false)
}

internal fun SimpleActivity.findOwnedMediaUri(path: String): Uri? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val collection = MediaStore.Files.getContentUri("external")
    return runCatching {
        contentResolver.query(
            collection, arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE),
            "${MediaStore.MediaColumns.DATA} = ?", arrayOf(path), null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val typedCollection = ownedMediaCollection(cursor.getInt(1)) ?: return@use null
            ContentUris.withAppendedId(typedCollection, cursor.getLong(0))
        }
    }.getOrNull()
}

private fun ownedMediaCollection(type: Int): Uri? = when (type) {
    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    else -> null
}

internal fun SimpleActivity.resolveOwnedDeletes(pending: List<OwnedDelete>, session: OwnedBulkSession) {
    for (chunk in pending.chunked(1000)) {
        if (session.stopped()) break
        val unchanged = chunk.filter { deletion ->
            val retained = deletion.backup ?: deletion.destination
            val safe = retained == null || runCatching {
                ownedFilesEqual(deletion.source, retained, session)
            }.getOrDefault(false)
            if (!safe) session.counted(false)
            safe
        }
        if (unchanged.isNotEmpty()) requestOwnedDeleteChunk(unchanged, session)
    }
}

private fun SimpleActivity.requestOwnedDeleteChunk(chunk: List<OwnedDelete>, session: OwnedBulkSession) {
    val token = PendingNativeJournal.prepare(applicationContext, chunk, session)
    if (token == null) {
        chunk.forEach { session.counted(false) }
        session.cancelled = true
        return
    }
    session.phase(R.string.bulk_waiting_permission)
    val approved = awaitOwnedUi(session, false) { result ->
        requestBulkDelete(chunk.map { it.uri }, result)
    }
    if (session.closed()) return // Journal survives; owner loss is not a native refusal.
    val deleted = PendingNativeJournal.resolve(applicationContext, token, approved)
    chunk.forEach { deletion ->
        session.counted(deletion.source.path in deleted)
        deletion.destination?.let { session.changedPaths.add(it.path) }
    }
    if (!approved) session.cancelled = true
}
