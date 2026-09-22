package com.yousef.facebooky.ui.chat

import android.widget.VideoView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yousef.facebooky.data.BlobStore
import com.yousef.facebooky.ui.icons.AppIcons

/** Plays a "blob:" (or url) video. Sticker mode = looping, muted, auto-play. */
@Composable
fun BlobVideo(
    reference: String,
    modifier: Modifier = Modifier,
    loop: Boolean = false,
    muted: Boolean = false,
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    var path by remember(reference) { mutableStateOf<String?>(null) }
    var failed by remember(reference) { mutableStateOf(false) }
    LaunchedEffect(reference) {
        path = try {
            if (reference.startsWith("content://") || reference.startsWith("file://")) reference
            else BlobStore.get(context).playablePath(reference)
        } catch (e: Exception) {
            failed = true; null
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val p = path
        when {
            p != null -> AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        if (p.startsWith("content://")) setVideoURI(android.net.Uri.parse(p)) else setVideoPath(p)
                        setOnPreparedListener { mp ->
                            mp.isLooping = loop
                            if (muted) mp.setVolume(0f, 0f)
                            if (autoPlay) start()
                        }
                        if (!autoPlay) setOnClickListener { if (isPlaying) pause() else start() }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            failed -> Icon(AppIcons.Ban, null)
            else -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        }
    }
}

// ---- Video sticker trimmer ----
@androidx.compose.runtime.Composable
fun VideoStickerTrimmer(
    uri: android.net.Uri,
    onConfirm: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    var durationMs by remember(uri) { mutableStateOf(0L) }
    androidx.compose.runtime.LaunchedEffect(uri) {
        durationMs = try {
            com.yousef.facebooky.util.VideoUtils.probe(ctx, uri).durationMs
        } catch (e: Exception) { 0L }
    }
    var start by remember(uri) { mutableStateOf(0f) }        // seconds
    var length by remember(uri) { mutableStateOf(5f) }       // seconds (2..15)
    val totalSec = (durationMs / 1000f).coerceAtLeast(0.5f)
    val maxLen = minOf(15f, totalSec).coerceAtLeast(2f)
    if (length > maxLen) length = maxLen
    if (start + length > totalSec) start = (totalSec - length).coerceAtLeast(0f)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { androidx.compose.material3.Text("قص ستيكر فيديو") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(200.dp)
                ) { BlobVideo(uri.toString(), Modifier.fillMaxSize(), loop = true, muted = true, autoPlay = true) }
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                androidx.compose.material3.Text("البداية: ${"%.1f".format(start)} ث")
                androidx.compose.material3.Slider(value = start, onValueChange = { start = it },
                    valueRange = 0f..(totalSec - 2f).coerceAtLeast(0f))
                androidx.compose.material3.Text("المدة: ${"%.1f".format(length)} ث (٢ إلى ١٥)")
                androidx.compose.material3.Slider(value = length, onValueChange = { length = it },
                    valueRange = 2f..maxLen)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val s = (start * 1000).toLong()
                val e = ((start + length) * 1000).toLong().coerceAtMost(durationMs.coerceAtLeast(s + 500))
                onConfirm(s, e)
            }) { androidx.compose.material3.Text("إرسال") }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("إلغاء") } },
    )
}
