package org.fossifymigration.baizhu.gallery.tests

import org.fossifymigration.baizhu.gallery.helpers.HiddenMediaFilter
import org.fossifymigration.baizhu.gallery.helpers.HiddenMediaRequest
import org.fossifymigration.baizhu.gallery.helpers.HiddenMediaState
import org.fossifymigration.baizhu.gallery.helpers.shouldRevealHiddenMedia

private const val VISIBLE = "/gallery/album/visible.jpg"
private const val USER_HIDDEN = "/gallery/album/.hidden.jpg"
private const val OTHER_HIDDEN = "/gallery/other/photo.jpg"
private val rows = listOf(VISIBLE, USER_HIDDEN, OTHER_HIDDEN)

private fun state(
    only: Boolean, scopedGrant: Boolean = false, explicitTemporary: Boolean = false,
    persistent: Boolean = false, other: Boolean = false, storage: Boolean = true, revision: Long = 1
) = HiddenMediaState(
    only, other, shouldRevealHiddenMedia(persistent, explicitTemporary, only, scopedGrant), storage, revision
)

private fun display(state: HiddenMediaState): List<String> {
    val filter = HiddenMediaFilter(
        onlyHidden = state.onlyHidden,
        hiddenAccessAllowed = state.showHidden && state.hasStorageAccess,
        hasNoMedia = { it == "/gallery/other" },
        includeOtherHidden = state.includeOtherHidden,
        isUserHidden = { it == USER_HIDDEN }
    )
    return rows.filter(filter::matches)
}

fun main() {
    // Even a queued load that observes the scoped grant before cleanup may not use it in mixed mode.
    check(display(state(only = true, scopedGrant = true)) == listOf(USER_HIDDEN))
    check(display(state(only = false, scopedGrant = true)) == listOf(VISIBLE))
    check(display(state(only = false, scopedGrant = true, other = true)) == listOf(VISIBLE))
    check(display(state(only = true, scopedGrant = true, other = true)) == listOf(USER_HIDDEN, OTHER_HIDDEN))
    check(display(state(only = true, scopedGrant = true, other = true, storage = false)).isEmpty())
    check(display(state(only = true, other = true)).isEmpty()) // Expired/restored filter is not authorization.
    check(display(state(only = false, scopedGrant = true, explicitTemporary = true)) == listOf(VISIBLE, USER_HIDDEN))
    check(display(state(only = false, persistent = true, other = true)) == rows)

    val normal = state(only = false)
    val hidden = state(only = true, scopedGrant = true)
    val oldNormalRequest = HiddenMediaRequest(1, normal)
    val hiddenRequest = HiddenMediaRequest(2, hidden)
    val newNormalRequest = HiddenMediaRequest(3, normal)
    // Off -> on -> off returns to equal policy and rows, but superseded publications must still be rejected.
    check(!oldNormalRequest.isCurrent(3, normal))
    check(!hiddenRequest.isCurrent(3, normal))
    check(!hiddenRequest.isCurrent(2, normal))
    check(newNormalRequest.isCurrent(3, normal))
    // Same generation, same rows: a bulk registry update or permission loss still invalidates the source.
    check(!hiddenRequest.isCurrent(2, hidden.copy(registryRevision = 2)))
    check(!hiddenRequest.isCurrent(2, hidden.copy(hasStorageAccess = false)))
    println("PASS: scoped hidden visibility, explicit grants, and stale publication rejection")
}
