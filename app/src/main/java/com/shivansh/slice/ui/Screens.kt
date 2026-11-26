package com.shivansh.slice.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shivansh.slice.ModeKind
import com.shivansh.slice.SliceViewModel
import com.shivansh.slice.split.Format
import com.shivansh.slice.split.Plan
import com.shivansh.slice.split.SavedPart
import com.shivansh.slice.split.SplitProgress
import com.shivansh.slice.split.SplitResult
import com.shivansh.slice.split.Splitter
import com.shivansh.slice.split.VideoInfo
import kotlin.math.roundToInt

// ---------------------------------------------------------------- start

@Composable
fun EmptyScreen(onPick: () -> Unit) {
    val c = LocalColors.current
    Column(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Wordmark(Modifier.padding(top = 18.dp))
        Spacer(Modifier.weight(1f))
        HeroStrip(Modifier.fillMaxWidth().height(116.dp))
        Spacer(Modifier.height(40.dp))
        Text("Cut any video\ninto parts.", style = Type.display, color = c.text)
        Spacer(Modifier.height(14.dp))
        Text(
            "Pick a video and choose how many parts. Every frame is copied as it is — nothing is re-compressed, so each part has exactly the original quality.",
            style = Type.body,
            color = c.muted,
        )
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fact("Lossless")
            Fact("Offline")
            Fact("No re-encoding")
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Pick a video", Modifier.fillMaxWidth(), onClick = onPick)
        Spacer(Modifier.height(12.dp))
        Text(
            "Or share a video to Slice from your gallery.",
            Modifier.fillMaxWidth(),
            style = Type.label,
            color = c.muted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Fact(text: String) {
    val c = LocalColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(c.accent))
        Spacer(Modifier.width(6.dp))
        Text(text, style = Type.label, color = c.text)
    }
}

/** A strip of film that keeps slicing itself into three; the middle piece lifts out. */
@Composable
private fun HeroStrip(modifier: Modifier) {
    val c = LocalColors.current
    val t = rememberInfiniteTransition(label = "hero")
    val open by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1500, delayMillis = 350, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "open",
    )
    Canvas(modifier) {
        val h = size.height * 0.7f
        val top = (size.height - h) / 2
        val gap = 16.dp.toPx() * open
        val lift = 14.dp.toPx() * open
        val slant = h * 0.2f
        val third = size.width / 3
        val hole = 7.dp.toPx()
        val pitch = 17.dp.toPx()
        for (i in 0..2) {
            val path = Path().apply {
                val lt = if (i == 0) 0f else i * third + slant / 2 + gap / 2
                val lb = if (i == 0) 0f else i * third - slant / 2 + gap / 2
                val rt = if (i == 2) size.width else (i + 1) * third + slant / 2 - gap / 2
                val rb = if (i == 2) size.width else (i + 1) * third - slant / 2 - gap / 2
                moveTo(lt, top); lineTo(rt, top); lineTo(rb, top + h); lineTo(lb, top + h); close()
            }
            translate(top = if (i == 1) -lift else 0f) {
                clipPath(path) {
                    drawRect(if (i == 1) c.accent else c.text, Offset(0f, top), Size(size.width, h))
                    var x = pitch / 2
                    while (x < size.width) {
                        for (y in listOf(top + hole * 0.8f, top + h - hole * 1.8f)) {
                            drawRoundRect(c.bg, Offset(x, y), Size(hole, hole), CornerRadius(hole * 0.25f))
                        }
                        x += pitch
                    }
                    // the picture band between the sprocket rows
                    drawRect(Color.Black.copy(alpha = if (i == 1) 0.18f else 0.10f), Offset(0f, top + hole * 2.6f), Size(size.width, h - hole * 5.2f))
                }
            }
        }
    }
}

@Composable
fun ReadingScreen() {
    val c = LocalColors.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(color = c.accent, trackColor = c.surface2, strokeWidth = 4.dp, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(20.dp))
        Text("Reading the video", style = Type.bodyStrong, color = c.text)
        Spacer(Modifier.height(4.dp))
        Text("Finding where it can be cut", style = Type.label, color = c.muted)
    }
}

