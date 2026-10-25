package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CountdownEventOrderTest {
    private val today = LocalDate.of(2026, 8, 7)

    @Test
    fun activeEventsComeFirstNearestToFarthestThenPastNewestToOldest() {
        val events = listOf(
            event("past-old", today.minusDays(10)),
            event("future-far", today.plusDays(30)),
            event("today", today),
            event("past-new", today.minusDays(1)),
            event("future-near", today.plusDays(2)),
        )

        val result = CountdownEventOrder.sortedForDisplay(events, today).map { it.id }

        assertEquals(
            listOf("today", "future-near", "future-far", "past-new", "past-old"),
            result,
        )
    }

    @Test
    fun yearlyEventsSortByTheirNextOccurrence() {
        val recurring = event(
            "recurring",
            LocalDate.of(2020, 8, 8),
            repeatRule = RepeatRule.YEARLY,
        )
        val oneShot = event("one-shot", today.plusDays(5))
        val past = event("past", today.minusDays(1))

        assertEquals(
            listOf("recurring", "one-shot", "past"),
            CountdownEventOrder.sortedForDisplay(listOf(past, oneShot, recurring), today).map { it.id },
        )
    }

    @Test
    fun legacyModeDoesNotPartitionDateOrdering() {
        val events = listOf(
            event("past", today.minusDays(1)),
            event("up-old", today.minusDays(30), countMode = CountMode.COUNT_UP),
            event("future", today.plusDays(2)),
            event("up-new", today.minusDays(2), countMode = CountMode.COUNT_UP),
        )

        assertEquals(
            listOf("future", "past", "up-new", "up-old"),
            CountdownEventOrder.sortedForDisplay(events, today).map { it.id },
        )
    }

    @Test
    fun equalDatesUseCreationTimeForStableOrdering() {
        val date = today.plusDays(5)
        val older = event("older", date, Instant.parse("2026-01-01T00:00:00Z"))
        val newer = event("newer", date, Instant.parse("2026-02-01T00:00:00Z"))

        assertEquals(
            listOf("older", "newer"),
            CountdownEventOrder.sortedForDisplay(listOf(newer, older), today).map { it.id },
        )
    }

    private fun event(
        id: String,
        date: LocalDate,
        createdAt: Instant = Instant.parse("2026-01-01T00:00:00Z"),
        repeatRule: RepeatRule = RepeatRule.NONE,
        countMode: CountMode = CountMode.COUNT_DOWN,
    ) = CountdownEvent(
        id = id,
        title = id,
        date = date,
        type = EventType.CUSTOM,
        createdAt = createdAt,
        repeatRule = repeatRule,
        countMode = countMode,
    )
}
