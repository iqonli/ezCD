// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.CountdownEvent
import java.time.LocalDate

object CountdownEventOrder {
    fun sortedForDisplay(events: List<CountdownEvent>, today: LocalDate): List<CountdownEvent> {
        val dated = events.map { it to CountdownOccurrenceResolver.displayDate(it, today) }
        val upcoming = dated.filter { !it.second.isBefore(today) }.sortedWith(
            compareBy<Pair<CountdownEvent, LocalDate>> { it.second }
                .thenBy { it.first.createdAt }.thenBy { it.first.id })
        val past = dated.filter { it.second.isBefore(today) }.sortedWith(
            compareByDescending<Pair<CountdownEvent, LocalDate>> { it.second }
                .thenByDescending { it.first.createdAt }.thenBy { it.first.id })
        return (upcoming + past).map { it.first }
    }
}
