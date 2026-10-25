package com.iqonli.ezcd.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderOptionTest {
    @Test
    fun storageKeysRoundTrip() {
        ReminderOption.entries.forEach { option ->
            assertEquals(option, ReminderOption.fromStorageKey(option.storageKey))
        }
    }

    @Test
    fun offsetsStayLimitedAndStable() {
        assertNull(ReminderOption.OFF.daysBefore)
        assertEquals(0, ReminderOption.ON_DAY.daysBefore)
        assertEquals(1, ReminderOption.ONE_DAY.daysBefore)
        assertEquals(3, ReminderOption.THREE_DAYS.daysBefore)
        assertEquals(7, ReminderOption.SEVEN_DAYS.daysBefore)
    }
}
