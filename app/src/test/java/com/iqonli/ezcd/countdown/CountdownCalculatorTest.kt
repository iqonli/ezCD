package com.iqonli.ezcd.countdown

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CountdownCalculatorTest {
    @Test
    fun sameDayReturnsZeroAndToday() {
        val date = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(date, date)
        assertEquals(0, value.days)
        assertEquals(0, value.elapsedDays)
        assertEquals(CountdownStatus.TODAY, value.status)
    }

    @Test
    fun tomorrowReturnsOneAndTomorrow() {
        val today = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(today, today.plusDays(1))
        assertEquals(1, value.days)
        assertEquals(0, value.elapsedDays)
        assertEquals(CountdownStatus.TOMORROW, value.status)
    }

    @Test
    fun twoDaysReturnsTwoDayMilestone() {
        val today = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(today, today.plusDays(2))
        assertEquals(2, value.days)
        assertEquals(CountdownStatus.TWO_DAYS, value.status)
    }

    @Test
    fun threeDaysReturnsThreeDayMilestone() {
        val today = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(today, today.plusDays(3))
        assertEquals(3, value.days)
        assertEquals(CountdownStatus.THREE_DAYS, value.status)
    }

    @Test
    fun futureDateUsesFutureStatus() {
        val today = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(today, today.plusDays(17))
        assertEquals(17, value.days)
        assertEquals(CountdownStatus.FUTURE, value.status)
    }

    @Test
    fun yesterdayReturnsElapsedDayAndDone() {
        val today = LocalDate.of(2026, 8, 7)
        val value = CountdownCalculator.value(today, today.minusDays(1))
        assertEquals(-1, value.days)
        assertEquals(1, value.elapsedDays)
        assertEquals(CountdownStatus.DONE, value.status)
    }

    @Test
    fun elapsedDaysCrossYearBoundary() {
        val today = LocalDate.of(2027, 1, 3)
        val target = LocalDate.of(2026, 12, 30)
        val value = CountdownCalculator.value(today, target)
        assertEquals(-4, value.days)
        assertEquals(4, value.elapsedDays)
        assertEquals(CountdownStatus.DONE, value.status)
    }

    @Test
    fun leapDayIsHandledByCalendarMath() {
        val today = LocalDate.of(2028, 2, 28)
        val target = LocalDate.of(2028, 3, 1)
        assertEquals(2, CountdownCalculator.daysUntil(today, target))
    }

    @Test
    fun yearBoundaryIsHandled() {
        val today = LocalDate.of(2026, 12, 31)
        val target = LocalDate.of(2027, 1, 1)
        assertEquals(1, CountdownCalculator.daysUntil(today, target))
    }
}
