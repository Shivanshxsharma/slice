package com.shivansh.slice.split

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs the real [CutPlanner] and [PartFilter] over the packet lists of three real videos
 * (made with ffmpeg, dumped with ffprobe into src/test/resources):
 *
 * - phone_h264:   H.264, no B-frames, keyframe every 1 s, AAC 48 kHz (how most phone cameras record)
 * - bframes_h264: H.264 with B-frames and irregular keyframes (scene cuts), AAC 44.1 kHz
 * - opengop_hevc: HEVC with open GOPs and B-frames, AAC with a priming packet before 0
 *
 * A fake extractor reads them the way Android's MediaExtractor does: each track in decode order,
 * always handing out the selected track whose next sample has the smallest timestamp.
 */
class SplitRoutingTest {

    private data class Pkt(val id: Int, val track: Int, val ptsUs: Long, val sync: Boolean, val size: Int)

    private class Video(val name: String) {
        val packets: List<Pkt>
        init {
            val text = SplitRoutingTest::class.java.getResource("/$name.csv")!!.readText()
            packets = text.lineSequence().drop(1).filter { it.isNotBlank() }.mapIndexed { i, line ->
                val (t, pts, sync, size) = line.split(',')
                Pkt(i, t.toInt(), pts.toLong(), sync == "1", size.toInt())
            }.toList()
        }
        val videoTrack = 0
        val keyframes = packets.filter { it.track == videoTrack && it.sync }.map { it.ptsUs }.sorted().toLongArray()
        val durationUs = packets.maxOf { it.ptsUs } + 33_333
        val tracks = packets.map { it.track }.toSet()
    }

    /** Behaves like MediaExtractor with every track selected. */
    private class FakeExtractor(packets: List<Pkt>) {
        private val byTrack = packets.groupBy { it.track } // file order within a track = decode order
        private val pos = HashMap<Int, Int>().apply { byTrack.keys.forEach { put(it, 0) } }

        fun peek(): Pkt? = byTrack.mapNotNull { (t, list) -> list.getOrNull(pos.getValue(t)) }.minByOrNull { it.ptsUs }

        fun advance() { val p = peek() ?: return; pos[p.track] = pos.getValue(p.track) + 1 }

        /** SEEK_TO_PREVIOUS_SYNC: each track goes to its last sync sample at or before [timeUs]. */
        fun seekTo(timeUs: Long) {
            for ((t, list) in byTrack) {
                val i = list.indices.lastOrNull { list[it].sync && list[it].ptsUs <= timeUs } ?: 0
                pos[t] = i
            }
        }
    }

    private class Part(val seg: Segment, val offsetUs: Long, val packets: List<Pkt>)

    /** Mirrors Splitter.split: part 1 reads from the very start, later parts seek to their keyframe. */
    private fun split(v: Video, segments: List<Segment>): List<Part> {
        val ex = FakeExtractor(v.packets)
        val firstUs = minOf(0L, ex.peek()!!.ptsUs)
        return segments.mapIndexed { k, seg ->
            val offset = if (k == 0) firstUs else seg.startUs
            val filter = PartFilter(seg, offset, k == segments.lastIndex, v.videoTrack, v.tracks)
            if (k == 0) { /* fresh extractor is already at the start */ } else ex.seekTo(seg.startUs)
            val out = ArrayList<Pkt>()
            var held: Pkt? = null
            while (true) {
                val p = ex.peek() ?: break
                when (filter.next(p.track, p.ptsUs, p.sync)) {
                    PartFilter.Action.STOP -> break
                    PartFilter.Action.SKIP -> Unit
                    PartFilter.Action.HOLD -> held = p
                    PartFilter.Action.FLUSH_AND_WRITE -> { out += held!!; out += p }
                    PartFilter.Action.WRITE -> out += p
                }
                ex.advance()
            }
            Part(seg, offset, out)
        }
    }

    private val videos = listOf("phone_h264", "bframes_h264", "opengop_hevc").map { Video(it) }
    private val modes = listOf(
        SplitMode.Count(2), SplitMode.Count(3), SplitMode.Count(5), SplitMode.Count(7), SplitMode.Count(13),
        SplitMode.MaxLength(4), SplitMode.MaxLength(10), SplitMode.MaxLength(30),
    )

