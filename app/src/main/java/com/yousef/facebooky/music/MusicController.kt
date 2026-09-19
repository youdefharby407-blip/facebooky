package com.yousef.facebooky.music

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.yousef.facebooky.R
import com.yousef.facebooky.data.MusicRepository
import com.yousef.facebooky.data.model.SharedPlayback
import com.yousef.facebooky.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

/** What this phone's player is doing right now. */
data class LocalPlayback(
    val songId: String? = null,
    val prepared: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/**
 * Shared ("listen together") music player.
 *
 * - Song / play-pause / position live in Firestore (rooms/main/state/player).
 * - Every device follows that state: when it changes, the local MediaPlayer loads the song,
 *   seeks to  position + (now - updatedAt)  and plays or pauses.
 * - Mute is LOCAL only: the player keeps running at volume 0, so the shared state and
 *   everyone else's playback are untouched, and un-muting is instantly in sync.
 */
class MusicController(
    private val context: Context,
    private val repo: MusicRepository,
    private val scope: CoroutineScope,
    private val uidProvider: () -> String?,
    /** Turns a song reference into something MediaPlayer can open (cached file for Firestore blobs). */
    private val resolve: suspend (String) -> String,
) {
    val defaultSong = Song(id = DEFAULT_SONG_ID, title = "عمرو دياب - معاك قلبي", isBundled = true)

    private val _songs = MutableStateFlow(listOf(defaultSong))
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _shared = MutableStateFlow<SharedPlayback?>(null)
    val shared: StateFlow<SharedPlayback?> = _shared.asStateFlow()

    private val _local = MutableStateFlow(LocalPlayback())
    val local: StateFlow<LocalPlayback> = _local.asStateFlow()

    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()

    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private var loadJob: Job? = null
    private val jobs = mutableListOf<Job>()
    private var lastPublished: SharedPlayback? = null

    fun start() {
        if (jobs.isNotEmpty()) return
        jobs += scope.launch {
            repo.observeSongs()
                .retryWhen { e, _ -> Log.w(TAG, "songs listener", e); delay(RETRY_MS); true }
                .collect { remote -> _songs.value = listOf(defaultSong) + remote }
        }
        jobs += scope.launch {
            repo.observePlayback()
                .retryWhen { e, _ -> Log.w(TAG, "player listener", e); delay(RETRY_MS); true }
                .collect { state -> if (state != null) onRemoteState(state) }
        }
    }

    // ---- user actions (shared) ----

    fun play(song: Song) {
        val sameSong = _shared.value?.songId == song.id
        publish(song, playing = true, positionMs = if (sameSong) currentPosition() else 0L)
    }

    fun togglePlayPause() {
        val s = _shared.value
        if (s == null || s.songId.isBlank()) {
            play(defaultSong)
            return
        }
        val ended = _local.value.durationMs > 0 && currentPosition() >= _local.value.durationMs - 500
        publish(songFor(s), playing = !s.playing, positionMs = if (ended) 0L else currentPosition())
    }

    fun seekTo(positionMs: Long) {
        val s = _shared.value ?: return
        publish(songFor(s), playing = s.playing, positionMs = positionMs)
    }

    // ---- local only ----

    fun toggleMute() {
        _muted.value = !_muted.value
        applyVolume()
    }

    /** Deletes a song for everyone. The bundled default ("معاك قلبي") can never be deleted. */
    fun deleteSong(song: Song, onError: (Exception) -> Unit) {
        if (song.isBundled || song.id == DEFAULT_SONG_ID) {
            onError(IllegalStateException("protected"))
            return
        }
        if (_shared.value?.songId == song.id) publish(defaultSong, playing = false, positionMs = 0L)
        repo.deleteSong(song.id, onError)
    }

    fun stop() = release()

    fun release() {
        jobs.forEach { it.cancel() }
        jobs.clear()
        releasePlayer()
    }

    // ---- internals ----

    private fun publish(song: Song, playing: Boolean, positionMs: Long) {
        val uid = uidProvider() ?: return
        val state = SharedPlayback(
            songId = song.id,
            songTitle = song.title,
            songUrl = song.url,
            playing = playing,
            positionMs = positionMs.coerceAtLeast(0L),
            updatedAtMs = System.currentTimeMillis(),
            updatedBy = uid,
        )
        lastPublished = state
        applyState(state) // optimistic: react instantly on this phone
        repo.publish(state)
    }

    private fun onRemoteState(s: SharedPlayback) {
        val mine = lastPublished
        // Echo of our own write (only the server timestamp changed) -> don't re-seek due to clock skew.
        if (mine != null && s.updatedBy == mine.updatedBy && s.songId == mine.songId &&
            s.playing == mine.playing && s.positionMs == mine.positionMs
        ) {
            _shared.value = mine
            return
        }
        applyState(s)
    }

    private fun songFor(s: SharedPlayback): Song =
        _songs.value.firstOrNull { it.id == s.songId }
            ?: if (s.songId == DEFAULT_SONG_ID) defaultSong else Song(s.songId, s.songTitle, s.songUrl)

    private fun targetPosition(s: SharedPlayback): Long =
        if (s.playing) s.positionMs + (System.currentTimeMillis() - s.updatedAtMs).coerceAtLeast(0L) else s.positionMs

    private fun currentPosition(): Long {
        val p = player
        val l = _local.value
        val s = _shared.value
        return if (p != null && l.prepared && l.songId == s?.songId) p.currentPosition.toLong()
        else s?.let { targetPosition(it) } ?: 0L
    }

    private fun applyState(s: SharedPlayback) {
        _shared.value = s
        if (s.songId.isBlank()) return
        if (_local.value.songId != s.songId || _local.value.failed) {
            load(songFor(s)) // onPrepared re-applies the latest state
            return
        }
        val p = player ?: return
        if (!_local.value.prepared) return

        val duration = p.duration.toLong()
        val target = targetPosition(s)
        if (duration > 0 && target >= duration) {
            // Song already finished for everyone.
            if (p.isPlaying) p.pause()
            refreshLocal()
            return
        }
        if (abs(p.currentPosition - target) > SYNC_TOLERANCE_MS) p.seekTo(target.toInt())
        if (s.playing && !p.isPlaying) p.start()
        if (!s.playing && p.isPlaying) p.pause()
        refreshLocal()
    }

    private fun load(song: Song) {
        releasePlayer()
        _local.value = LocalPlayback(songId = song.id, loading = true)
        if (!song.isBundled && song.url.isBlank()) {
            _local.update { it.copy(loading = false, failed = true) }
            return
        }
        loadJob = scope.launch {
            val source = if (song.isBundled) null else try {
                resolve(song.url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "song download failed", e)
                _local.value = LocalPlayback(songId = song.id, failed = true)
                return@launch
            }
            val mp = MediaPlayer()
            player = mp
            try {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                if (source == null) {
                    context.resources.openRawResourceFd(R.raw.default_song).use { afd ->
                        mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                    }
                } else {
                    mp.setDataSource(source)
                }
                mp.setOnPreparedListener { prepared ->
                    if (player !== prepared) return@setOnPreparedListener
                    _local.update { it.copy(prepared = true, loading = false, durationMs = prepared.duration.toLong()) }
                    applyVolume()
                    startTicker()
                    _shared.value?.let { if (it.songId == song.id) applyState(it) }
                }
                mp.setOnCompletionListener { refreshLocal() }
                mp.setOnErrorListener { failedPlayer, what, extra ->
                    Log.w(TAG, "MediaPlayer error $what/$extra")
                    if (player === failedPlayer) _local.update { it.copy(prepared = false, loading = false, failed = true) }
                    true
                }
                mp.prepareAsync()
            } catch (e: Exception) {
                Log.w(TAG, "load failed", e)
                releasePlayer()
                _local.value = LocalPlayback(songId = song.id, failed = true)
            }
        }
    }

    private fun refreshLocal() {
        val p = player ?: return
        if (!_local.value.prepared) return
        _local.update { it.copy(isPlaying = p.isPlaying, positionMs = p.currentPosition.toLong()) }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                refreshLocal()
                delay(250)
            }
        }
    }

    private fun applyVolume() {
        val v = if (_muted.value) 0f else 1f
        player?.setVolume(v, v)
    }

    private fun releasePlayer() {
        loadJob?.cancel()
        loadJob = null
        ticker?.cancel()
        ticker = null
        player?.release()
        player = null
        _local.value = LocalPlayback()
    }

    companion object {
        const val DEFAULT_SONG_ID = "default"
        private const val TAG = "MusicController"

        private const val SYNC_TOLERANCE_MS = 1500L
        private const val RETRY_MS = 3000L
    }
}
