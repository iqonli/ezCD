package com.iqonli.ezcd.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetSizeTest {
    @Test
    fun oneCellPortraitAndNarrowDimensionsUseCompactLayout() {
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(57, 102))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(90, 100))
        assertEquals(WidgetSize.COMPACT, WidgetSize.fromDimensions(56, 50))
    }

    @Test
    fun wideShortDimensionsUseHorizontalShortLayout() {
        assertEquals(WidgetSize.SHORT, WidgetSize.fromDimensions(127, 51))
        assertEquals(WidgetSize.SHORT, WidgetSize.fromDimensions(260, 60))
    }

    @Test
    fun normalDimensionsUseStandardLayout() {
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(120, 80))
        assertEquals(WidgetSize.STANDARD, WidgetSize.fromDimensions(179, 119))
    }

    @Test
    fun roomyDimensionsUseLargeLayout() {
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(180, 120))
        assertEquals(WidgetSize.LARGE, WidgetSize.fromDimensions(260, 180))
    }
}
