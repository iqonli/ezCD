package com.iqonli.ezcd.countdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class CountdownDateDomainTest {
    @Test
    fun supportedRangeMatchesAndroidDatePickerDefaults() {
        assertTrue(CountdownDateDomain.contains(LocalDate.of(1900, 1, 1)))
        assertTrue(CountdownDateDomain.contains(LocalDate.of(2100, 12, 31)))
        assertFalse(CountdownDateDomain.contains(LocalDate.of(1899, 12, 31)))
        assertFalse(CountdownDateDomain.contains(LocalDate.of(2101, 1, 1)))
    }

    @Test
    fun supportedBoundariesRemainRepresentableForReminderScheduling() {
        val zones = listOf(
            ZoneId.of("UTC"),
            ZoneId.of("America/New_York"),
            ZoneId.of("Pacific/Kiritimati"),
        )

        zones.forEach { zone ->
            CountdownDateDomain.MIN_DATE.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
            CountdownDateDomain.MAX_DATE.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
        }
    }
}
