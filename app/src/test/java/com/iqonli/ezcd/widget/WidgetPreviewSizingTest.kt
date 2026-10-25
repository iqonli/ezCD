package com.iqonli.ezcd.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WidgetPreviewSizingTest {
    @Test
    fun representativeSizesCoverEveryWidgetBucket() {
        assertEquals(
            WidgetSize.entries.toSet(),
            WidgetPreviewSizing.orderedSizes.map { WidgetPreviewSizing.representative(it).size }.toSet(),
        )
        assertEquals(WidgetPreviewDimensions(56, 50), WidgetPreviewSizing.representative(WidgetSize.COMPACT))
        assertEquals(WidgetPreviewDimensions(180, 50), WidgetPreviewSizing.representative(WidgetSize.SHORT))
        assertEquals(WidgetPreviewDimensions(160, 100), WidgetPreviewSizing.representative(WidgetSize.STANDARD))
        assertEquals(WidgetPreviewDimensions(240, 160), WidgetPreviewSizing.representative(WidgetSize.LARGE))
    }

    @Test
    fun previewFitPreservesAspectRatioWithoutOverflow() {
        WidgetPreviewSizing.orderedSizes.forEach { size ->
            val dimensions = WidgetPreviewSizing.representative(size)
            val fitted = WidgetPreviewSizing.fit(
                containerWidth = 320,
                containerHeight = 176,
                dimensions = dimensions,
            )

            assertTrue(fitted.width <= 320)
            assertTrue(fitted.height <= 176)
            val expected = dimensions.widthDp.toFloat() / dimensions.heightDp
            val actual = fitted.width.toFloat() / fitted.height
            assertTrue(abs(expected - actual) < 0.02f)
        }
    }
}
