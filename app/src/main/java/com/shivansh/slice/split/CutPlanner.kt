package com.shivansh.slice.split

import kotlin.math.abs

/** One output part: from [startUs] (a keyframe, or 0 for the first part) up to [endUs]. */
data class Segment(val startUs: Long, val endUs: Long) {
    val durationUs: Long get() = endUs - startUs
}

/** How a video should be cut, as the user entered it. */
sealed interface SplitMode {
    data class Count(val parts: Int) : SplitMode
    data class MaxLength(val seconds: Int) : SplitMode
}

data class Plan(
    val segments: List<Segment>,
    /** Parts that came out longer than asked because the keyframes are too far apart. */
    val overLength: Int = 0,
    /** The count the user asked for was more than the keyframes allow; this many were planned. */
    val cappedFrom: Int? = null,
)

/**
 * Picks where to cut. A lossless cut can only start a part on a keyframe, because every frame
 * up to the next keyframe is stored as a difference from it. So every part after the first
 * starts on a keyframe, and nothing is ever re-encoded.
 *
 * Pure Kotlin, no Android types, so it is unit-tested on the PC with real keyframe lists.
 */
object CutPlanner {

    /** Keyframes we can cut at: after time 0 and before the end, sorted, no duplicates. */
    fun cutPoints(keyframesUs: LongArray, durationUs: Long): LongArray =
        keyframesUs.filter { it > 0 && it < durationUs }.distinct().sorted().toLongArray()

    /** The most parts this video can be split into: one per keyframe. */
    fun maxParts(keyframesUs: LongArray, durationUs: Long): Int =
        cutPoints(keyframesUs, durationUs).size + 1

    fun plan(keyframesUs: LongArray, durationUs: Long, mode: SplitMode): Plan = when (mode) {
        is SplitMode.Count -> byCount(keyframesUs, durationUs, mode.parts)
        is SplitMode.MaxLength -> byMaxLength(keyframesUs, durationUs, mode.seconds * 1_000_000L)
    }

    /**
     * [parts] pieces as close to equal as the keyframes allow. Each cut goes to the keyframe
     * nearest its ideal time, while leaving enough later keyframes for the remaining cuts.
     */
    fun byCount(keyframesUs: LongArray, durationUs: Long, parts: Int): Plan {
        require(durationUs > 0) { "empty video" }
        val points = cutPoints(keyframesUs, durationUs)
        val wanted = parts.coerceAtLeast(1)
        val n = wanted.coerceAtMost(points.size + 1)
        val cuts = ArrayList<Long>(n - 1)
        var lo = 0 // first index of [points] still free to use
        for (i in 1 until n) {
            val target = durationUs * i / n
            val remainingAfter = (n - 1) - i // cuts still needed after this one
            val hi = points.size - 1 - remainingAfter
            var best = lo
            for (j in lo..hi) {
                if (abs(points[j] - target) < abs(points[best] - target)) best = j
                if (points[j] > target) break // sorted: later keyframes only get further away
            }
            cuts += points[best]
            lo = best + 1
        }
        return Plan(segmentsFrom(cuts, durationUs), cappedFrom = if (n < wanted) wanted else null)
    }

    /**
     * Pieces no longer than [maxUs] (good for Status/Stories limits). Each cut goes to the
     * last keyframe that keeps the part within the limit. If no keyframe fits, the part runs
     * to the next keyframe and is counted in [Plan.overLength].
     */
    fun byMaxLength(keyframesUs: LongArray, durationUs: Long, maxUs: Long): Plan {
        require(durationUs > 0) { "empty video" }
        require(maxUs > 0) { "length must be positive" }
        val points = cutPoints(keyframesUs, durationUs)
        val cuts = ArrayList<Long>()
        var start = 0L
        var over = 0
        var idx = 0 // first point after [start]
        while (durationUs - start > maxUs) {
            while (idx < points.size && points[idx] <= start) idx++
            if (idx >= points.size) break // no keyframe left: the rest is one (long) part
            var pick = -1
            var j = idx
            while (j < points.size && points[j] - start <= maxUs) { pick = j; j++ }
            if (pick == -1) { pick = idx; over++ }
            cuts += points[pick]
            start = points[pick]
            idx = pick + 1
        }
        if (durationUs - start > maxUs) over++
        return Plan(segmentsFrom(cuts, durationUs), overLength = over)
    }

    private fun segmentsFrom(cuts: List<Long>, durationUs: Long): List<Segment> {
        val bounds = listOf(0L) + cuts + durationUs
        return bounds.zipWithNext { a, b -> Segment(a, b) }
    }
}