// ---------------------------------------------------------------- setup

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(vm: SliceViewModel, onClose: () -> Unit) {
    val c = LocalColors.current
    val v = vm.info ?: return
    val focus = LocalFocusManager.current
    val plan = remember(v, vm.mode, vm.partsText, vm.secondsText) { vm.plan() }
    val maxParts = remember(v) { vm.maxParts() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 130.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.surface2).pressable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Close, "Close", tint = c.text) }
                Spacer(Modifier.width(14.dp))
                Text(v.name, Modifier.weight(1f), style = Type.bodyStrong, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Poster(vm, v)
            Spacer(Modifier.height(10.dp))
            Text(metaLine(v), style = Type.numberSmall, color = c.muted)
            Spacer(Modifier.height(22.dp))

            Segmented(
                listOf("Number of parts", "Max length"),
                if (vm.mode == ModeKind.Count) 0 else 1,
                Modifier.fillMaxWidth(),
            ) { vm.mode = if (it == 0) ModeKind.Count else ModeKind.Length }
            Spacer(Modifier.height(20.dp))

            if (vm.mode == ModeKind.Count) {
                val n = vm.partsText.toIntOrNull() ?: 0
                NumberInput(
                    text = vm.partsText,
                    unit = if (n == 1) "part" else "parts",
                    canMinus = n > 2,
                    canPlus = n < maxParts,
                    onMinus = { vm.partsText = (n - 1).coerceAtLeast(2).toString() },
                    onPlus = { vm.partsText = (n + 1).coerceAtMost(maxParts).toString() },
                    onText = { vm.partsText = it },
                    onDone = { focus.clearFocus() },
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(2, 3, 4, 5, 6, 10).filter { it <= maxParts }.forEach { k ->
                        Chip("$k", n == k) { vm.partsText = "$k"; focus.clearFocus() }
                    }
                }
            } else {
                val s = vm.secondsText.toIntOrNull() ?: 0
                val step = when { s <= 10 -> 1; s < 60 -> 5; else -> 15 }
                NumberInput(
                    text = vm.secondsText,
                    unit = "seconds max per part" + if (s >= 60) "  (${Format.seconds(s)})" else "",
                    canMinus = s > 1,
                    canPlus = true,
                    onMinus = { vm.secondsText = (if (s <= 10) s - 1 else s - step).coerceAtLeast(1).toString() },
                    onPlus = { vm.secondsText = (s + step).toString() },
                    onText = { vm.secondsText = it },
                    onDone = { focus.clearFocus() },
                )
                Spacer(Modifier.height(14.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 90, 180, 300).filter { it * 1_000_000L < v.durationUs }.forEach { k ->
                        Chip(Format.seconds(k), s == k) { vm.secondsText = "$k"; focus.clearFocus() }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
            if (plan != null) {
                PlanPreview(vm, v, plan, maxParts)
            }
        }

        // Bottom action, over a fade so the list scrolls under it.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.bg.copy(alpha = 0.92f), c.bg)))
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 14.dp),
        ) {
            val parts = plan?.segments?.size ?: 0
            PrimaryButton(
                if (parts >= 2) "Split into $parts parts" else "Choose at least 2 parts",
                Modifier.fillMaxWidth(),
                enabled = parts >= 2,
            ) { focus.clearFocus(); vm.split() }
        }
    }
}

private fun metaLine(v: VideoInfo): String {
    val (w, h) = if (v.rotation == 90 || v.rotation == 270) v.height to v.width else v.width to v.height
    return buildList {
        add(Format.clock(v.durationUs))
        if (w > 0 && h > 0) add("$w×$h")
        add(v.codecLabel)
        if (v.frameRate > 0) add("${v.frameRate.roundToInt()} fps")
        if (v.sizeBytes > 0) add(Format.bytes(v.sizeBytes))
    }.joinToString("  ·  ")
}

