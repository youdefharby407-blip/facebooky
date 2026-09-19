package com.yousef.facebooky.ui.chat

import android.Manifest
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yousef.facebooky.R
import com.yousef.facebooky.ui.icons.AppIcons
import com.yousef.facebooky.ui.theme.BarColor
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import com.yousef.facebooky.data.model.ChatMessage
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.yousef.facebooky.data.model.UserProfile
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.yousef.facebooky.data.model.ConnectionStatus
import com.yousef.facebooky.data.model.MessageType
import com.yousef.facebooky.ui.ChatViewModel
import com.yousef.facebooky.ui.rememberPermissionRequester
import com.yousef.facebooky.ui.sheets.AttachSheet
import com.yousef.facebooky.ui.sheets.MusicSheet

@Composable
fun ChatScreen(vm: ChatViewModel) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.events.collect { snackbar.showSnackbar(it) } }

    val permissions = rememberPermissionRequester()
    val keyboard = LocalSoftwareKeyboardController.current
    var showAttach by remember { mutableStateOf(false) }
    var showMusic by rememberSaveable { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<ChatMessage?>(null) }
    val clipboard = LocalClipboardManager.current

    val voice by vm.voicePlayer.state.collectAsStateWithLifecycle()
    val shared by vm.music.shared.collectAsStateWithLifecycle()
    val muted by vm.music.muted.collectAsStateWithLifecycle()

    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendImage(uri)
    }
    val pickSticker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendSticker(uri)
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.addMusic(uri)
    }

    fun startCall(video: Boolean) {
        if (!vm.ensureProfile()) return
        val perms = if (video) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
        else arrayOf(Manifest.permission.RECORD_AUDIO)
        permissions.request(perms) { vm.startCall(video) }
    }

    actionsFor?.let { m ->
        MessageActionsSheet(
            message = m,
            myReaction = vm.myUid?.let { m.reactions[it] },
            isMine = m.senderUid == vm.myUid,
            onReact = { vm.react(m, it); actionsFor = null },
            onReply = { vm.startReply(m); actionsFor = null },
            onCopy = { clipboard.setText(AnnotatedString(m.text)); actionsFor = null },
            onDeleteForMe = { vm.deleteForMe(m); actionsFor = null },
            onDeleteForEveryone = { vm.deleteForEveryone(m); actionsFor = null },
            onDismiss = { actionsFor = null },
        )
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
    Image(
        painterResource(R.drawable.chat_bg), null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22000000), Color(0x88000000))))
    )
    Scaffold(
        topBar = {
            ChatHeader(
                profile = vm.profile,
                locked = vm.chatLocked,
                onProfile = { vm.openProfileEditor() },
                connection = vm.connection,
                musicActive = shared?.playing == true,
                onMusic = { showMusic = true },
                onVoiceCall = { startCall(false) },
                onVideoCall = { startCall(true) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
        ) {
            AnimatedVisibility(vm.uploadsInProgress > 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
            }
            val nowPlaying = shared
            AnimatedVisibility(
                visible = nowPlaying?.playing == true,
                enter = expandVertically(), exit = shrinkVertically(),
            ) {
                NowPlayingStrip(
                    title = nowPlaying?.songTitle.orEmpty(),
                    muted = muted,
                    onToggleMute = vm::toggleMute,
                    onOpen = { showMusic = true },
                )
            }

            MessageList(
                vm = vm,
                voice = voice,
                onOpenImage = { viewingImage = it },
                onLongPress = { actionsFor = it },
                modifier = Modifier.weight(1f),
            )

            val replying = vm.replyingTo
            AnimatedVisibility(replying != null, enter = expandVertically(), exit = shrinkVertically()) {
                val m = replying ?: return@AnimatedVisibility
                ReplyStrip(
                    name = if (m.senderUid == vm.myUid) "You" else vm.nameOf(m),
                    text = m.preview,
                    onCancel = vm::cancelReply,
                )
            }

            InputBar(
                text = vm.draft,
                onTextChange = { vm.draft = it },
                onSend = vm::sendDraft,
                emojiOpen = showEmoji,
                onToggleEmoji = {
                    showEmoji = !showEmoji
                    if (showEmoji) keyboard?.hide() else keyboard?.show()
                },
                onAttach = { showAttach = true },
                onMic = {
                    if (vm.ensureProfile()) permissions.request(arrayOf(Manifest.permission.RECORD_AUDIO)) { vm.startRecording() }
                },
                recordingStartedAt = vm.recordingStartedAt,
                onCancelRecording = vm::cancelRecording,
                onSendRecording = vm::finishRecording,
            )
            AnimatedVisibility(showEmoji, enter = expandVertically(), exit = shrinkVertically()) {
                EmojiPanel(onEmoji = { vm.draft += it })
            }
        }
    }

    }

    if (showAttach) {
        AttachSheet(
            onDismiss = { showAttach = false },
            onPhoto = { showAttach = false; pickImage.launch(imageOnly) },
            onSticker = { showAttach = false; pickSticker.launch(imageOnly) },
            onMusic = { showAttach = false; showMusic = true },
            onProfile = { showAttach = false; vm.openProfileEditor() },
        )
    }
    if (showMusic) {
        MusicSheet(
            vm = vm,
            onAddMusic = { pickAudio.launch(arrayOf("audio/*")) },
            onDismiss = { showMusic = false },
        )
    }
    viewingImage?.let { url ->
        Dialog(onDismissRequest = { viewingImage = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { viewingImage = null },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(model = url, contentDescription = "Photo", modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun MessageList(
    vm: ChatViewModel,
    voice: com.yousef.facebooky.audio.VoicePlaybackState,
    onOpenImage: (String) -> Unit,
    onLongPress: (ChatMessage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val messages = vm.messages
    val scope = rememberCoroutineScope()
    val myUid = vm.myUid
    val listState = rememberLazyListState()
    val newest = messages.lastOrNull()

    // Keep the newest message in view when I send, or when I'm already near the bottom.
    LaunchedEffect(newest?.id) {
        if (newest != null && (newest.senderUid == myUid || listState.firstVisibleItemIndex <= 2)) {
            listState.animateScrollToItem(0)
        }
    }

    if (messages.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                when {
                    vm.chatLocked -> "Chat is locked.\nPublish the Firestore rules in Firebase Console."
                    vm.connection == ConnectionStatus.OFFLINE -> "You're offline.\nMessages will appear when you reconnect."
                    vm.connection == ConnectionStatus.CONNECTED -> "No messages yet. Say hi 👋"
                    else -> "Loading chat…"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val newestFirst = messages.asReversed()
    val byId = remember(messages) { messages.associateBy { it.id } }
    LazyColumn(
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        itemsIndexed(newestFirst, key = { _, m -> m.id }) { index, m ->
            val older = newestFirst.getOrNull(index + 1)
            val isMine = m.senderUid == myUid
            MessageItem(
                message = m,
                isMine = isMine,
                showSender = older == null || older.senderUid != m.senderUid,
                senderName = vm.nameOf(m),
                senderPhoto = vm.photoOf(m),
                myUid = myUid,
                voice = voice,
                onToggleVoice = { if (it.type == MessageType.VOICE) vm.voicePlayer.toggle(it.id, it.mediaUrl) },
                onOpenImage = onOpenImage,
                onPlaySong = vm::playSongFromMessage,
                onLongPress = onLongPress,
                replyTargetDeleted = m.replyToId.isNotBlank() &&
                    byId[m.replyToId]?.type == MessageType.DELETED,
                onQuoteClick = { id ->
                    val target = newestFirst.indexOfFirst { it.id == id }
                    if (target >= 0) scope.launch { listState.animateScrollToItem(target) }
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun ChatHeader(
    profile: UserProfile?,
    locked: Boolean,
    onProfile: () -> Unit,
    connection: ConnectionStatus,
    musicActive: Boolean,
    onMusic: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
) {
    Surface(color = BarColor, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(62.dp)
                .padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasProfile = profile?.isComplete == true
            if (hasProfile) {
                Avatar(profile!!.displayPhoto, 40.dp, Modifier.clickable(onClick = onProfile))
            } else {
                Image(
                    painterResource(R.mipmap.ic_launcher_foreground), "My Space",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (hasProfile) "Welcome, ${profile!!.name}" else "My Space",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ConnectionLine(connection, locked, prefix = if (hasProfile) "My Space" else null)
            }
            HeaderButton(AppIcons.Music, "Music", onMusic, tint = if (musicActive) MaterialTheme.colorScheme.primary else null)
            HeaderButton(AppIcons.Video, "Video call", onVideoCall)
            HeaderButton(AppIcons.Phone, "Voice call", onVoiceCall)
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color? = null) {
    IconButton(onClick = onClick) {
        Icon(icon, label, Modifier.size(22.dp), tint = tint ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ConnectionLine(connection: ConnectionStatus, locked: Boolean, prefix: String?) {
    Crossfade(targetState = connection to locked, label = "connection") { (c, isLocked) ->
        val (status, color) = when {
            c == ConnectionStatus.OFFLINE -> "Offline" to MaterialTheme.colorScheme.error
            isLocked -> "Locked" to MaterialTheme.colorScheme.error
            c == ConnectionStatus.CONNECTED -> "Connected" to Color(0xFF22C55E)
            else -> "Connecting…" to Color(0xFFF59E0B)
        }
        val label = if (prefix != null) "$prefix · $status" else status
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NowPlayingStrip(title: String, muted: Boolean, onToggleMute: () -> Unit, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(AppIcons.Music, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleMute) {
            Icon(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                if (muted) "Unmute" else "Mute",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}


@Composable
private fun ReplyStrip(name: String, text: String, onCancel: () -> Unit) {
    Surface(color = BarColor) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 4.dp, top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Replying to $name", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(text, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onCancel) { Icon(AppIcons.Close, "Cancel reply", Modifier.size(20.dp)) }
        }
    }
}

private val QuickReactions = listOf("❤️", "😂", "😮", "😢", "👍", "🙏")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MessageActionsSheet(
    message: ChatMessage,
    myReaction: String?,
    isMine: Boolean,
    onReact: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onDeleteForMe: () -> Unit,
    onDeleteForEveryone: () -> Unit,
    onDismiss: () -> Unit,
) {
    val deleted = message.type == MessageType.DELETED
    var confirmEveryone by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            if (!deleted) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    QuickReactions.forEach { emoji ->
                        val selected = emoji == myReaction
                        Box(
                            Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { onReact(emoji) },
                            contentAlignment = Alignment.Center,
                        ) { Text(emoji, style = MaterialTheme.typography.headlineSmall) }
                    }
                }
                Spacer(Modifier.height(12.dp))
                ActionRow(AppIcons.Reply, "Reply", onReply)
                if (message.type == MessageType.TEXT) ActionRow(AppIcons.Copy, "Copy", onCopy)
            }
            ActionRow(AppIcons.EyeOff, "Delete for me", onDeleteForMe)
            if (isMine && !deleted) {
                if (confirmEveryone) {
                    ActionRow(AppIcons.Trash, "Tap again to delete for everyone", onDeleteForEveryone, danger = true)
                } else {
                    ActionRow(AppIcons.Trash, "Delete for everyone", { confirmEveryone = true }, danger = true)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit, danger: Boolean = false) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}
