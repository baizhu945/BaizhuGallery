package org.fossify.gallery.helpers

import kotlin.system.measureNanoTime

// Compiles with the actual production helpers, never a rewritten benchmark algorithm.
private data class Counters(var fs: Long = 0, var original: Long = 0, var selected: Long = 0)
private fun workload(n: Int, roots: Int, mediaOnly: Boolean): Pair<Long, Counters> {
    val c = Counters()
    val registry = UserHiddenPaths(emptySet(), (0 until roots).map { "/gallery/album_$it" }.toSet())
    val paths = (0 until n).map { "/gallery/album_${it % roots}/sub/IMG_$it.jpg" }
    val probe: (String) -> Boolean = { c.fs++; false }
    val elapsed = measureNanoTime {
        for (path in paths) {
            val selected = /*MEDIA_CALL*/ registry.matches(path, probe)
            if (selected) c.selected++
        }
    }
    check(c.selected == n.toLong())
    return elapsed to c
}
private fun filterWorkload(n: Int, roots: Int): Pair<Long, Counters> {
    val c = Counters()
    val registry = UserHiddenPaths(emptySet(), (0 until roots).map { "/gallery/album_$it" }.toSet())
    val filter = HiddenMediaFilter(true, true,
        hasNoMedia = { c.fs++; it.startsWith("/gallery/album_") && !it.endsWith("/sub") },
        originalPath = { c.original++; it },
        isUserHidden = { registry.matches(it) { c.fs++; false } })
    val paths = (0 until n).map { "/gallery/album_${it % roots}/sub/IMG_$it.jpg" }
    val elapsed = measureNanoTime { for (path in paths) if (filter.matches(path)) c.selected++ }
    check(c.selected == n.toLong())
    return elapsed to c
}
private fun assertions() {
    val r = UserHiddenPaths(setOf("/gallery/exact.jpg"), setOf("/gallery/a", "/gallery/a/nested"))
    check(r.matches("/gallery/exact.jpg") { false })
    check(r.matches("/gallery/a/x.jpg") { false })
    check(r.matches("/gallery/a/nested/x.jpg") { it == "/gallery/a/nested" })
    check(!r.matches("/gallery/ab/x.jpg") { false })
    check(!r.matches("/gallery/a/.private/x.jpg") { false })
    check(!r.matches("/gallery/a/nomedia/x.jpg") { it == "/gallery/a/nomedia" })
    val filter = HiddenMediaFilter(false, false, { false })
    check(filter.matches("/gallery/a/x.jpg"));check(!filter.matches("/gallery/.private/x.jpg"))
    val mapped = HiddenMediaFilter(true, true, { false }, { "/gallery/.private/x.jpg" }, true)
    check(mapped.matches("/recycled/x.jpg"))
    println("assertions,9,passed")
}
fun main(args: Array<String>) {
    assertions()
    println("version,kind,images,roots,run,elapsed_ms,fs_probe_calls,original_path_calls,selected")
    for (n in listOf(2000,20000)) for (roots in listOf(500,2000)) {
        for (kind in /*KINDS*/ listOf("registry-general","filter")) {
            repeat(3) { if (kind == "filter") filterWorkload(n,roots) else workload(n,roots,kind == "registry-media") }
            repeat(5) { run ->
                val (ns,c) = if (kind == "filter") filterWorkload(n,roots) else workload(n,roots,kind == "registry-media")
                println("${args.first()},$kind,$n,$roots,${run+1},${ns/1e6},${c.fs},${c.original},${c.selected}")
            }
        }
    }
}
