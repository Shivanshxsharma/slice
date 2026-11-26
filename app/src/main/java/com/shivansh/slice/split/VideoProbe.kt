package com.shivansh.slice.split

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Everything we learn about a picked video before splitting it. */
class VideoInfo(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationUs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int,
    val videoMime: String,
    val audioMimes: List<String>,
    val frameRate: Float,
    /** Presentation times of every keyframe in the video track, sorted. */
    val keyframesUs: LongArray,
    /** ISO-6709 latitude/longitude from the original, copied into every part. */
    val location: Pair<Float, Float>?,
) {
    val baseName: String get() = name.substringBeforeLast('.', name).ifBlank { "video" }

    /** Typical gap between keyframes: how close to the asked length a cut can land. */
    val keyframeGapUs: Long
        get() {
            val k = CutPlanner.cutPoints(keyframesUs, durationUs)
            if (k.isEmpty()) return durationUs
            val gaps = (listOf(0L) + k.toList()).zipWithNext { a, b -> b - a }.sorted()
            return gaps[gaps.size / 2]
        }

    val codecLabel: String get() = codecName(videoMime)

    companion object {
        fun codecName(mime: String): String = when (mime) {
            MediaFormat.MIMETYPE_VIDEO_AVC -> "H.264"
            MediaFormat.MIMETYPE_VIDEO_HEVC -> "HEVC"
            MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> "Dolby Vision"
            MediaFormat.MIMETYPE_VIDEO_AV1 -> "AV1"
            MediaFormat.MIMETYPE_VIDEO_VP9 -> "VP9"
            MediaFormat.MIMETYPE_VIDEO_VP8 -> "VP8"
            MediaFormat.MIMETYPE_VIDEO_MPEG4 -> "MPEG-4"
            MediaFormat.MIMETYPE_VIDEO_H263 -> "H.263"
            MediaFormat.MIMETYPE_AUDIO_AAC -> "AAC"
            MediaFormat.MIMETYPE_AUDIO_OPUS -> "Opus"
            MediaFormat.MIMETYPE_AUDIO_VORBIS -> "Vorbis"
            MediaFormat.MIMETYPE_AUDIO_AMR_NB -> "AMR"
            MediaFormat.MIMETYPE_AUDIO_AMR_WB -> "AMR-WB"
            MediaFormat.MIMETYPE_AUDIO_AC3 -> "AC-3"
            MediaFormat.MIMETYPE_AUDIO_EAC3 -> "E-AC-3"
            MediaFormat.MIMETYPE_AUDIO_FLAC -> "FLAC"
            else -> mime.substringAfter('/').uppercase()
        }
    }
}

class SplitException(message: String) : Exception(message)

object VideoProbe {

    suspend fun probe(context: Context, uri: Uri): VideoInfo {
        val resolver = context.contentResolver
        var name = "video.mp4"
        var size = -1L
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        }

        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw SplitException("Couldn't open this video.")
        pfd.use {
            if (size <= 0) size = pfd.statSize
            val extractor = MediaExtractor()
            try {
                try {
                    extractor.setDataSource(pfd.fileDescriptor)
                } catch (e: Exception) {
                    throw SplitException("This file isn't a video Android can read.")
                }
                var videoTrack = -1
                val audioMimes = ArrayList<String>()
                var durationUs = 0L
                var frameRate = 0f
                var width = 0
                var height = 0
                var videoMime = ""
                for (i in 0 until extractor.trackCount) {
                    val f = extractor.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (f.containsKey(MediaFormat.KEY_DURATION)) durationUs = maxOf(durationUs, f.getLong(MediaFormat.KEY_DURATION))
                    if (mime.startsWith("video/") && videoTrack == -1) {
                        videoTrack = i
                        videoMime = mime
                        width = f.intOr(MediaFormat.KEY_WIDTH)
                        height = f.intOr(MediaFormat.KEY_HEIGHT)
                        frameRate = f.numberOr(MediaFormat.KEY_FRAME_RATE)
                    } else if (mime.startsWith("audio/")) {
                        audioMimes += mime
                    }
                }
                if (videoTrack == -1) throw SplitException("This file has no video track.")

                val retriever = MediaMetadataRetriever()
                var rotation = 0
                var location: Pair<Float, Float>? = null
                try {
                    retriever.setDataSource(pfd.fileDescriptor)
                    rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    location = parseIso6709(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION))
                    val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    if (durationUs <= 0 && d != null) durationUs = d * 1000
                    if (frameRate <= 0f) {
                        val frames = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
                        if (frames != null && durationUs > 0) frameRate = frames * 1_000_000f / durationUs
                    }
                } catch (_: Exception) {
                } finally {
                    runCatching { retriever.release() }
                }

                extractor.selectTrack(videoTrack)
                val keyframes = scanKeyframes(extractor)
                if (durationUs <= 0) durationUs = (keyframes.lastOrNull() ?: 0L) + 1_000_000L
                if (durationUs <= 0) throw SplitException("This video has no length.")

                return VideoInfo(
                    uri = uri, name = name, sizeBytes = size, durationUs = durationUs,
                    width = width, height = height, rotation = rotation, videoMime = videoMime,
                    audioMimes = audioMimes, frameRate = frameRate, keyframesUs = keyframes,
                    location = location,
                )
            } finally {
                extractor.release()
            }
        }
    }

    /**
     * Lists keyframe times. First hops from keyframe to keyframe with SEEK_TO_NEXT_SYNC, which
     * on MP4/MOV only reads the keyframes themselves (fast even for multi-GB files). If the
     * container can't seek like that, falls back to walking every sample.
     */
    private suspend fun scanKeyframes(extractor: MediaExtractor): LongArray {
        val out = ArrayList<Long>()
        extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
        var t = extractor.sampleTime
        var hopsOk = true
        while (t >= 0) {
            currentCoroutineContext().ensureActive()
            if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC == 0) { hopsOk = false; break }
            if (out.isNotEmpty() && t <= out.last()) { hopsOk = false; break }
            out += t
            extractor.seekTo(t + 1, MediaExtractor.SEEK_TO_NEXT_SYNC)
            t = extractor.sampleTime
        }
        if (hopsOk && out.isNotEmpty()) return out.toLongArray()

        // Linear walk: reads every sample but works for any container.
        out.clear()
        extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        var n = 0
        while (true) {
            val time = extractor.sampleTime
            if (time < 0) break
            if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) out += time
            if (++n % 256 == 0) currentCoroutineContext().ensureActive()
            if (!extractor.advance()) break
        }
        return out.distinct().sorted().toLongArray()
    }

    /** "+37.4219-122.0840/" or "+37.4219-122.0840+012.3/" → (lat, lon). */
    internal fun parseIso6709(s: String?): Pair<Float, Float>? {
        if (s.isNullOrBlank()) return null
        val m = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(s) ?: return null
        val lat = m.groupValues[1].toFloatOrNull() ?: return null
        val lon = m.groupValues[2].toFloatOrNull() ?: return null
        if (lat !in -90f..90f || lon !in -180f..180f) return null
        return lat to lon
    }

    private fun MediaFormat.intOr(key: String, default: Int = 0): Int =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrDefault(default) else default

    private fun MediaFormat.numberOr(key: String): Float {
        if (!containsKey(key)) return 0f
        return runCatching { getInteger(key).toFloat() }.getOrElse { runCatching { getFloat(key) }.getOrDefault(0f) }
    }
}
