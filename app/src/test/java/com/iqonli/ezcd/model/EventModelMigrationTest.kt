package com.iqonli.ezcd.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EventModelMigrationTest {
    @Test
    fun legacyFirstFlightMigratesToTrip() {
        assertEquals(EventType.TRIP, EventType.fromLegacyName("FIRST_FLIGHT"))
        assertEquals(EventType.TRIP, EventType.fromStorageKey("first_flight"))
    }

    @Test
    fun presetIconsRemainStable() {
        assertEquals(EventIcon.AIRPLANE, EventIcon.defaultFor(EventType.TRIP))
        assertEquals(EventIcon.BOOK, EventIcon.defaultFor(EventType.EXAM))
        assertEquals(EventIcon.CONFETTI, EventIcon.defaultFor(EventType.PARTY))
        assertEquals(EventIcon.CAKE, EventIcon.defaultFor(EventType.BIRTHDAY))
        assertEquals(EventIcon.HEART, EventIcon.defaultFor(EventType.ANNIVERSARY))
        assertEquals(EventIcon.MUSIC, EventIcon.defaultFor(EventType.CONCERT))
        assertEquals(EventIcon.HOURGLASS, EventIcon.defaultFor(EventType.DEADLINE))
        assertEquals(EventIcon.CALENDAR, EventIcon.defaultFor(EventType.EVENT))
        assertEquals(EventIcon.STAR, EventIcon.defaultFor(EventType.CUSTOM))
    }

    @Test
    fun iconStorageKeysRoundTrip() {
        EventIcon.entries.forEach { icon ->
            assertEquals(icon, EventIcon.fromStorageKey(icon.storageKey))
        }
    }
}
