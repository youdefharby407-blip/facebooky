package com.yousef.facebooky.ui.chat

import android.Manifest
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
        containerColor = MaterialTheme.colorScheme.background,
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
                modifier = Modifier.weight(1f),
            )

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
    modifier: Modifier = Modifier,
) {
    val messages = vm.messages
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
                voice = voice,
                onToggleVoice = { if (it.type == MessageType.VOICE) vm.voicePlayer.toggle(it.id, it.mediaUrl) },
                onOpenImage = onOpenImage,
                onPlaySong = vm::playSongFromMessage,
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
    var menu by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasProfile = profile?.isComplete == true
            if (hasProfile) {
                Avatar(profile!!.displayPhoto, 38.dp, Modifier.clickable(onClick = onProfile))
            } else {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.ChatBubble, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(19.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (hasProfile) "Welcome, ${profile!!.name}" else "FaceBooky",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ConnectionLine(connection, locked, showAppName = hasProfile)
            }
            IconButton(onClick = onMusic) {
                Icon(
                    Icons.Rounded.MusicNote, "Music",
                    tint = if (musicActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Rounded.Call, "Call", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Voice call") },
                        leadingIcon = { Icon(Icons.Rounded.Call, null) },
                        onClick = { menu = false; onVoiceCall() },
                    )
                    DropdownMenuItem(
                        text = { Text("Video call") },
                        leadingIcon = { Icon(Icons.Rounded.Videocam, null) },
                        onClick = { menu = false; onVideoCall() },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionLine(connection: ConnectionStatus, locked: Boolean, showAppName: Boolean) {
    Crossfade(targetState = connection to locked, label = "connection") { (c, isLocked) ->
        val (status, color) = when {
            c == ConnectionStatus.OFFLINE -> "Offline" to MaterialTheme.colorScheme.error
            isLocked -> "Locked" to MaterialTheme.colorScheme.error
            c == ConnectionStatus.CONNECTED -> "Connected" to Color(0xFF22C55E)
            else -> "Connecting…" to Color(0xFFF59E0B)
        }
        val label = if (showAppName) "FaceBooky · $status" else status
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
        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
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
