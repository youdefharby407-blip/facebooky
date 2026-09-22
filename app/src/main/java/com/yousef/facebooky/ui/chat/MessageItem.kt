@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.yousef.facebooky.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import com.yousef.facebooky.ui.icons.AppIcons
import com.yousef.facebooky.ui.theme.TheirBubble
import androidx.compose.ui.text.font.FontStyle
import com.yousef.facebooky.util.formatTime
import java.text.SimpleDateFormat
import java.util.Locale

private val MineShape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
private val TheirsShape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageItem(
    message: ChatMessage,
    isMine: Boolean,
    showSender: Boolean,
    senderName: String,
    senderPhoto: String,
    myUid: String?,
    voice: VoicePlaybackState,
    onToggleVoice: (ChatMessage) -> Unit,
    onOpenImage: (String) -> Unit,
    onPlaySong: (ChatMessage) -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    onQuoteClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    replyTargetDeleted: Boolean = false,
    senderIsAdmin: Boolean = false,
    onReply: (ChatMessage) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    seen: Boolean = false,
) {
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val longPress = { onLongPress(message) }
    // Swipe a message sideways (like WhatsApp) to reply to it.
    val dragX = remember(message.id) { mutableFloatStateOf(0f) }
    val dragXAnim = animateFloatAsState(dragX.floatValue, label = "swipe")
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = if (showSender) 10.dp else 2.dp)
            .offset { IntOffset(dragXAnim.value.roundToInt(), 0) }
            .pointerInput(message.id) {
                var fired = false
                detectHorizontalDragGestures(
                    onDragStart = { fired = false },
                    onDragEnd = {
                        dragX.floatValue = 0f
                    },
                    onDragCancel = { dragX.floatValue = 0f },
                ) { change, delta ->
                    dragX.floatValue = (dragX.floatValue + delta).coerceIn(-150f, 150f)
                    val d = dragX.floatValue
                    if (!fired && message.type != MessageType.DELETED && kotlin.math.abs(d) > 55f) {
                        // Toward the phone edge (wall) = react; toward the center (outward) = reply.
                        val towardWall = if (isMine) d > 0 else d < 0
                        fired = true
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (towardWall) onLongPress(message) else onReply(message)
                    }
                    change.consume()
                }
            },
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isMine) {
            if (showSender) Avatar(senderPhoto, 30.dp, Modifier.clickable { onAvatarClick(message.senderUid) }) else Spacer(Modifier.width(30.dp))
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 290.dp),
        ) {
            if (showSender && !isMine) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                    Text(
                        senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    if (senderIsAdmin) AdminTag()
                }
            } else if (isMine && senderIsAdmin && showSender) {
                Row(Modifier.padding(bottom = 2.dp)) { AdminTag() }
            }
            val bubbleColor = if (isMine) MaterialTheme.colorScheme.primary else TheirBubble
            val contentColor = if (isMine) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            val shape = if (isMine) MineShape else TheirsShape
            val hasQuote = message.replyToId.isNotBlank() && message.type != MessageType.DELETED
            val quoteText = if (replyTargetDeleted) "Deleted message" else message.replyToText
            if (hasQuote && message.type != MessageType.TEXT) {
                ReplyQuote(
                    name = message.replyToName, text = quoteText,
                    color = TheirBubble, contentColor = MaterialTheme.colorScheme.onSurface,
                    onClick = { onQuoteClick(message.replyToId) },
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            when (message.type) {
                MessageType.IMAGE -> AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Photo",
                    contentScale = ContentScale.Crop,
                    placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier
                        .size(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = { onOpenImage(message.mediaUrl) }, onLongClick = longPress),
                )
                MessageType.STICKER -> AsyncImage(
                    model = message.mediaUrl,
                    contentDescription = "Sticker",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(120.dp)
                        .combinedClickable(onClick = {}, onLongClick = longPress),
                )
                MessageType.VIDEO -> Box(
                    Modifier
                        .size(230.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .combinedClickable(onClick = {}, onLongClick = longPress),
                ) {
                    BlobVideo(message.mediaUrl, Modifier.fillMaxSize(), loop = false, muted = false, autoPlay = false)
                    Icon(Icons.Rounded.PlayArrow, "Play", tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.align(Alignment.Center).size(48.dp))
                }
                MessageType.VIDEO_STICKER -> Box(
                    Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(onClick = {}, onLongClick = longPress),
                ) {
                    BlobVideo(message.mediaUrl, Modifier.fillMaxSize(), loop = true, muted = true, autoPlay = true)
                }
                MessageType.VOICE -> VoiceBubble(message, voice, bubbleColor, contentColor, shape, onToggleVoice, longPress)
                MessageType.MUSIC -> MusicBubble(message, bubbleColor, contentColor, shape, onPlaySong, longPress)
                MessageType.DELETED -> Surface(
                    shape = shape,
                    color = TheirBubble,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = longPress),
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Ban, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isMine) "You deleted this message" else "This message was deleted",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                        )
                    }
                }
                else -> Surface(
                    shape = shape, color = bubbleColor, contentColor = contentColor,
                    modifier = Modifier.combinedClickable(onClick = {}, onLongClick = longPress),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(IntrinsicSize.Max)) {
                        if (hasQuote) {
                            ReplyQuote(
                                name = message.replyToName, text = quoteText,
                                color = contentColor.copy(alpha = 0.12f), contentColor = contentColor,
                                onClick = { onQuoteClick(message.replyToId) },
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            )
                        }
                        Text(message.text.ifBlank { "Unsupported message" }, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            if (message.reactions.isNotEmpty() && message.type != MessageType.DELETED) {
                Reactions(message.reactions, myUid, onClick = longPress)
            }
            val time = message.timestamp?.let { timeFormat.format(it) }.orEmpty()
            val label = buildString {
                append(time)
                if (message.edited && message.type != MessageType.DELETED) append(" · edited")
                if (message.pending && isMine) append(" · sending")
                else if (isMine && seen) append(" · seen")
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine && seen) MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun ReplyQuote(
    name: String,
    text: String,
    color: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color)
            .clickable(onClick = onClick)
            .height(IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(contentColor.copy(alpha = 0.8f))
        )
        Column(Modifier.padding(horizontal = 8.dp, vertical = 5.dp)) {
            Text(name, style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text, style = MaterialTheme.typography.bodySmall, color = contentColor.copy(alpha = 0.8f),
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun Reactions(reactions: Map<String, String>, myUid: String?, onClick: () -> Unit) {
    val counts = reactions.values.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
    val mine = myUid != null && reactions.containsKey(myUid)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant,
        ),
        modifier = Modifier
            .padding(top = 2.dp)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            counts.take(4).forEach { (emoji, n) ->
                Text(emoji, style = MaterialTheme.typography.bodyMedium)
                if (n > 1) Text("$n", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 2.dp))
            }
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
    onLongPress: () -> Unit,
) {
    val active = voice.messageId == message.id
    val duration = if (active && voice.durationMs > 0) voice.durationMs else message.durationMs
    val progress = if (active && duration > 0) (voice.positionMs.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Surface(
        shape = shape, color = color, contentColor = contentColor,
        modifier = Modifier.combinedClickable(onClick = { onToggle(message) }, onLongClick = onLongPress),
    ) {
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
    onLongPress: () -> Unit,
) {
    Surface(
        shape = shape, color = color, contentColor = contentColor,
        modifier = Modifier.combinedClickable(onClick = { onPlay(message) }, onLongClick = onLongPress),
    ) {
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
