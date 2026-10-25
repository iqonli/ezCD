// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.model

import java.util.Locale
import kotlin.math.*

/** Opaque sRGB only. Input filtering does not impose length limits or silently clamp values. */
object OpaqueColor {
    const val WHITE = -1
    const val BLUE = -14575885 // #2196F3
    val presets = listOf(0xFFF44336, 0xFFFF9800, 0xFFFFEB3B, 0xFF4CAF50,
        0xFF00BCD4, 0xFF2196F3, 0xFF9C27B0).map { it.toInt() }
    fun digits(text: CharSequence): String = text.filter { it in '0'..'9' }.toString()
    fun hexDigits(text: CharSequence): String = text.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
        .toString().uppercase(Locale.ROOT)
    fun parseHex(text: String): Int? = if (text.length == 6 && hexDigits(text) == text.uppercase(Locale.ROOT))
        text.toIntOrNull(16)?.or(0xFF000000.toInt()) else null
    fun hex(color: Int): String = String.format(Locale.ROOT, "%06X", color and 0xFFFFFF)
    fun rgb(r: Int, g: Int, b: Int): Int {
        require(r in 0..255 && g in 0..255 && b in 0..255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    fun channels(color: Int) = intArrayOf((color ushr 16) and 255, (color ushr 8) and 255, color and 255)
    fun hsl(color: Int): DoubleArray {
        val (r, g, b) = channels(color).map { it / 255.0 }
        val hi = maxOf(r, g, b); val lo = minOf(r, g, b); val delta = hi - lo; val l = (hi + lo) / 2
        if (delta == 0.0) return doubleArrayOf(0.0, 0.0, l * 100)
        val s = delta / (1 - abs(2 * l - 1))
        val h = when (hi) { r -> ((g - b) / delta).let { if (it < 0) it + 6 else it }; g -> (b - r) / delta + 2; else -> (r - g) / delta + 4 }
        return doubleArrayOf(h * 60, s * 100, l * 100)
    }
    fun fromHsl(h: Int, s: Int, l: Int): Int {
        require(h in 0..360 && s in 0..100 && l in 0..100)
        val hue = (h % 360) / 60.0; val light = l / 100.0
        val c = (1 - abs(2 * light - 1)) * s / 100.0; val x = c * (1 - abs(hue % 2 - 1)); val m = light - c / 2
        val raw = when (hue.toInt()) { 0 -> doubleArrayOf(c,x,0.0); 1 -> doubleArrayOf(x,c,0.0); 2 -> doubleArrayOf(0.0,c,x); 3 -> doubleArrayOf(0.0,x,c); 4 -> doubleArrayOf(x,0.0,c); else -> doubleArrayOf(c,0.0,x) }
        return rgb(((raw[0]+m)*255).roundToInt().coerceIn(0,255), ((raw[1]+m)*255).roundToInt().coerceIn(0,255), ((raw[2]+m)*255).roundToInt().coerceIn(0,255))
    }
    fun foreground(color: Int): Int {
        val parts = channels(color).map { v -> (v / 255.0).let { if (it <= .04045) it / 12.92 else ((it + .055) / 1.055).pow(2.4) } }
        val luminance = parts[0] * .2126 + parts[1] * .7152 + parts[2] * .0722
        return if ((luminance + .05) / .05 >= 1.05 / (luminance + .05)) 0xFF000000.toInt() else WHITE
    }
}
