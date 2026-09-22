package com.yousef.facebooky.util

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.max

object VideoUtils {

    const val MAX_VIDEO_BYTES = 15L * 1024 * 1024

    data class VideoInfo(val durationMs: Long, val width: Int, val height: Int)

    fun probe(context: Context, uri: Uri): VideoInfo {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(context, uri)
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            return VideoInfo(dur, w, h)
        } finally {
            r.release()
        }
    }

    /** Reads a whole video as bytes if it fits under [MAX_VIDEO_BYTES], else throws. */
    fun readWhole(context: Context, uri: Uri): ByteArray {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= MAX_VIDEO_BYTES) { "too_large" }
            return bytes
        }
        error("unreadable")
    }

    /**
     * Trims [uri] to [startMs, endMs] WITHOUT re-encoding (fast, no quality loss), muxing only
     * the video track, and returns the resulting mp4 bytes. Used for video stickers.
     */
    fun trimToStickerBytes(context: Context, uri: Uri, startMs: Long, endMs: Long): ByteArray {
        val out = File(context.cacheDir, "vsticker_${System.currentTimeMillis()}.mp4")
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(context, uri)
            // pick the video track only (no audio -> looping muted sticker)
            var videoTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                    videoTrack = i; format = f; break
                }
            }
            require(videoTrack >= 0 && format != null) { "no_video_track" }
            extractor.selectTrack(videoTrack)
            extractor.seekTo(startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            muxer = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val maxSize = max(1, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1_500_000))
            val outTrack = muxer.addTrack(format)
            muxer.start()

            val buffer = ByteBuffer.allocate(maxSize)
            val info = android.media.MediaCodec.BufferInfo()
            val endUs = endMs * 1000
            while (true) {
                info.offset = 0
                info.size = extractor.readSampleData(buffer, 0)
                if (info.size < 0) break
                val ptsUs = extractor.sampleTime
                if (ptsUs > endUs) break
                info.presentationTimeUs = ptsUs
                info.flags = extractor.sampleFlags
                muxer.writeSampleData(outTrack, buffer, info)
                extractor.advance()
            }
            muxer.stop()
        } finally {
            runCatching { extractor.release() }
            runCatching { muxer?.release() }
        }
        val bytes = out.readBytes()
        out.delete()
        require(bytes.size <= MAX_VIDEO_BYTES) { "too_large" }
        return bytes
    }

    private fun MediaFormat.getInteger(key: String, def: Int): Int =
        if (containsKey(key)) getInteger(key) else def
}
