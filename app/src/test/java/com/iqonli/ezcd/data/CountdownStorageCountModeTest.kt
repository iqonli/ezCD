package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.ReminderOption
import com.iqonli.ezcd.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CountdownStorageCountModeTest {
    @Test
    fun schemas1Through6DefaultToCountdown() {
        (CountdownStorageSchema.LEGACY_VERSION..CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION)
            .forEach { version ->
                assertEquals(CountMode.COUNT_DOWN, CountdownStorageSchema.countModeFor(version, null))
            }
    }

    @Test
    fun schema7UsesStrictStableCountModeKeys() {
        assertEquals(
            CountMode.COUNT_DOWN,
            CountdownStorageSchema.countModeFor(
                CountdownStorageSchema.COUNT_MODE_VERSION,
                CountMode.COUNT_DOWN.storageKey,
            ),
        )
        assertEquals(
            CountMode.COUNT_UP,
            CountdownStorageSchema.countModeFor(
                CountdownStorageSchema.COUNT_MODE_VERSION,
                CountMode.COUNT_UP.storageKey,
            ),
        )
        assertNull(
            CountdownStorageSchema.countModeFor(
                CountdownStorageSchema.COUNT_MODE_VERSION,
                "unsupported",
            ),
        )
        assertNull(
            CountdownStorageSchema.countModeFor(
                CountdownStorageSchema.COUNT_MODE_VERSION,
                null,
            ),
        )
    }

    @Test
    fun legacyModeDoesNotRestrictReminderOrRecurrenceAnymore() {
        listOf(event("reminder").copy(reminder = ReminderOption.ON_DAY),
            event("repeat").copy(repeatRule = RepeatRule.YEARLY)).forEach {
            CountdownValidation.validateStoredEvents(listOf(it))
            assertEquals(it, CountdownStorageCodec.decode(CountdownStorageCodec.encode(listOf(it))).single())
        }
    }

    @Test
    fun validCountUpPassesStorageValidation() {
        CountdownValidation.validateStoredEvents(listOf(event("valid")))
    }

    private fun event(id: String) = CountdownEvent(
        id = id,
        title = id,
        date = LocalDate.of(2026, 9, 29),
        type = EventType.EVENT,
        createdAt = Instant.parse("2026-09-01T00:00:00Z"),
        countMode = CountMode.COUNT_UP,
    )
}
