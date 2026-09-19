package com.yousef.facebooky.call

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Thin wrapper around one PeerConnection + local mic/camera. Created once and reused across calls. */
class WebRtcClient(private val context: Context) {

    val eglBase: EglBase = EglBase.create()
    private val factory: PeerConnectionFactory

    init {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions()
        )
        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        adm.release()
    }

    private var peer: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var capturer: CameraVideoCapturer? = null
    private var textureHelper: SurfaceTextureHelper? = null
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private var remoteDescriptionSet = false

    private val _localVideo = MutableStateFlow<VideoTrack?>(null)
    val localVideo: StateFlow<VideoTrack?> = _localVideo.asStateFlow()
    private val _remoteVideo = MutableStateFlow<VideoTrack?>(null)
    val remoteVideo: StateFlow<VideoTrack?> = _remoteVideo.asStateFlow()

    fun createPeer(
        video: Boolean,
        onIceCandidate: (IceCandidate) -> Unit,
        onConnectionState: (PeerConnection.PeerConnectionState) -> Unit,
    ) {
        close()
        val config = PeerConnection.RTCConfiguration(IceConfig.iceServers()).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) onIceCandidate(candidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState != null) onConnectionState(newState)
            }
            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) _remoteVideo.value = track
            }
        }
        val pc = factory.createPeerConnection(config, observer) ?: error("Could not create PeerConnection")
        peer = pc

        val aSource = factory.createAudioSource(MediaConstraints())
        val aTrack = factory.createAudioTrack("audio0", aSource)
        pc.addTrack(aTrack, listOf("stream0"))
        audioSource = aSource
        audioTrack = aTrack

        if (video) startCamera()?.let { pc.addTrack(it, listOf("stream0")) }
    }

    suspend fun createOffer(video: Boolean): SessionDescription {
        val pc = peer ?: error("No peer connection")
        val sdp = pc.awaitCreate(offer = true, constraints = constraints(video))
        pc.awaitSet(local = true, sdp = sdp)
        return sdp
    }

    suspend fun createAnswer(video: Boolean): SessionDescription {
        val pc = peer ?: error("No peer connection")
        val sdp = pc.awaitCreate(offer = false, constraints = constraints(video))
        pc.awaitSet(local = true, sdp = sdp)
        return sdp
    }

    suspend fun setRemoteDescription(sdp: SessionDescription) {
        val pc = peer ?: error("No peer connection")
        pc.awaitSet(local = false, sdp = sdp)
        remoteDescriptionSet = true
        pendingCandidates.forEach { pc.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    fun addRemoteCandidate(candidate: IceCandidate) {
        val pc = peer ?: return
        if (remoteDescriptionSet) pc.addIceCandidate(candidate) else pendingCandidates += candidate
    }

    fun setMicEnabled(enabled: Boolean) {
        audioTrack?.setEnabled(enabled)
    }

    fun setCameraEnabled(enabled: Boolean) {
        videoTrack?.setEnabled(enabled)
        val cam = capturer ?: return
        try {
            if (enabled) cam.startCapture(1280, 720, 30) else cam.stopCapture()
        } catch (_: InterruptedException) {
        }
    }

    fun switchCamera() {
        capturer?.switchCamera(null)
    }

    fun close() {
        _localVideo.value = null
        _remoteVideo.value = null
        try {
            capturer?.stopCapture()
        } catch (_: InterruptedException) {
        }
        capturer?.dispose()
        capturer = null
        peer?.dispose() // closes the connection and releases senders/receivers
        peer = null
        videoSource?.dispose()
        videoSource = null
        audioSource?.dispose()
        audioSource = null
        textureHelper?.dispose()
        textureHelper = null
        videoTrack = null
        audioTrack = null
        pendingCandidates.clear()
        remoteDescriptionSet = false
    }

    private fun startCamera(): VideoTrack? {
        val enumerator = Camera2Enumerator(context)
        val names = enumerator.deviceNames
        val name = names.firstOrNull { enumerator.isFrontFacing(it) } ?: names.firstOrNull() ?: return null
        val cam = enumerator.createCapturer(name, null) ?: return null
        val helper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        val source = factory.createVideoSource(cam.isScreencast)
        cam.initialize(helper, context, source.capturerObserver)
        cam.startCapture(1280, 720, 30)
        val track = factory.createVideoTrack("video0", source)
        capturer = cam
        textureHelper = helper
        videoSource = source
        videoTrack = track
        _localVideo.value = track
        return track
    }

    private fun constraints(video: Boolean) = MediaConstraints().apply {
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", video.toString()))
    }

    private suspend fun PeerConnection.awaitCreate(offer: Boolean, constraints: MediaConstraints): SessionDescription =
        suspendCancellableCoroutine { cont ->
            val obs = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {
                    if (sdp != null) cont.resume(sdp) else cont.resumeWithException(IllegalStateException("Empty SDP"))
                }
                override fun onCreateFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "createOffer/Answer failed"))
                }
                override fun onSetSuccess() {}
                override fun onSetFailure(error: String?) {}
            }
            if (offer) createOffer(obs, constraints) else createAnswer(obs, constraints)
        }

    private suspend fun PeerConnection.awaitSet(local: Boolean, sdp: SessionDescription): Unit =
        suspendCancellableCoroutine { cont ->
            val obs = object : SdpObserver {
                override fun onCreateSuccess(sdp: SessionDescription?) {}
                override fun onCreateFailure(error: String?) {}
                override fun onSetSuccess() {
                    cont.resume(Unit)
                }
                override fun onSetFailure(error: String?) {
                    cont.resumeWithException(IllegalStateException(error ?: "setDescription failed"))
                }
            }
            if (local) setLocalDescription(obs, sdp) else setRemoteDescription(obs, sdp)
        }
}
