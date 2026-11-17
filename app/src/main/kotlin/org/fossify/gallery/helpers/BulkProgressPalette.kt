package org.fossify.gallery.helpers

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getProperBackgroundColor
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor

private const val MIN_TEXT_CONTRAST = 4.5
private const val MIN_PROGRESS_CONTRAST = 3.0
private const val TRACK_BLEND = 0.22f

/** Commons custom themes recolor legacy dialog surfaces after the builder theme is resolved. */
internal data class BulkProgressPalette(val text: Int, val active: Int, val track: Int) {
    fun apply(bar: ProgressBar) {
        bar.progressTintList = ColorStateList.valueOf(active)
        bar.secondaryProgressTintList = ColorStateList.valueOf(active)
        bar.indeterminateTintList = ColorStateList.valueOf(active)
        bar.progressBackgroundTintList = ColorStateList.valueOf(track)
    }
}

internal fun progressPalette(activity: BaseSimpleActivity, builder: AlertDialog.Builder): BulkProgressPalette {
    val material = builder is MaterialAlertDialogBuilder
    val surface = opaque(if (material) dialogColor(builder.context,
        com.google.android.material.R.attr.colorSurface, activity.getProperBackgroundColor())
        else activity.getProperBackgroundColor())
    val requestedText = if (material) dialogColor(builder.context, android.R.attr.textColorPrimary,
        activity.getProperTextColor()) else activity.getProperTextColor()
    val contrasting = listOf(Color.BLACK, Color.WHITE).maxBy { ColorUtils.calculateContrast(it, surface) }
    val text = if (ColorUtils.calculateContrast(opaque(requestedText), surface) >= MIN_TEXT_CONTRAST) {
        opaque(requestedText)
    } else contrasting
    val track = ColorUtils.blendARGB(surface, text, TRACK_BLEND)
    val primary = opaque(if (material) dialogColor(builder.context,
        androidx.appcompat.R.attr.colorPrimary, activity.getProperPrimaryColor())
        else activity.getProperPrimaryColor())
    val visiblePrimary = ColorUtils.calculateContrast(primary, surface) >= MIN_PROGRESS_CONTRAST &&
        ColorUtils.calculateContrast(primary, track) >= MIN_PROGRESS_CONTRAST
    return BulkProgressPalette(text, if (visiblePrimary) primary else text, track)
}

private fun opaque(color: Int) = ColorUtils.setAlphaComponent(color, Color.alpha(Color.BLACK))

private fun dialogColor(context: Context, attribute: Int, fallback: Int): Int {
    val values = context.obtainStyledAttributes(intArrayOf(attribute))
    return try { values.getColor(0, fallback) } finally { values.recycle() }
}
