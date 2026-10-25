// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.RepeatRule
import java.time.LocalDate
import kotlin.math.abs

// Retained for old data/API callers only; neither value selects a counting algorithm anymore.
enum class CountUpState { ELAPSED, STARTS_IN }
data class EventCountValue(
    val mode: CountMode,
    val displayDate: LocalDate,
    val magnitude: Long,
    val countdownStatus: CountdownStatus? = null,
    val countUpState: CountUpState? = null,
)

object EventCountResolver {
    fun resolve(event: CountdownEvent, today: LocalDate): EventCountValue =
        resolve(event.date, event.repeatRule, event.countMode, today, event.includeToday)

    @Suppress("UNUSED_PARAMETER")
    fun resolve(date: LocalDate, repeatRule: RepeatRule, mode: CountMode, today: LocalDate,
        includeToday: Boolean = false): EventCountValue {
        val displayDate = CountdownOccurrenceResolver.displayDate(date, repeatRule, today)
        val difference = CountdownCalculator.value(today, displayDate)
        return EventCountValue(
            mode = CountMode.COUNT_DOWN,
            displayDate = displayDate,
            magnitude = abs(difference.days) + if (includeToday) 1L else 0L,
            // Real calendar state is independent of inclusive counting (today stays today).
            countdownStatus = difference.status,
        )
    }
}
