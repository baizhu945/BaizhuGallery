package org.fossify.gallery.extensions

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.handleHiddenFolderPasswordProtection
import org.fossify.commons.extensions.isExternalStorageManager
import org.fossify.commons.helpers.isRPlus
import org.fossify.gallery.dialogs.GrantAllFilesDialog

fun BaseSimpleActivity.toggleHiddenOnly(onChanged: () -> Unit) {
    if (config.filterHiddenOnly) {
        config.filterHiddenOnly = false
        onChanged()
        return
    }

    if (isRPlus() && !isExternalStorageManager()) {
        GrantAllFilesDialog(this)
        return
    }

    if (config.shouldShowHidden) {
        config.filterHiddenOnly = true
        onChanged()
    } else {
        handleHiddenFolderPasswordProtection {
            // Authentication is asynchronous; storage access can change while it is open.
            if (isRPlus() && !isExternalStorageManager()) {
                GrantAllFilesDialog(this)
            } else {
                config.temporarilyShowHidden = true
                config.filterHiddenOnly = true
                onChanged()
            }
        }
    }
}
