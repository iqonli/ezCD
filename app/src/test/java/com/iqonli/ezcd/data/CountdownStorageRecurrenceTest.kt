package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownStorageRecurrenceTest {
    @Test
    fun releasedSchemasBeforeVersion5MigrateToNonRepeatingEvents() {
        listOf(
            CountdownStorageSchema.LEGACY_VERSION,
            CountdownStorageSchema.PREVIOUS_VERSION,
            CountdownStorageSchema.NOTIFICATION_VERSION,
            CountdownStorageSchema.REMINDER_VERSION,
        ).forEach { schemaVersion ->
            assertEquals(
                RepeatRule.NONE,
                CountdownStorageSchema.repeatRuleFor(schemaVersion, rawStorageKey = null),
            )
        }
    }

    @Test
    fun schemas5And6RemainReadableForRecurrencePayloads() {
        assertEquals(5, CountdownStorageSchema.REPEAT_RULE_VERSION)
        assertEquals(6, CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION)

        listOf(
            CountdownStorageSchema.REPEAT_RULE_VERSION,
            CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION,
            CountdownStorageSchema.COUNT_MODE_VERSION,
        ).forEach { schemaVersion ->
            RepeatRule.entries.forEach { repeatRule ->
                assertEquals(
                    repeatRule,
                    CountdownStorageSchema.repeatRuleFor(schemaVersion, repeatRule.storageKey),
                )
            }
        }
    }

    @Test
    fun currentSchemaUsesStableRecurrenceStorageKeys() {
        assertEquals("none", RepeatRule.NONE.storageKey)
        assertEquals("weekly", RepeatRule.WEEKLY.storageKey)
        assertEquals("monthly", RepeatRule.MONTHLY.storageKey)
        assertEquals("yearly", RepeatRule.YEARLY.storageKey)
        assertNull(
            CountdownStorageSchema.repeatRuleFor(
                CountdownStorageSchema.CURRENT_VERSION,
                "unsupported",
            ),
        )
    }
}
