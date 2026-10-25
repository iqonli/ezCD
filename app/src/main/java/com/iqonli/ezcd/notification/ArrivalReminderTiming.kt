// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.notification

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

internal object ArrivalReminderTiming {
    fun triggerTime(now: ZonedDateTime, date: LocalDate, lastFailure: Instant? = null, reminderMinuteOfDay: Int = 9 * 60): ZonedDateTime {
        require(reminderMinuteOfDay in 0..1439)
        val morning = date.atTime(LocalTime.of(reminderMinuteOfDay / 60, reminderMinuteOfDay % 60)).atZone(now.zone)
        if (date != now.toLocalDate() || morning.isAfter(now)) return morning
        if (lastFailure == null) return now
        val retry = lastFailure.atZone(now.zone).plusMinutes(15)
        // A late failure gets a bounded same-day attempt, never a planned next-day catch-up.
        return if (retry.toLocalDate() != date || !retry.isAfter(now)) now else retry
    }

    data class Plan(val triggerMillis: Long, val date: LocalDate, val zoneId: String)

    fun keepEarlier(previous: Plan?, proposed: Plan): Boolean = previous != null &&
        previous.date == proposed.date && previous.zoneId == proposed.zoneId &&
        previous.triggerMillis <= proposed.triggerMillis
}
