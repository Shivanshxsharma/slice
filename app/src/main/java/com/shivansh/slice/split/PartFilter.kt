package com.shivansh.slice.split

/**
 * Decides, sample by sample, what goes into one part while the extractor reads from the part's
 * starting keyframe. Pure Kotlin so the routing is unit-tested on the PC against real packet
 * lists (see SplitRoutingTest).
 *
 * - Video: everything from the starting keyframe up to (not including) the keyframe that starts
 *   the next part.
 * - Audio: every packet whose time is in [start, end).
 * - Part 1 keeps the original timeline (shifted by [offsetUs] if a track starts before 0).
 *
 * Open GOPs (common in x265 encodes): right after a keyframe in decode order there can be
 * "leading" frames that display *before* it and are predicted from both sides of it. They can't
 * be decoded at the start of the next part, so this part keeps them, together with the next
 * keyframe they need. That costs one repeated frame at the end of the part instead of losing
 * the leading frames. Closed-GOP video (what phone cameras record) never takes this path.
 */
class PartFilter(
    private val seg: Segment,
    private val offsetUs: Long,
    private val isLast: Boolean,
    private val videoTrack: Int,
    private val tracks: Set<Int>,
) {
    enum class Action {
        /** Write this sample. */
        WRITE,
        /** Not for this part. */
        SKIP,
        /** Keep a copy of this sample; the next decision says whether it gets written. */
        HOLD,
        /** Write the held sample, then this one. */
        FLUSH_AND_WRITE,
        /** The part is complete. */
        STOP,
    }

    private val done = HashSet<Int>()
    private var videoDone = false
    private var heldKeyUs = Long.MIN_VALUE
    private var leading = false

    fun next(track: Int, timeUs: Long, sync: Boolean): Action {
        if (done.size == tracks.size) return Action.STOP
        // Samples come out roughly in time order, so once the video has reached the next cut and
        // we're well past it, nothing for this part can still be coming. This also ends the part
        // when an audio track stops before the video does.
        if (videoDone && timeUs > seg.endUs + LATE_US) return Action.STOP
        if (track !in tracks || track in done) return Action.SKIP
        val isVideo = track == videoTrack

        if (isVideo && heldKeyUs != Long.MIN_VALUE) {
            // Just past the next part's keyframe: is this one of its leading frames?
            if (timeUs < heldKeyUs) {
                val first = !leading
                leading = true
                return if (first) Action.FLUSH_AND_WRITE else Action.WRITE
            }
            return finish(track)
        }

        if (!isLast && timeUs >= seg.endUs && (sync || !isVideo)) {
            if (isVideo) {
                heldKeyUs = timeUs
                return Action.HOLD
            }
            return finish(track)
        }
        // Before the start: audio that belongs to the previous part, or (open GOP) frames that
        // display before the keyframe; those were kept by the previous part.
        val inRange = timeUs >= offsetUs && (timeUs >= seg.startUs || offsetUs < seg.startUs)
        return if (inRange) Action.WRITE else Action.SKIP
    }

    private fun finish(track: Int): Action {
        done += track
        if (track == videoTrack) videoDone = true
        return if (done.size == tracks.size) Action.STOP else Action.SKIP
    }

    companion object {
        const val LATE_US = 2_000_000L
    }
}
