// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.content.Context
import android.content.res.Configuration

internal data class WidgetTheme(
    val dark: Boolean,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val accentTextColor: Int,
)

internal data class WidgetPalette(
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val accentTextColor: Int,
)

internal object WidgetPaletteResolver {
    fun resolve(background: WidgetBackground, dark: Boolean): WidgetPalette =
        if (dark) darkPalette(background) else lightPalette(background)

    private fun lightPalette(background: WidgetBackground): WidgetPalette = when (background) {
        WidgetBackground.CLASSIC -> palette(0x222222, 0x666666, 0x1565C0)
        WidgetBackground.MIST -> palette(0x102A43, 0x334E68, 0x0B4F6C)
        WidgetBackground.HORIZON -> palette(0x102A43, 0x334E68, 0x075E68)
        WidgetBackground.FOREST -> palette(0x102A2A, 0x2F4F46, 0x0B523E)
        WidgetBackground.SUNSET -> palette(0x3B1F2A, 0x5A3443, 0x6F2344)
        WidgetBackground.PULSE -> palette(0x251B35, 0x4B3A55, 0x55245E)
        WidgetBackground.BREEZE -> palette(0x16324F, 0x334E68, 0x075875)
        WidgetBackground.EMBER -> palette(0x3A2412, 0x5C3A20, 0x633800)
        WidgetBackground.MONOGRAM -> palette(0x0E2A52, 0x345070, 0x143F7C)
    }

    private fun darkPalette(background: WidgetBackground): WidgetPalette = when (background) {
        WidgetBackground.CLASSIC -> palette(0xF7FAFF, 0xD0DBE8, 0x9AF1ED)
        WidgetBackground.MIST -> palette(0xF7FAFF, 0xD0DBE8, 0x9DD8FF)
        WidgetBackground.HORIZON -> palette(0xF7FAFF, 0xD0DBE8, 0x8FE3E0)
        WidgetBackground.FOREST -> palette(0xF7FAFF, 0xD0DBE8, 0xA8E6C4)
        WidgetBackground.SUNSET -> palette(0xFFF7FA, 0xE6D3DC, 0xFFB7D0)
        WidgetBackground.PULSE -> palette(0xFFF7FA, 0xE5D6E8, 0xE9C5FF)
        WidgetBackground.BREEZE -> palette(0xF7FAFF, 0xD0DBE8, 0xA9E7FF)
        WidgetBackground.EMBER -> palette(0xFFF9F0, 0xF0DDC8, 0xFFD9A0)
        WidgetBackground.MONOGRAM -> palette(0xF8FBFF, 0xDCE7F5, 0xFFFFFF)
    }

    private fun palette(primary: Int, secondary: Int, accent: Int): WidgetPalette = WidgetPalette(
        primaryTextColor = opaque(primary),
        secondaryTextColor = opaque(secondary),
        accentTextColor = opaque(accent),
    )

    private fun opaque(rgb: Int): Int = (0xFF000000L or rgb.toLong()).toInt()
}

internal object WidgetThemeResolver {
    fun resolve(
        context: Context,
        appearance: WidgetAppearance,
        background: WidgetBackground = WidgetBackground.CLASSIC,
    ): WidgetTheme {
        val dark = false
        val palette = WidgetPaletteResolver.resolve(background, dark)

        return WidgetTheme(
            dark = dark,
            primaryTextColor = palette.primaryTextColor,
            secondaryTextColor = palette.secondaryTextColor,
            accentTextColor = palette.accentTextColor,
        )
    }
}
