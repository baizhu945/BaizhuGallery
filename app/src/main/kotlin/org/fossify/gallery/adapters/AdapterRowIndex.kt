package org.fossify.gallery.adapters

import androidx.recyclerview.widget.RecyclerView

/** Adapter-local, collision-free Commons selection keys; RecyclerView stable IDs stay disabled. */
internal class AdapterRowIndex {
    private val keys = HashMap<String, Int>()
    private val positions = HashMap<Int, Int>()
    private var nextKey = 0

    fun key(path: String): Int = keys.getOrPut(path) { nextKey++ }

    fun position(key: Int): Int = positions[key] ?: -1

    fun rebuild(paths: List<String?>) {
        positions.clear()
        paths.forEachIndexed { position, path ->
            if (path != null) positions[key(path)] = position
        }
    }
}

/** Linear prefix/suffix reconciliation. No mutable model is retained as comparison state. */
internal data class AdapterRow(val identity: String, val state: List<Any?>)

internal fun dispatchRowChanges(
    adapter: RecyclerView.Adapter<*>,
    old: List<AdapterRow>,
    new: List<AdapterRow>
) {
    var prefix = 0
    while (prefix < minOf(old.size, new.size) && old[prefix].identity == new[prefix].identity) prefix++
    var suffix = 0
    while (suffix < minOf(old.size, new.size) - prefix &&
        old[old.lastIndex - suffix].identity == new[new.lastIndex - suffix].identity
    ) suffix++
    val removed = old.size - prefix - suffix
    val inserted = new.size - prefix - suffix
    if (removed > 0) adapter.notifyItemRangeRemoved(prefix, removed)
    if (inserted > 0) adapter.notifyItemRangeInserted(prefix, inserted)
    for (i in 0 until prefix) {
        if (old[i] != new[i]) adapter.notifyItemChanged(i, true)
    }
    for (i in 0 until suffix) {
        val position = new.size - suffix + i
        if (old[old.size - suffix + i] != new[position]) adapter.notifyItemChanged(position, true)
    }
}
