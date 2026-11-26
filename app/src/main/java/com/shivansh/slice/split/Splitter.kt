package com.shivansh.slice.split

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import java.io.File
import java.io.FileDescriptor
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class SavedPart(
    val index: Int,
    val uri: Uri,
    val name: String,
    val startUs: Long,
    val durationUs: Long,
    val bytes: Long,
)

data class SplitProgress(val part: Int, val parts: Int, val fraction: Float)

class SplitResult(
    val parts: List<SavedPart>,
    /** Audio tracks this phone's muxer can't write losslessly, so they were left out. */
    val droppedTracks: List<String>,
)

/**
 * Lossless splitter. Every compressed video frame and audio packet is copied byte-for-byte
 * from the original into the parts (MediaExtractor → MediaMuxer); nothing is decoded or
 * re-encoded, so the quality is exactly the original's and it runs at storage speed.
 *
 * Video: part k holds the samples from its starting keyframe up to (not including) the next
 * part's keyframe. Audio: part k holds the packets whose time falls in [start, end). Together
 * the parts contain every sample of the original exactly once.
 */
object Splitter {
    const val FOLDER = "Movies/Slice"

    private class Track(val index: Int, val format: MediaFormat, val label: String, val endUs: Long, val isVideo: Boolean)

    private class Container(val format: Int, val ext: String, val mime: String)

    private val MP4 = Container(MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4, "mp4", "video/mp4")
    private val WEBM = Container(MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM, "webm", "video/webm")

    suspend fun split(
        context: Context,
        info: VideoInfo,
        segments: List<Segment>,
        onProgress: (SplitProgress) -> Unit,
    ): SplitResult {
        val needed = info.sizeBytes + 16L * 1024 * 1024
        val free = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)
        if (free < needed) {
            throw SplitException("Not enough free space. The parts need about ${Format.bytes(needed)}, the phone has ${Format.bytes(free)} free.")
        }

        val resolver = context.contentResolver
        val pfd = resolver.openFileDescriptor(info.uri, "r") ?: throw SplitException("Couldn't open this video.")
        val created = ArrayList<Uri>()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(pfd.fileDescriptor)
            val (container, tracks, dropped) = chooseTracks(context, extractor)
            tracks.forEach { extractor.selectTrack(it.index) }

