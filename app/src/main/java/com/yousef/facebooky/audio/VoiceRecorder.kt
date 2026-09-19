package com.yousef.facebooky.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

class VoiceRecorder(private val context: Context) {

    data class Recording(val file: File, val durationMs: Long)

    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L

    /** Caller must already hold RECORD_AUDIO. */
    fun start(): Boolean {
        cancel()
        val file = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val r = newRecorder()
        return try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(64_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            output = file
            startedAt = SystemClock.elapsedRealtime()
            paused = false; pausedAccumMs = 0L
            true
        } catch (e: Exception) {
            r.release()
            file.delete()
            false
        }
    }

    /** Returns null if the recording failed or was too short. */
    fun stop(): Recording? {
        val r = recorder ?: return null
        val file = output
        val extraPause = pausedAccumMs + (if (paused) SystemClock.elapsedRealtime() - pauseStartedAt else 0L)
        val duration = SystemClock.elapsedRealtime() - startedAt - extraPause
        recorder = null
        output = null
        val ok = try {
            r.stop()
            true
        } catch (e: RuntimeException) {
            false
        } finally {
            r.release()
        }
        if (!ok || file == null || duration < MIN_DURATION_MS) {
            file?.delete()
            return null
        }
        return Recording(file, duration)
    }

    private var paused = false
    private var pausedAccumMs = 0L
    private var pauseStartedAt = 0L

    /** Pause/resume (API 24+). Returns the new paused state. */
    fun setPaused(pause: Boolean): Boolean {
        val r = recorder ?: return paused
        try {
            if (pause && !paused) {
                r.pause(); paused = true; pauseStartedAt = SystemClock.elapsedRealtime()
            } else if (!pause && paused) {
                r.resume(); paused = false; pausedAccumMs += SystemClock.elapsedRealtime() - pauseStartedAt
            }
        } catch (e: Exception) { /* ignore */ }
        return paused
    }

    fun cancel() {
        recorder?.let {
            runCatching { it.stop() }
            it.release()
        }
        recorder = null
        output?.delete()
        output = null
        paused = false; pausedAccumMs = 0L
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()

    private companion object {
        const val MIN_DURATION_MS = 700L
    }
}
