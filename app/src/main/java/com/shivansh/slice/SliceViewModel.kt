package com.shivansh.slice

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shivansh.slice.split.CutPlanner
import com.shivansh.slice.split.Plan
import com.shivansh.slice.split.SplitException
import com.shivansh.slice.split.SplitMode
import com.shivansh.slice.split.SplitProgress
import com.shivansh.slice.split.SplitResult
import com.shivansh.slice.split.Splitter
import com.shivansh.slice.split.VideoInfo
import com.shivansh.slice.split.VideoProbe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface Stage {
    data object Empty : Stage
    data object Reading : Stage
    data object Setup : Stage
    data class Splitting(val progress: SplitProgress) : Stage
    data class Done(val result: SplitResult) : Stage
}

enum class ModeKind { Count, Length }

class SliceViewModel(app: Application) : AndroidViewModel(app) {
    var stage by mutableStateOf<Stage>(Stage.Empty)
        private set
    var info by mutableStateOf<VideoInfo?>(null)
        private set
    var error by mutableStateOf<String?>(null)
    var mode by mutableStateOf(ModeKind.Count)
    var partsText by mutableStateOf("2")
    var secondsText by mutableStateOf("30")

    var poster by mutableStateOf<ImageBitmap?>(null)
        private set
    /** Frames spread across the video for the timeline, filled in as they decode. */
    val filmstrip = mutableStateListOf<ImageBitmap?>()
    /** Part number → first frame, for the results list. */
    val partThumbs = mutableStateMapOf<Int, ImageBitmap>()

    private var job: Job? = null
    private var thumbsJob: Job? = null
    // MediaMetadataRetriever isn't thread-safe: all frame grabs go through one thread.
    private val frameDispatcher = Dispatchers.IO.limitedParallelism(1)

    val splitMode: SplitMode?
        get() = when (mode) {
            ModeKind.Count -> partsText.toIntOrNull()?.takeIf { it >= 1 }?.let { SplitMode.Count(it) }
            ModeKind.Length -> secondsText.toIntOrNull()?.takeIf { it >= 1 }?.let { SplitMode.MaxLength(it) }
        }

    fun plan(): Plan? {
        val i = info ?: return null
        val m = splitMode ?: return null
        return CutPlanner.plan(i.keyframesUs, i.durationUs, m)
    }

    fun maxParts(): Int = info?.let { CutPlanner.maxParts(it.keyframesUs, it.durationUs) } ?: 1

    fun load(uri: Uri) {
        if (stage is Stage.Splitting) return
        job?.cancel()
        clearVideo()
        stage = Stage.Reading
        job = viewModelScope.launch {
            try {
                val probed = withContext(Dispatchers.IO) { VideoProbe.probe(getApplication(), uri) }
                info = probed
                partsText = "2"
                stage = Stage.Setup
                loadFrames(probed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SplitException) {
                fail(e.message)
            } catch (e: Exception) {
                fail("Couldn't read this video: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun fail(message: String?) {
        error = message ?: "Something went wrong."
        stage = if (info != null) Stage.Setup else Stage.Empty
    }

    private fun loadFrames(v: VideoInfo) {
        thumbsJob?.cancel()
        thumbsJob = viewModelScope.launch(frameDispatcher) {
            withRetriever(v) { r ->
                val aspect = aspectOf(v)
                val first = grab(r, v.durationUs / 10, 720, aspect)
                val count = 10
                withContext(Dispatchers.Main) { poster = first; filmstrip.clear(); repeat(count) { filmstrip.add(null) } }
                for (i in 0 until count) {
                    val t = v.durationUs * (2 * i + 1) / (2 * count)
                    val bmp = grab(r, t, 200, aspect)
                    withContext(Dispatchers.Main) { if (info === v && i < filmstrip.size) filmstrip[i] = bmp }
                }
            }
        }
    }

    private suspend fun withRetriever(v: VideoInfo, block: suspend (MediaMetadataRetriever) -> Unit) {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(getApplication(), v.uri)
            block(r)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Thumbnails are decoration; the split doesn't need them.
        } finally {
            runCatching { r.release() }
        }
    }

    private fun aspectOf(v: VideoInfo): Float {
        val (w, h) = if (v.rotation == 90 || v.rotation == 270) v.height to v.width else v.width to v.height
        return if (w > 0 && h > 0) w.toFloat() / h else 16f / 9f
    }

    private fun grab(r: MediaMetadataRetriever, timeUs: Long, width: Int, aspect: Float): ImageBitmap? = runCatching {
        val h = (width / aspect).toInt().coerceAtLeast(1)
        val bmp: Bitmap? = r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width, h)
        bmp?.asImageBitmap()
    }.getOrNull()

    fun split() {
        val v = info ?: return
        val p = plan() ?: return
        if (stage is Stage.Splitting) return
        stage = Stage.Splitting(SplitProgress(1, p.segments.size, 0f))
        job = viewModelScope.launch {
            try {
                var lastPart = 0
                var lastFraction = -1f
                val result = withContext(Dispatchers.IO) {
                    Splitter.split(getApplication(), v, p.segments) { prog ->
                        // Called often from the IO thread; only post visible changes.
                        if (prog.part != lastPart || prog.fraction - lastFraction >= 0.004f) {
                            lastPart = prog.part
                            lastFraction = prog.fraction
                            viewModelScope.launch(Dispatchers.Main) { if (stage is Stage.Splitting) stage = Stage.Splitting(prog) }
                        }
                    }
                }
                stage = Stage.Done(result)
                loadPartThumbs(result)
            } catch (e: CancellationException) {
                stage = Stage.Setup
            } catch (e: SplitException) {
                fail(e.message)
            } catch (e: Exception) {
                fail("Splitting failed: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun loadPartThumbs(result: SplitResult) {
        val v = info ?: return
        thumbsJob?.cancel()
        thumbsJob = viewModelScope.launch(frameDispatcher) {
            withRetriever(v) { r ->
                for (p in result.parts) {
                    val bmp = grab(r, p.startUs, 320, aspectOf(v)) ?: continue
                    withContext(Dispatchers.Main) { partThumbs[p.index] = bmp }
                }
            }
        }
    }

    /** Puts a ready-made video on screen without reading a file. Only for the README screenshots. */
    internal fun showSample(sample: VideoInfo, posterImage: ImageBitmap, frames: List<ImageBitmap>) {
        info = sample
        poster = posterImage
        filmstrip.clear()
        filmstrip.addAll(frames)
        stage = Stage.Setup
    }

    /** Shows a finished split without writing files. Only for the README screenshots. */
    internal fun showSampleResult(result: SplitResult, thumbs: Map<Int, ImageBitmap>) {
        partThumbs.putAll(thumbs)
        stage = Stage.Done(result)
    }

    fun cancelSplit() {
        if (stage is Stage.Splitting) job?.cancel()
    }

    /** Back to the start screen, forgetting the current video. */
    fun reset() {
        job?.cancel()
        clearVideo()
        stage = Stage.Empty
    }

    /** From the results, back to the same video to split it differently. */
    fun backToSetup() {
        if (info != null) { partThumbs.clear(); stage = Stage.Setup }
    }

    private fun clearVideo() {
        thumbsJob?.cancel()
        info = null
        poster = null
        filmstrip.clear()
        partThumbs.clear()
    }
}
