package org.fossify.gallery.helpers

import android.content.Context
import android.net.Uri
import org.fossify.commons.extensions.rescanPaths
import org.fossify.gallery.extensions.updateDirectoryPath
import org.fossify.gallery.extensions.OwnedBulkSession
import org.fossify.gallery.extensions.OwnedDelete
import org.fossify.gallery.extensions.ownedMediaMutations
import org.fossify.gallery.extensions.recordOwnedDeletion
import org.fossify.gallery.extensions.recordOwnedMove
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

private const val JOURNAL_PREFS = "pending_native_media_mutations"
private const val JOURNAL_ACTIVE = "active"
private const val JOURNAL_SIGNAL = "pending_native_media_signal"
private const val JOURNAL_BATCH = "batch"
private const val JOURNAL_MAX_ENTRIES = 1000
private const val JOURNAL_MAX_CHARACTERS = 2 * 1024 * 1024
private const val JOURNAL_HASH_BUFFER = 128 * 1024
private const val OUTCOME_UNKNOWN = "unknown"
private const val OUTCOME_APPROVED = "approved"
private const val OUTCOME_CANCELLED = "cancelled"
private const val OUTCOME_LOCAL = "local"

internal data class PendingNativeEntry(
    val source: String,
    val uri: String,
    val backup: String?,
    val destination: String?,
    val hidden: Boolean,
    val fingerprint: String?
)

internal data class PendingNativeBatch(
    val token: String,
    val outcome: String,
    val entries: List<PendingNativeEntry>
)

/** One bounded durable batch. All JSON, hashing, commits and reconciliation run on the mutation worker. */
internal object PendingNativeJournal {
    fun activeToken(context: Context): String? = context.getSharedPreferences(JOURNAL_SIGNAL, Context.MODE_PRIVATE)
        .getString(JOURNAL_ACTIVE, null)

    fun prepare(
        context: Context,
        deletions: List<OwnedDelete>,
        session: OwnedBulkSession,
        local: Boolean = false
    ): String? {
        val stored = context.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE).contains(JOURNAL_BATCH)
        if (stored || activeToken(context) != null || deletions.size > JOURNAL_MAX_ENTRIES) return null
        val entries = deletions.map { deletion ->
            check(!session.stopped())
            val retained = deletion.backup ?: deletion.destination
            val fingerprint = retained?.let { pendingNativeFingerprint(it, session::stopped) }
            check(retained == null || fingerprint != null)
            PendingNativeEntry(
                deletion.source.path, deletion.uri.toString(), deletion.backup?.path,
                deletion.destination?.path, deletion.hidden, fingerprint
            )
        }
        val outcome = if (local) OUTCOME_LOCAL else OUTCOME_UNKNOWN
        val batch = PendingNativeBatch(UUID.randomUUID().toString(), outcome, entries)
        return if (write(context, batch)) batch.token else null
    }

    /** Synchronous delete proof bypasses a possibly lagging MediaStore row, but never the durable bin insert. */
    fun completeLocal(context: Context, token: String, deleted: Boolean): Boolean {
        val batch = read(context) ?: return false
        if (batch.token != token || batch.outcome != OUTCOME_LOCAL) return false
        val entry = batch.entries.single()
        val backup = entry.backup?.let { File(it) } ?: return false
        if (pendingNativeFingerprint(backup) != entry.fingerprint) return false
        if (deleted) {
            context.recordOwnedDeletion(File(entry.source), backup)
        } else if (pendingNativeFingerprint(File(entry.source)) != entry.fingerprint) {
            return false
        }
        val cleared = context.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        return cleared && context.getSharedPreferences(JOURNAL_SIGNAL, Context.MODE_PRIVATE).edit().clear().commit()
    }

    /** UI callback supplies only the tiny token/outcome; persistence never blocks the UI thread. */
    fun recordResult(context: Context, token: String?, approved: Boolean) {
        if (token == null) return
        val app = context.applicationContext
        ownedMediaMutations.execute { runCatching { resolve(app, token, approved) } }
    }

    fun reconcileSoon(context: Context) {
        val app = context.applicationContext
        ownedMediaMutations.execute { runCatching { reconcile(app) } }
    }

    /** Called directly only by the mutation worker after its live permission callback. */
    fun resolve(context: Context, token: String, approved: Boolean): Set<String> {
        val batch = read(context) ?: return emptySet()
        if (batch.token != token) return emptySet()
        val outcome = if (approved) OUTCOME_APPROVED else OUTCOME_CANCELLED
        if (!write(context, batch.copy(outcome = outcome))) return emptySet()
        return reconcile(context)
    }

    private fun read(context: Context): PendingNativeBatch? {
        val raw = context.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE).getString(JOURNAL_BATCH, null)
        return raw?.let { runCatching { PendingNativeCodec.decode(it) }.getOrNull() }
    }

    private fun write(context: Context, batch: PendingNativeBatch): Boolean {
        val json = PendingNativeCodec.encode(batch)
        if (json.length > JOURNAL_MAX_CHARACTERS) return false
        val stored = context.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE).edit()
            .putString(JOURNAL_BATCH, json).commit()
        if (!stored) return false
        return context.getSharedPreferences(JOURNAL_SIGNAL, Context.MODE_PRIVATE).edit()
            .putString(JOURNAL_ACTIVE, batch.token).commit()
    }

    private fun reconcile(context: Context): Set<String> {
        val batch = read(context)
        if (batch == null) {
            if (!context.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE).contains(JOURNAL_BATCH)) {
                context.getSharedPreferences(JOURNAL_SIGNAL, Context.MODE_PRIVATE).edit().clear().commit()
            }
            return emptySet()
        }
        val completed = linkedSetOf<String>()
        val retained = batch.entries.filter { entry ->
            val result = runCatching { reconcilePendingNativeEntry(context, entry, batch.outcome) }.getOrDefault(0)
            if (result == 1) completed.add(entry.source)
            result == 0
        }
        if (retained.isEmpty()) {
            val cleared = context.getSharedPreferences(JOURNAL_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
            if (cleared) context.getSharedPreferences(JOURNAL_SIGNAL, Context.MODE_PRIVATE).edit().clear().commit()
        } else {
            write(context, batch.copy(entries = retained))
        }
        if (completed.isNotEmpty()) {
            GalleryPresentationChangeWatch.markChanged()
            refreshPendingNativeChanges(context, batch.entries.filter { it.source in completed })
        }
        return completed
    }
}