    @Test
    fun everyPacketIsKeptAndOnlyOpenGopCutKeyframesRepeat() {
        for (v in videos) for (m in modes) {
            val plan = CutPlanner.plan(v.keyframes, v.durationUs, m)
            val parts = split(v, plan.segments)
            val written = parts.flatMap { it.packets }
            val ids = written.map { it.id }.toSet()
            val missing = v.packets.filter { it.id !in ids }
            assertTrue("${v.name} $m: lost ${missing.size} packets, e.g. ${missing.take(3)}", missing.isEmpty())

            val repeats = written.groupBy { it.id }.filterValues { it.size > 1 }.values.map { it.first() }
            if (v.name == "opengop_hevc") {
                // Each part (but the last) ends with the next part's keyframe, which its leading frames need.
                val cuts = plan.segments.drop(1).map { it.startUs }.toSet()
                repeats.forEach { assertTrue("${v.name} $m: repeated $it", it.track == v.videoTrack && it.sync && it.ptsUs in cuts) }
                repeats.forEach { r -> assertEquals(2, written.count { it.id == r.id }) }
            } else {
                assertTrue("${v.name} $m: repeated ${repeats.take(3)}", repeats.isEmpty())
            }
        }
    }

    @Test
    fun openGopLeadingFramesStayWithThePartBeforeTheCut() {
        val v = videos.first { it.name == "opengop_hevc" }
        val parts = split(v, CutPlanner.plan(v.keyframes, v.durationUs, SplitMode.Count(5)).segments)
        for (k in 0 until parts.lastIndex) {
            val cut = parts[k + 1].seg.startUs
            val video = parts[k].packets.filter { it.track == v.videoTrack }
            // nothing displayed after the cut except the cut keyframe itself
            assertTrue(video.all { it.ptsUs < cut || (it.ptsUs == cut && it.sync) })
            // and the next part has no frame displayed before its keyframe
            assertTrue(parts[k + 1].packets.filter { it.track == v.videoTrack }.all { it.ptsUs >= cut })
        }
    }

    @Test
    fun partsStartOnTheirKeyframeAndTimestampsStartAtZero() {
        for (v in videos) for (m in modes) {
            val parts = split(v, CutPlanner.plan(v.keyframes, v.durationUs, m).segments)
            parts.forEachIndexed { k, part ->
                val firstVideo = part.packets.first { it.track == v.videoTrack }
                assertTrue("${v.name} $m part $k starts on a non-keyframe", firstVideo.sync)
                if (k > 0) assertEquals("${v.name} $m part $k", part.seg.startUs, firstVideo.ptsUs)
                val minTs = part.packets.minOf { it.ptsUs - part.offsetUs }
                assertTrue("${v.name} $m part $k has a negative timestamp", minTs >= 0)
                if (k > 0) assertEquals("${v.name} $m part $k video should start at 0", 0L, firstVideo.ptsUs - part.offsetUs)
            }
        }
    }

    @Test
    fun audioIsSplitExactlyAtTheCuts() {
        for (v in videos) for (m in modes) {
            val parts = split(v, CutPlanner.plan(v.keyframes, v.durationUs, m).segments)
            parts.forEachIndexed { k, part ->
                val audio = part.packets.filter { it.track != v.videoTrack }
                val expected = v.packets.filter {
                    it.track != v.videoTrack && (k == 0 || it.ptsUs >= part.seg.startUs) && (k == parts.lastIndex || it.ptsUs < part.seg.endUs)
                }
                assertEquals("${v.name} $m part $k audio", expected.map { it.id }, audio.map { it.id })
            }
        }
    }

    @Test
    fun videoInEachPartIsOneUnbrokenRunInDecodeOrder() {
        for (v in videos.filter { it.name != "opengop_hevc" }) for (m in modes) {
            val order = v.packets.filter { it.track == v.videoTrack }.map { it.id }
            val parts = split(v, CutPlanner.plan(v.keyframes, v.durationUs, m).segments)
            for (part in parts) {
                val ids = part.packets.filter { it.track == v.videoTrack }.map { it.id }
                val from = order.indexOf(ids.first())
                assertEquals("${v.name} $m", order.subList(from, from + ids.size), ids)
            }
        }
    }

    @Test
    fun partsAddUpToTheWholeFileSize() {
        for (v in videos.filter { it.name != "opengop_hevc" }) {
            val parts = split(v, CutPlanner.plan(v.keyframes, v.durationUs, SplitMode.Count(4)).segments)
            assertEquals(v.packets.sumOf { it.size.toLong() }, parts.sumOf { p -> p.packets.sumOf { it.size.toLong() } })
        }
    }
}
