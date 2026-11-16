package org.fossify.gallery.tests

import java.io.File
import java.nio.file.Files
import org.fossify.gallery.helpers.HiddenMediaFilter

private class Fixture(val root: File) {
    fun file(path: String): File = File(root, path).also {
        check(it.parentFile.mkdirs() || it.parentFile.isDirectory)
        it.writeText("")
    }

    fun hasNoMedia(directory: String): Boolean {
        val parent = File(directory)
        val insideFixture = parent == root || parent.path.startsWith(root.path + File.separator)
        return insideFixture && File(parent, ".nomedia").isFile
    }

    fun filter(onlyHidden: Boolean = true, allowed: Boolean = true): HiddenMediaFilter =
        HiddenMediaFilter(onlyHidden, allowed, ::hasNoMedia)
}

fun main() {
    val root = Files.createTempDirectory("gallery-hidden-filter-").toFile()
    var passed = 0
    fun test(name: String, body: Fixture.() -> Unit) {
        val fixture = Fixture(File(root, "case-${passed + 1}"))
        check(fixture.root.mkdirs())
        try {
            fixture.body()
        } catch (failure: Throwable) {
            throw AssertionError("FAIL: $name", failure)
        }
        passed++
        println("PASS: $name")
    }

    try {
        test("ordinary mode allows visible files") {
            check(filter(onlyHidden = false).matches(file("photos/a.jpg").path))
        }
        test("ordinary mode allows hidden files even without hidden access") {
            check(filter(onlyHidden = false, allowed = false).matches(file(".private/a.jpg").path))
        }
        test("ordinary mode does not resolve original paths or inspect directories") {
            val classifier = HiddenMediaFilter(false, false, { error("Unexpected directory probe") }, { error("Unexpected resolution") })
            check(classifier.matches("anything"))
        }
        test("denied hidden access rejects dot files") {
            check(!filter(allowed = false).matches(file("photos/.a.jpg").path))
        }
        test("denied hidden access rejects nomedia files") {
            file("photos/.nomedia")
            check(!filter(allowed = false).matches(file("photos/a.jpg").path))
        }
        test("denied hidden access rejects visible files") {
            check(!filter(allowed = false).matches(file("photos/a.jpg").path))
        }
        test("denied hidden access does not inspect paths") {
            val classifier = HiddenMediaFilter(true, false, { error("Unexpected probe") }, { error("Unexpected resolution") })
            check(!classifier.matches("anything"))
        }
        test("enabled hidden mode rejects visible files") {
            check(!filter().matches(file("photos/a.jpg").path))
        }
        test("dot file is hidden") {
            check(filter().matches(file("photos/.a.jpg").path))
        }
        test("relative dot file is hidden") {
            check(filter().matches(".a.jpg"))
        }
        test("immediate dot directory is hidden") {
            check(filter().matches(file(".private/a.jpg").path))
        }
        test("distant dot ancestor is hidden") {
            check(filter().matches(file(".private/one/two/a.jpg").path))
        }
        test("interior dot in filename is not hidden") {
            check(!filter().matches(file("photos/a.edit.jpg").path))
        }
        test("interior dot in directory is not hidden") {
            check(!filter().matches(file("photos.edit/a.jpg").path))
        }
        test("exact nomedia in immediate parent is hidden") {
            file("photos/.nomedia")
            check(filter().matches(file("photos/a.jpg").path))
        }
        test("exact nomedia in distant ancestor is hidden") {
            file("photos/.nomedia")
            check(filter().matches(file("photos/one/two/a.jpg").path))
        }
        test("nomedia at fixture root is inherited") {
            file(".nomedia")
            check(filter().matches(file("one/two/a.jpg").path))
        }
        test("nomedia near misses do not hide ordinary files") {
            listOf("nomedia", ".nomedia.bak", ".nomediax", ".NOMEDIA").forEach { file("photos/$it") }
            check(!filter().matches(file("photos/a.jpg").path))
        }
        test("sibling nomedia does not hide adjacent directory") {
            file("photos/private/.nomedia")
            check(!filter().matches(file("photos/public/a.jpg").path))
        }
        test("directory prefix is not a directory boundary") {
            file("photos/.nomedia")
            check(!filter().matches(file("photos-extra/a.jpg").path))
        }
        test("descendant nomedia does not hide parent media") {
            file("photos/child/.nomedia")
            check(!filter().matches(file("photos/a.jpg").path))
        }
        test("recycle path uses hidden original dot directory") {
            val recycled = file("recycle/token.jpg").path
            val original = file(".private/a.jpg").path
            val classifier = HiddenMediaFilter(true, true, ::hasNoMedia) { if (it == recycled) original else it }
            check(classifier.matches(recycled))
        }
        test("recycle path uses original nomedia ancestry") {
            file("original/.nomedia")
            val original = file("original/nested/a.jpg").path
            val classifier = HiddenMediaFilter(true, true, ::hasNoMedia) { original }
            check(classifier.matches(file("recycle/token.jpg").path))
        }
        test("hidden recycle location does not hide visible original") {
            val original = file("photos/a.jpg").path
            val classifier = HiddenMediaFilter(true, true, ::hasNoMedia) { original }
            check(!classifier.matches(file(".recycle/token.jpg").path))
        }
        test("positive memoization stays consistent within instance") {
            val marker = file("photos/.nomedia")
            val path = file("photos/a.jpg").path
            val probes = mutableListOf<String>()
            val classifier = HiddenMediaFilter(true, true, { probes.add(it); hasNoMedia(it) })
            check(classifier.matches(path))
            val firstProbes = probes.toList()
            check(marker.delete())
            check(classifier.matches(file("photos/b.jpg").path))
            check(probes == firstProbes)
            check(!filter().matches(path))
        }
        test("negative memoization stays consistent within instance") {
            val path = file("photos/nested/a.jpg").path
            val probes = mutableListOf<String>()
            val classifier = HiddenMediaFilter(true, true, { probes.add(it); hasNoMedia(it) })
            check(!classifier.matches(path))
            val firstProbes = probes.toList()
            file("photos/.nomedia")
            check(!classifier.matches(file("photos/nested/b.jpg").path))
            check(probes == firstProbes)
            check(filter().matches(path))
        }
        test("cached ancestor is reused by sibling descendants") {
            file("photos/.nomedia")
            val probes = mutableListOf<String>()
            val classifier = HiddenMediaFilter(true, true, { probes.add(it); hasNoMedia(it) })
            check(classifier.matches(file("photos/one/a.jpg").path))
            check(classifier.matches(file("photos/two/b.jpg").path))
            check(probes.count { it == File(this.root, "photos").path } == 1)
        }
        test("type intersection composition (harness only, not production type filtering)") {
            data class Item(val path: String, val selectedType: Boolean)
            val items = listOf(
                Item(file(".private/selected.jpg").path, true),
                Item(file(".private/unselected.mp4").path, false),
                Item(file("public/selected.jpg").path, true)
            )
            val classifier = filter()
            val result = items.filter { it.selectedType && classifier.matches(it.path) }
            check(result == listOf(items.first()))
        }
        println("Passed $passed tests (27 production classifier tests; 1 harness-only type intersection).")
    } finally {
        check(root.deleteRecursively()) { "Could not clean fixture directory: $root" }
    }
}