            var bufferSize = 1 shl 20
            for (t in tracks) if (t.format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                bufferSize = maxOf(bufferSize, t.format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            val io = Io(ByteBuffer.allocateDirect(bufferSize))

            // Part 1 keeps the original timeline; if a track starts before 0 (AAC priming),
            // shift everything so the earliest sample lands on 0. Read before any seek: a fresh
            // extractor sits on the very first sample of every track.
            val firstUs = minOf(0L, extractor.sampleTime.takeIf { it != -1L } ?: 0L)
            var fresh = true

            val total = info.sizeBytes.coerceAtLeast(1)
            val digits = if (segments.size >= 10) segments.size.toString().length else 1
            val saved = ArrayList<SavedPart>()
            for ((k, seg) in segments.withIndex()) {
                currentCoroutineContext().ensureActive()
                val name = "${info.baseName}_part${(k + 1).toString().padStart(digits, '0')}.${container.ext}"
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, container.mime)
                    put(MediaStore.Video.Media.RELATIVE_PATH, "$FOLDER/")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val out = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: throw SplitException("Couldn't create a file in $FOLDER.")
                created += out

                val isLast = k == segments.lastIndex
                val offsetUs = if (k == 0) firstUs else seg.startUs
                // Leave out tracks that ended before this part starts (e.g. audio shorter than video):
                // an empty track makes the muxer fail on Android 10–11.
                var partTracks = tracks.filter { it.isVideo || it.endUs <= 0 || it.endUs > seg.startUs + 20_000 }
                var bytes = 0L
                while (true) {
                    val written = resolver.openFileDescriptor(out, "rwt")!!.use { fd ->
                        val seek = !fresh
                        fresh = false
                        writePart(extractor, io, fd.fileDescriptor, container, info, partTracks, seg, offsetUs, isLast, seek) { done ->
                            onProgress(SplitProgress(k + 1, segments.size, ((saved.sumOf { it.bytes } + done).toFloat() / total).coerceIn(0f, 1f)))
                        }
                    }
                    val empty = written.counts.filterValues { it == 0 }.keys
                    if (written.ok) { bytes = written.bytes; break }
                    // Muxer refused to finish. If a track got no samples, retry without it.
                    if (empty.isEmpty() || empty.any { idx -> partTracks.first { it.index == idx }.isVideo }) {
                        throw SplitException("Couldn't write part ${k + 1}. This phone's muxer rejected the video.")
                    }
                    partTracks = partTracks.filter { it.index !in empty }
                }

                resolver.update(out, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
                // MediaStore renames on a clash ("x_part1 (1).mp4"); show the name it really got.
                val finalName = runCatching {
                    resolver.query(out, arrayOf(MediaStore.Video.Media.DISPLAY_NAME), null, null, null)?.use { c ->
                        if (c.moveToFirst()) c.getString(0) else null
                    }
                }.getOrNull() ?: name
                saved += SavedPart(k + 1, out, finalName, seg.startUs, seg.durationUs, bytes)
                onProgress(SplitProgress(k + 1, segments.size, (saved.sumOf { it.bytes }.toFloat() / total).coerceIn(0f, 1f)))
            }
            return SplitResult(saved, dropped)
        } catch (e: Throwable) {
            created.forEach { runCatching { resolver.delete(it, null, null) } }
            if (e is CancellationException || e is SplitException) throw e
            throw SplitException("Splitting failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            extractor.release()
            pfd.close()
        }
    }

    private class Io(var buffer: ByteBuffer) {
        val info = MediaCodec.BufferInfo()
        /** The next part's keyframe, kept aside in case open-GOP leading frames follow it. */
        var held: ByteBuffer = ByteBuffer.allocateDirect(0)
        var heldSize = 0
        var heldTimeUs = 0L

        /** Reads the current sample into [buffer] (or [held]), growing it when a sample is bigger. */
        fun read(extractor: MediaExtractor, intoHeld: Boolean): Int {
            val size = extractor.sampleSize.toInt()
            var target = if (intoHeld) held else buffer
            if (size > target.capacity()) {
                target = ByteBuffer.allocateDirect(size + (64 shl 10))
                if (intoHeld) held = target else buffer = target
            }
            target.clear()
            return extractor.readSampleData(target, 0)
        }
    }

    private class Written(val ok: Boolean, val bytes: Long, val counts: Map<Int, Int>)

    private suspend fun writePart(
        extractor: MediaExtractor,
        io: Io,
        fd: FileDescriptor,
        container: Container,
        info: VideoInfo,
        tracks: List<Track>,
        seg: Segment,
        offsetUs: Long,
        isLast: Boolean,
        seek: Boolean,
        onBytes: (Long) -> Unit,
    ): Written {
        val muxer = MediaMuxer(fd, container.format)
        val muxIndex = HashMap<Int, Int>()
        val counts = HashMap<Int, Int>()
        var started = false
        var bytes = 0L
        try {
            for (t in tracks) { muxIndex[t.index] = muxer.addTrack(t.format); counts[t.index] = 0 }
            if (container === MP4) {
                if (info.rotation in intArrayOf(90, 180, 270)) runCatching { muxer.setOrientationHint(info.rotation) }
                info.location?.let { (lat, lon) -> runCatching { muxer.setLocation(lat, lon) } }
            }
            muxer.start()
            started = true

            val video = tracks.first { it.isVideo }.index
            val filter = PartFilter(seg, offsetUs, isLast, video, tracks.map { it.index }.toSet())
            // Seeking to an exact keyframe time puts the video on that keyframe and each audio
            // track on the packet at or just before it. (Part 1's first attempt doesn't seek, so
            // packets timed before 0 are kept.)
            if (seek) extractor.seekTo(seg.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            var n = 0
            loop@ while (true) {
                val t = extractor.sampleTrackIndex
                if (t < 0) break // end of file
                val time = extractor.sampleTime
                val sync = extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0
                fun write(buf: ByteBuffer, size: Int, ptsUs: Long, key: Boolean) {
                    if (size <= 0) return
                    io.info.set(0, size, ptsUs - offsetUs, if (key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                    muxer.writeSampleData(muxIndex.getValue(t), buf, io.info)
                    counts[t] = counts.getValue(t) + 1
                    bytes += size
                }
                when (filter.next(t, time, sync)) {
                    PartFilter.Action.STOP -> break@loop
                    PartFilter.Action.SKIP -> Unit
                    PartFilter.Action.HOLD -> {
                        io.heldSize = io.read(extractor, intoHeld = true)
                        io.heldTimeUs = time
                    }
                    PartFilter.Action.FLUSH_AND_WRITE -> {
                        write(io.held, io.heldSize, io.heldTimeUs, key = true)
                        val read = io.read(extractor, intoHeld = false)
                        write(io.buffer, read, time, sync)
                    }
                    PartFilter.Action.WRITE -> {
                        val read = io.read(extractor, intoHeld = false)
                        write(io.buffer, read, time, sync)
                    }
                }
                if (++n % 64 == 0) {
                    currentCoroutineContext().ensureActive()
                    onBytes(bytes)
                }
                if (!extractor.advance()) break
            }
            return try {
                muxer.stop()
                Written(true, bytes, counts)
            } catch (_: IllegalStateException) {
                Written(false, bytes, counts)
            } finally {
                started = false
            }
        } finally {
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
        }
    }

    /**
     * Finds a container the phone's muxer accepts for this video codec (MP4 first, WebM for
     * VP8/VP9), then which audio tracks can go with it. Tested on a throwaway file so a bad
     * track is found before any real file is written.
     */
    private fun chooseTracks(context: Context, extractor: MediaExtractor): Triple<Container, List<Track>, List<String>> {
        var video: Track? = null
        val audio = ArrayList<Track>()
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            val end = if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else 0L
            if (mime.startsWith("video/") && video == null) video = Track(i, f, VideoInfo.codecName(mime), end, true)
            else if (mime.startsWith("audio/")) audio += Track(i, f, VideoInfo.codecName(mime), end, false)
        }
        val v = video ?: throw SplitException("This file has no video track.")
        val vMime = v.format.getString(MediaFormat.KEY_MIME)
        val order = if (vMime == MediaFormat.MIMETYPE_VIDEO_VP8 || vMime == MediaFormat.MIMETYPE_VIDEO_VP9) listOf(WEBM, MP4) else listOf(MP4, WEBM)

        // Dolby Vision profile 8 is HEVC underneath; if the muxer can't tag it as DV, the same
        // bytes still play as HEVC/HDR.
        val videoFormats = buildList {
            add(v.format)
            if (vMime == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION) add(MediaFormat(v.format).apply { setString(MediaFormat.KEY_MIME, MediaFormat.MIMETYPE_VIDEO_HEVC) })
        }

        val probe = File(context.cacheDir, "mux-probe.tmp")
        try {
            for (c in order) for (vf in videoFormats) {
                if (!accepts(probe, c, listOf(vf))) continue
                val okVideo = Track(v.index, vf, v.label, v.endUs, true)
                val kept = arrayListOf(okVideo)
                val dropped = ArrayList<String>()
                // Added one at a time on top of the ones already kept: some muxers take only one audio track.
                for (a in audio) if (accepts(probe, c, kept.map { it.format } + a.format)) kept += a else dropped += a.label
                return Triple(c, kept, dropped)
            }
        } finally {
            probe.delete()
        }
        throw SplitException("This phone can't save ${v.label} video without re-encoding it.")
    }

    private fun accepts(file: File, c: Container, formats: List<MediaFormat>): Boolean {
        var muxer: MediaMuxer? = null
        return try {
            muxer = MediaMuxer(file.path, c.format)
            formats.forEach { muxer.addTrack(it) }
            true
        } catch (_: Exception) {
            false
        } finally {
            runCatching { muxer?.release() }
        }
    }
}
