package com.shivansh.slice.split

import java.util.Locale
import kotlin.math.roundToLong

/** Human-readable sizes and times. Pure Kotlin. */
object Format {
    fun bytes(b: Long): String {
        if (b < 1024) return "$b B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = b / 1024.0
        var i = 0
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (v >= 100) "${v.roundToLong()} ${units[i]}" else String.format(Locale.US, "%.1f %s", v, units[i])
    }

    /** Clock time for a position, to the nearest second: 0:07, 1:05, 1:02:09. */
    fun clock(us: Long): String {
        val s = ((us + 500_000L) / 1_000_000L).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec) else String.format(Locale.US, "%d:%02d", m, sec)
    }

    /** A part length with tenths: 9.8 s, 31.0 s, 1:02.4, 1:02:09. */
    fun length(us: Long): String {
        val tenths = (us / 100_000.0).roundToLong().coerceAtLeast(0)
        val totalS = tenths / 10
        val t = tenths % 10
        return when {
            totalS < 60 -> "$totalS.$t s"
            totalS < 3600 -> String.format(Locale.US, "%d:%02d.%d", totalS / 60, totalS % 60, t)
            else -> clock(us)
        }
    }

    /** Seconds entered for "max length", shown compactly: 45 s, 1 min, 1 min 30 s. */
    fun seconds(s: Int): String = when {
        s < 60 -> "$s s"
        s % 60 == 0 -> "${s / 60} min"
        else -> "${s / 60} min ${s % 60} s"
    }
}
