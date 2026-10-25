package com.iqonli.ezcd.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountModeTest {
    @Test
    fun stableStorageKeysRoundTrip() {
        assertEquals("count_down", CountMode.COUNT_DOWN.storageKey)
        assertEquals("count_up", CountMode.COUNT_UP.storageKey)
        CountMode.entries.forEach { mode ->
            assertEquals(mode, CountMode.fromStorageKey(mode.storageKey))
        }
        assertNull(CountMode.fromStorageKey("unsupported"))
    }
}
