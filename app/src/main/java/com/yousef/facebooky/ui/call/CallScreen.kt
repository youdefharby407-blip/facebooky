package com.yousef.facebooky.ui.call

import android.Manifest
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yousef.facebooky.call.CallPhase
import com.yousef.facebooky.call.CallUiState
import com.yousef.facebooky.ui.ChatViewModel
import com.yousef.facebooky.ui.chat.Avatar
import com.yousef.facebooky.ui.rememberPermissionRequester
import com.yousef.facebooky.ui.theme.CallGreen
import com.yousef.facebooky.ui.theme.CallRed
import com.yousef.facebooky.util.formatTime
import kotlinx.coroutines.delay
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

private val CallBg = Color(0xFF0F1115)

@Composable
fun CallScreen(vm: ChatViewModel, state: CallUiState) {
    BackHandler(enabled = true) { /* keep the call screen until the call ends */ }
    val permissions = rememberPermissionRequester()
    val calls = vm.calls
    val rtc = calls.rtc
    val remote by rtc.remoteVideo.collectAsStateWithLifecycle()
    val local by rtc.localVideo.collectAsStateWithLifecycle()
    val showVideo = state.isVideo && state.phase != CallPhase.INCOMING

    Box(
        Modifier
            .fillMaxSize()
            .background(CallBg)
    ) {
        val remoteTrack = remote
        if (showVideo && remoteTrack != null) {
            VideoRenderer(remoteTrack, rtc.eglBase.eglBaseContext, mirror = false, overlay = false, modifier = Modifier.fillMaxSize())
        } else {
            PeerInfo(state, Modifier.align(Alignment.Center))
        }

        val localTrack = local
        if (showVideo && localTrack != null && !state.cameraOff) {
            VideoRenderer(
                localTrack, rtc.eglBase.eglBaseContext, mirror = true, overlay = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(width = 104.dp, height = 150.dp)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        if (showVideo && remoteTrack != null) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(20.dp)
            ) {
                Text(state.peerName, color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(statusText(state), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelMedium)
            }
        }

        // Controls stay in a compact row at the bottom, never covered by video.
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 28.dp, start = 16.dp, end = 16.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.phase == CallPhase.INCOMING) {
                CallButton(Icons.Rounded.CallEnd, "Decline", CallRed, Color.White) { calls.reject() }
                CallButton(if (state.isVideo) Icons.Rounded.Videocam else Icons.Rounded.Call, "Accept", CallGreen, Color.White) {
                    if (vm.ensureProfile()) {
                        val perms = if (state.isVideo) arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
                        else arrayOf(Manifest.permission.RECORD_AUDIO)
                        permissions.request(perms) { vm.acceptCall() }
                    }
                }
            } else {
                ToggleButton(if (state.micMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic, "Mute", state.micMuted) { calls.toggleMic() }
                ToggleButton(Icons.AutoMirrored.Rounded.VolumeUp, "Speaker", state.speakerOn) { calls.toggleSpeaker() }
                if (state.isVideo) {
                    ToggleButton(if (state.cameraOff) Icons.Rounded.VideocamOff else Icons.Rounded.Videocam, "Camera", state.cameraOff) {
                        calls.toggleCamera()
                    }
                    ToggleButton(Icons.Rounded.Cameraswitch, "Switch", false) { calls.switchCamera() }
                }
                CallButton(Icons.Rounded.CallEnd, "End", CallRed, Color.White) { calls.end() }
            }
        }
    }
}

@Composable
private fun PeerInfo(state: CallUiState, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(state.peerPhoto, 112.dp)
        Spacer(Modifier.height(18.dp))
        Text(state.peerName.ifBlank { "My Space" }, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(statusText(state), color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun statusText(state: CallUiState): String {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.connectedAt) {
        while (state.connectedAt != null) {
            now = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    return when (state.phase) {
        CallPhase.OUTGOING -> "Calling…"
        CallPhase.INCOMING -> if (state.isVideo) "Incoming video call" else "Incoming voice call"
        CallPhase.CONNECTING -> "Connecting…"
        CallPhase.ACTIVE -> state.connectedAt?.let { formatTime(now - it) } ?: "Connected"
        CallPhase.IDLE -> ""
    }
}

@Composable
private fun CallButton(icon: ImageVector, label: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(60.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = bg, contentColor = fg),
        ) { Icon(icon, label, Modifier.size(28.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun ToggleButton(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    CallButton(
        icon, label,
        bg = if (active) Color.White else Color.White.copy(alpha = 0.16f),
        fg = if (active) CallBg else Color.White,
        onClick = onClick,
    )
}

@Composable
private fun VideoRenderer(
    track: VideoTrack,
    eglContext: EglBase.Context,
    mirror: Boolean,
    overlay: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val renderer = remember {
        SurfaceViewRenderer(context).apply {
            init(eglContext, null)
            setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            setEnableHardwareScaler(true)
            setMirror(mirror)
            if (overlay) setZOrderMediaOverlay(true)
        }
    }
    DisposableEffect(track) {
        runCatching { track.addSink(renderer) }
        onDispose { runCatching { track.removeSink(renderer) } }
    }
    DisposableEffect(Unit) {
        onDispose { renderer.release() }
    }
    AndroidView(factory = { renderer }, modifier = modifier)
}
