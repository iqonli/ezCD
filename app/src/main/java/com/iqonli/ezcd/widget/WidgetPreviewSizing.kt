// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import kotlin.math.roundToInt

internal data class WidgetPreviewDimensions(
    val widthDp: Int,
    val heightDp: Int,
) {
    val size: WidgetSize
        get() = WidgetSize.fromDimensions(widthDp, heightDp)
}

internal data class WidgetPreviewPixels(
    val width: Int,
    val height: Int,
)

internal object WidgetPreviewSizing {
    val orderedSizes: List<WidgetSize> = listOf(
        WidgetSize.COMPACT,
        WidgetSize.SHORT,
        WidgetSize.STANDARD,
        WidgetSize.LARGE,
    )

    fun representative(size: WidgetSize): WidgetPreviewDimensions = when (size) {
        WidgetSize.COMPACT -> WidgetPreviewDimensions(56, 50)
        WidgetSize.SHORT -> WidgetPreviewDimensions(180, 50)
        WidgetSize.STANDARD -> WidgetPreviewDimensions(160, 100)
        WidgetSize.LARGE -> WidgetPreviewDimensions(240, 160)
    }

    fun fit(
        containerWidth: Int,
        containerHeight: Int,
        dimensions: WidgetPreviewDimensions,
    ): WidgetPreviewPixels {
        if (containerWidth <= 0 || containerHeight <= 0) {
            return WidgetPreviewPixels(1, 1)
        }

        val scale = minOf(
            containerWidth.toFloat() / dimensions.widthDp,
            containerHeight.toFloat() / dimensions.heightDp,
        )
        return WidgetPreviewPixels(
            width = (dimensions.widthDp * scale).roundToInt().coerceAtLeast(1),
            height = (dimensions.heightDp * scale).roundToInt().coerceAtLeast(1),
        )
    }
}
