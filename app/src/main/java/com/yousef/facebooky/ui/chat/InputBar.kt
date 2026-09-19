package com.yousef.facebooky.ui.chat

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yousef.facebooky.util.formatTime
import kotlinx.coroutines.delay

@Composable
fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    emojiOpen: Boolean,
    onToggleEmoji: () -> Unit,
    onAttach: () -> Unit,
    onMic: () -> Unit,
    recordingStartedAt: Long?,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        AnimatedContent(
            targetState = recordingStartedAt,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "inputMode",
        ) { startedAt ->
            if (startedAt != null) {
                RecordingRow(startedAt, onCancelRecording, onSendRecording)
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(onClick = onToggleEmoji) {
                        Icon(if (emojiOpen) Icons.Rounded.Keyboard else Icons.Rounded.EmojiEmotions, "Emoji")
                    }
                    IconButton(onClick = onAttach) { Icon(Icons.Rounded.Add, "Attach") }
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) {
                            Text("Message", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = onTextChange,
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    val hasText = text.isNotBlank()
                    FilledIconButton(onClick = if (hasText) onSend else onMic, modifier = Modifier.size(44.dp)) {
                        AnimatedContent(targetState = hasText, label = "sendMic") { send ->
                            if (send) Icon(Icons.AutoMirrored.Rounded.Send, "Send") else Icon(Icons.Rounded.Mic, "Voice message")
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(startedAt: Long, onCancel: () -> Unit, onSend: () -> Unit) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(200)
        }
    }
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) { Icon(Icons.Rounded.Delete, "Cancel", tint = MaterialTheme.colorScheme.error) }
        Box(
            Modifier
                .size(10.dp)
                .alpha(pulse)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.error)
        )
        Spacer(Modifier.width(10.dp))
        Text(formatTime(now - startedAt), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text("Recording…", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(12.dp))
        FilledIconButton(onClick = onSend, modifier = Modifier.size(44.dp)) { Icon(Icons.AutoMirrored.Rounded.Send, "Send voice") }
        Spacer(Modifier.width(4.dp))
    }
}

private val EMOJIS = listOf(
    "😀", "😂", "🤣", "😊", "😍", "🥰", "😘", "😎", "🤩", "🥳", "😅", "😉",
    "🙂", "🤔", "🙄", "😴", "😢", "😭", "😡", "🤯", "😱", "🥺", "🤗", "🤭",
    "👍", "👎", "👏", "🙏", "💪", "👋", "🤝", "✌️", "👌", "🤞", "👀", "🫶",
    "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "💔", "✨", "🔥", "💯", "🎉",
    "🌹", "🌸", "☀️", "🌙", "⭐", "⚽", "🎵", "🎶", "☕", "🍕", "🎂", "🎁",
)

@Composable
fun EmojiPanel(onEmoji: (String) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(46.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(horizontal = 8.dp),
        ) {
            items(EMOJIS) { e ->
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .clickable { onEmoji(e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 26.sp) }
            }
        }
    }
}
