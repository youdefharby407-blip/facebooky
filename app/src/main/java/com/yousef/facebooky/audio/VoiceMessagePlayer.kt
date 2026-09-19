package com.yousef.facebooky.audio

import android.media.AudioAttributes
import android.media.MediaPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VoicePlaybackState(
    val messageId: String? = null,
    val loading: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Plays one voice message at a time. [resolve] turns the message reference into
 * something MediaPlayer can open (a cached local file for Firestore blobs).
 */
class VoiceMessagePlayer(
    private val scope: CoroutineScope,
    private val resolve: suspend (String) -> String,
) {

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var loadJob: Job? = null
    private val _state = MutableStateFlow(VoicePlaybackState())
    val state: StateFlow<VoicePlaybackState> = _state.asStateFlow()

    fun toggle(messageId: String, url: String) {
        val current = _state.value
        val p = player
        if (current.messageId == messageId && p != null && !current.loading) {
            if (p.isPlaying) {
                p.pause()
                _state.update { it.copy(isPlaying = false) }
            } else {
                p.start()
                _state.update { it.copy(isPlaying = true) }
                startTicker()
            }
            return
        }
        release()
        _state.value = VoicePlaybackState(messageId = messageId, loading = true)
        loadJob = scope.launch {
            val source = try {
                resolve(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value.messageId == messageId) release()
                return@launch
            }
            if (_state.value.messageId != messageId) return@launch
            val mp = MediaPlayer()
            player = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                mp.setDataSource(source)
                mp.setOnPreparedListener {
                    if (player !== it) return@setOnPreparedListener
                    it.start()
                    _state.value = VoicePlaybackState(messageId, false, true, 0L, it.duration.toLong())
                    startTicker()
                }
                mp.setOnCompletionListener {
                    ticker?.cancel()
                    _state.update { s -> s.copy(isPlaying = false, positionMs = 0L) }
                }
                mp.setOnErrorListener { _, _, _ ->
                    release()
                    true
                }
                mp.prepareAsync()
            } catch (e: Exception) {
                release()
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val p = player
                if (p != null && p.isPlaying) _state.update { it.copy(positionMs = p.currentPosition.toLong()) }
                delay(200)
            }
        }
    }

    fun release() {
        loadJob?.cancel()
        loadJob = null
        ticker?.cancel()
        player?.release()
        player = null
        _state.value = VoicePlaybackState()
    }
}
