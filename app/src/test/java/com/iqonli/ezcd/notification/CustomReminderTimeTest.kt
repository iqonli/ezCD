package com.iqonli.ezcd.notification

import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CustomReminderTimeTest {
    private val date = LocalDate.of(2026,10,24)
    private val zone = ZoneId.of("Asia/Shanghai")
    @Test fun customTimeIsUsedForFutureReminderDates() {
        val now = date.atTime(10,0).atZone(zone)
        for (minute in listOf(0,1,540,1125,1439)) {
            val result = ArrivalReminderTiming.triggerTime(now,date.plusDays(1),reminderMinuteOfDay = minute)
            assertEquals(LocalTime.of(minute/60,minute%60),result.toLocalTime())
            assertEquals(date.plusDays(1),result.toLocalDate())
        }
    }
    @Test fun twoEventsOnTheSameDateKeepTheirOwnTimes() {
        val now = date.atTime(9,0).atZone(zone)
        assertEquals(now,ArrivalReminderTiming.triggerTime(now,date,reminderMinuteOfDay = 540))
        assertEquals(date.atTime(18,45).atZone(zone),ArrivalReminderTiming.triggerTime(now,date,reminderMinuteOfDay = 1125))
    }
    @Test fun overdueSameDayReminderAndRetryStayOnThatCivilDate() {
        val now = date.atTime(20,0).atZone(zone)
        assertEquals(now,ArrivalReminderTiming.triggerTime(now,date,reminderMinuteOfDay = 1125))
        assertEquals(now.plusMinutes(15),ArrivalReminderTiming.triggerTime(now,date,now.toInstant(),1125))
        val late = date.atTime(23,55).atZone(zone)
        assertEquals(late,ArrivalReminderTiming.triggerTime(late,date,late.toInstant(),1430))
    }
    @Test fun nonexistentDstTimeMovesToTheNextValidLocalTime() {
        val before = LocalDate.of(2026,3,7).atTime(12,0).atZone(ZoneId.of("America/New_York"))
        val next = ArrivalReminderTiming.triggerTime(before,before.toLocalDate().plusDays(1),reminderMinuteOfDay = 150)
        assertEquals(LocalTime.of(3,30),next.toLocalTime())
    }
    @Test fun outOfRangeMinutesAreRejected() {
        val now = date.atStartOfDay(zone)
        assertThrows(IllegalArgumentException::class.java) { ArrivalReminderTiming.triggerTime(now,date,reminderMinuteOfDay = -1) }
        assertThrows(IllegalArgumentException::class.java) { ArrivalReminderTiming.triggerTime(now,date,reminderMinuteOfDay = 1440) }
    }
}
