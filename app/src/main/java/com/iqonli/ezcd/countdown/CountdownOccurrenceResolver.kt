// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.RepeatRule
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

object CountdownOccurrenceResolver {
    fun displayDate(event: CountdownEvent, today: LocalDate): LocalDate =
        displayDate(event.date, event.repeatRule, today)

    fun displayDate(anchorDate: LocalDate, repeatRule: RepeatRule, today: LocalDate): LocalDate =
        when (repeatRule) {
            RepeatRule.NONE -> anchorDate
            else -> checkNotNull(nextOccurrenceOnOrAfter(anchorDate, repeatRule, today))
        }

    fun nextOccurrenceOnOrAfter(event: CountdownEvent, threshold: LocalDate): LocalDate? =
        nextOccurrenceOnOrAfter(event.date, event.repeatRule, threshold)

    fun nextOccurrenceOnOrAfter(
        anchorDate: LocalDate,
        repeatRule: RepeatRule,
        threshold: LocalDate,
    ): LocalDate? = when (repeatRule) {
        RepeatRule.NONE -> anchorDate.takeUnless { it.isBefore(threshold) }
        RepeatRule.WEEKLY -> nextWeeklyOccurrenceOnOrAfter(anchorDate, threshold)
        RepeatRule.MONTHLY -> nextMonthlyOccurrenceOnOrAfter(anchorDate, threshold)
        RepeatRule.YEARLY -> nextYearlyOccurrenceOnOrAfter(anchorDate, threshold)
    }

    private fun nextWeeklyOccurrenceOnOrAfter(anchorDate: LocalDate, threshold: LocalDate): LocalDate {
        if (!anchorDate.isBefore(threshold)) return anchorDate
        val elapsedDays = ChronoUnit.DAYS.between(anchorDate, threshold)
        val weeks = (elapsedDays + DAYS_PER_WEEK - 1L) / DAYS_PER_WEEK
        return anchorDate.plusWeeks(weeks)
    }

    private fun nextMonthlyOccurrenceOnOrAfter(anchorDate: LocalDate, threshold: LocalDate): LocalDate {
        if (!anchorDate.isBefore(threshold)) return anchorDate

        val anchorMonth = YearMonth.from(anchorDate)
        var month = YearMonth.from(threshold)
        if (month.isBefore(anchorMonth)) month = anchorMonth

        var candidate = monthlyDate(anchorDate, month)
        if (candidate.isBefore(anchorDate)) candidate = anchorDate
        if (candidate.isBefore(threshold)) {
            month = month.plusMonths(1)
            candidate = monthlyDate(anchorDate, month)
        }
        return candidate
    }

    private fun nextYearlyOccurrenceOnOrAfter(anchorDate: LocalDate, threshold: LocalDate): LocalDate {
        if (!anchorDate.isBefore(threshold)) return anchorDate

        var year = maxOf(anchorDate.year, threshold.year)
        var candidate = yearlyDate(anchorDate, year)
        if (candidate.isBefore(anchorDate)) {
            candidate = anchorDate
        }
        if (candidate.isBefore(threshold)) {
            year += 1
            candidate = yearlyDate(anchorDate, year)
        }
        return candidate
    }

    private fun monthlyDate(anchorDate: LocalDate, month: YearMonth): LocalDate =
        month.atDay(anchorDate.dayOfMonth.coerceAtMost(month.lengthOfMonth()))

    private fun yearlyDate(anchorDate: LocalDate, year: Int): LocalDate {
        val month = YearMonth.of(year, anchorDate.month)
        val day = anchorDate.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        return LocalDate.of(year, anchorDate.month, day)
    }

    private const val DAYS_PER_WEEK = 7L
}
