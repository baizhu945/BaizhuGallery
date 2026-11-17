package org.fossifymigration.baizhu.gallery.extensions

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.extensions.launchViewIntent
import org.fossifymigration.baizhu.gallery.BuildConfig
import org.fossifymigration.baizhu.gallery.R
import org.fossifymigration.baizhu.gallery.activities.SimpleActivity

fun SimpleActivity.launchAbout() {
    AlertDialog.Builder(this)
        .setTitle("${getString(R.string.app_name)} ${BuildConfig.VERSION_NAME}")
        .setMessage(getString(R.string.independent_about_description, BuildConfig.APPLICATION_ID))
        .setPositiveButton(R.string.independent_source) { _, _ ->
            launchViewIntent("https://github.com/baizhu945/BaizhuGallery")
        }
        .setNegativeButton(R.string.independent_issues) { _, _ ->
            launchViewIntent("https://github.com/baizhu945/BaizhuGallery/issues")
        }
        .setNeutralButton(R.string.upstream_about_title) { _, _ -> launchUpstreamAbout() }
        .show()
}
