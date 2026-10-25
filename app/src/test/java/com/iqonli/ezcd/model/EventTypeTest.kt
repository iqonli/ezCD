package com.iqonli.ezcd.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventTypeTest {
    @Test
    fun storageKeysAreStableAndUnique() {
        val expected = mapOf(
            EventType.TRIP to "trip",
            EventType.EXAM to "exam",
            EventType.PARTY to "party",
            EventType.BIRTHDAY to "birthday",
            EventType.ANNIVERSARY to "anniversary",
            EventType.CONCERT to "concert",
            EventType.DEADLINE to "deadline",
            EventType.EVENT to "event",
            EventType.CUSTOM to "custom",
        )

        assertEquals(expected, EventType.entries.associateWith { it.storageKey })
        assertEquals(EventType.entries.size, EventType.entries.map { it.storageKey }.toSet().size)
    }

    @Test
    fun storageKeyLookupHandlesKnownLegacyAndUnknownValues() {
        assertEquals(EventType.TRIP, EventType.fromStorageKey("trip"))
        assertEquals(EventType.TRIP, EventType.fromStorageKey("first_flight"))
        assertNull(EventType.fromStorageKey("something_new"))
    }

    @Test
    fun legacyNamesRemainReadableForSchemaOne() {
        assertEquals(EventType.BIRTHDAY, EventType.fromLegacyName("BIRTHDAY"))
        assertEquals(EventType.TRIP, EventType.fromLegacyName("FIRST_FLIGHT"))
        assertNull(EventType.fromLegacyName("birthday"))
        assertTrue(EventType.entries.all { EventType.fromLegacyName(it.name) == it })
    }
}
