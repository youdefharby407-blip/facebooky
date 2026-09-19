package com.yousef.facebooky.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.yousef.facebooky.audio.VoicePlaybackState
import com.yousef.facebooky.data.model.ChatMessage
import com.yousef.facebooky.data.model.MessageType
import com.yousef.facebooky.util.formatTime
import java.text.SimpleDateFormat
import java.util.Locale

private val MineShape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
private val TheirsShape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

@Composable
fun MessageItem(
    message: ChatMessage,
    isMine: Boolean,
    showSender: Boolean,
    voice: VoicePlaybackState,
    onToggleVoice: (ChatMessage) -> Unit,
    onOpenImage: (String) -> Unit,
    onPlaySong: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (showSender) 10.dp else 2.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isMine) {
            if (showSender) Avatar(message.senderPhoto, 30.dp) else Spacer(Modifier.width(30.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            if (showSender && !isMine) {
                Text(
                    message.senderName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                )
            }
            val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val shape = if (isMine) MineShape else TheirsShape
            when (message.type) {
                MessageType.IMAGE -> AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onOpenImage(message.mediaUrl) },
                )
                MessageType.STICKER -> AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Sticker",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(120.dp),
                )
                MessageType.VOICE -> VoiceBubble(message, voice, bubbleColor, contentColor, shape, onToggleVoice)
                MessageType.MUSIC -> MusicBubble(message, bubbleColor, contentColor, shape, onPlaySong)
                else -> Surface(shape = shape, color = bubbleColor, contentColor = contentColor) {
                    Text(
                        message.text.ifBlank { "Unsupported message" },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
            val time = message.timestamp?.let { timeFormat.format(it) }.orEmpty()
            Text(
                if (message.pending && isMine) "$time · sending" else time,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun VoiceBubble(
    message: ChatMessage,
    voice: VoicePlaybackState,
    color: Color,
    contentColor: Color,
    shape: RoundedCornerShape,
    onToggle: (ChatMessage) -> Unit,
) {
    val active = voice.messageId == message.id
    val duration = if (active && voice.durationMs > 0) voice.durationMs else message.durationMs
    val progress = if (active && duration > 0) (voice.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Surface(shape = shape, color = color, contentColor = contentColor) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 2.dp, end = 12.dp)) {
            IconButton(onClick = { onToggle(message) }) {
                when {
                    active && voice.loading -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = contentColor,
                    )
                    active && voice.isPlaying -> Icon(Icons.Rounded.Pause, "Pause")
                    else -> Icon(Icons.Rounded.PlayArrow, "Play")
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                color = contentColor,
                trackColor = contentColor.copy(alpha = 0.25f),
                modifier = Modifier
                    .width(120.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                formatTime(if (active && voice.positionMs > 0) voice.positionMs else duration),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun MusicBubble(
    message: ChatMessage,
    color: Color,
    contentColor: Color,
    shape: RoundedCornerShape,
    onPlay: (ChatMessage) -> Unit,
) {
    Surface(shape = shape, color = color, contentColor = contentColor, modifier = Modifier.clickable { onPlay(message) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(10.dp)) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(contentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.MusicNote, null) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.widthIn(max = 170.dp)) {
                Text(message.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Tap to play for everyone", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.75f))
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Rounded.PlayArrow, "Play")
        }
    }
}

@Composable
fun Avatar(url: String?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(size * 0.6f))
        } else {
            AsyncImage(model = url, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size))
        }
    }
}
