package com.yousef.facebooky.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

data class AudioFileInfo(val title: String, val extension: String, val mimeType: String, val sizeBytes: Long)

object MediaUtils {

    /** Reads display name / size of a picked audio file. Returns null if unreadable. */
    fun queryAudio(context: Context, uri: Uri): AudioFileInfo? {
        var name: String? = null
        var size = -1L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val s = c.getColumnIndex(OpenableColumns.SIZE)
                    if (n >= 0) name = c.getString(n)
                    if (s >= 0 && !c.isNull(s)) size = c.getLong(s)
                }
            }
        } catch (e: Exception) {
            return null
        }
        val fileName = name ?: uri.lastPathSegment ?: "Song"
        val dot = fileName.lastIndexOf('.')
        val title = (if (dot > 0) fileName.substring(0, dot) else fileName).replace('_', ' ').trim().take(120)
        val ext = (if (dot > 0) fileName.substring(dot + 1) else "mp3")
            .lowercase().filter { it.isLetterOrDigit() }.take(5).ifBlank { "mp3" }
        val mime = context.contentResolver.getType(uri)?.takeIf { it.startsWith("audio/") } ?: "audio/mpeg"
        return AudioFileInfo(title.ifBlank { "Song" }, ext, mime, size)
    }
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
