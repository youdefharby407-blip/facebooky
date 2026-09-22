package com.yousef.facebooky.ui.chat

import android.Manifest
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.yousef.facebooky.R
import com.yousef.facebooky.ui.icons.AppIcons
import com.yousef.facebooky.ui.theme.BarColor
import com.yousef.facebooky.ui.theme.chatThemeById
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
import androidx.compose.ui.focus.FocusRequester
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
    val inputFocus = remember { FocusRequester() }
    var showAttach by remember { mutableStateOf(false) }
    var showMusic by rememberSaveable { mutableStateOf(false) }
    var showEmoji by remember { mutableStateOf(false) }
    var viewingImage by remember { mutableStateOf<String?>(null) }
    var actionsFor by remember { mutableStateOf<ChatMessage?>(null) }
    var confirmClear by remember { mutableStateOf<ClearMode?>(null) }
    var showRooms by remember { mutableStateOf(false) }
    var showTheme by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showAddMember by remember { mutableStateOf(false) }
    var showAdminLogin by remember { mutableStateOf(false) }
    val pickBackground = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.setRoomBackground(it) }
    }
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
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.sendVideo(uri)
    }
    var trimUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val pickVideoSticker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) trimUri = uri
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

    when (confirmClear) {
        ClearMode.ME -> AlertDialog(
            onDismissRequest = { confirmClear = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = { Text("Clear chat for you?") },
            text = { Text("All current messages disappear on this phone only. Others still see them.") },
            confirmButton = {
                TextButton(onClick = { vm.clearChatForMe(); confirmClear = null }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmClear = null }) { Text("Cancel") } },
        )
        ClearMode.EVERYONE -> ClearForEveryoneDialog(
            onClear = vm::clearChatForEveryone,
            onClose = { confirmClear = null },
        )
        null -> Unit
    }

    actionsFor?.let { m ->
        MessageActionsSheet(
            message = m,
            myReaction = vm.myUid?.let { m.reactions[it] },
            isMine = m.senderUid == vm.myUid,
            onReact = { vm.react(m, it); actionsFor = null },
            onReply = { vm.startReply(m); actionsFor = null },
            onEdit = { vm.startEdit(m); actionsFor = null },
            onCopy = { clipboard.setText(AnnotatedString(m.text)); actionsFor = null },
            onDeleteForMe = { vm.deleteForMe(m); actionsFor = null },
            onDeleteForEveryone = { vm.deleteForEveryone(m); actionsFor = null },
            onDismiss = { actionsFor = null },
        )
    }

    val theme = chatThemeById(vm.roomTheme)
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(theme.colors))) {
    if (vm.roomBackground.isNotBlank()) {
        AsyncImage(
            model = vm.roomBackground, contentDescription = null,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x66000000), Color(0x22000000), Color(0x99000000))))
        )
    } else if (vm.roomTheme == "wallpaper") {
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
    }
    Scaffold(
        topBar = {
            ChatHeader(
                vm = vm,
                profile = vm.profile,
                peer = vm.peer,
                room = vm.currentRoom,
                inPrivateRoom = vm.roomId != "main",
                locked = vm.chatLocked,
                onProfile = { vm.openProfileEditor() },
                onBackToLobby = { vm.openLobby() },
                onSecretAdmin = { showAdminLogin = true },
                onOpenHeaderTarget = {
                    if (vm.currentRoom?.isGroup == true) showMembers = true
                    else vm.peer?.let { vm.showPersonCard(it.uid) }
                },
                connection = vm.connection,
                musicActive = shared?.playing == true,
                onMusic = { showMusic = true },
                onVoiceCall = { startCall(false) },
                onVideoCall = { startCall(true) },
                onRooms = { showRooms = true },
                onTheme = { showTheme = true },
                onAddMembers = { showAddMember = true },
                onSetBackground = { pickBackground.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onViewMembers = { showMembers = true },
                onClearForMe = { confirmClear = ClearMode.ME },
                onClearForEveryone = { confirmClear = ClearMode.EVERYONE },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
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

            val editingMsg = vm.editing
            AnimatedVisibility(editingMsg != null, enter = expandVertically(), exit = shrinkVertically()) {
                Surface(color = BarColor) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(34.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("تعديل الرسالة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text(editingMsg?.text.orEmpty(), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { vm.cancelEdit() }) { Icon(AppIcons.Close, "Cancel", Modifier.size(20.dp)) }
                    }
                }
            }
            LaunchedEffect(editingMsg?.id) {
                if (editingMsg != null) { showEmoji = false; inputFocus.requestFocus(); keyboard?.show() }
            }
            val replying = vm.replyingTo
            LaunchedEffect(replying?.id) {
                if (replying != null) {
                    showEmoji = false
                    inputFocus.requestFocus()
                    keyboard?.show()
                }
            }
            AnimatedVisibility(replying != null, enter = expandVertically(), exit = shrinkVertically()) {
                val m = replying ?: return@AnimatedVisibility
                ReplyStrip(
                    name = if (m.senderUid == vm.myUid) "You" else vm.nameOf(m),
                    text = m.preview,
                    onCancel = { vm.cancelReply(); keyboard?.hide() },
                )
            }

            InputBar(
                text = vm.draft,
                onTextChange = { vm.onDraftChange(it) },
                onSend = vm::sendDraft,
                emojiOpen = showEmoji,
                onToggleEmoji = {
                    if (showEmoji) {
                        showEmoji = false
                        inputFocus.requestFocus()
                        keyboard?.show()
                    } else {
                        keyboard?.hide()
                        showEmoji = true
                    }
                },
                onAttach = { keyboard?.hide(); showEmoji = false; showAttach = true },
                onMicStart = {
                    if (vm.ensureProfile()) permissions.request(arrayOf(Manifest.permission.RECORD_AUDIO)) { vm.startRecording() }
                },
                onMicRelease = vm::finishRecording,
                onMicLock = vm::lockRecording,
                onMicCancel = vm::cancelRecording,
                recordingStartedAt = vm.recordingStartedAt,
                recordingLocked = vm.recordingLocked,
                recordingPaused = vm.recordingPaused,
                onTogglePause = vm::toggleRecordingPause,
                onCancelRecording = vm::cancelRecording,
                onSendRecording = vm::finishRecording,
                focusRequester = inputFocus,
                onFocused = { showEmoji = false },
            )
            AnimatedVisibility(showEmoji, enter = expandVertically(), exit = shrinkVertically()) {
                EmojiPanel(onEmoji = { vm.draft += it })
            }
        }
    }

    }

    if (showRooms) {
        RoomsSheet(vm = vm, onDismiss = { showRooms = false })
    }
    if (showTheme) {
        ThemeSheet(current = vm.roomTheme, onPick = { vm.changeRoomTheme(it) }, onDismiss = { showTheme = false })
    }
    vm.infoCard?.let { p ->
        PersonCardDialog(profile = p, onDismiss = { vm.dismissPersonCard() })
    }
    if (showMembers) {
        MembersSheet(vm = vm, onAdd = { showMembers = false; showAddMember = true }, onDismiss = { showMembers = false })
    }
    if (showAddMember) {
        AddMemberDialog(onAdd = vm::addMemberById, onClose = { showAddMember = false })
    }
    trimUri?.let { uri ->
        VideoStickerTrimmer(
            uri = uri,
            onConfirm = { start, end -> vm.sendVideoSticker(uri, start, end); trimUri = null },
            onDismiss = { trimUri = null },
        )
    }
    if (showAdminLogin) {
        AdminLoginDialog(
            onLogin = vm::loginAdmin,
            onClose = { showAdminLogin = false },
        )
    }
    if (vm.showAdmin) {
        AdminScreen(vm = vm, onClose = { vm.closeAdmin() })
    }

    if (showAttach) {
        AttachSheet(
            onDismiss = { showAttach = false },
            onPhoto = { showAttach = false; pickImage.launch(imageOnly) },
            onVideo = { showAttach = false; pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
            onSticker = { showAttach = false; pickSticker.launch(imageOnly) },
            onVideoSticker = { showAttach = false; pickVideoSticker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
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
        // Empty chat = just the wallpaper. Only a real problem is explained.
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (vm.chatLocked) Text(
                "Chat is locked.\nPublish the Firestore rules in Firebase Console.",
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
                senderIsAdmin = vm.isAdminSender(m.senderUid),
                onReply = { vm.startReply(it) },
                onAvatarClick = { vm.showPersonCard(it) },
                seen = vm.isSeenByOthers(m),
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
    vm: ChatViewModel,
    profile: UserProfile?,
    peer: UserProfile?,
    room: com.yousef.facebooky.data.model.RoomInfo?,
    inPrivateRoom: Boolean,
    locked: Boolean,
    onProfile: () -> Unit,
    onBackToLobby: () -> Unit,
    onSecretAdmin: () -> Unit,
    onOpenHeaderTarget: () -> Unit,
    connection: ConnectionStatus,
    musicActive: Boolean,
    onMusic: () -> Unit,
    onVoiceCall: () -> Unit,
    onVideoCall: () -> Unit,
    onRooms: () -> Unit,
    onTheme: () -> Unit,
    onAddMembers: () -> Unit,
    onSetBackground: () -> Unit,
    onViewMembers: () -> Unit,
    onClearForMe: () -> Unit,
    onClearForEveryone: () -> Unit,
) {
    val isGroup = room?.isGroup == true
    var menu by remember { mutableStateOf(false) }
    // Secret admin door: tap the "Welcome"/title area 10 times quickly.
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    Surface(color = BarColor, contentColor = MaterialTheme.colorScheme.onSurface) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(62.dp)
                .padding(start = 6.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val hasProfile = profile?.isComplete == true
            if (inPrivateRoom) {
                IconButton(onClick = onBackToLobby) { Icon(AppIcons.Reply, "Back", Modifier.size(22.dp)) }
            } else {
                Spacer(Modifier.width(8.dp))
            }
            // Avatar: group = stacked member photos; private = the peer; lobby = me.
            val headerClick = if (inPrivateRoom) onOpenHeaderTarget else onProfile
            if (isGroup) {
                val photos = vm.currentMembers().filter { it.uid != vm.myUid }.mapNotNull { it.photoUrl.takeIf { p -> p.isNotBlank() } }
                GroupAvatar(photos, Modifier.clickable(onClick = headerClick))
            } else {
                val shownPhoto = if (inPrivateRoom) peer?.photoUrl.orEmpty() else profile?.displayPhoto.orEmpty()
                if (shownPhoto.isNotBlank()) {
                    Avatar(shownPhoto, 40.dp, Modifier.clickable(onClick = headerClick))
                } else {
                    Image(
                        painterResource(R.mipmap.ic_launcher_foreground), "My Space",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (inPrivateRoom) {
                            onOpenHeaderTarget()
                        } else {
                            val now = System.currentTimeMillis()
                            taps = if (now - lastTap < 700) taps + 1 else 1
                            lastTap = now
                            if (taps >= 10) {
                                taps = 0
                                onSecretAdmin()
                            }
                        }
                    },
            ) {
                val title = when {
                    isGroup -> vm.currentMembers().filter { it.uid != vm.myUid }.joinToString(", ") { it.name.ifBlank { "…" } }.ifBlank { "Group" }
                    inPrivateRoom -> peer?.name?.takeIf { it.isNotBlank() } ?: "Chat"
                    hasProfile -> "Welcome, ${profile!!.name}"
                    else -> "My Space"
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (inPrivateRoom && !isGroup) {
                    val peerUid = peer?.uid
                    val sub = when {
                        locked -> "Locked"
                        vm.peerTyping -> "يكتب الآن…"
                        vm.isOnline(peerUid) -> "متصل الآن"
                        vm.lastSeenMs(peerUid) > 0 -> "آخر ظهور " + formatLastSeen(vm.lastSeenMs(peerUid))
                        else -> "My Space"
                    }
                    val col = when {
                        vm.peerTyping || vm.isOnline(peerUid) -> Color(0xFF22C55E)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Text(sub, style = MaterialTheme.typography.labelMedium, color = col, maxLines = 1, overflow = TextOverflow.Ellipsis)
                } else {
                    ConnectionLine(connection, locked, prefix = if (inPrivateRoom) "My Space" else if (hasProfile) "My Space" else null)
                }
            }
            HeaderButton(AppIcons.Music, "Music", onMusic, tint = if (musicActive) MaterialTheme.colorScheme.primary else null)
            HeaderButton(AppIcons.Video, "Video call", onVideoCall)
            HeaderButton(AppIcons.Phone, "Voice call", onVoiceCall)
            Box {
                HeaderButton(AppIcons.More, "More", { menu = true })
                DropdownMenu(
                    expanded = menu,
                    onDismissRequest = { menu = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    DropdownMenuItem(
                        text = { Text("View chats / My ID") },
                        leadingIcon = { Icon(AppIcons.AddUser, null, Modifier.size(20.dp)) },
                        onClick = { menu = false; onRooms() },
                    )
                    if (inPrivateRoom) {
                        DropdownMenuItem(
                            text = { Text("Add new members") },
                            leadingIcon = { Icon(AppIcons.AddUser, null, Modifier.size(20.dp)) },
                            onClick = { menu = false; onAddMembers() },
                        )
                        DropdownMenuItem(
                            text = { Text("Set background image") },
                            leadingIcon = { Icon(AppIcons.User, null, Modifier.size(20.dp)) },
                            onClick = { menu = false; onSetBackground() },
                        )
                        if (isGroup) DropdownMenuItem(
                            text = { Text("Members") },
                            leadingIcon = { Icon(AppIcons.User, null, Modifier.size(20.dp)) },
                            onClick = { menu = false; onViewMembers() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(if (hasProfile) "Edit profile" else "Set up profile") },
                        leadingIcon = { Icon(AppIcons.User, null, Modifier.size(20.dp)) },
                        onClick = { menu = false; onProfile() },
                    )
                    DropdownMenuItem(
                        text = { Text("Chat theme") },
                        leadingIcon = { Icon(AppIcons.Smile, null, Modifier.size(20.dp)) },
                        onClick = { menu = false; onTheme() },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear chat for me") },
                        leadingIcon = { Icon(AppIcons.Eraser, null, Modifier.size(20.dp)) },
                        onClick = { menu = false; onClearForMe() },
                    )
                    DropdownMenuItem(
                        text = { Text("Clear chat for everyone", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(AppIcons.Trash, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; onClearForEveryone() },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color? = null) {
    IconButton(onClick = onClick, modifier = Modifier.size(42.dp)) {
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
    Surface(color = BarColor, contentColor = MaterialTheme.colorScheme.onSurface) {
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
    onEdit: () -> Unit,
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
                if (isMine && message.type == MessageType.TEXT) ActionRow(AppIcons.Copy, "Edit", onEdit)
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

private enum class ClearMode { ME, EVERYONE }

@Composable
private fun ClearForEveryoneDialog(onClear: (String, (String?) -> Unit) -> Unit, onClose: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text("Clear chat for everyone?") },
        text = {
            Column {
                Text("All messages disappear for everyone. Enter the admin password to continue.")
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = password.isNotBlank() && !busy,
                onClick = {
                    busy = true
                    onClear(password) { result ->
                        busy = false
                        if (result == null) onClose() else error = result
                    }
                },
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Clear for everyone", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onClose, enabled = !busy) { Text("Cancel") } },
    )
}

private fun formatLastSeen(ms: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - ms
    val sdfTime = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return when {
        diff < 60_000 -> "الآن"
        diff < 3_600_000 -> "منذ ${diff / 60_000} د"
        isSameDay(ms, now) -> sdfTime.format(java.util.Date(ms))
        diff < 172_800_000 -> "أمس " + sdfTime.format(java.util.Date(ms))
        else -> java.text.SimpleDateFormat("d MMM, h:mm a", java.util.Locale.getDefault()).format(java.util.Date(ms))
    }
}
private fun isSameDay(a: Long, b: Long): Boolean {
    val ca = java.util.Calendar.getInstance().apply { timeInMillis = a }
    val cb = java.util.Calendar.getInstance().apply { timeInMillis = b }
    return ca.get(java.util.Calendar.YEAR) == cb.get(java.util.Calendar.YEAR) &&
        ca.get(java.util.Calendar.DAY_OF_YEAR) == cb.get(java.util.Calendar.DAY_OF_YEAR)
}