@Composable
private fun Poster(vm: SliceViewModel, v: VideoInfo) {
    val c = LocalColors.current
    val (w, h) = if (v.rotation == 90 || v.rotation == 270) v.height to v.width else v.width to v.height
    val aspect = if (w > 0 && h > 0) w.toFloat() / h else 16f / 9f
    val shown by animateFloatAsState(if (vm.poster != null) 1f else 0f, tween(400), label = "poster")
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .aspectRatio(aspect.coerceAtLeast(0.6f), matchHeightConstraintsFirst = aspect < 1f)
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface2),
    ) {
        vm.poster?.let {
            Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = shown)
        }
        Text(
            Format.clock(v.durationUs),
            Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            style = Type.numberSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun NumberInput(
    text: String,
    unit: String,
    canMinus: Boolean,
    canPlus: Boolean,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onText: (String) -> Unit,
    onDone: () -> Unit,
) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton(plus = false, enabled = canMinus, onClick = onMinus)
            BasicTextField(
                value = text,
                onValueChange = { new -> onText(new.filter(Char::isDigit).take(4)) },
                modifier = Modifier.widthIn(min = 120.dp, max = 190.dp),
                textStyle = Type.bigNumber.copy(color = c.text, textAlign = TextAlign.Center),
                singleLine = true,
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
            )
            StepButton(plus = true, enabled = canPlus, onClick = onPlus)
        }
        Text(unit, style = Type.label, color = c.muted)
    }
}

@Composable
private fun PlanPreview(vm: SliceViewModel, v: VideoInfo, plan: Plan, maxParts: Int) {
    val c = LocalColors.current
    val segs = plan.segments
    Text("PREVIEW", style = Type.tiny, color = c.muted)
    Spacer(Modifier.height(8.dp))
    Timeline(vm.filmstrip, v.durationUs, segs, Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth()) {
        Text("0:00", style = Type.numberSmall, color = c.muted)
        Spacer(Modifier.weight(1f))
        Text(Format.clock(v.durationUs), style = Type.numberSmall, color = c.muted)
    }

    val warnings = buildList {
        if (maxParts <= 1) add("This video has only one keyframe, so it can't be cut without re-encoding.")
        plan.cappedFrom?.let { if (maxParts > 1) add("This video can be cut into at most $maxParts parts without re-encoding, so it'll be $maxParts.") }
        if (plan.overLength > 0) {
            val gap = Format.length(v.keyframeGapUs)
            add(
                if (plan.overLength == 1) "1 part is longer than the limit: this video's keyframes are about $gap apart, and cuts can only go on keyframes."
                else "${plan.overLength} parts are longer than the limit: this video's keyframes are about $gap apart, and cuts can only go on keyframes.",
            )
        }
    }
    warnings.forEach {
        Spacer(Modifier.height(12.dp))
        Notice(it, warn = true)
    }

    Spacer(Modifier.height(16.dp))
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp)) {
            segs.forEachIndexed { i, s ->
                if (i > 0) Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(c.line.copy(alpha = 0.6f)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp)).background(if (i % 2 == 0) c.accentSoft else c.surface2).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text((i + 1).toString().padStart(2, '0'), style = Type.numberSmall, color = c.text)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text("${Format.clock(s.startUs)} → ${Format.clock(s.endUs)}", Modifier.weight(1f), style = Type.numberSmall, color = c.muted)
                    Text(Format.length(s.durationUs), style = Type.number, color = c.text)
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    Notice(
        "Cuts land on keyframes (about every ${Format.length(v.keyframeGapUs)} in this video), so parts can differ by up to that much. That's what keeps every frame untouched.",
        warn = false,
    )
}

@Composable
fun Notice(text: String, warn: Boolean) {
    val c = LocalColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (warn) c.warn.copy(alpha = 0.12f) else c.surface2)
            .padding(14.dp),
    ) {
        Icon(if (warn) Icons.Rounded.Warning else Icons.Rounded.Info, null, Modifier.size(18.dp), tint = if (warn) c.warn else c.muted)
        Spacer(Modifier.width(10.dp))
        Text(text, style = Type.label, color = if (warn) c.text else c.muted)
    }
}

// ---------------------------------------------------------------- splitting

