package org.fossifymigration.baizhu.gallery.helpers

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.helpers.isRPlus

/** Result delivery persists even after recreation, when there is no surviving in-memory callback. */
class BulkDeleteRequests(private val activity: BaseSimpleActivity) {
    private var pending: ((Boolean) -> Unit)? = null
    private var pendingToken: String? = null
    private val launcher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        val approved = it.resultCode == Activity.RESULT_OK
        val token = pendingToken ?: PendingNativeJournal.activeToken(activity)
        PendingNativeJournal.recordResult(activity, token, approved)
        val callback = pending
        pending = null
        pendingToken = null
        callback?.invoke(approved)
    }

    init {
        PendingNativeJournal.reconcileSoon(activity)
        activity.lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> PendingNativeJournal.reconcileSoon(activity)
                Lifecycle.Event.ON_DESTROY -> {
                    // The OS request may still complete. Destruction is UNKNOWN, never a refusal.
                    pending = null
                    pendingToken = null
                }
                else -> Unit
            }
        })
    }

    fun launch(uris: List<Uri>, callback: (Boolean) -> Unit) {
        val canLaunch = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        val unsupported = !isRPlus() || uris.isEmpty()
        if (unsupported || pending != null || !canLaunch) {
            callback(false)
            return
        }
        pending = callback
        pendingToken = PendingNativeJournal.activeToken(activity)
        try {
            val request = MediaStore.createDeleteRequest(activity.contentResolver, uris.distinct())
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } catch (ignored: Exception) {
            PendingNativeJournal.recordResult(activity, pendingToken, false)
            pending = null
            pendingToken = null
            callback(false)
        }
    }
}
