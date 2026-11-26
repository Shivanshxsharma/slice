package com.shivansh.slice

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivansh.slice.split.SavedPart
import com.shivansh.slice.split.SplitProgress
import com.shivansh.slice.split.SplitResult
import com.shivansh.slice.split.VideoInfo
import com.shivansh.slice.ui.DoneScreen
import com.shivansh.slice.ui.EmptyScreen
import com.shivansh.slice.ui.LocalColors
import com.shivansh.slice.ui.SetupScreen
import com.shivansh.slice.ui.SliceTheme
import com.shivansh.slice.ui.SplittingScreen
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the app's real screens at phone size (1080×2400, 420 dpi) for the README, using the
 * painted sample "video" in src/test/resources/readme. Skipped unless -Pscreenshots=<dir> is set.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-night-420dpi")
class ReadmeScreenshots {
    @get:Rule val compose = createComposeRule()

    private val outDir: String? = System.getProperty("slice.screenshots")

    @Before
    fun onlyWhenAsked() = assumeTrue("pass -Pscreenshots=<dir> to render", !outDir.isNullOrBlank())

    private fun img(name: String): ImageBitmap =
        javaClass.getResourceAsStream("/readme/$name.png")!!.use { BitmapFactory.decodeStream(it) }.asImageBitmap()

    /** 2:36 of 4K HEVC at 60 fps with a keyframe about every second, like a phone recording. */
    private val sample = VideoInfo(
        uri = Uri.parse("content://media/picker/0/video/1"),
        name = "Sunset drive.mp4",
        sizeBytes = 1_946_000_000L,
        durationUs = 156_480_000L,
        width = 3840, height = 2160, rotation = 0,
        videoMime = "video/hevc", audioMimes = listOf("audio/mp4a-latm"),
        frameRate = 60f,
        keyframesUs = LongArray(157) { i -> i * 1_000_000L + if (i == 0) 0 else ((i * 37) % 9 - 4) * 4_000L },
        location = null,
    )

    private fun vm(): SliceViewModel = SliceViewModel(RuntimeEnvironment.getApplication()).apply {
        showSample(sample, img("poster"), (0 until 10).map { img("strip_$it") })
    }

    private fun shoot(name: String, advanceMs: Long = 1_500, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent { SliceTheme { Phone(content) } }
        compose.mainClock.advanceTimeBy(advanceMs)
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        File(outDir!!).mkdirs()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun start() = shoot("start", advanceMs = 1_450) { EmptyScreen(onPick = {}) }

    @Test fun parts() {
        val vm = vm().apply { mode = ModeKind.Count; partsText = "4" }
        shoot("parts") { SetupScreen(vm, onClose = {}) }
    }

    @Test fun maxLength() {
        val vm = vm().apply { mode = ModeKind.Length; secondsText = "30" }
        shoot("max-length") { SetupScreen(vm, onClose = {}) }
    }

    @Test fun splitting() = shoot("splitting") { SplittingScreen("Sunset drive.mp4", SplitProgress(3, 4, 0.62f), onCancel = {}) }

    @Test fun done() {
        val vm = vm()
        val plan = vm.apply { mode = ModeKind.Count; partsText = "4" }.plan()!!
        val parts = plan.segments.mapIndexed { i, s ->
            SavedPart(i + 1, Uri.parse("content://media/external/video/media/${100 + i}"), "Sunset drive_part${i + 1}.mp4",
                s.startUs, s.durationUs, sample.sizeBytes * s.durationUs / sample.durationUs)
        }
        val result = SplitResult(parts, emptyList())
        vm.showSampleResult(result, (1..4).associateWith { img("part_$it") })
        shoot("done") { DoneScreen(vm, result, {}, {}, {}, {}, {}) }
    }

    @Config(qualifiers = "w412dp-h915dp-notnight-420dpi")
    @Test fun partsLight() {
        val vm = vm().apply { mode = ModeKind.Count; partsText = "4" }
        shoot("parts-light") { SetupScreen(vm, onClose = {}) }
    }
}

/** Status bar and gesture bar around a screen, since Robolectric has no system bars. */
@Composable
private fun Phone(content: @Composable () -> Unit) {
    val c = LocalColors.current
    Column(Modifier.fillMaxSize().background(c.bg)) {
        Row(Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 26.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("9:41", color = c.text, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Canvas(Modifier.size(width = 62.dp, height = 14.dp)) {
                val col = c.text
                // signal
                for (i in 0 until 4) {
                    val h = size.height * (0.35f + 0.65f * i / 3)
                    drawRoundRect(col, Offset(i * 4.5.dp.toPx(), size.height - h), Size(3.dp.toPx(), h), CornerRadius(1.dp.toPx()))
                }
                // wifi
                val cx = 27.dp.toPx()
                for (r in listOf(3f, 6.5f, 10f)) {
                    drawArc(col, 225f, 90f, false, Offset(cx - r.dp.toPx(), size.height - r.dp.toPx()), Size(2 * r.dp.toPx(), 2 * r.dp.toPx()), style = Stroke(1.8.dp.toPx()))
                }
                // battery
                val bx = 40.dp.toPx()
                drawRoundRect(col, Offset(bx, 1.dp.toPx()), Size(19.dp.toPx(), 12.dp.toPx()), CornerRadius(3.dp.toPx()), style = Stroke(1.4.dp.toPx()))
                drawRoundRect(col, Offset(bx + 2.2.dp.toPx(), 3.2.dp.toPx()), Size(12.dp.toPx(), 7.6.dp.toPx()), CornerRadius(1.5.dp.toPx()))
                drawRoundRect(col, Offset(bx + 19.8.dp.toPx(), 5.dp.toPx()), Size(1.8.dp.toPx(), 4.dp.toPx()), CornerRadius(1.dp.toPx()))
            }
        }
        Box(Modifier.weight(1f)) { content() }
        Box(Modifier.fillMaxWidth().height(22.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.width(110.dp).height(4.dp).clip(CircleShape).background(c.text.copy(alpha = 0.85f)))
        }
    }
}

