package com.iqonli.ezcd.widget

import com.iqonli.ezcd.countdown.*
import com.iqonli.ezcd.model.*
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CountUpContentTest {
    private val today = LocalDate.of(2026, 9, 29)
    private fun event(date: LocalDate) = CountdownEvent("event", "Event", date, EventType.EVENT,
        createdAt = Instant.EPOCH, countMode = CountMode.COUNT_UP)
    @Test fun everyWidgetSizeShowsDigitsWithoutCompletionOrSignMarkers() {
        for (offset in listOf(-12345L, -1L, 0L, 1L, 12345L)) for (include in listOf(false, true)) {
            val content = WidgetEventContentFactory.from(event(today.plusDays(offset)).copy(includeToday = include), today)
            val expected = (kotlin.math.abs(offset) + if (include) 1 else 0).toString()
            assertNull(content.countUpState)
            for (size in WidgetSize.entries) {
                assertEquals(expected, content.countTextFor(size))
                val presentation = WidgetPresentationResolver.resolve(content, size, 2f)
                assertEquals(expected, presentation.countText)
                assertNull(presentation.milestone); assertFalse(presentation.showMilestone)
            }
        }
    }
    @Test fun nextSelectionUsesDatesRatherThanLegacyMode() {
        val upcoming = event(today.plusDays(1))
        val recurring = upcoming.copy(id = "recurring", date = today.minusWeeks(1), repeatRule = RepeatRule.WEEKLY)
        assertEquals(upcoming, WidgetEventResolver.resolveNext(listOf(upcoming), today))
        assertEquals(recurring, WidgetEventResolver.resolveNext(listOf(upcoming, recurring), today))
        assertEquals(upcoming, WidgetEventResolver.resolve(WidgetEventSelection.FIXED, upcoming.id, listOf(upcoming), today))
    }
    @Test fun everyDateStateAndRecurrenceUsesTheSameCalendarRule() {
        for (rule in RepeatRule.entries) for (offset in -5L..5L) {
            val item = event(today.plusDays(offset)).copy(repeatRule = rule)
            val display = CountdownOccurrenceResolver.displayDate(item, today)
            val previous = CountdownCalculator.value(today, display)
            val current = EventCountResolver.resolve(item, today)
            assertEquals(display, current.displayDate); assertEquals(previous.status, current.countdownStatus)
            assertEquals(kotlin.math.abs(previous.days), current.magnitude)
        }
    }
    @Test fun dateCountsFollowLocalCalendarAcrossTimeZonesAndDst() {
        val cases = listOf(
            Triple("2026-03-09T04:30:00Z", "America/New_York", "2026-03-08"),
            Triple("2026-11-02T05:30:00Z", "America/New_York", "2026-11-01"),
            Triple("2026-01-01T00:30:00Z", "Pacific/Kiritimati", "2025-12-31"),
            Triple("2026-01-01T00:30:00Z", "America/Argentina/Buenos_Aires", "2025-12-30"))
        for ((instant, zone, start) in cases) {
            val clock = Clock.fixed(Instant.parse(instant), ZoneId.of(zone))
            val local = LocalDate.now(clock)
            val value = EventCountResolver.resolve(event(LocalDate.parse(start)), local)
            assertEquals(java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(start), local), value.magnitude)
        }
    }
}
