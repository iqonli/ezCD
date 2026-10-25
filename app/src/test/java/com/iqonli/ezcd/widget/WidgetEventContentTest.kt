package com.iqonli.ezcd.widget

import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownStatus
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventIcon
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.ReminderOption
import com.iqonli.ezcd.model.RepeatRule
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetEventContentTest {
    private val today = LocalDate.of(2026, 8, 23)

    @Test
    fun eventContentUsesSavedTitleIconAndCountdown() {
        val event = event(
            id = "custom",
            title = "Visa renewal",
            date = today.plusDays(12),
            type = EventType.CUSTOM,
            icon = EventIcon.FLAG,
        )

        val content = WidgetEventContentFactory.from(event, today)

        assertEquals(R.drawable.ic_event_flag, content.iconRes)
        assertEquals("Visa renewal", content.title)
        assertEquals(today.plusDays(12), content.date)
        assertEquals("12", content.countText)
        assertEquals(R.string.widget_days_left, content.unitRes)
        assertEquals(CountdownStatus.FUTURE, content.status)
    }

    @Test
    fun eventContentMatchesTomorrowTodayAndElapsedStates() {
        val tomorrow = WidgetEventContentFactory.from(event("tomorrow", "Tomorrow", today.plusDays(1)), today)
        assertEquals("1", tomorrow.countText)
        assertEquals(R.string.widget_days_left, tomorrow.unitRes)
        assertEquals(CountdownStatus.TOMORROW, tomorrow.status)

        val sameDay = WidgetEventContentFactory.from(event("today", "Today", today), today)
        assertEquals("0", sameDay.countText)
        assertEquals(R.string.status_today, sameDay.unitRes)
        assertEquals(CountdownStatus.TODAY, sameDay.status)

        val oneDayAgo = WidgetEventContentFactory.from(event("yesterday", "Yesterday", today.minusDays(1)), today)
        assertEquals("1", oneDayAgo.countText)
        assertEquals(R.string.widget_day_ago, oneDayAgo.unitRes)
        assertEquals(CountdownStatus.DONE, oneDayAgo.status)

        val severalDaysAgo = WidgetEventContentFactory.from(event("past", "Past", today.minusDays(6)), today)
        assertEquals("6", severalDaysAgo.countText)
        assertEquals(R.string.widget_days_ago, severalDaysAgo.unitRes)
        assertEquals(CountdownStatus.DONE, severalDaysAgo.status)
    }

    @Test
    fun yearlyContentUsesNextOccurrenceInsteadOfElapsedState() {
        val event = event(
            id = "birthday",
            title = "Birthday",
            date = LocalDate.of(2020, 8, 20),
            repeatRule = RepeatRule.YEARLY,
        )

        val content = WidgetEventContentFactory.from(event, today)

        assertEquals(LocalDate.of(2027, 8, 20), content.date)
        assertEquals(CountdownStatus.FUTURE, content.status)
        assertEquals("362", content.countText)
    }

    @Test
    fun weeklyAndMonthlyContentUseResolvedNextOccurrences() {
        val weekly = event(
            id = "weekly",
            title = "Weekly",
            date = LocalDate.of(2026, 8, 6),
            repeatRule = RepeatRule.WEEKLY,
        )
        val monthly = event(
            id = "monthly",
            title = "Monthly",
            date = LocalDate.of(2026, 1, 31),
            repeatRule = RepeatRule.MONTHLY,
        )

        assertEquals(
            LocalDate.of(2026, 8, 27),
            WidgetEventContentFactory.from(weekly, today).date,
        )
        assertEquals(
            LocalDate.of(2026, 8, 31),
            WidgetEventContentFactory.from(monthly, today).date,
        )
    }

    @Test
    fun compactElapsedContentContainsOnlyDigits() {
        val future = WidgetEventContentFactory.from(event("future", "Future", today.plusDays(6)), today)
        val elapsed = WidgetEventContentFactory.from(event("elapsed", "Elapsed", today.minusDays(6)), today)

        assertEquals("6", future.countTextFor(WidgetSize.COMPACT))
        assertEquals("6", elapsed.countTextFor(WidgetSize.COMPACT))
        assertEquals("6", elapsed.countTextFor(WidgetSize.SHORT))
        assertEquals("6", elapsed.countTextFor(WidgetSize.STANDARD))
        assertEquals("6", elapsed.countTextFor(WidgetSize.LARGE))
    }

    @Test
    fun fixedSelectionResolvesTheSavedEvent() {
        val first = event("first", "First", today.plusDays(5))
        val second = event("second", "Second", today.plusDays(8))

        assertEquals(
            second,
            WidgetEventResolver.resolve(
                selection = WidgetEventSelection.FIXED,
                eventId = "second",
                events = listOf(first, second),
                today = today,
            ),
        )
        assertNull(
            WidgetEventResolver.resolve(
                selection = WidgetEventSelection.FIXED,
                eventId = "missing",
                events = listOf(first, second),
                today = today,
            ),
        )
    }

    @Test
    fun nextResolutionCanBeComputedOnceAndReusedForAWidgetBatch() {
        val past = event("past-batch", "Past", today.minusDays(2))
        val next = event("next-batch", "Next", today.plusDays(3))
        val later = event("later-batch", "Later", today.plusDays(8))

        assertEquals(next, WidgetEventResolver.resolveNext(listOf(later, past, next), today))
    }

    @Test
    fun nextSelectionSkipsPastOneShotButKeepsPastAnchoredYearlyEvent() {
        val past = event("past", "Past", today.minusDays(1))
        val later = event("later", "Later", today.plusDays(10))
        val recurring = event(
            "yearly",
            "Yearly",
            LocalDate.of(2020, 8, 25),
            repeatRule = RepeatRule.YEARLY,
        )

        assertEquals(
            recurring,
            WidgetEventResolver.resolve(
                selection = WidgetEventSelection.NEXT,
                eventId = null,
                events = listOf(later, past, recurring),
                today = today,
            ),
        )
        assertNull(
            WidgetEventResolver.resolve(
                selection = WidgetEventSelection.NEXT,
                eventId = null,
                events = listOf(past),
                today = today,
            ),
        )
    }

    private fun event(
        id: String,
        title: String,
        date: LocalDate,
        type: EventType = EventType.EVENT,
        icon: EventIcon = EventIcon.CALENDAR,
        repeatRule: RepeatRule = RepeatRule.NONE,
    ) = CountdownEvent(
        id = id,
        title = title,
        date = date,
        type = type,
        icon = icon,
        reminder = ReminderOption.OFF,
        createdAt = Instant.EPOCH,
        repeatRule = repeatRule,
    )
}
