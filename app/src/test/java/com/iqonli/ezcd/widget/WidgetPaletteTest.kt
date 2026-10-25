package com.iqonli.ezcd.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class WidgetPaletteTest {
    @Test
    fun everyTextRoleClearsFourPointFiveAgainstStyleStops() {
        WidgetBackground.entries.forEach { background ->
            listOf(false, true).forEach { dark ->
                val palette = WidgetPaletteResolver.resolve(background, dark)
                val foregrounds = listOf(
                    palette.primaryTextColor,
                    palette.secondaryTextColor,
                    palette.accentTextColor,
                )
                backgroundStops(background, dark).forEach { stop ->
                    foregrounds.forEach { foreground ->
                        val ratio = contrast(foreground, stop)
                        assertTrue(
                            "$background dark=$dark contrast=$ratio",
                            ratio >= 4.5,
                        )
                    }
                }
            }
        }
    }

    private fun backgroundStops(background: WidgetBackground, dark: Boolean): List<Int> =
        if (dark) {
            when (background) {
                WidgetBackground.CLASSIC,
                WidgetBackground.MONOGRAM,
                -> colors(0x07162F)
                WidgetBackground.MIST -> colors(0x09182B, 0x18334B)
                WidgetBackground.HORIZON -> colors(0x051126, 0x0A2730, 0x153752)
                WidgetBackground.FOREST -> colors(0x071A18, 0x16362F)
                WidgetBackground.SUNSET -> colors(0x1B1022, 0x3C2231)
                WidgetBackground.PULSE -> colors(0x0C2344, 0x172A46, 0x55202A)
                WidgetBackground.BREEZE -> colors(0x0B2D4A, 0x173B55, 0x4E4526)
                WidgetBackground.EMBER -> colors(0x5A1A22, 0x704116, 0x3A2714)
            }
        } else {
            when (background) {
                WidgetBackground.CLASSIC -> colors(0xFFFDFD)
                WidgetBackground.MIST -> colors(0xE7F0F7, 0xC5D7E6)
                WidgetBackground.HORIZON -> colors(0xF1E4CB, 0xD4E7E2, 0xC1DDF2)
                WidgetBackground.FOREST -> colors(0xD9EADF, 0xBBD9C9)
                WidgetBackground.SUNSET -> colors(0xF6DFC9, 0xE9C2CE)
                WidgetBackground.PULSE -> colors(0xC7D5E7, 0xEEF2F7, 0xE6B6BE)
                WidgetBackground.BREEZE -> colors(0xB5DFF4, 0xF6FAFC, 0xEBDCA7)
                WidgetBackground.EMBER -> colors(0xEFB0A6, 0xF3CB75, 0xF0DFA9)
                WidgetBackground.MONOGRAM -> colors(0xF8FBFF)
            }
        }

    private fun colors(vararg rgb: Int): List<Int> =
        rgb.map { value -> (0xFF000000L or value.toLong()).toInt() }

    private fun contrast(first: Int, second: Int): Double {
        val firstLuminance = luminance(first)
        val secondLuminance = luminance(second)
        return (max(firstLuminance, secondLuminance) + 0.05) /
            (min(firstLuminance, secondLuminance) + 0.05)
    }

    private fun luminance(color: Int): Double {
        fun channel(shift: Int): Double {
            val value = ((color shr shift) and 0xFF) / 255.0
            return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
        }

        return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    }
}
