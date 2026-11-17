package org.fossifymigration.baizhu.gallery.adapters

fun main() {
    check("Aa".hashCode() == "BB".hashCode())
    val index = AdapterRowIndex()
    val a = index.key("Aa"); val b = index.key("BB")
    check(a != b)
    index.rebuild(listOf("Aa", null, "BB"))
    check(index.position(a) == 0 && index.position(b) == 2)
    index.rebuild(listOf("BB", "Aa"))
    check(index.position(a) == 1 && index.position(b) == 0)
    index.rebuild(listOf("BB"))
    check(index.position(a) == -1 && index.key("Aa") == a)
    val paths = (0 until 20000).map { "/gallery/IMG_$it.jpg" }
    index.rebuild(paths)
    check(paths.map(index::key).toSet().size == 20000)
    check(paths.withIndex().all { index.position(index.key(it.value)) == it.index })
    println("AdapterRowIndex: 7 checks passed; exact pure class extracted from production file, no Android stubs; dispatchRowChanges NOT tested")
}
