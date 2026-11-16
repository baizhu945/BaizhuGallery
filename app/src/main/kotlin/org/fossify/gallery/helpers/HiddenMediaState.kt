package org.fossify.gallery.helpers

/** All policy inputs that can invalidate an in-flight media request. */
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
