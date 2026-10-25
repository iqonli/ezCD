package com.iqonli.ezcd.notification

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ArrivalReminderTimingTest {
    private val start = ZonedDateTime.of(2026, 9, 28, 0, 0, 0, 0, ZoneId.of("America/Argentina/Buenos_Aires"))

    @Test fun firstOverdueAttemptDoesNotWaitFifteenMinutesOrCrossMidnight() {
        for (time in listOf("08:59", "09:00", "12:00", "23:44", "23:45", "23:50", "23:59")) {
            val now = start.with(LocalTime.parse(time))
            val actual = ArrivalReminderTiming.triggerTime(now, now.toLocalDate())
            assertEquals(if (time == "08:59") start.withHour(9) else now, actual)
            assertEquals(now.toLocalDate(), actual.toLocalDate())
        }
    }

    @Test fun retryDeadlineComesFromFailureAndRepeatedReconciliationDoesNotSlideIt() {
        val failed = start.withHour(12)
        val expected = failed.plusMinutes(15)
        for (minute in 0..14) assertEquals(expected,
            ArrivalReminderTiming.triggerTime(failed.plusMinutes(minute.toLong()), start.toLocalDate(), failed.toInstant()))
        for (time in listOf("23:44", "23:45", "23:50", "23:59")) {
            val now = start.with(LocalTime.parse(time))
            val next = ArrivalReminderTiming.triggerTime(now, now.toLocalDate(), now.toInstant())
            assertEquals(now.toLocalDate(), next.toLocalDate())
            assertFalse(next.isBefore(now))
        }
    }

    @Test fun plannedEarlierAttemptIsKeptButChangedCivilDayOrZoneIsReconciled() {
        val first = ArrivalReminderTiming.Plan(start.withHour(12).toInstant().toEpochMilli(), start.toLocalDate(), start.zone.id)
        assertTrue(ArrivalReminderTiming.keepEarlier(first, first.copy(triggerMillis = first.triggerMillis + 60_000)))
        assertFalse(ArrivalReminderTiming.keepEarlier(first, first.copy(triggerMillis = first.triggerMillis - 1)))
        assertFalse(ArrivalReminderTiming.keepEarlier(first, first.copy(date = first.date.plusDays(1))))
        assertFalse(ArrivalReminderTiming.keepEarlier(first, first.copy(zoneId = "Europe/Madrid")))
    }

    @Test fun futureSchedulesStayAtNineAcrossDstAndZoneChanges() {
        for (zone in listOf("Europe/Madrid", "America/New_York", "Pacific/Apia")) {
            for (day in listOf("2026-03-28", "2026-10-24")) {
                val now = java.time.LocalDate.parse(day).atTime(23, 50).atZone(ZoneId.of(zone))
                val next = ArrivalReminderTiming.triggerTime(now, now.toLocalDate().plusDays(1), now.toInstant())
                assertEquals(LocalTime.of(9, 0), next.toLocalTime())
                assertEquals(now.toLocalDate().plusDays(1), next.toLocalDate())
            }
        }
    }
}
