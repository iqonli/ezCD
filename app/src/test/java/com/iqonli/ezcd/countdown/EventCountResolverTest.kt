package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EventCountResolverTest {
    private fun event(date: LocalDate, include: Boolean = false, mode: CountMode = CountMode.COUNT_DOWN) =
        CountdownEvent("event", "Event", date, EventType.EVENT, createdAt = Instant.EPOCH,
            countMode = mode, includeToday = include)

    @Test fun selectedDayStartsAtOneWhenIncludedAndIncrementsAtMidnight() {
        val anchor = LocalDate.of(2026, 10, 10)
        assertEquals(1L, EventCountResolver.resolve(event(anchor, true), anchor).magnitude)
        assertEquals(2L, EventCountResolver.resolve(event(anchor, true), anchor.plusDays(1)).magnitude)
    }
    @Test fun selectedDayStartsAtZeroWhenExcludedAndNextDayIsOne() {
        val anchor = LocalDate.of(2026, 10, 10)
        assertEquals(0L, EventCountResolver.resolve(event(anchor), anchor).magnitude)
        assertEquals(1L, EventCountResolver.resolve(event(anchor), anchor.plusDays(1)).magnitude)
    }
    @Test fun upcomingDateWithoutInclusionReachesZeroOnActualDate() {
        val anchor = LocalDate.of(2026, 6, 6)
        assertEquals(1L, EventCountResolver.resolve(event(anchor), anchor.minusDays(1)).magnitude)
        assertEquals(0L, EventCountResolver.resolve(event(anchor), anchor).magnitude)
    }
    @Test fun upcomingDateWithInclusionCountsBothBoundaryDates() {
        val anchor = LocalDate.of(2026, 9, 10)
        assertEquals(2L, EventCountResolver.resolve(event(anchor, true), anchor.minusDays(1)).magnitude)
        assertEquals(1L, EventCountResolver.resolve(event(anchor, true), anchor).magnitude)
    }
    @Test fun legacyModeDoesNotChangeAnyDateOrInclusionResult() {
        val today = LocalDate.of(2026, 2, 28)
        for (offset in -400L..400L) for (include in listOf(false, true)) {
            val down = EventCountResolver.resolve(event(today.plusDays(offset), include), today)
            val up = EventCountResolver.resolve(event(today.plusDays(offset), include, CountMode.COUNT_UP), today)
            assertEquals(down, up)
            assertEquals(kotlin.math.abs(offset) + if (include) 1L else 0L, down.magnitude)
            assertNull(down.countUpState)
        }
    }
    @Test fun inclusionNeverChangesActualTodayState() {
        val today = LocalDate.of(2026, 10, 24)
        assertEquals(CountdownStatus.TODAY, EventCountResolver.resolve(event(today, true), today).countdownStatus)
    }
    @Test fun recurrenceUsesNextOccurrenceEvenForALegacyMode() {
        val today = LocalDate.of(2026, 3, 1)
        val value = EventCountResolver.resolve(event(LocalDate.of(2024, 2, 29), true, CountMode.COUNT_UP)
            .copy(repeatRule = RepeatRule.YEARLY), today)
        assertEquals(LocalDate.of(2027, 2, 28), value.displayDate)
        assertEquals(365L, value.magnitude)
    }
}
