// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.countdown

import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

data class CountdownTimeSnapshot(
    val now: ZonedDateTime,
) {
    val today: LocalDate = now.toLocalDate()
    val zone: ZoneId = now.zone
}

object CountdownTime {
    fun snapshot(clock: Clock = Clock.systemDefaultZone()): CountdownTimeSnapshot =
        CountdownTimeSnapshot(ZonedDateTime.now(clock))
}

object CountdownTemporalInvalidationPolicy {
    fun delayUntilNextDateBoundaryMillis(snapshot: CountdownTimeSnapshot): Long {
        val nextBoundary = snapshot.today
            .plusDays(1)
            .atStartOfDay(snapshot.zone)
            .plusSeconds(1)
        return Duration.between(snapshot.now, nextBoundary)
            .toMillis()
            .coerceAtLeast(MIN_DELAY_MILLIS)
    }

    private const val MIN_DELAY_MILLIS = 1_000L
}
