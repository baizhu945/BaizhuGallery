package org.fossify.gallery.extensions

import android.net.Uri
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.toast
import org.fossify.gallery.R
import org.fossify.gallery.activities.SimpleActivity
import org.fossify.gallery.helpers.BulkOperationProgress

fun BaseSimpleActivity.beginOperation(title: Int, total: Int? = null): BulkOperationProgress? {
    val operation = BulkOperationProgress.begin(this, title, total)
    if (operation == null && !isFinishing && !isDestroyed) {
        toast(R.string.bulk_already_running)
    }
    return operation
}

fun BaseSimpleActivity.requestBulkDelete(uris: List<Uri>, callback: (Boolean) -> Unit) {
    runOnUiThread {
        val host = this as? SimpleActivity
        if (host == null || isFinishing || isDestroyed) callback(false)
        else host.bulkDeleteRequests.launch(uris, callback)
    }
}
