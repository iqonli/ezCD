package com.iqonli.ezcd.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetBackgroundTest {
    @Test
    fun storageKeysRoundTripAndClassicIsFallback() {
        WidgetBackground.entries.forEach { background ->
            assertEquals(background, WidgetBackground.fromStorageKey(background.storageKey))
        }
        assertEquals(WidgetBackground.CLASSIC, WidgetBackground.fromStorageKey(null))
        assertEquals(WidgetBackground.CLASSIC, WidgetBackground.fromStorageKey("unknown"))
    }

    @Test
    fun backgroundOrderKeepsSpecialStyleLast() {
        assertEquals(9, WidgetBackground.entries.size)
        assertEquals(WidgetBackground.MONOGRAM, WidgetBackground.entries.last())
        assertEquals("monogram", WidgetBackground.MONOGRAM.storageKey)
    }
}
