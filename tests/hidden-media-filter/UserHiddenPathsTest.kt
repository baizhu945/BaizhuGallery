package org.fossify.gallery.tests

import org.fossify.gallery.helpers.UserHiddenPaths

fun runUserHiddenPathsTests() {
    var passed = 0
    fun test(name: String, body: () -> Unit) {
        try {
            body()
        } catch (failure: Throwable) {
            throw AssertionError("FAIL: $name", failure)
        }
        passed++
        println("PASS: $name")
    }
    val noMarkers: (String) -> Boolean = { false }

    test("exact file records do not claim siblings or descendants") {
        val registry = UserHiddenPaths(setOf("/photos/a.jpg"), emptySet())
        check(registry.matches("/photos/a.jpg", noMarkers))
        check(!registry.matches("/photos/a.jpg.bak", noMarkers))
        check(!registry.matches("/photos/a.jpg/child", noMarkers))
        check(!registry.matches("/photos/b.jpg", noMarkers))
    }
    test("exact file records explicitly trust dotfiles and nomedia ancestry") {
        val path = "/photos/.cache/.a.jpg"
        val registry = UserHiddenPaths(setOf(path), emptySet())
        check(registry.matches(path) { error("An exact claim needs no directory probe") })
    }
    test("claimed folder allows ordinary descendants but not independently hidden children") {
        val registry = UserHiddenPaths(emptySet(), setOf("/photos"))
        val markers: (String) -> Boolean = { it == "/photos/preexisting" }
        check(registry.matches("/photos/a.jpg", markers))
        check(registry.matches("/photos/ordinary/deeper/a.jpg", markers))
        check(!registry.matches("/photos/.cache/a.jpg", markers))
        check(!registry.matches("/photos/ordinary/.a.jpg", markers))
        check(!registry.matches("/photos/preexisting/a.jpg", markers))
        check(!registry.matches("/photos/preexisting/deeper/a.jpg", markers))
    }
    test("claimed root explicitly trusts its own dot name and nomedia marker") {
        val registry = UserHiddenPaths(emptySet(), setOf("/photos/.private"))
        val probes = mutableListOf<String>()
        val markers: (String) -> Boolean = { probes.add(it); it == "/photos/.private" }
        check(registry.matches("/photos/.private", markers))
        check(registry.matches("/photos/.private/a.jpg", markers))
        check(registry.matches("/photos/.private/ordinary/a.jpg", markers))
        check("/photos/.private" !in probes)
        check(!registry.matches("/photos/.private/.nested/a.jpg", markers))
    }
    test("ordinary claimed root trusts its own nomedia but not a child marker") {
        val registry = UserHiddenPaths(emptySet(), setOf("/photos"))
        val markers: (String) -> Boolean = { it == "/photos" || it == "/photos/child" }
        check(registry.matches("/photos/a.jpg", markers))
        check(!registry.matches("/photos/child/a.jpg", markers))
    }
    test("deepest explicit folder claim overrides independently hidden ancestry") {
        val registry = UserHiddenPaths(emptySet(), setOf("/photos", "/photos/.cache/claimed", "/photos/preexisting"))
        val markers: (String) -> Boolean = { it == "/photos/preexisting" }
        check(registry.matches("/photos/.cache/claimed/a.jpg", markers))
        check(registry.matches("/photos/preexisting/a.jpg", markers))
        check(!registry.matches("/photos/.cache/unclaimed/a.jpg", markers))
        check(!registry.matches("/photos/.cache/claimed/.still-hidden.jpg", markers))
        check(!registry.matches("/photos/preexisting/.nested/a.jpg", markers))
    }
    test("an exact file claim can override exclusions below a claimed folder") {
        val registry = UserHiddenPaths(setOf("/photos/.cache/.chosen.jpg"), setOf("/photos"))
        check(registry.matches("/photos/.cache/.chosen.jpg", noMarkers))
        check(!registry.matches("/photos/.cache/.other.jpg", noMarkers))
    }
    test("matching is case-sensitive with safe folder boundaries") {
        val registry = UserHiddenPaths(setOf("/Photos/A.jpg"), setOf("/Private"))
        check(registry.matches("/Photos/A.jpg", noMarkers))
        check(!registry.matches("/photos/A.jpg", noMarkers))
        check(!registry.matches("/Photos/a.jpg", noMarkers))
        check(registry.matches("/Private/a.jpg", noMarkers))
        check(!registry.matches("/private/a.jpg", noMarkers))
        check(!registry.matches("/Private-extra/a.jpg", noMarkers))
        check(!registry.matches("/Private2/a.jpg", noMarkers))
    }
    test("trailing separators normalize on records and candidates") {
        val registry = UserHiddenPaths(setOf("/photos/a.jpg/"), setOf("/private///"))
        check(registry.matches("/photos/a.jpg", noMarkers))
        check(registry.matches("/photos/a.jpg///", noMarkers))
        check(registry.matches("/private", noMarkers))
        check(registry.matches("/private/", noMarkers))
        check(registry.matches("/private/ordinary/a.jpg", noMarkers))
        check(!registry.matches("/private-other/a.jpg", noMarkers))
    }
    test("root folder claim preserves independent hidden exclusions") {
        val registry = UserHiddenPaths(emptySet(), setOf("/"))
        check(registry.matches("/photos/a.jpg", noMarkers))
        check(!registry.matches("/.cache/a.jpg", noMarkers))
        check(!registry.matches("/photos/.a.jpg", noMarkers))
        check(!registry.matches("relative/a.jpg", noMarkers))
    }
    test("empty registry and empty records do not claim arbitrary paths") {
        for (registry in listOf(UserHiddenPaths(emptySet(), emptySet()), UserHiddenPaths(setOf(""), setOf("")))) {
            check(!registry.matches("/photos/a.jpg", noMarkers))
            check(!registry.matches("relative/a.jpg", noMarkers))
            check(!registry.matches("", noMarkers))
        }
    }
    println("Passed $passed production registry tests.")
}
