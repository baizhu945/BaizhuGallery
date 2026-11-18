package org.fossifymigration.baizhu.gallery.helpers

/** A temporary grant created by Hidden only is not permission to reveal items in mixed browsing. */
internal fun shouldRevealHiddenMedia(
    showHidden: Boolean, temporaryShowHidden: Boolean, hiddenOnly: Boolean, temporaryHiddenOnly: Boolean
): Boolean = showHidden || temporaryShowHidden || (hiddenOnly && temporaryHiddenOnly)

/** A worker must keep the generation and policy of its source, not retag it at publication. */
internal data class HiddenMediaRequest(val generation: Int, val state: HiddenMediaState) {
    fun isCurrent(generation: Int, state: HiddenMediaState) = this.generation == generation && this.state == state
}

/** All hidden-media policy inputs that can invalidate an in-flight media request. */
data class HiddenMediaState(
    val onlyHidden: Boolean,
    val includeOtherHidden: Boolean,
    val showHidden: Boolean,
    val hasStorageAccess: Boolean,
    val registryRevision: Long
) {
    val isFilteringActive: Boolean
        get() = onlyHidden || !includeOtherHidden || !showHidden || !hasStorageAccess
}
