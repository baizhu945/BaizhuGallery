package org.fossifymigration.baizhu.gallery.extensions

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.helpers.isRPlus
import org.fossifymigration.baizhu.gallery.R
import org.fossifymigration.baizhu.gallery.dialogs.GrantAllFilesDialog

fun BaseSimpleActivity.toggleHiddenOnly(onChanged: () -> Unit) {
    if (config.filterHiddenOnly) {
        config.filterHiddenOnly = false
        onChanged()
    } else {
        withHiddenMediaAuthorization { needsTemporaryAccess ->
            if (needsTemporaryAccess) config.enableHiddenOnlyWithTemporaryAccess() else config.filterHiddenOnly = true
            onChanged()
        }
    }
}

fun BaseSimpleActivity.toggleOtherHidden(onChanged: () -> Unit) {
    if (config.includeOtherHidden) {
        config.includeOtherHidden = false
        onChanged()
    } else {
        ConfirmationDialog(this, getString(R.string.other_hidden_description)) {
            requestHiddenMediaAccess {
                config.includeOtherHidden = true
                onChanged()
            }
        }
    }
}

internal fun BaseSimpleActivity.requestHiddenMediaAccess(onGranted: () -> Unit) {
    withHiddenMediaAuthorization { needsTemporaryAccess ->
        if (needsTemporaryAccess) config.temporarilyShowHidden = true
        onGranted()
    }
}

private fun BaseSimpleActivity.withHiddenMediaAuthorization(onGranted: (needsTemporaryAccess: Boolean) -> Unit) {
    if (isRPlus() && !isExternalStorageManager()) {
        GrantAllFilesDialog(this)
    } else if (config.shouldShowHidden) {
        onGranted(false)
    } else {
        handleHiddenFolderPasswordProtection {
            // Authentication is asynchronous; storage access can change while it is open.
            if (isFinishing || isDestroyed) return@handleHiddenFolderPasswordProtection
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                onGranted(!config.shouldShowHidden)
            }
        }
    }
}
