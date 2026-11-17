package org.fossifymigration.baizhu.gallery.extensions

import androidx.documentfile.provider.DocumentFile
import org.fossifymigration.baizhu.gallery.activities.SimpleActivity
import java.security.MessageDigest

private const val OWNED_DOCUMENT_HASH_BUFFER_SIZE = 128 * 1024

/** Read through the verified document identity, never a reconstructed destination path. */
internal fun SimpleActivity.hashOwnedDocument(document: DocumentFile, session: OwnedBulkSession): ByteArray {
    check(!session.stopped())
    val size = document.length()
    val modified = document.lastModified()
    val digest = MessageDigest.getInstance("SHA-256")
    val input = contentResolver.openInputStream(document.uri) ?: error("Document verification unavailable")
    var consumed = 0L
    input.use { stream ->
        val buffer = ByteArray(OWNED_DOCUMENT_HASH_BUFFER_SIZE)
        while (true) {
            check(!session.stopped())
            val count = stream.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
            consumed += count
        }
    }
    check(consumed == size && document.length() == size && document.lastModified() == modified)
    return digest.digest()
}

internal fun SimpleActivity.verifyOwnedDocumentCopy(
    source: DocumentFile,
    destination: DocumentFile,
    originalHash: ByteArray,
    session: OwnedBulkSession
) {
    check(originalHash.contentEquals(hashOwnedDocument(destination, session)))
    check(originalHash.contentEquals(hashOwnedDocument(source, session)))
}
