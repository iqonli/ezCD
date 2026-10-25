// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

/** User-facing notation is RGB or RGBA; convert to Android's internal ARGB Int only here. */
object WidgetColor {
    fun parse(raw: String): Int? {
        val value=raw.removePrefix("#")
        if (value.length !in listOf(6,8) || value.any { it !in "0123456789abcdefABCDEF" }) return null
        val bits=value.toLongOrNull(16) ?: return null
        return if (value.length == 6) (bits or 0xFF000000).toInt()
            else ((bits ushr 8) or ((bits and 255L) shl 24)).toInt()
    }
    fun overWhite(color: Int): Int {
        val alpha=color ushr 24
        fun channel(shift: Int): Int = (((color ushr shift) and 255)*alpha + 255*(255-alpha)+127)/255
        return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    fun hex(color: Int): String {
        val argb=color.toLong() and 0xFFFFFFFFL
        val rgba=((argb and 0xFFFFFFL) shl 8) or (argb ushr 24)
        return java.lang.String.format(java.util.Locale.ROOT,"%08X",rgba)
    }
}
