// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import com.iqonli.ezcd.data.CountdownLoadResult
import com.iqonli.ezcd.model.CountdownEvent
import java.time.LocalDate

internal sealed interface WidgetRenderData {
    fun resolve(configuration: WidgetConfiguration?): CountdownEvent?

    data class Ready(
        val eventsById: Map<String, CountdownEvent>,
        val nextEvent: CountdownEvent?,
    ) : WidgetRenderData {
        override fun resolve(configuration: WidgetConfiguration?): CountdownEvent? {
            configuration ?: return null
            return when (configuration.eventSelection) {
                WidgetEventSelection.FIXED -> configuration.eventId?.let(eventsById::get)
                WidgetEventSelection.NEXT -> nextEvent
            }
        }
    }

    data class Failure(
        val problem: com.iqonli.ezcd.data.CountdownDataProblem,
    ) : WidgetRenderData {
        override fun resolve(configuration: WidgetConfiguration?): CountdownEvent? = null
    }

    companion object {
        fun from(loadResult: CountdownLoadResult, today: LocalDate): WidgetRenderData =
            when (loadResult) {
                is CountdownLoadResult.Failure -> Failure(loadResult.problem)
                is CountdownLoadResult.Success -> Ready(
                    eventsById = loadResult.events.associateBy(CountdownEvent::id),
                    nextEvent = WidgetEventResolver.resolveNext(loadResult.events, today),
                )
            }
    }
}