@Composable
fun SplittingScreen(name: String, progress: SplitProgress, onCancel: () -> Unit) {
    val c = LocalColors.current
    val f by animateFloatAsState(progress.fraction, tween(220), label = "progress")
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        Text("${(f * 100).roundToInt()}%", style = Type.bigNumber.copy(fontSize = Type.bigNumber.fontSize * 1.3f), color = c.text)
        Spacer(Modifier.height(6.dp))
        Text("Writing part ${progress.part} of ${progress.parts}", style = Type.bodyStrong, color = c.muted)
        Spacer(Modifier.height(28.dp))
        SegmentBar(progress.parts, f, Modifier.fillMaxWidth().height(10.dp))
        Spacer(Modifier.height(18.dp))
        Text(name, style = Type.label, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Text("Copying frames as they are. Keep Slice open.", style = Type.label, color = c.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        SecondaryButton("Cancel", Modifier.fillMaxWidth(), onClick = onCancel)
        Spacer(Modifier.height(14.dp))
    }
}

/** Progress drawn as one pill per part (or a single bar when there are many parts). */
@Composable
private fun SegmentBar(parts: Int, fraction: Float, modifier: Modifier) {
    val c = LocalColors.current
    if (parts in 2..24) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until parts) {
                val local = (fraction * parts - i).coerceIn(0f, 1f)
                Box(Modifier.weight(1f).fillMaxHeight().clip(CircleShape).background(c.surface2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(local).clip(CircleShape).background(c.accent))
                }
            }
        }
    } else {
        Box(modifier.clip(CircleShape).background(c.surface2)) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(CircleShape).background(c.accent))
        }
    }
}

// ---------------------------------------------------------------- done

@Composable
fun DoneScreen(
    vm: SliceViewModel,
    result: SplitResult,
    onPlay: (SavedPart) -> Unit,
    onShare: (SavedPart) -> Unit,
    onShareAll: () -> Unit,
    onAgain: () -> Unit,
    onNew: () -> Unit,
) {
    val c = LocalColors.current
    val v = vm.info
    val (w, h) = if (v != null && (v.rotation == 90 || v.rotation == 270)) v.height to v.width else (v?.width ?: 16) to (v?.height ?: 9)
    val aspect = if (w > 0 && h > 0) w.toFloat() / h else 16f / 9f
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 200.dp),
        ) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.size(56.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, null, Modifier.size(30.dp), tint = c.onAccent)
            }
            Spacer(Modifier.height(18.dp))
            Text("${result.parts.size} parts saved", style = Type.display, color = c.text)
            Spacer(Modifier.height(6.dp))
            Text("${Splitter.FOLDER}  ·  original quality", style = Type.body, color = c.muted)
            if (result.droppedTracks.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Notice("This phone can't copy the ${result.droppedTracks.joinToString()} audio track as it is, so the parts don't include it.", warn = true)
            }
            Spacer(Modifier.height(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                result.parts.forEach { p -> PartRow(p, vm.partThumbs[p.index], aspect, { onPlay(p) }, { onShare(p) }) }
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.bg.copy(alpha = 0.92f), c.bg)))
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PrimaryButton("Share all ${result.parts.size}", Modifier.fillMaxWidth(), onClick = onShareAll)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Change parts", Modifier.weight(1f), onClick = onAgain)
                SecondaryButton("New video", Modifier.weight(1f), onClick = onNew)
            }
        }
    }
}

@Composable
private fun PartRow(p: SavedPart, thumb: androidx.compose.ui.graphics.ImageBitmap?, aspect: Float, onPlay: () -> Unit, onShare: () -> Unit) {
    val c = LocalColors.current
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.pressable(onClick = onPlay).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(if (aspect >= 1f) 104.dp else 58.dp)
                    .aspectRatio(aspect.coerceIn(0.5f, 2.4f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(c.surface2),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(thumb, transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) }, label = "thumb") { t ->
                    if (t != null) Image(t, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Box(Modifier.fillMaxSize())
                }
                Box(Modifier.size(28.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, "Play", Modifier.size(18.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Part ${p.index}", style = Type.bodyStrong, color = c.text)
                Text("${Format.length(p.durationUs)}  ·  ${Format.bytes(p.bytes)}", style = Type.numberSmall, color = c.muted)
                Text(p.name, style = Type.label.copy(fontSize = Type.tiny.fontSize), color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(c.surface2).pressable(onClick = onShare),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Share, "Share part ${p.index}", Modifier.size(20.dp), tint = c.text) }
        }
    }
}
