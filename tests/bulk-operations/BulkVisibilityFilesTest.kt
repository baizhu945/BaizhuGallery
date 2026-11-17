package org.fossifymigration.baizhu.gallery.extensions

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest

private fun hash(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).toList()
private fun success(result: VisibilityFileResult) = check(result.verified && result.renamed && !result.failed)
private fun failure(result: VisibilityFileResult) = check(result.failed && !result.verified && !result.renamed)

fun main(args: Array<String>) {
    val root = File(args.single())
    var passed = 0
    fun test(name: String, body: (File) -> Unit) {
        val directory = File(root, "case-${passed + 1}").apply { check(mkdir()) }
        body(directory)
        passed++
        println("PASS $name")
    }
    fun file(dir: File, name: String, text: String = name) = File(dir, name).apply { writeText(text) }
    for (hide in listOf(true, false)) {
        test("existing target preserves distinct hashes: hide=$hide") { dir ->
            val source = file(dir, if (hide) "photo" else ".photo", "source bytes")
            val target = file(dir, if (hide) ".photo" else "photo", "different target bytes")
            val before = listOf(hash(source), hash(target))
            check(before[0] != before[1])
            val result = moveVisibilityFile(source.path, hide, true)
            failure(result)
            val counts = VisibilityBatchCounts().apply { settle(result.failed || !result.verified) }
            check(counts.completed == 1 && counts.failed == 1)
            check(listOf(hash(source), hash(target)) == before)
        }
        for (keep in listOf(true, false)) test("content and mtime: hide=$hide keep=$keep") { dir ->
            val source = file(dir, if (hide) "photo" else ".photo", "\u0000photo\ncontent\u00ff")
            val target = File(dir, if (hide) ".photo" else "photo")
            val before = hash(source)
            val old = 1_600_000_000_000L
            check(source.setLastModified(old))
            val started = System.currentTimeMillis()
            val result = moveVisibilityFile(source.path, hide, keep)
            success(result)
            check(result.source == source.path && result.destination == target.path)
            check(!source.exists() && hash(target) == before)
            if (keep) check(target.lastModified() == old)
            else check(target.lastModified() in started..System.currentTimeMillis() && target.lastModified() != old)
        }
        test("source symlink is not followed: hide=$hide") { dir ->
            val real = file(dir, "real", "untouched")
            val link = File(dir, if (hide) "link" else ".link")
            Files.createSymbolicLink(link.toPath(), real.toPath())
            failure(moveVisibilityFile(link.path, hide, true))
            check(Files.isSymbolicLink(link.toPath()) && real.readText() == "untouched")
            check(!File(dir, if (hide) ".link" else "link").exists())
        }
        for (dangling in listOf(false, true)) test("target symlink blocks rename: hide=$hide dangling=$dangling") { dir ->
            val real = if (dangling) File(dir, "absent") else file(dir, "real", "untouched")
            val source = file(dir, if (hide) "link" else ".link", "source")
            val target = File(dir, if (hide) ".link" else "link")
            Files.createSymbolicLink(target.toPath(), real.toPath())
            failure(moveVisibilityFile(source.path, hide, true))
            check(source.readText() == "source" && Files.readSymbolicLink(target.toPath()) == real.toPath())
            check(if (dangling) !real.exists() else real.readText() == "untouched")
        }
        test("directory rejected: hide=$hide") { dir ->
            val source = File(dir, if (hide) "folder" else ".folder").apply { check(mkdir()) }
            file(source, "child", "untouched")
            failure(moveVisibilityFile(source.path, hide, true))
            check(File(source, "child").readText() == "untouched")
        }
    }
    for (name in listOf(".photo", "...photo")) test("leading dots: $name") { dir ->
        val source = file(dir, name, "payload")
        val unchanged = moveVisibilityFile(source.path, true, true)
        check(unchanged.verified && !unchanged.renamed && !unchanged.failed)
        success(moveVisibilityFile(source.path, false, true))
        check(File(dir, "photo").readText() == "payload")
    }
    for (path in listOf("", "relative/photo", "content://media/1", "unknown://item", "/a/....")) {
        test("unknown or invalid path: '$path'") { _ ->
            check(visibilityDestination(path, false) == null)
            val result = moveVisibilityFile(path, false, true)
            check(!result.verified && !result.renamed && !result.failed && result.destination == path)
        }
    }
    test("missing hide fails; missing ordinary unhide settles stale claim") { dir ->
        val path = File(dir, "missing").path
        failure(moveVisibilityFile(path, true, true))
        val result = moveVisibilityFile(path, false, true)
        check(result.verified && !result.renamed && !result.failed && !File(path).exists())
    }
    test("nonwritable parent reports unsupported failure") { dir ->
        val source = file(dir, "photo")
        val permissions = Files.getPosixFilePermissions(dir.toPath())
        try {
            Files.setPosixFilePermissions(dir.toPath(), PosixFilePermissions.fromString("r-x------"))
            check(!dir.canWrite()) { "Run as an unprivileged user, not root" }
            val result = moveVisibilityFile(source.path, true, true)
            failure(result)
            check(result.unsupported && source.readText() == "photo")
        } finally {
            Files.setPosixFilePermissions(dir.toPath(), permissions)
        }
    }
    test("settled counts include failures, not only successful work") { _ ->
        val counts = VisibilityBatchCounts()
        check(counts.completed == 0 && counts.failed == 0)
        for ((index, failed) in listOf(false, true, true, false).withIndex()) {
            counts.settle(failed)
            check(counts.completed == index + 1 && counts.failed == listOf(0, 1, 2, 2)[index])
        }
    }
    test("1000 mixed items: no overwrite or fake success counts") { dir ->
        val counts = VisibilityBatchCounts()
        var renamed = 0
        repeat(1000) { index ->
            val source = File(dir, "photo-$index")
            val target = File(dir, ".photo-$index")
            if (index % 4 != 1) source.writeText("source-$index")
            if (index % 4 == 0) target.writeText("occupied-$index")
            if (index % 4 == 3) Files.createDirectory(target.toPath())
            val result = moveVisibilityFile(source.path, true, true)
            counts.settle(result.failed || !result.verified)
            if (index % 4 == 2) {
                success(result)
                renamed++
                check(!source.exists() && target.readText() == "source-$index")
            } else {
                failure(result)
                check(!result.unsupported)
                check(if (index % 4 == 1) !source.exists() else source.readText() == "source-$index")
                if (index % 4 == 0) check(target.readText() == "occupied-$index")
                if (index % 4 == 3) check(Files.isDirectory(target.toPath(), NOFOLLOW_LINKS))
            }
        }
        check(counts.completed == 1000 && counts.failed == 750 && renamed == 250)
    }
    println("BulkVisibilityFiles: $passed tests passed (including 1000-item batch)")
}
