package com.yousef.facebooky.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.yousef.facebooky.data.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

enum class CallPhase { IDLE, OUTGOING, INCOMING, CONNECTING, ACTIVE }

data class CallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val callId: String? = null,
    val isVideo: Boolean = false,
    val peerName: String = "",
    val peerPhoto: String = "",
    val micMuted: Boolean = false,
    val speakerOn: Boolean = false,
    val cameraOff: Boolean = false,
    val connectedAt: Long? = null,
)

/**
 * Call flow (single shared chat, so a call rings on every other open device):
 *  caller: create peer -> offer -> calls/{id} {state: ringing, offer}
 *  callee: sees ringing call -> Accept -> set offer -> answer -> {state: accepted, answer, calleeUid}
 *  both:   exchange ICE via calls/{id}/candidates; End -> {state: ended}; Reject -> {state: rejected}
 */
class CallManager(
    context: Context,
    private val signaling: CallSignaling,
    private val scope: CoroutineScope,
) {
    private val app = context.applicationContext
    private val rtcLazy = lazy { WebRtcClient(app) }
    val rtc: WebRtcClient by rtcLazy

    private val _state = MutableStateFlow(CallUiState())
    val state: StateFlow<CallUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private val audioManager = app.getSystemService(AudioManager::class.java)
    private var myUid: String? = null
    private val callJobs = mutableListOf<Job>()
    private var ringingJob: Job? = null
    private var ringtone: Ringtone? = null
    private val finishedCallIds = mutableSetOf<String>()

    fun startListening(uid: String) {
        myUid = uid
        ringingJob?.cancel()
        ringingJob = scope.launch {
            signaling.observeRinging()
                .retryWhen { e, _ -> Log.w(TAG, "ringing listener", e); delay(5000); true }
                .collect { list ->
                    if (_state.value.phase != CallPhase.IDLE) return@collect
                    val now = System.currentTimeMillis()
                    val incoming = list.firstOrNull {
                        it.callerUid != uid && it.id !in finishedCallIds && now - it.createdAtMs < RING_TIMEOUT_MS
                    } ?: return@collect
                    _state.value = CallUiState(
                        phase = CallPhase.INCOMING,
                        callId = incoming.id,
                        isVideo = incoming.video,
                        peerName = incoming.callerName,
                        peerPhoto = incoming.callerPhoto,
                    )
                    startRingtone()
                    watchCall(incoming.id, isCaller = false)
                }
        }
    }

    fun startCall(me: UserProfile, video: Boolean) {
        if (_state.value.phase != CallPhase.IDLE) return
        val id = signaling.newCallId()
        _state.value = CallUiState(phase = CallPhase.OUTGOING, callId = id, isVideo = video, peerName = "FaceBooky chat", speakerOn = video)
        callJobs += scope.launch {
            try {
                setupAudio(speaker = video)
                rtc.createPeer(
                    video = video,
                    onIceCandidate = { signaling.sendCandidate(id, me.uid, it) },
                    onConnectionState = ::onPeerState,
                )
                val offer = rtc.createOffer(video)
                signaling.createCall(id, me, video, offer)
                watchCall(id, isCaller = true)
                listenCandidates(id, me.uid)
                delay(RING_TIMEOUT_MS)
                if (_state.value.phase == CallPhase.OUTGOING && _state.value.callId == id) {
                    _messages.tryEmit("No answer")
                    end()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "startCall failed", e)
                _messages.tryEmit("Call failed. Check your connection.")
                cleanup()
            }
        }
    }

    fun accept(me: UserProfile) {
        val s = _state.value
        if (s.phase != CallPhase.INCOMING) return
        val id = s.callId ?: return
        stopRingtone()
        _state.update { it.copy(phase = CallPhase.CONNECTING, speakerOn = it.isVideo) }
        callJobs += scope.launch {
            try {
                setupAudio(speaker = s.isVideo)
                val call = signaling.getCall(id)
                val offer = call?.offer
                if (call == null || offer == null || call.state != CallStates.RINGING) error("Call is no longer available")
                rtc.createPeer(
                    video = s.isVideo,
                    onIceCandidate = { signaling.sendCandidate(id, me.uid, it) },
                    onConnectionState = ::onPeerState,
                )
                rtc.setRemoteDescription(offer)
                val answer = rtc.createAnswer(s.isVideo)
                signaling.accept(id, me, answer)
                listenCandidates(id, me.uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "accept failed", e)
                _messages.tryEmit("Could not join the call")
                cleanup()
            }
        }
    }

    fun reject() {
        val id = _state.value.callId
        if (_state.value.phase == CallPhase.INCOMING && id != null) signaling.setState(id, CallStates.REJECTED)
        cleanup()
    }

    fun end() {
        val s = _state.value
        val id = s.callId
        if (id != null && s.phase != CallPhase.IDLE && s.phase != CallPhase.INCOMING) signaling.setState(id, CallStates.ENDED)
        cleanup()
    }

    fun toggleMic() {
        val muted = !_state.value.micMuted
        if (rtcLazy.isInitialized()) rtc.setMicEnabled(!muted)
        _state.update { it.copy(micMuted = muted) }
    }

    fun toggleSpeaker() {
        val on = !_state.value.speakerOn
        setSpeaker(on)
        _state.update { it.copy(speakerOn = on) }
    }

    fun toggleCamera() {
        val off = !_state.value.cameraOff
        if (rtcLazy.isInitialized()) rtc.setCameraEnabled(!off)
        _state.update { it.copy(cameraOff = off) }
    }

    fun switchCamera() {
        if (rtcLazy.isInitialized()) rtc.switchCamera()
    }

    fun release() {
        ringingJob?.cancel()
        end()
    }

    // ---- internals ----

    private fun watchCall(id: String, isCaller: Boolean) {
        callJobs += scope.launch {
            var answerApplied = false
            signaling.observeCall(id)
                .retryWhen { e, attempt -> Log.w(TAG, "call listener", e); delay(2000); attempt < 5 }
                .collect { doc ->
                    if (doc == null) return@collect
                    when (doc.state) {
                        CallStates.REJECTED -> {
                            if (isCaller) _messages.tryEmit("Call declined")
                            cleanup()
                        }
                        CallStates.ENDED -> cleanup()
                        CallStates.ACCEPTED -> {
                            val answer = doc.answer
                            if (isCaller && !answerApplied && answer != null) {
                                answerApplied = true
                                _state.update {
                                    it.copy(phase = CallPhase.CONNECTING, peerName = doc.calleeName.ifBlank { it.peerName })
                                }
                                try {
                                    rtc.setRemoteDescription(answer)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    Log.w(TAG, "setRemote(answer) failed", e)
                                    _messages.tryEmit("Call failed")
                                    end()
                                }
                            } else if (!isCaller && doc.calleeUid != myUid && _state.value.phase == CallPhase.INCOMING) {
                                cleanup() // answered on another device
                            }
                        }
                    }
                }
        }
    }

    private fun listenCandidates(id: String, myUid: String) {
        callJobs += scope.launch {
            signaling.observeRemoteCandidates(id, myUid)
                .retryWhen { e, attempt -> Log.w(TAG, "candidates listener", e); delay(2000); attempt < 5 }
                .collect { rtc.addRemoteCandidate(it) }
        }
    }

    /** Called on a WebRTC thread -> hop to main before touching anything. */
    private fun onPeerState(state: PeerConnection.PeerConnectionState) {
        scope.launch {
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> _state.update {
                    if (it.phase == CallPhase.IDLE) it
                    else it.copy(phase = CallPhase.ACTIVE, connectedAt = it.connectedAt ?: SystemClock.elapsedRealtime())
                }
                PeerConnection.PeerConnectionState.FAILED -> {
                    if (_state.value.phase != CallPhase.IDLE) {
                        _messages.tryEmit("Connection failed. A TURN server may be needed on this network.")
                        end()
                    }
                }
                else -> Unit
            }
        }
    }

    private fun cleanup() {
        stopRingtone()
        _state.value.callId?.let { finishedCallIds += it }
        val jobs = callJobs.toList()
        callJobs.clear()
        if (rtcLazy.isInitialized()) rtc.close()
        resetAudio()
        _state.value = CallUiState()
        jobs.forEach { it.cancel() }
    }

    private fun setupAudio(speaker: Boolean) {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        setSpeaker(speaker)
    }

    private fun resetAudio() {
        setSpeaker(false)
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    @Suppress("DEPRECATION")
    private fun setSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (on) {
                audioManager.availableCommunicationDevices
                    .firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?.let { audioManager.setCommunicationDevice(it) }
            } else {
                audioManager.clearCommunicationDevice()
            }
        } else {
            audioManager.isSpeakerphoneOn = on
        }
    }

    private fun startRingtone() {
        stopRingtone()
        ringtone = runCatching {
            RingtoneManager.getRingtone(app, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
        }.getOrNull()?.also {
            if (Build.VERSION.SDK_INT >= 28) it.isLooping = true
            it.play()
        }
    }

    private fun stopRingtone() {
        ringtone?.stop()
        ringtone = null
    }

    private companion object {
        const val TAG = "CallManager"
        const val RING_TIMEOUT_MS = 45_000L
    }
}
