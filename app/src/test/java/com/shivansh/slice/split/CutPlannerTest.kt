package com.shivansh.slice.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CutPlannerTest {

    /** Keyframes every [gapS] seconds over [durS] seconds, like a phone camera. */
    private fun regular(durS: Double, gapS: Double): Pair<LongArray, Long> {
        val d = (durS * 1e6).toLong()
        val k = generateSequence(0L) { it + (gapS * 1e6).toLong() }.takeWhile { it < d }.toList().toLongArray()
        return k to d
    }

    private fun assertWellFormed(segs: List<Segment>, keyframes: LongArray, durationUs: Long) {
        assertEquals(0L, segs.first().startUs)
        assertEquals(durationUs, segs.last().endUs)
        segs.zipWithNext { a, b -> assertEquals(a.endUs, b.startUs) }
        segs.forEach { assertTrue(it.durationUs > 0) }
        segs.drop(1).forEach { assertTrue("cut at ${it.startUs} is not a keyframe", it.startUs in keyframes) }
    }

    @Test
    fun countGivesExactlyThatManyNearlyEqualParts() {
        val (k, d) = regular(47.3, 1.0)
        for (n in 2..20) {
            val plan = CutPlanner.byCount(k, d, n)
            assertEquals(n, plan.segments.size)
            assertNull(plan.cappedFrom)
            assertWellFormed(plan.segments, k, d)
            val ideal = d / n
            // each cut is within half a keyframe gap of its ideal spot, so parts differ by ≤ 1 gap
            plan.segments.forEach { assertTrue("n=$n ${it.durationUs} vs $ideal", abs(it.durationUs - ideal) <= 1_000_000) }
        }
    }

    @Test
    fun countIsCappedByTheNumberOfKeyframes() {
        val (k, d) = regular(10.0, 2.0) // keyframes at 0,2,4,6,8 → at most 5 parts
        assertEquals(5, CutPlanner.maxParts(k, d))
        val plan = CutPlanner.byCount(k, d, 9)
        assertEquals(5, plan.segments.size)
        assertEquals(9, plan.cappedFrom)
        assertWellFormed(plan.segments, k, d)
    }

    @Test
    fun countUsesEveryKeyframeWhenAskedForTheMaximum() {
        val k = longArrayOf(0, 900_000, 1_000_000, 5_000_000, 9_500_000)
        val d = 10_000_000L
        val plan = CutPlanner.byCount(k, d, 5)
        assertEquals(listOf(0L, 900_000L, 1_000_000L, 5_000_000L, 9_500_000L), plan.segments.map { it.startUs })
    }

    @Test
    fun singleKeyframeMeansOnePart() {
        val plan = CutPlanner.byCount(longArrayOf(0), 30_000_000, 4)
        assertEquals(1, plan.segments.size)
        assertEquals(1, CutPlanner.maxParts(longArrayOf(0), 30_000_000))
    }

    @Test
    fun maxLengthNeverExceedsTheLimitWhenKeyframesAllowIt() {
        val (k, d) = regular(125.0, 1.0)
        for (limit in listOf(5, 15, 30, 60)) {
            val plan = CutPlanner.byMaxLength(k, d, limit * 1_000_000L)
            assertEquals(0, plan.overLength)
            assertWellFormed(plan.segments, k, d)
            plan.segments.forEach { assertTrue(it.durationUs <= limit * 1_000_000L) }
            // and uses no more parts than needed (greedy: every part but the last is full length)
            plan.segments.dropLast(1).forEach { assertEquals(limit * 1_000_000L, it.durationUs) }
        }
    }

    @Test
    fun maxLengthWithIrregularKeyframes() {
        val k = longArrayOf(0, 2_100_000, 4_700_000, 9_800_000, 13_000_000, 14_900_000, 21_000_000)
        val d = 24_000_000L
        val plan = CutPlanner.byMaxLength(k, d, 10_000_000)
        assertWellFormed(plan.segments, k, d)
        assertEquals(listOf(0L, 9_800_000L, 14_900_000L), plan.segments.map { it.startUs })
        assertEquals(0, plan.overLength)
    }

    @Test
    fun maxLengthShorterThanTheKeyframeGapIsReported() {
        val (k, d) = regular(20.0, 4.0)
        val plan = CutPlanner.byMaxLength(k, d, 3_000_000)
        assertWellFormed(plan.segments, k, d)
        assertEquals(5, plan.segments.size) // one part per keyframe: the best possible
        assertEquals(5, plan.overLength)
    }

    @Test
    fun videoShorterThanTheLimitIsOnePart() {
        val (k, d) = regular(12.0, 1.0)
        val plan = CutPlanner.byMaxLength(k, d, 30_000_000)
        assertEquals(1, plan.segments.size)
        assertEquals(0, plan.overLength)
    }

    @Test
    fun formatting() {
        assertEquals("0:07", Format.clock(7_400_000))
        assertEquals("0:39", Format.clock(38_996_000)) // a range ending at 38.996 s reads 0:39, matching "39.0 s"
        assertEquals("1:02:09", Format.clock(3_729_000_000))
        assertEquals("31.2 s", Format.length(31_240_000))
        assertEquals("1:02.4", Format.length(62_400_000))
        assertEquals("1 min 30 s", Format.seconds(90))
        assertEquals("1.2 GB", Format.bytes(1_288_490_189))
        assertEquals("512 KB", Format.bytes(524_288).replace(".0", ""))
    }
}
