package com.iqonli.ezcd.notification

import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.ReminderOption
import com.iqonli.ezcd.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ArrivalRecurringNotificationPolicyTest {
    private val today = LocalDate.of(2026, 9, 16)

    @Test
    fun weeklyReminderUsesTheNextOccurrence() {
        val event = event(LocalDate.of(2026, 9, 3), ReminderOption.ONE_DAY, RepeatRule.WEEKLY)

        assertEquals(LocalDate.of(2026, 9, 16), ArrivalNotificationPolicy.scheduledDate(event, today))
        assertTrue(ArrivalNotificationPolicy.isDue(event, today, null))
    }

    @Test
    fun deliveredWeeklyReminderRollsForwardOneWeek() {
        val event = event(today, ReminderOption.ON_DAY, RepeatRule.WEEKLY)

        assertEquals(
            today.plusWeeks(1),
            ArrivalNotificationPolicy.nextPendingDate(listOf(event), today) { _, scheduledDate ->
                scheduledDate == today
            },
        )
    }

    @Test
    fun missedWeeklyReminderUsesTheNextValidOccurrence() {
        val event = event(LocalDate.of(2026, 9, 3), ReminderOption.THREE_DAYS, RepeatRule.WEEKLY)

        assertEquals(
            LocalDate.of(2026, 9, 21),
            ArrivalNotificationPolicy.scheduledDate(event, today.plusDays(1)),
        )
    }

    @Test
    fun monthlyReminderHandlesShortMonthsAndReturnsToAnchorDay() {
        val event = event(LocalDate.of(2026, 1, 31), ReminderOption.ON_DAY, RepeatRule.MONTHLY)

        assertEquals(
            LocalDate.of(2026, 2, 28),
            ArrivalNotificationPolicy.scheduledDate(event, LocalDate.of(2026, 2, 1)),
        )
        assertEquals(
            LocalDate.of(2026, 3, 31),
            ArrivalNotificationPolicy.scheduledDate(event, LocalDate.of(2026, 3, 1)),
        )
    }

    @Test
    fun deliveredMonthlyReminderRollsForwardInsteadOfDisappearing() {
        val event = event(LocalDate.of(2026, 1, 31), ReminderOption.ON_DAY, RepeatRule.MONTHLY)
        val february = LocalDate.of(2026, 2, 28)

        assertEquals(
            LocalDate.of(2026, 3, 31),
            ArrivalNotificationPolicy.nextPendingDate(listOf(event), february) { _, scheduledDate ->
                scheduledDate == february
            },
        )
    }

    @Test
    fun exhaustedRecurringDeliveryAdvancesToTheNextOccurrence() {
        val event = event(today, ReminderOption.ON_DAY, RepeatRule.WEEKLY)

        assertEquals(
            today.plusWeeks(1),
            ArrivalNotificationPolicy.nextPendingDate(
                events = listOf(event),
                today = today,
                wasDelivered = { _, _ -> false },
                canAttempt = { _, scheduledDate -> scheduledDate != today },
            ),
        )
    }

    @Test
    fun yearlyReminderUsesTheNextOccurrence() {
        val event = event(LocalDate.of(2020, 9, 19), ReminderOption.THREE_DAYS, RepeatRule.YEARLY)

        assertEquals(today, ArrivalNotificationPolicy.scheduledDate(event, today))
        assertTrue(ArrivalNotificationPolicy.isDue(event, today, null))
    }

    @Test
    fun missedYearlyReminderSkipsThisOccurrenceAndSchedulesNextYear() {
        val event = event(LocalDate.of(2020, 9, 18), ReminderOption.THREE_DAYS, RepeatRule.YEARLY)

        assertEquals(
            LocalDate.of(2027, 9, 15),
            ArrivalNotificationPolicy.scheduledDate(event, today),
        )
        assertFalse(ArrivalNotificationPolicy.isDue(event, today, null))
    }

    @Test
    fun deliveredYearlyReminderRollsForwardInsteadOfDisappearing() {
        val event = event(LocalDate.of(2020, 9, 16), ReminderOption.ON_DAY, RepeatRule.YEARLY)

        assertEquals(
            LocalDate.of(2027, 9, 16),
            ArrivalNotificationPolicy.nextPendingDate(listOf(event), today) { _, scheduledDate ->
                scheduledDate == today
            },
        )
    }

    @Test
    fun leapDayReminderUsesFebruary28InNonLeapYear2100() {
        val event = event(LocalDate.of(2024, 2, 29), ReminderOption.SEVEN_DAYS, RepeatRule.YEARLY)

        assertEquals(
            LocalDate.of(2100, 2, 21),
            ArrivalNotificationPolicy.scheduledDate(event, LocalDate.of(2100, 2, 20)),
        )
    }

    @Test
    fun changingRepeatRuleResetsDeliveryState() {
        val previous = event(LocalDate.of(2026, 9, 16), ReminderOption.ON_DAY, RepeatRule.YEARLY)
        val updated = previous.copy(repeatRule = RepeatRule.MONTHLY)

        assertTrue(ArrivalNotificationPolicy.shouldResetDeliveryState(previous, updated))
    }

    private fun event(
        date: LocalDate,
        reminder: ReminderOption,
        repeatRule: RepeatRule,
    ): CountdownEvent = CountdownEvent(
        id = "event",
        title = "Event",
        date = date,
        type = EventType.EVENT,
        reminder = reminder,
        createdAt = Instant.EPOCH,
        repeatRule = repeatRule,
    )
}
