package org.fossify.gallery.extensions

import androidx.documentfile.provider.DocumentFile
import org.fossify.commons.extensions.getFileInputStreamSync
import org.fossify.commons.extensions.getMimeType
import org.fossify.commons.extensions.getRealPathFromURI
import org.fossify.commons.extensions.getSomeDocumentFile
import org.fossify.gallery.activities.SimpleActivity
import java.io.File
import java.io.InputStream
import java.io.OutputStream

private const val OWNED_DOCUMENT_BUFFER_SIZE = 128 * 1024

/** Existing grants only. All provider I/O runs on the owned mutation worker. */
internal fun SimpleActivity.transferOwnedDocument(
    source: File,
    proposed: File,
    hidden: Boolean,
    copy: Boolean,
    session: OwnedBulkSession,
    pending: MutableList<OwnedDelete>,
    conflict: () -> OwnedConflict
) {
    val parent = proposed.parentFile ?: error("Missing destination")
    val directory = resolveOwnedDocument(parent)
    require(directory.isDirectory && directory.canWrite())
    val sourceDocument = resolveOwnedDocument(source)
    require(sourceDocument.isFile && sourceDocument.canRead())
    val name = chooseOwnedDocumentName(directory, proposed.name, session, conflict) ?: return
    check(!session.stopped())
    val previous = directory.listFiles().mapTo(HashSet()) { it.uri }
    val created = directory.createFile(source.path.getMimeType(), name) ?: error("Document creation failed")
    // Some providers return an existing document instead of creating one. Never truncate that URI.
    require(created.uri !in previous && created.uri != sourceDocument.uri)
    var verified = false
    var newClaim: String? = null
    try {
        val actualFile = verifiedOwnedDocumentPath(created, hidden)
        // Claim the verified empty document before the first byte; hash failure/cancellation may retain it.
        if (hidden) {
            val path = checkNotNull(actualFile).path
            if (path !in config.userHiddenFiles) newClaim = path
            config.updateUserHiddenFiles(listOf(path), emptyList())
        }
        val expected = sourceDocument.length()
        val originalHash = hashOwnedDocument(sourceDocument, session)
        check(sourceDocument.length() == expected)
        val copied = copyOwnedDocumentBytes(source, created, expected, session)
        check(copied == expected && created.length() == copied && sourceDocument.length() == expected)
        verified = true
        // Keep the completed copy on uncertainty; never delete a changed/corrupt source pair.
        verifyOwnedDocumentCopy(sourceDocument, created, originalHash, session)
        if (actualFile != null) {
            session.changedPaths.add(actualFile.path)
        }
        finishOwnedDocumentTransfer(source, sourceDocument, actualFile, hidden, copy, session, pending)
    } finally {
        // Only our incomplete document is disposable, and only with a positively retained source.
        if (!verified) discardUnfinishedDocument(created, sourceDocument, session, newClaim)
    }
}

private fun SimpleActivity.discardUnfinishedDocument(
    created: DocumentFile, source: DocumentFile, session: OwnedBulkSession, newClaim: String?
) {
    if (session.closed() || !source.exists() || !created.delete()) return
    newClaim?.let { config.removeUserHiddenFiles(listOf(it)) }
}

private fun chooseOwnedDocumentName(
    directory: DocumentFile,
    proposed: String,
    session: OwnedBulkSession,
    conflict: () -> OwnedConflict
): String? {
    if (directory.findFile(proposed) == null) return proposed
    return when (conflict()) {
        OwnedConflict.SKIP -> null.also { session.counted(false) }
        OwnedConflict.CANCEL -> null.also { session.cancelled = true }
        OwnedConflict.KEEP_BOTH -> {
            val extension = File(proposed).extension.let { if (it.isEmpty()) "" else ".$it" }
            val stem = proposed.removeSuffix(extension)
            var index = 1
            var name = "$stem ($index)$extension"
            while (directory.findFile(name) != null) {
                check(!session.stopped())
                index++
                name = "$stem ($index)$extension"
            }
            name
        }
    }
}

private fun SimpleActivity.copyOwnedDocumentBytes(
    source: File,
    created: DocumentFile,
    expected: Long,
    session: OwnedBulkSession
): Long {
    val input = getFileInputStreamSync(source.path) ?: error("Source stream unavailable")
    return input.use { stream ->
        val output = contentResolver.openOutputStream(created.uri, "w") ?: error("Output stream unavailable")
        output.use { target ->
            streamOwnedDocument(stream, target, expected, session)
        }
    }
}

private fun SimpleActivity.resolveOwnedDocument(file: File): DocumentFile = getSomeDocumentFile(file.path)
    ?: file.takeIf { it.exists() }?.let { DocumentFile.fromFile(it) }
    ?: error("Document grant required")

private fun SimpleActivity.verifiedOwnedDocumentPath(created: DocumentFile, hidden: Boolean): File? {
    val actual = if (created.uri.scheme == "file") created.uri.path else getRealPathFromURI(created.uri)
    val mapped = actual?.takeIf { it.startsWith('/') }?.takeIf { path ->
        created.uri.scheme == "file" || getSomeDocumentFile(path)?.uri == created.uri
    }?.let { File(it) }
    if (hidden && mapped == null) {
        check(created.length() == 0L)
        created.delete()
        error("Hidden destination cannot be tracked safely")
    }
    return mapped
}

private fun streamOwnedDocument(
    input: InputStream,
    output: OutputStream,
    expected: Long,
    session: OwnedBulkSession
): Long {
    val buffer = ByteArray(OWNED_DOCUMENT_BUFFER_SIZE)
    var completed = 0L
    while (true) {
        check(!session.stopped())
        val count = input.read(buffer)
        if (count < 0) break
        output.write(buffer, 0, count)
        completed += count
        session.copiedBytes(completed, expected)
    }
    output.flush()
    return completed
}

private fun SimpleActivity.finishOwnedDocumentTransfer(
    source: File,
    sourceDocument: DocumentFile,
    actual: File?,
    hidden: Boolean,
    copy: Boolean,
    session: OwnedBulkSession,
    pending: MutableList<OwnedDelete>
) {
    if (copy) {
        // A verified copy may have no filesystem mapping. Never invent a hidden-origin claim.
        session.counted(actual != null || !hidden)
    } else if (actual == null || session.stopped()) {
        session.counted(false) // Retain both copies if the destination cannot be mapped safely.
    } else if (sourceDocument.delete()) {
        recordOwnedMove(source, actual, hidden)
        session.counted(true)
    } else {
        val uri = findOwnedMediaUri(source.path)
        if (uri == null) session.counted(false)
        else pending.add(OwnedDelete(source, uri, destination = actual, hidden = hidden))
    }
}
