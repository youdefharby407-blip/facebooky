package com.yousef.facebooky.ui.chat

import com.yousef.facebooky.ui.icons.AppIcons
import com.yousef.facebooky.ui.theme.BarColor
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
    onMicStart: () -> Unit,
    onMicRelease: () -> Unit,   // released without lock/cancel -> send
    onMicLock: () -> Unit,      // dragged up -> hands-free
    onMicCancel: () -> Unit,    // dragged left -> discard
    recordingStartedAt: Long?,
    recordingLocked: Boolean,
    recordingPaused: Boolean,
    onTogglePause: () -> Unit,
    onCancelRecording: () -> Unit,
    onSendRecording: () -> Unit,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
) {
    Surface(color = BarColor, contentColor = MaterialTheme.colorScheme.onSurface) {
        AnimatedContent(
            targetState = recordingStartedAt,
            transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
            label = "inputMode",
        ) { startedAt ->
            if (startedAt != null) {
                if (recordingLocked) {
                    LockedRecordingRow(startedAt, recordingPaused, onTogglePause, onCancelRecording, onSendRecording)
                } else {
                    HoldingRow(startedAt)
                }
            } else {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    IconButton(onClick = onToggleEmoji) {
                        Icon(if (emojiOpen) AppIcons.Keyboard else AppIcons.Smile, "Emoji", Modifier.size(24.dp))
                    }
                    IconButton(onClick = onAttach) { Icon(AppIcons.Plus, "Attach", Modifier.size(24.dp)) }
                    Box(
                        Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (it.isFocused) onFocused() },
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    val hasText = text.isNotBlank()
                    if (hasText) {
                        FilledIconButton(onClick = onSend, modifier = Modifier.size(44.dp)) {
                            Icon(AppIcons.Send, "Send", Modifier.size(20.dp))
                        }
                    } else {
                        HoldMicButton(onMicStart, onMicRelease, onMicLock, onMicCancel)
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
        }
    }
}

@Composable
private fun HoldMicButton(
    onStart: () -> Unit,
    onRelease: () -> Unit,
    onLock: () -> Unit,
    onCancel: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onStart()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    var decided = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val dx = change.position.x - down.position.x
                        val dy = change.position.y - down.position.y
                        if (!decided) {
                            if (dx < -90f) { decided = true; onCancel(); break }        // slide left = cancel
                            if (dy < -90f) { decided = true; onLock(); break }           // slide up = hands-free
                        }
                        if (change.changedToUp()) { onRelease(); return@awaitEachGesture } // release = send
                        change.consume()
                    }
                    // If we broke out on cancel/lock, wait for the finger to lift quietly.
                    if (decided) {
                        while (true) {
                            val e = awaitPointerEvent()
                            val c = e.changes.firstOrNull { it.id == down.id } ?: break
                            if (c.changedToUp()) break
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(AppIcons.Mic, "Hold to record", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp))
    }
}

/** Shown while the finger is held down (before locking). */
@Composable
private fun HoldingRow(startedAt: Long) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt) { while (true) { now = SystemClock.elapsedRealtime(); delay(200) } }
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot",
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        Spacer(Modifier.width(10.dp))
        Text(formatTime(now - startedAt), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        Text("← slide to cancel  ·  slide up to lock ↑",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Hands-free recording: pause/resume, delete, send. */
@Composable
private fun LockedRecordingRow(
    startedAt: Long,
    paused: Boolean,
    onTogglePause: () -> Unit,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(startedAt, paused) { while (!paused) { now = SystemClock.elapsedRealtime(); delay(200) } }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCancel) { Icon(AppIcons.Trash, "Delete", tint = MaterialTheme.colorScheme.error) }
        if (!paused) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
        }
        Spacer(Modifier.width(8.dp))
        Text(formatTime(now - startedAt), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onTogglePause) {
            Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                if (paused) "Resume" else "Pause", Modifier.size(26.dp))
        }
        Spacer(Modifier.width(6.dp))
        FilledIconButton(onClick = onSend, modifier = Modifier.size(44.dp)) { Icon(AppIcons.Send, "Send voice", Modifier.size(20.dp)) }
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
    Surface(color = BarColor, contentColor = MaterialTheme.colorScheme.onSurface) {
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
