// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.countdown

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class CountdownStatus {
    FUTURE,
    THREE_DAYS,
    TWO_DAYS,
    TOMORROW,
    TODAY,
    DONE,
}

data class CountdownValue(
    val days: Long,
    val status: CountdownStatus,
) {
    val elapsedDays: Long
        get() = (-days).coerceAtLeast(0)
}

object CountdownCalculator {
    fun daysUntil(today: LocalDate, target: LocalDate): Long =
        ChronoUnit.DAYS.between(today, target)

    fun value(today: LocalDate, target: LocalDate): CountdownValue {
        val days = daysUntil(today, target)
        val status = when {
            days > 3 -> CountdownStatus.FUTURE
            days == 3L -> CountdownStatus.THREE_DAYS
            days == 2L -> CountdownStatus.TWO_DAYS
            days == 1L -> CountdownStatus.TOMORROW
            days == 0L -> CountdownStatus.TODAY
            else -> CountdownStatus.DONE
        }
        return CountdownValue(days = days, status = status)
    }
}
