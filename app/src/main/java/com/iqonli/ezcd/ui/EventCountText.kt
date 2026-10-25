// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.content.Context
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownStatus
import com.iqonli.ezcd.countdown.CountUpState
import com.iqonli.ezcd.countdown.EventCountValue
import com.iqonli.ezcd.model.CountMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

internal object EventCountText {
    fun status(context: Context, value: EventCountValue): String {
        val status=checkNotNull(value.countdownStatus)
        val key=when(status) { CountdownStatus.TODAY -> "today";CountdownStatus.DONE -> "passed";else -> "left" }
        if (TextPool(context).get(key).isNotEmpty()) return TextPool.status(context,status)+" · "+value.magnitude+" "+context.getString(R.string.ez_days)
        return when (status) {
            CountdownStatus.TODAY -> context.getString(R.string.status_today) + " · " +
                context.resources.getQuantityString(R.plurals.count_up_elapsed, quantity(value.magnitude), value.magnitude)
            CountdownStatus.DONE -> context.resources.getQuantityString(
                R.plurals.status_days_ago, quantity(value.magnitude), value.magnitude)
            else -> context.resources.getQuantityString(
                R.plurals.share_days_left, quantity(value.magnitude), value.magnitude)
        }
    }

    fun countUpStatus(context: Context, state: CountUpState, days: Long): String =
        context.resources.getQuantityString(
            if (state == CountUpState.ELAPSED) R.plurals.count_up_elapsed else R.plurals.count_up_starts_in,
            quantity(days), days,
        )

    fun countUpUnit(state: CountUpState, days: Long): Int = when (state) {
        CountUpState.ELAPSED -> if (days == 1L) R.string.count_up_day_elapsed else R.string.count_up_days_elapsed
        CountUpState.STARTS_IN -> if (days == 1L) R.string.count_up_day_until_start else R.string.count_up_days_until_start
    }

    fun date(context: Context, date: LocalDate, mode: CountMode, style: FormatStyle = FormatStyle.MEDIUM): String {
        val text = TextPool.date(context,date,style)
        return text
    }

    fun modeLabel(mode: CountMode): Int = if (mode == CountMode.COUNT_UP) R.string.count_mode_up else R.string.count_mode_down

    private fun quantity(days: Long): Int = days.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}
