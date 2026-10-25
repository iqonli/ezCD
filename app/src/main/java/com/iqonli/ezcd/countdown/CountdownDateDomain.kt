// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.countdown

import java.time.LocalDate

/**
 * Product date range shared by creation, import validation, countdown math, and reminders.
 *
 * Android's platform DatePicker defaults to 1900-01-01 through 2100-12-31. CountAway uses
 * the same bounds so dates created by the normal UI are representable everywhere the app
 * needs them. Out-of-range persisted data must be rejected explicitly rather than clamped.
 */
object CountdownDateDomain {
    val MIN_DATE: LocalDate = LocalDate.of(1900, 1, 1)
    val MAX_DATE: LocalDate = LocalDate.of(2100, 12, 31)

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(MIN_DATE) && !date.isAfter(MAX_DATE)
}
