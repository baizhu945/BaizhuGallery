package org.fossifymigration.baizhu.gallery.extensions

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val EXIF_MILLISECOND_DIGITS = 3

internal fun Context.recycledTaken(source: File, backup: File, modified: Long): Long {
    val cached = mediaDB.getMediaFromPath(source.parent.orEmpty()).firstOrNull { it.path == source.path }
    cached?.taken?.takeIf { it > 0L }?.let { return it }
    return readRecycledExifTime(backup)?.takeIf { it > 0L } ?: modified
}

private fun readRecycledExifTime(backup: File): Long? = runCatching {
    val exif = ExifInterface(backup.path)
    val stamp = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return@runCatching null
    val date = LocalDateTime.parse(stamp, DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.ROOT))
    val zone = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)?.let(ZoneOffset::of)
        ?: ZoneId.systemDefault()
    val fraction = exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL)
        ?.padEnd(EXIF_MILLISECOND_DIGITS, '0')?.take(EXIF_MILLISECOND_DIGITS)?.toLongOrNull() ?: 0L
    date.atZone(zone).toInstant().toEpochMilli() + fraction
}.getOrNull()
