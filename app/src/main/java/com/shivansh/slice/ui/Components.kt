package com.shivansh.slice.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shivansh.slice.split.Segment

/** Squishes a little while pressed, like the system's own buttons. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium), label = "press")
    return this
        .scale(s)
        .clickable(interactionSource = source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalColors.current
    val bg by animateColorAsState(if (enabled) c.accent else c.surface2, label = "btn")
    Box(
        modifier
            .height(60.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.bodyStrong.copy(fontSize = Type.bodyStrong.fontSize * 1.08f), color = if (enabled) c.onAccent else c.muted)
    }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp))
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.bodyStrong, color = c.text)
    }
}

/** Two-option pill switch with a sliding thumb. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val c = LocalColors.current
    BoxWithConstraints(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface2)
            .padding(4.dp),
    ) {
        val w = maxWidth / options.size
        val x by animateDpAsState(w * selected, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow), label = "thumb")
        Box(
            Modifier
                .offset(x = x)
                .width(w)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(if (c.dark) Color(0xFF2C2C33) else c.surface),
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.Tab) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = Type.label.copy(fontWeight = Type.bodyStrong.fontWeight), color = if (i == selected) c.text else c.muted)
                }
            }
        }
    }
}

/** Round − / + button drawn with lines (core icons have no minus). */
@Composable
fun StepButton(plus: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val tint = if (enabled) c.text else c.muted.copy(alpha = 0.4f)
    Box(
        Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(c.surface2)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(18.dp)) {
            val sw = 2.4.dp.toPx()
            drawLine(tint, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), sw, StrokeCap.Round)
            if (plus) drawLine(tint, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), sw, StrokeCap.Round)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalColors.current
    val bg by animateColorAsState(if (selected) c.accentSoft else Color.Transparent, label = "chip")
    val border by animateColorAsState(if (selected) c.accent else c.line, label = "chipBorder")
    Box(
        Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .pressable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.numberSmall, color = if (selected) c.text else c.muted)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = LocalColors.current
    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(c.surface)
            .then(if (c.dark) Modifier.border(1.dp, c.line.copy(alpha = 0.6f), RoundedCornerShape(22.dp)) else Modifier),
    ) { content() }
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val c = LocalColors.current
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text("slice", style = Type.title.copy(fontSize = Type.title.fontSize * 1.1f), color = c.text)
        Text(".", style = Type.title.copy(fontSize = Type.title.fontSize * 1.1f), color = c.accent)
    }
}

/**
 * Filmstrip of the video with the planned parts drawn over it: alternate parts dimmed,
 * an accent line at every cut, and the part number centred in each part that's wide enough.
 */
@Composable
fun Timeline(
    frames: List<ImageBitmap?>,
    durationUs: Long,
    segments: List<Segment>,
    modifier: Modifier = Modifier,
    height: Dp = 68.dp,
) {
    val c = LocalColors.current
    val measurer = rememberTextMeasurer()
    val stripShape = RoundedCornerShape(12.dp)
    Box(modifier.height(height + 12.dp)) {
        Row(
            Modifier
                .padding(vertical = 6.dp)
                .fillMaxSize()
                .clip(stripShape)
                .background(c.surface2),
        ) {
            frames.forEach { f ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (f != null) Image(f, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                }
            }
        }
        val tagStyle = Type.tiny
        Canvas(Modifier.fillMaxSize()) {
            if (durationUs <= 0) return@Canvas
            val top = 6.dp.toPx()
            val bottom = size.height - 6.dp.toPx()
            val r = 12.dp.toPx()
            fun xOf(us: Long) = (us.toDouble() / durationUs * size.width).toFloat()
            val strip = Path().apply { addRoundRect(RoundRect(0f, top, size.width, bottom, CornerRadius(r))) }
            clipPath(strip) {
                segments.forEachIndexed { i, s ->
                    if (i % 2 == 1) {
                        val x0 = xOf(s.startUs)
                        drawRect(Color.Black.copy(alpha = 0.42f), Offset(x0, top), Size(xOf(s.endUs) - x0, bottom - top))
                    }
                }
            }
            segments.forEachIndexed { i, s ->
                val x0 = xOf(s.startUs)
                val x1 = xOf(s.endUs)
                val label = measurer.measure((i + 1).toString(), tagStyle)
                val pillW = label.size.width + 10.dp.toPx()
                val pillH = label.size.height + 4.dp.toPx()
                if (x1 - x0 > pillW + 6.dp.toPx()) {
                    val cx = (x0 + x1) / 2
                    val y = bottom - pillH - 5.dp.toPx()
                    drawRoundRect(Color.Black.copy(alpha = 0.6f), Offset(cx - pillW / 2, y), Size(pillW, pillH), CornerRadius(pillH / 2))
                    drawText(label, color = Color.White, topLeft = Offset(cx - label.size.width / 2f, y + 2.dp.toPx()))
                }
            }
            segments.drop(1).forEach { s ->
                val x = xOf(s.startUs)
                drawLine(c.accent, Offset(x, 0f), Offset(x, size.height), 3.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
