package com.iqonli.ezcd.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CountdownTimeTest {
    @Test
    fun snapshotUsesInjectedClockAndZoneAsOneTemporalOperation() {
        val zone = ZoneId.of("America/New_York")
        val clock = Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), zone)

        val snapshot = CountdownTime.snapshot(clock)

        assertEquals(zone, snapshot.zone)
        assertEquals(ZonedDateTime.now(clock), snapshot.now)
        assertEquals(snapshot.now.toLocalDate(), snapshot.today)
    }

    @Test
    fun nextDateInvalidationTracksCivilMidnightAcrossDst() {
        val zone = ZoneId.of("America/New_York")
        val springForwardDay = CountdownTimeSnapshot(
            ZonedDateTime.of(2026, 3, 8, 0, 30, 0, 0, zone),
        )
        val fallBackDay = CountdownTimeSnapshot(
            ZonedDateTime.of(2026, 11, 1, 0, 30, 0, 0, zone),
        )

        assertEquals(
            22L * 60L * 60L * 1000L + 30L * 60L * 1000L + 1_000L,
            CountdownTemporalInvalidationPolicy.delayUntilNextDateBoundaryMillis(springForwardDay),
        )
        assertEquals(
            24L * 60L * 60L * 1000L + 30L * 60L * 1000L + 1_000L,
            CountdownTemporalInvalidationPolicy.delayUntilNextDateBoundaryMillis(fallBackDay),
        )
    }

    @Test
    fun oppositeSideTimezonesCanProduceDifferentCivilDatesForSameInstant() {
        val instant = Instant.parse("2026-01-01T00:30:00Z")

        val newYork = CountdownTime.snapshot(Clock.fixed(instant, ZoneId.of("America/New_York")))
        val kiritimati = CountdownTime.snapshot(Clock.fixed(instant, ZoneId.of("Pacific/Kiritimati")))

        assertEquals("2025-12-31", newYork.today.toString())
        assertEquals("2026-01-01", kiritimati.today.toString())
    }
}
