package com.iqonli.ezcd.notification

import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.ReminderOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ArrivalNotificationPolicyTest {
    private val today = LocalDate.of(2026, 8, 7)

    @Test
    fun arrivalReminderIsDeliveredOnlyOnceForItsScheduledDate() {
        val event = event("one", today, ReminderOption.ON_DAY)
        assertTrue(ArrivalNotificationPolicy.isDue(event, today, null))
        assertFalse(ArrivalNotificationPolicy.isDue(event, today, today))
    }

    @Test
    fun advanceReminderUsesSelectedOffset() {
        val event = event("advance", today.plusDays(3), ReminderOption.THREE_DAYS)
        assertEquals(today, ArrivalNotificationPolicy.scheduledDate(event))
        assertTrue(ArrivalNotificationPolicy.isDue(event, today, null))
    }

    @Test
    fun impossiblePastReminderScheduleIsRejected() {
        assertFalse(
            ArrivalNotificationPolicy.isSchedulePossible(
                today.plusDays(2),
                ReminderOption.THREE_DAYS,
                today,
            ),
        )
        assertTrue(
            ArrivalNotificationPolicy.isSchedulePossible(
                today.plusDays(3),
                ReminderOption.THREE_DAYS,
                today,
            ),
        )
        assertTrue(
            ArrivalNotificationPolicy.isSchedulePossible(
                today.minusDays(10),
                ReminderOption.OFF,
                today,
            ),
        )
    }

    @Test
    fun missedReminderDoesNotCatchUpAfterItsScheduledDate() {
        val event = event("missed", today, ReminderOption.ONE_DAY)

        assertEquals(today.minusDays(1), ArrivalNotificationPolicy.scheduledDate(event))
        assertFalse(ArrivalNotificationPolicy.isDue(event, today, null))
        assertNull(ArrivalNotificationPolicy.nextPendingDate(listOf(event), today) { _, _ -> false })
    }

    @Test
    fun disabledAndOtherDatesAreNotDue() {
        assertFalse(ArrivalNotificationPolicy.isDue(event("off", today, ReminderOption.OFF), today, null))
        assertFalse(
            ArrivalNotificationPolicy.isDue(
                event("future", today.plusDays(1), ReminderOption.ON_DAY),
                today,
                null,
            ),
        )
    }

    @Test
    fun nextPendingDateSkipsPastAndDeliveredReminders() {
        val events = listOf(
            event("past", today, ReminderOption.ONE_DAY),
            event("delivered", today, ReminderOption.ON_DAY),
            event("next", today.plusDays(3), ReminderOption.ONE_DAY),
        )
        val next = ArrivalNotificationPolicy.nextPendingDate(events, today) { event, scheduledDate ->
            event.id == "delivered" && scheduledDate == today
        }
        assertEquals(today.plusDays(2), next)
    }

    @Test
    fun noEnabledEventsMeansNoSchedule() {
        val events = listOf(event("off", today.plusDays(2), ReminderOption.OFF))
        assertNull(ArrivalNotificationPolicy.nextPendingDate(events, today) { _, _ -> false })
    }

    @Test
    fun cosmeticEditsKeepDeliveredState() {
        val previous = event("same", today.plusDays(2), ReminderOption.ONE_DAY)
        val updated = previous.copy(title = "Updated title", type = EventType.CUSTOM)

        assertFalse(ArrivalNotificationPolicy.shouldResetDeliveryState(previous, updated))
    }

    @Test
    fun newDateOrReminderResetsDeliveredState() {
        val previous = event("changed", today.plusDays(2), ReminderOption.ONE_DAY)

        assertTrue(
            ArrivalNotificationPolicy.shouldResetDeliveryState(
                previous,
                previous.copy(date = previous.date.plusDays(1)),
            ),
        )
        assertTrue(
            ArrivalNotificationPolicy.shouldResetDeliveryState(
                previous,
                previous.copy(reminder = ReminderOption.THREE_DAYS),
            ),
        )
        assertTrue(ArrivalNotificationPolicy.shouldResetDeliveryState(null, previous))
    }

    @Test
    fun legacyModeDoesNotDisableExplicitReminder() {
        val countUp = event("up", today, ReminderOption.ON_DAY).copy(countMode = CountMode.COUNT_UP)

        assertEquals(today, ArrivalNotificationPolicy.scheduledDate(countUp))
        assertEquals(today, ArrivalNotificationPolicy.scheduledDate(countUp, today))
        assertTrue(ArrivalNotificationPolicy.isDue(countUp, today, null))
        assertEquals(today, ArrivalNotificationPolicy.nextPendingDate(listOf(countUp), today) { _, _ -> false })
    }

    @Test
    fun changingModeResetsDeliveryState() {
        val previous = event("mode", today.plusDays(2), ReminderOption.ONE_DAY)
        assertTrue(
            ArrivalNotificationPolicy.shouldResetDeliveryState(
                previous,
                previous.copy(
                    countMode = CountMode.COUNT_UP,
                    reminder = ReminderOption.OFF,
                ),
            ),
        )
    }

    @Test
    fun triggerUsesNineAmOrNearNowWhenNineHasPassed() {
        val zone = ZoneId.of("America/Argentina/Buenos_Aires")
        val before = ZonedDateTime.of(2026, 8, 7, 8, 0, 0, 0, zone)
        val after = ZonedDateTime.of(2026, 8, 7, 10, 0, 0, 0, zone)

        assertEquals(
            ZonedDateTime.of(2026, 8, 7, 9, 0, 0, 0, zone),
            ArrivalNotificationScheduler.triggerTime(before, today),
        )
        assertEquals(after, ArrivalNotificationScheduler.triggerTime(after, today))
    }

    @Test
    fun exhaustedOneShotDeliveryDoesNotCreateAnIndefiniteRetryLoop() {
        val event = event("failed", today, ReminderOption.ON_DAY)

        assertNull(
            ArrivalNotificationPolicy.nextPendingDate(
                events = listOf(event),
                today = today,
                wasDelivered = { _, _ -> false },
                canAttempt = { _, _ -> false },
            ),
        )
    }

    @Test
    fun advanceReminderCanCrossTheYearBoundaryUsingCivilDates() {
        val event = event("new-year", LocalDate.of(2027, 1, 2), ReminderOption.THREE_DAYS)

        assertEquals(
            LocalDate.of(2026, 12, 30),
            ArrivalNotificationPolicy.scheduledDate(event, LocalDate.of(2026, 12, 30)),
        )
    }

    @Test
    fun extremeUnexpectedDateCannotOverflowSchedulerIntoACrash() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 8, 7, 8, 0, 0, 0, zone)

        assertNull(ArrivalNotificationScheduler.triggerMillis(now, LocalDate.MAX))
    }

    private fun event(id: String, date: LocalDate, reminder: ReminderOption): CountdownEvent = CountdownEvent(
        id = id,
        title = id,
        date = date,
        type = EventType.EVENT,
        reminder = reminder,
        createdAt = Instant.EPOCH,
    )
}
