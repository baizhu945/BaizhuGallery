package org.fossify.gallery.tests

import java.io.File
import java.nio.file.Files
import org.fossify.gallery.helpers.HiddenMediaFilter
import org.fossify.gallery.helpers.UserHiddenPaths

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

    // Legacy physical-classification tests explicitly opt into other hidden media.
    fun filter(onlyHidden: Boolean = true, allowed: Boolean = true): HiddenMediaFilter =
        HiddenMediaFilter(onlyHidden, allowed, ::hasNoMedia, includeOtherHidden = true)
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
        test("matches maps original path exactly once and public classifier still maps") {
            var mappings = 0
            val trusted = mutableListOf<String>()
            val classifier = HiddenMediaFilter(
                onlyHidden = true,
                hiddenAccessAllowed = true,
                hasNoMedia = { false },
                originalPath = { mappings++; "/.private/$it" },
                isUserHidden = { trusted.add(it); true }
            )
            check(classifier.matches("a.jpg"))
            check(mappings == 1)
            check(trusted == listOf("/.private/a.jpg"))
            check(classifier.isHidden("b.jpg"))
            check(mappings == 2)
        }
        test("ordinary mode allows visible files with or without authorization") {
            val path = file("photos/a.jpg").path
            check(filter(onlyHidden = false).matches(path))
            check(filter(onlyHidden = false, allowed = false).matches(path))
        }
        test("ordinary mode fails closed for hidden files without access") {
            check(!filter(onlyHidden = false, allowed = false).matches(file(".private/a.jpg").path))
        }
        test("denied hidden access rejects dot files") {
            check(!filter(allowed = false).matches(file("photos/.a.jpg").path))
        }
        test("denied hidden access rejects nomedia files") {
            file("photos/.nomedia")
            check(!filter(allowed = false).matches(file("photos/a.jpg").path))
        }
        test("denied hidden access rejects visible files in only-hidden mode") {
            check(!filter(allowed = false).matches(file("photos/a.jpg").path))
        }
        test("physical classification remains available without authorization") {
            val classifier = filter(allowed = false)
            val hidden = file(".private/a.jpg").path
            check(classifier.isHidden(hidden))
            check(!classifier.isHidden(file("photos/a.jpg").path))
            check(!classifier.matches(hidden))
        }
        test("enabled hidden mode rejects visible files") {
            check(!filter().matches(file("photos/a.jpg").path))
        }
        test("dot file is physically hidden") {
            val path = file("photos/.a.jpg").path
            check(filter().isHidden(path))
            check(filter().matches(path))
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
            val classifier = HiddenMediaFilter(
                true, true, ::hasNoMedia, originalPath = { if (it == recycled) original else it }, includeOtherHidden = true
            )
            check(classifier.matches(recycled))
        }
        test("recycle path uses original nomedia ancestry") {
            file("original/.nomedia")
            val original = file("original/nested/a.jpg").path
            val classifier = HiddenMediaFilter(true, true, ::hasNoMedia, originalPath = { original }, includeOtherHidden = true)
            check(classifier.matches(file("recycle/token.jpg").path))
        }
        test("hidden recycle location does not hide visible original") {
            val original = file("photos/a.jpg").path
            val classifier = HiddenMediaFilter(true, true, ::hasNoMedia, originalPath = { original }, includeOtherHidden = true)
            check(!classifier.matches(file(".recycle/token.jpg").path))
        }
        test("positive memoization stays consistent within instance") {
            val marker = file("photos/.nomedia")
            val path = file("photos/a.jpg").path
            val probes = mutableListOf<String>()
            val classifier = HiddenMediaFilter(true, true, { probes.add(it); hasNoMedia(it) }, includeOtherHidden = true)
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
            val classifier = HiddenMediaFilter(true, true, { probes.add(it); hasNoMedia(it) }, includeOtherHidden = true)
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
            val classifier = HiddenMediaFilter(true, true, { probes.add(it); hasNoMedia(it) }, includeOtherHidden = true)
            check(classifier.matches(file("photos/one/a.jpg").path))
            check(classifier.matches(file("photos/two/b.jpg").path))
            check(probes.count { it == File(this.root, "photos").path } == 1)
        }
        test("registration policy is independent of file size") {
            // Names describe the policy scenario, not sizes: no length or size argument exists.
            val tinyUserHidden = File(root, ".user/tiny.jpg").path
            val largeSystemHidden = File(root, ".system/large.jpg").path
            val visible = File(root, "photos/normal.jpg").path
            val registry = UserHiddenPaths(setOf(tinyUserHidden), emptySet())
            for (onlyHidden in listOf(false, true)) {
                val classifier = HiddenMediaFilter(
                    onlyHidden, true, ::hasNoMedia, isUserHidden = { registry.matches(it, ::hasNoMedia) }
                )
                check(classifier.matches(tinyUserHidden))
                check(!classifier.matches(largeSystemHidden))
                check(classifier.matches(visible) == !onlyHidden)
                check(classifier.isHidden(largeSystemHidden))
            }
        }
        test("includeOtherHidden requires both explicit opt-in and authorization") {
            val unknown = file(".system/a.jpg").path
            val registered = file(".user/a.jpg").path
            for (onlyHidden in listOf(false, true)) {
                for (allowed in listOf(false, true)) {
                    for (includeOther in listOf(false, true)) {
                        val classifier = HiddenMediaFilter(
                            onlyHidden, allowed, ::hasNoMedia, includeOtherHidden = includeOther,
                            isUserHidden = { it == registered }
                        )
                        check(classifier.matches(unknown) == (allowed && includeOther))
                        check(classifier.matches(registered) == allowed)
                    }
                }
            }
        }
        test("recycle normalization passes original path to registry") {
            val recycled = file("recycle/token.jpg").path
            val original = file(".private/a.jpg").path
            val received = mutableListOf<String>()
            val registry = UserHiddenPaths(setOf(original), emptySet())
            val classifier = HiddenMediaFilter(
                true, true, ::hasNoMedia, originalPath = { check(it == recycled); original },
                isUserHidden = { received.add(it); registry.matches(it, ::hasNoMedia) }
            )
            check(classifier.matches(recycled))
            check(received == listOf(original))
        }
        println("Passed $passed production classifier tests.")
        runUserHiddenPathsTests()
        runHiddenMediaStateTests()
    } finally {
        check(root.deleteRecursively()) { "Could not clean fixture directory: $root" }
    }
}
