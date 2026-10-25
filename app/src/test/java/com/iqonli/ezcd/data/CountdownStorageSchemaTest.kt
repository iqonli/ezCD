package com.iqonli.ezcd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownStorageSchemaTest {
    @Test
    fun currentSchemaIsVersion11AndReleasedSchemasRemainExplicit() {
        assertEquals(6, CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION)
        assertEquals(7, CountdownStorageSchema.COUNT_MODE_VERSION)
        assertEquals(11, CountdownStorageSchema.CURRENT_VERSION)
        assertEquals(9, CountdownStorageSchema.COLOR_TIME_VERSION)
        assertEquals(8, CountdownStorageSchema.INCLUDE_TODAY_VERSION)
    }

    @Test
    fun allReleasedSchemasRemainSupported() {
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.LEGACY_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.PREVIOUS_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.NOTIFICATION_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.REMINDER_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.REPEAT_RULE_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.EXPANDED_RECURRENCE_VERSION))
        assertTrue(CountdownStorageSchema.isSupported(CountdownStorageSchema.COUNT_MODE_VERSION))
    }

    @Test
    fun missingOrFutureSchemasAreRejected() {
        assertFalse(CountdownStorageSchema.isSupported(-1))
        assertFalse(CountdownStorageSchema.isSupported(CountdownStorageSchema.CURRENT_VERSION + 1))
    }

    @Test
    fun unsupportedSchemaProblemsAreClassifiedPrecisely() {
        assertEquals(
            CountdownDataProblem.UNSUPPORTED_SCHEMA,
            CountdownStorageSchema.problemFor(CountdownStorageSchema.CURRENT_VERSION + 1),
        )
        assertEquals(
            CountdownDataProblem.CORRUPT,
            CountdownStorageSchema.problemFor(CountdownStorageSchema.LEGACY_VERSION - 1),
        )
        assertNull(CountdownStorageSchema.problemFor(CountdownStorageSchema.CURRENT_VERSION))
    }
}
