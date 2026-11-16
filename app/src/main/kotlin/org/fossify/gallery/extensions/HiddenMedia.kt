package org.fossify.gallery.extensions

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.R
import org.fossify.gallery.dialogs.GrantAllFilesDialog

fun BaseSimpleActivity.toggleHiddenOnly(onChanged: () -> Unit) {
    if (config.filterHiddenOnly) {
        config.filterHiddenOnly = false
        onChanged()
    } else {
        requestHiddenMediaAccess {
            config.filterHiddenOnly = true
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
    if (isRPlus() && !isExternalStorageManager()) {
        GrantAllFilesDialog(this)
    } else if (config.shouldShowHidden) {
        onGranted()
    } else {
        handleHiddenFolderPasswordProtection {
            // Authentication is asynchronous; storage access can change while it is open.
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                config.temporarilyShowHidden = true
                onGranted()
            }
        }
    }
}
