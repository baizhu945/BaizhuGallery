package org.fossify.gallery.tests

import org.fossify.gallery.helpers.HiddenMediaState

fun runHiddenMediaStateTests() {
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

    val unrestricted = HiddenMediaState(
        onlyHidden = false, includeOtherHidden = true, showHidden = true, hasStorageAccess = true, registryRevision = 7L
    )
    for (onlyHidden in listOf(false, true)) {
        for (includeOther in listOf(false, true)) {
            for (showHidden in listOf(false, true)) {
                for (storage in listOf(false, true)) {
                    test("state filtering: only=$onlyHidden other=$includeOther show=$showHidden storage=$storage") {
                        val state = HiddenMediaState(onlyHidden, includeOther, showHidden, storage, 7L)
                        // Only the fully authorized mixed-mode opt-in may bypass filtering/cache policy.
                        check(state.isFilteringActive == (state != unrestricted))
                    }
                }
            }
        }
    }
    test("identical state values compare equal and have equal hash codes") {
        val same = HiddenMediaState(false, true, true, true, 7L)
        check(unrestricted == same)
        check(unrestricted.hashCode() == same.hashCode())
        check(unrestricted == unrestricted.copy())
    }
    test("each policy input and registry revision participates in equality") {
        val changed = listOf(
            unrestricted.copy(onlyHidden = true),
            unrestricted.copy(includeOtherHidden = false),
            unrestricted.copy(showHidden = false),
            unrestricted.copy(hasStorageAccess = false),
            unrestricted.copy(registryRevision = 8L)
        )
        check(changed.all { it != unrestricted })
        check((changed + unrestricted).toSet().size == 6)
    }
    test("registry revision invalidates equality without changing filtering activity") {
        for (state in listOf(unrestricted, unrestricted.copy(showHidden = false))) {
            val revised = state.copy(registryRevision = state.registryRevision + 1)
            check(revised != state)
            check(revised.isFilteringActive == state.isFilteringActive)
        }
    }
    println("Passed $passed production state tests.")
}
