package com.shivansh.slice.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Immutable
data class SliceColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val warn: Color,
    val dark: Boolean,
)

private val Dark = SliceColors(
    bg = Color(0xFF000000),
    surface = Color(0xFF111113),
    surface2 = Color(0xFF1B1B1F),
    line = Color(0xFF2A2A30),
    text = Color(0xFFF4F2EE),
    muted = Color(0xFF8E8C94),
    accent = Color(0xFFFF4D6D),
    onAccent = Color(0xFF0A0A0A),
    accentSoft = Color(0x33FF4D6D),
    warn = Color(0xFFFFC145),
    dark = true,
)

private val Light = SliceColors(
    bg = Color(0xFFF4F2EE),
    surface = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEAE7E1),
    line = Color(0xFFDCD8D0),
    text = Color(0xFF0E0E10),
    muted = Color(0xFF6C6A70),
    accent = Color(0xFFF03A60),
    onAccent = Color(0xFF0A0A0A),
    accentSoft = Color(0x26F03A60),
    warn = Color(0xFFB36B00),
    dark = false,
)

val LocalColors = staticCompositionLocalOf { Dark }

object Type {
    private const val TNUM = "tnum"
    val display = TextStyle(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.03).em)
    val title = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.01).em)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val bodyStrong = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val label = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val tiny = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.06.em)
    val bigNumber = TextStyle(fontSize = 64.sp, lineHeight = 68.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04).em, fontFeatureSettings = TNUM)
    val number = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TNUM)
    val numberSmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TNUM)
}

@Composable
fun SliceTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) Dark else Light
    val scheme = if (c.dark) {
        darkColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface, onSurface = c.text, onBackground = c.text)
    } else {
        lightColorScheme(primary = c.accent, onPrimary = c.onAccent, background = c.bg, surface = c.surface, onSurface = c.text, onBackground = c.text)
    }
    CompositionLocalProvider(LocalColors provides c) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
