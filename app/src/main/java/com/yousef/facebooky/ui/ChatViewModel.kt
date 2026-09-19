package com.yousef.facebooky.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.yousef.facebooky.audio.VoiceMessagePlayer
import com.yousef.facebooky.audio.VoiceRecorder
import com.yousef.facebooky.call.CallManager
import com.yousef.facebooky.call.CallSignaling
import com.yousef.facebooky.data.AuthRepository
import com.yousef.facebooky.data.ChatRepository
import com.yousef.facebooky.data.MusicRepository
import com.yousef.facebooky.data.BlobStore
import com.yousef.facebooky.data.UserRepository
import com.yousef.facebooky.data.model.ChatMessage
import com.yousef.facebooky.data.model.ConnectionStatus
import com.yousef.facebooky.data.model.MessageType
import com.yousef.facebooky.data.model.ReplyTarget
import com.yousef.facebooky.data.model.Song
import com.yousef.facebooky.data.model.UserProfile
import com.yousef.facebooky.music.MusicController
import com.yousef.facebooky.util.ImageUtils
import com.yousef.facebooky.util.MediaUtils
import com.yousef.facebooky.util.networkAvailableFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.min

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = FirebaseFirestore.getInstance()
    private val authRepo = AuthRepository()
    private val userRepo = UserRepository(db, app)
    private val chatRepo = ChatRepository(db)
    private val blobs = BlobStore.get(app)
    private val musicRepo = MusicRepository(db)
    private val recorder = VoiceRecorder(app)

    val voicePlayer = VoiceMessagePlayer(viewModelScope, blobs::playablePath)
    val music = MusicController(app, musicRepo, viewModelScope, { authRepo.currentUid }, blobs::playablePath)
    val calls = CallManager(app, CallSignaling(db), viewModelScope)

    var myUid by mutableStateOf<String?>(null)
        private set
    var profile by mutableStateOf<UserProfile?>(null)
        private set
    var messages by mutableStateOf<List<ChatMessage>>(emptyList())
        private set
    var connection by mutableStateOf(ConnectionStatus.CONNECTING)
        private set
    var uploadsInProgress by mutableIntStateOf(0)
        private set
    var showProfileSheet by mutableStateOf(false)
        private set
    var savingProfile by mutableStateOf(false)
        private set
    var recordingStartedAt by mutableStateOf<Long?>(null)
        private set
    var draft by mutableStateOf("")
    /** Everyone's live name + photo (uid -> profile). */
    var people by mutableStateOf<Map<String, UserProfile>>(emptyMap())
        private set
    /** Message I'm replying to (shown above the input bar). */
    var replyingTo by mutableStateOf<ChatMessage?>(null)
        private set
    /** Song currently being uploaded (title to progress 0..1). */
    var musicUpload by mutableStateOf<Pair<String, Float>?>(null)
        private set
    /** True while Firestore refuses to give us the chat (rules not published). */
    var chatLocked by mutableStateOf(false)
        private set

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: Flow<String> = merge(_events, calls.messages)

    private val hiddenPrefs = app.getSharedPreferences("hidden_messages", android.content.Context.MODE_PRIVATE)
    private var hiddenIds: Set<String> = hiddenPrefs.getStringSet(HIDDEN_KEY, emptySet()).orEmpty().toSet()
    private var allMessages: List<ChatMessage> = emptyList()
    private var syncJob: Job? = null
    private var globalClearedAt = 0L
    private var localClearedAt = hiddenPrefs.getLong(CLEARED_KEY, 0L)
    private var networkUp = true
    private var fromCache = true
    private var pendingAction: (() -> Unit)? = null

    init {
        viewModelScope.launch {
            app.networkAvailableFlow().collect {
                networkUp = it
                updateConnection()
            }
        }
        viewModelScope.launch { signInLoop() }
    }

    // ---------- auth / startup ----------

    private suspend fun signInLoop() {
        var attempt = 0
        var uid: String? = null
        while (uid == null) {
            try {
                uid = authRepo.ensureSignedIn()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "anonymous sign-in failed", e)
                attempt++
                delay(min(30_000L, 2_000L * attempt))
            }
        }
        myUid = uid
        profile = userRepo.cached(uid)
        updateConnection()
        if (profile != null) syncProfile()
        onSignedIn(uid)
    }

    private fun onSignedIn(uid: String) {
        if (profile == null) viewModelScope.launch {
            runCatching { userRepo.fetch(uid) }.getOrNull()?.let { if (profile == null) profile = it }
        }
        viewModelScope.launch {
            chatRepo.observe()
                .retryWhen { e, _ ->
                    Log.w(TAG, "messages listener", e)
                    if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        chatLocked = true // shown inline, no popup
                    }
                    delay(3000)
                    true
                }
                .collect { snap ->
                    chatLocked = false
                    allMessages = snap.messages
                    refreshVisible()
                    fromCache = snap.fromCache
                    updateConnection()
                }
        }
        viewModelScope.launch {
            userRepo.observeAll()
                .retryWhen { e, _ -> Log.w(TAG, "users listener", e); delay(5000); true }
                .collect { people = it }
        }
        viewModelScope.launch {
            chatRepo.observeClearedAt()
                .retryWhen { e, _ -> Log.w(TAG, "chat state listener", e); delay(5000); true }
                .collect {
                    globalClearedAt = it
                    refreshVisible()
                }
        }
        music.start()
        calls.startListening(uid)
    }

    private fun updateConnection() {
        connection = when {
            !networkUp -> ConnectionStatus.OFFLINE
            myUid == null || fromCache -> ConnectionStatus.CONNECTING
            else -> ConnectionStatus.CONNECTED
        }
    }

    // ---------- profile ----------

    /** Returns true if the user can post; otherwise opens the profile sheet. */
    fun ensureProfile(): Boolean {
        if (myUid == null) {
            _events.tryEmit("Connecting… try again in a moment")
            return false
        }
        if (profile?.isComplete == true) return true
        pendingAction = null
        showProfileSheet = true
        return false
    }

    private fun withProfile(action: (UserProfile) -> Unit) {
        val p = profile
        if (myUid == null) {
            _events.tryEmit("Connecting… try again in a moment")
            return
        }
        if (p != null && p.isComplete) {
            action(p)
        } else {
            pendingAction = { profile?.let(action) }
            showProfileSheet = true
        }
    }

    fun openProfileEditor() {
        pendingAction = null
        showProfileSheet = true
    }

    fun dismissProfileSheet() {
        if (savingProfile) return
        showProfileSheet = false
        pendingAction = null
    }

    /**
     * Saves the profile on the phone immediately (so it works offline and the sheet closes at once),
     * then uploads it to Firebase in the background, retrying until it succeeds.
     */
    fun saveProfile(name: String, photo: Uri?) {
        val uid = myUid ?: return
        val trimmed = name.trim().take(40)
        if (trimmed.isEmpty()) {
            _events.tryEmit("Please enter your name")
            return
        }
        if (photo == null && profile?.displayPhoto.isNullOrBlank()) {
            _events.tryEmit("Please choose a photo")
            return
        }
        savingProfile = true
        viewModelScope.launch {
            try {
                val bytes = photo?.let { withContext(Dispatchers.IO) { ImageUtils.avatar(getApplication(), it) } }
                val p = withContext(Dispatchers.IO) { userRepo.saveLocal(uid, trimmed, bytes) }
                profile = p
                showProfileSheet = false
                val action = pendingAction
                pendingAction = null
                action?.invoke()
                syncJob?.cancel()
                syncProfile()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "saveProfile", e)
                _events.tryEmit("Couldn't read that photo. Try another one.")
            } finally {
                savingProfile = false
            }
        }
    }


    /** Background upload of the local profile (photo -> Storage, name/photo -> Firestore). */
    private fun syncProfile() {
        if (syncJob?.isActive == true) return
        syncJob = viewModelScope.launch {
            var attempt = 0
            while (true) {
                val uid = myUid ?: return@launch
                val p = profile ?: return@launch
                if (!userRepo.needsSync(uid)) return@launch
                var photoUrl = p.photoUrl
                var error: Exception? = null
                if (photoUrl.isBlank() && p.localPhoto.isNotBlank()) {
                    try {
                        val bytes = withContext(Dispatchers.IO) { File(p.localPhoto).readBytes() }
                        photoUrl = blobs.upload(uid, "profile", "image/jpeg", bytes)
                        userRepo.setRemotePhoto(uid, photoUrl)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "profile photo upload", e)
                        error = e
                    }
                }
                val synced = p.copy(uid = uid, photoUrl = photoUrl)
                try {
                    userRepo.push(synced, complete = error == null)
                    if (profile?.name == p.name && profile?.localPhoto == p.localPhoto) profile = synced
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "profile push", e)
                    error = error ?: e
                }
                if (error == null) return@launch
                attempt++
                delay(min(120_000L, 10_000L * attempt))
            }
        }
    }

    /** Turns Firebase errors into a short message that says what to fix. */
    private fun friendlyError(e: Exception): String = when {
        e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            "Blocked by Firebase: publish the latest Firestore rules."
        !networkUp -> "You're offline. It will be sent when you're back online."
        else -> "Couldn't reach Firebase. Will retry automatically."
    }

    // ---------- sending ----------

    fun sendDraft() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        withProfile { p ->
            chatRepo.send(chatRepo.newId(), p, MessageType.TEXT, text = text.take(4000), reply = takeReply(), onError = ::onSendError)
        }
    }

    // ---------- reply / react ----------

    fun startReply(m: ChatMessage) {
        replyingTo = m
    }

    fun cancelReply() {
        replyingTo = null
    }

    private fun takeReply(): ReplyTarget? {
        val m = replyingTo ?: return null
        replyingTo = null
        val name = if (m.senderUid == myUid) (profile?.name ?: m.senderName) else nameOf(m)
        return ReplyTarget(m.id, name, m.preview.take(200))
    }

    fun nameOf(m: ChatMessage): String = people[m.senderUid]?.name?.takeIf { it.isNotBlank() } ?: m.senderName

    fun photoOf(m: ChatMessage): String = people[m.senderUid]?.photoUrl?.takeIf { it.isNotBlank() } ?: m.senderPhoto

    /** Tap the same emoji again to remove it. */
    fun react(m: ChatMessage, emoji: String) {
        val uid = myUid ?: return
        if (m.type == MessageType.DELETED) return
        val next = if (m.reactions[uid] == emoji) null else emoji
        chatRepo.react(m.id, uid, next, ::onSendError)
    }

    fun deleteForEveryone(m: ChatMessage) {
        if (m.senderUid != myUid || m.type == MessageType.DELETED) return
        if (replyingTo?.id == m.id) replyingTo = null
        chatRepo.deleteForEveryone(m.id, ::onSendError)
    }

    /** Hides a message on this phone only. */
    fun deleteForMe(m: ChatMessage) {
        if (replyingTo?.id == m.id) replyingTo = null
        hiddenIds = hiddenIds + m.id
        hiddenPrefs.edit().putStringSet(HIDDEN_KEY, hiddenIds).apply()
        refreshVisible()
    }

    // ---------- clear chat ----------

    private fun refreshVisible() {
        val cutoff = maxOf(globalClearedAt, localClearedAt)
        messages = allMessages.filter { m ->
            m.id !in hiddenIds && (cutoff == 0L || (m.timestamp?.time ?: Long.MAX_VALUE) > cutoff)
        }
    }

    /** Clears the chat on this phone only (new messages still show). */
    fun clearChatForMe() {
        // Use the newest message's (server) time, so a wrong phone clock can't hide new messages.
        val newest = allMessages.mapNotNull { it.timestamp?.time }.maxOrNull() ?: return
        localClearedAt = maxOf(localClearedAt, newest)
        hiddenPrefs.edit().putLong(CLEARED_KEY, localClearedAt).apply()
        replyingTo = null
        refreshVisible()
    }

    /** Clears the chat for everyone (requires a profile). */
    fun clearChatForEveryone() = withProfile { p ->
        replyingTo = null
        chatRepo.clearForEveryone(p.uid, ::onSendError)
    }

    fun sendImage(uri: Uri) = withProfile { p ->
        uploadAndSend(p, MessageType.IMAGE, "images", "jpg", "image/jpeg") { ImageUtils.chatImage(getApplication(), uri) }
    }

    fun sendSticker(uri: Uri) = withProfile { p ->
        uploadAndSend(p, MessageType.STICKER, "stickers", "png", "image/png") { ImageUtils.sticker(getApplication(), uri) }
    }

    fun startRecording() {
        if (!ensureProfile()) return
        if (recorder.start()) {
            voicePlayer.release()
            recordingStartedAt = SystemClock.elapsedRealtime()
        } else {
            _events.tryEmit("Couldn't start the microphone")
        }
    }

    fun cancelRecording() {
        recorder.cancel()
        recordingStartedAt = null
    }

    fun finishRecording() {
        val rec = recorder.stop()
        recordingStartedAt = null
        if (rec == null) {
            _events.tryEmit("Recording too short")
            return
        }
        val p = profile ?: return
        uploadAndSend(p, MessageType.VOICE, "voice", "m4a", "audio/mp4", durationMs = rec.durationMs) {
            try {
                rec.file.readBytes()
            } finally {
                rec.file.delete()
            }
        }
    }

    private fun uploadAndSend(
        p: UserProfile,
        type: String,
        kind: String,
        ext: String,
        contentType: String,
        durationMs: Long = 0L,
        produce: suspend () -> ByteArray,
    ) {
        val reply = takeReply()
        viewModelScope.launch {
            uploadsInProgress++
            try {
                val bytes = withContext(Dispatchers.IO) { produce() }
                val ref = blobs.upload(p.uid, kind, contentType, bytes)
                chatRepo.send(chatRepo.newId(), p, type, mediaUrl = ref, durationMs = durationMs, reply = reply, onError = ::onSendError)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "upload failed", e)
                _events.tryEmit(friendlyError(e))
            } finally {
                uploadsInProgress--
            }
        }
    }

    private fun onSendError(e: Exception) {
        Log.w(TAG, "send failed", e)
        _events.tryEmit(
            if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) friendlyError(e)
            else "Message was not accepted by the server"
        )
    }

    // ---------- music ----------

    fun addMusic(uri: Uri) = withProfile { p ->
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { MediaUtils.queryAudio(getApplication(), uri) }
            if (info == null) {
                _events.tryEmit("Couldn't read that file")
                return@launch
            }
            if (info.sizeBytes > MAX_MUSIC_BYTES) {
                _events.tryEmit("That song is too large (max 15 MB)")
                return@launch
            }
            uploadsInProgress++
            musicUpload = info.title to 0f
            try {
                val bytes = withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
                        val buf = input.readBytes()
                        if (buf.size > MAX_MUSIC_BYTES) throw IllegalArgumentException("too large")
                        buf
                    } ?: throw IllegalStateException("unreadable")
                }
                val id = musicRepo.newSongId()
                val ref = blobs.upload(p.uid, "music", info.mimeType, bytes) { progress ->
                    viewModelScope.launch { if (musicUpload != null) musicUpload = info.title to progress }
                }
                musicRepo.addSong(id, info.title, ref, "", p.uid, bytes.size.toLong())
                chatRepo.send(
                    chatRepo.newId(), p, MessageType.MUSIC,
                    text = info.title, mediaUrl = ref, refId = id, onError = ::onSendError,
                )
                _events.tryEmit("Added \"${info.title}\"")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "music upload failed", e)
                _events.tryEmit(
                    when {
                        e is IllegalArgumentException -> "That song is too large (max 15 MB)"
                        e is FirebaseFirestoreException -> friendlyError(e)
                        else -> "Couldn't upload the song (max 15 MB, audio files only)"
                    }
                )
            } finally {
                uploadsInProgress--
                musicUpload = null
            }
        }
    }

    fun playSong(song: Song) = withProfile { music.play(song) }

    fun playSongFromMessage(message: ChatMessage) {
        val song = music.songs.value.firstOrNull { it.id == message.refId }
            ?: Song(id = message.refId.ifBlank { message.id }, title = message.text, url = message.mediaUrl)
        playSong(song)
    }

    fun toggleMusic() = withProfile { music.togglePlayPause() }

    fun seekMusic(positionMs: Long) = withProfile { music.seekTo(positionMs) }

    fun toggleMute() = music.toggleMute()

    // ---------- calls ----------

    fun startCall(video: Boolean) {
        val p = profile ?: return
        calls.startCall(p, video)
    }

    fun acceptCall() {
        val p = profile ?: return
        calls.accept(p)
    }

    override fun onCleared() {
        recorder.cancel()
        voicePlayer.release()
        music.release()
        calls.release()
        super.onCleared()
    }

    private companion object {
        const val TAG = "ChatViewModel"
        const val HIDDEN_KEY = "ids"
        const val CLEARED_KEY = "cleared_at"
        const val MAX_MUSIC_BYTES = 15L * 1024 * 1024
    }
}
