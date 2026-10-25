package com.iqonli.ezcd.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPinFallbackResolverTest {
    @Test
    fun noNewWidgetIsNone() {
        assertTrue(
            WidgetPinFallbackResolver.resolve(
                baselineIds = setOf(1, 2),
                currentIds = setOf(1, 2),
            ) is WidgetPinFallbackOutcome.None,
        )
    }

    @Test
    fun exactlyOneNewWidgetIsSelected() {
        assertEquals(
            WidgetPinFallbackOutcome.Single(3),
            WidgetPinFallbackResolver.resolve(
                baselineIds = setOf(1, 2),
                currentIds = setOf(1, 2, 3),
            ),
        )
    }

    @Test
    fun removedBaselineWidgetDoesNotHideTheSingleNewWidget() {
        assertEquals(
            WidgetPinFallbackOutcome.Single(3),
            WidgetPinFallbackResolver.resolve(
                baselineIds = setOf(1, 2),
                currentIds = setOf(2, 3),
            ),
        )
    }

    @Test
    fun multipleNewWidgetsAreNeverGuessed() {
        val outcome = WidgetPinFallbackResolver.resolve(
            baselineIds = setOf(1),
            currentIds = setOf(1, 2, 3),
        )

        assertEquals(
            setOf(2, 3),
            (outcome as WidgetPinFallbackOutcome.Multiple).appWidgetIds,
        )
    }
}
