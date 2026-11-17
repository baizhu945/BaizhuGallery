package org.fossifymigration.baizhu.gallery.extensions

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.getDoesFilePathExist
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossifymigration.baizhu.gallery.R

fun BaseSimpleActivity.claimHiddenPaths(paths: Collection<String>, folders: Boolean, onChanged: () -> Unit) {
    val selectedPaths = paths.toList()
    val claim = {
        requestHiddenMediaAccess {
            ensureBackgroundThread {
                val filter = getHiddenMediaFilter()
                val existingHidden = selectedPaths.filter { path ->
                    val probe = if (folders) "${path.trimEnd('/')}/gallery-origin-probe.jpg" else path
                    getDoesFilePathExist(path, config.OTGPath) && filter.isHidden(probe)
                }
                if (folders) {
                    config.addUserHiddenFolders(existingHidden)
                } else {
                    config.addUserHiddenFiles(existingHidden)
                }
                runOnUiThread {
                    toast(R.string.my_hidden_claimed)
                    onChanged()
                }
            }
        }
    }
    if (folders) {
        ConfirmationDialog(this, getString(R.string.claim_hidden_folder_description)) { claim() }
    } else {
        claim()
    }
}

fun BaseSimpleActivity.forgetHiddenPaths(paths: Collection<String>, folders: Boolean, onChanged: () -> Unit) {
    val selectedPaths = paths.toList()
    ConfirmationDialog(this, getString(R.string.forget_hidden_description)) {
        if (folders) {
            config.removeUserHiddenFolders(selectedPaths)
        } else {
            config.removeUserHiddenFiles(selectedPaths)
        }
        toast(R.string.my_hidden_forgotten)
        onChanged()
    }
}