// 1 = confirmed deletion, 2 = confirmed cancellation, 0 = unresolved (retain every recovery record).
private fun reconcilePendingNativeEntry(context: Context, entry: PendingNativeEntry, outcome: String): Int {
    val source = File(entry.source)
    val retained = (entry.backup ?: entry.destination)?.let { File(it) }
    val retainedValid = retained == null ||
        (entry.fingerprint != null && pendingNativeFingerprint(retained) == entry.fingerprint)
    val fileState = pendingNativeFileState(source)
    val sourceAbsent = fileState == 0 && pendingNativeAuthorityAbsent(context, entry.uri, outcome)
    if (sourceAbsent && retainedValid) {
        if (entry.destination != null) context.recordOwnedMove(source, File(entry.destination), entry.hidden)
        else context.recordOwnedDeletion(source, entry.backup?.let { File(it) })
        return 1
    }
    val cancelled = outcome == OUTCOME_CANCELLED && fileState == 1 && retainedValid
    return if (cancelled && cleanupCancelledNativeEntry(entry)) 2 else 0
}

private fun pendingNativeAuthorityAbsent(context: Context, uri: String, outcome: String): Boolean =
    outcome == OUTCOME_LOCAL || pendingNativeUriState(context, uri) == 0

private fun pendingNativeFileState(source: File): Int = when {
    source.isFile -> 1
    source.parentFile?.list()?.none { it == source.name } == true -> 0
    else -> -1
}

private fun cleanupCancelledNativeEntry(entry: PendingNativeEntry): Boolean {
    val retained = entry.backup ?: entry.destination
    if (retained != null && pendingNativeFingerprint(File(entry.source)) != entry.fingerprint) return false
    return entry.backup == null || File(entry.backup).delete()
}

private fun refreshPendingNativeChanges(context: Context, entries: List<PendingNativeEntry>) {
    val paths = entries.flatMap { listOfNotNull(it.source, it.destination) }.distinct()
    runCatching { context.rescanPaths(ArrayList(paths)) }
    paths.mapNotNull { File(it).parent }.distinct().forEach { parent ->
        runCatching { context.updateDirectoryPath(parent) }
    }
}

private fun pendingNativeUriState(context: Context, value: String): Int = runCatching {
    context.contentResolver.query(Uri.parse(value), arrayOf("_id"), null, null, null)?.use {
        if (it.count == 0) 0 else 1
    } ?: -1
}.getOrDefault(-1)

private fun pendingNativeFingerprint(file: File, stopped: () -> Boolean = { false }): String? = runCatching {
    require(file.isFile)
    val size = file.length()
    val modified = file.lastModified()
    val digest = MessageDigest.getInstance("SHA-256")
    var consumed = 0L
    file.inputStream().use { input ->
        val buffer = ByteArray(JOURNAL_HASH_BUFFER)
        while (true) {
            check(!stopped())
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
            consumed += count
        }
    }
    check(consumed == size && file.length() == size && file.lastModified() == modified)
    digest.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()

private object PendingNativeCodec {
    fun encode(batch: PendingNativeBatch): String {
        val entries = JSONArray()
        batch.entries.forEach { entry ->
            entries.put(JSONObject().apply {
                put("source", entry.source)
                put("uri", entry.uri)
                put("backup", entry.backup ?: "")
                put("destination", entry.destination ?: "")
                put("hidden", entry.hidden)
                put("fingerprint", entry.fingerprint ?: "")
            })
        }
        return JSONObject().put("token", batch.token).put("outcome", batch.outcome).put("entries", entries).toString()
    }

    fun decode(raw: String): PendingNativeBatch {
        require(raw.length <= JOURNAL_MAX_CHARACTERS)
        val json = JSONObject(raw)
        val array = json.getJSONArray("entries")
        require(array.length() <= JOURNAL_MAX_ENTRIES)
        val entries = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            PendingNativeEntry(
                item.getString("source"), item.getString("uri"),
                item.optString("backup").takeIf { it.isNotEmpty() },
                item.optString("destination").takeIf { it.isNotEmpty() }, item.getBoolean("hidden"),
                item.optString("fingerprint").takeIf { it.isNotEmpty() }
            )
        }
        return PendingNativeBatch(json.getString("token"), json.getString("outcome"), entries)
    }
}
