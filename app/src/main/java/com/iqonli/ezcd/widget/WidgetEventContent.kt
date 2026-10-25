// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.content.Context
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownEventOrder
import com.iqonli.ezcd.countdown.CountdownOccurrenceResolver
import com.iqonli.ezcd.countdown.CountdownStatus
import com.iqonli.ezcd.countdown.CountUpState
import com.iqonli.ezcd.countdown.EventCountResolver
import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.ui.EventCountText
import com.iqonli.ezcd.ui.EventIconPresentation
import java.time.LocalDate

internal data class WidgetEventContent(
    val iconRes: Int,
    val title: String,
    val date: LocalDate,
    val countText: String,
    val unitRes: Int?,
    val status: CountdownStatus?,
    val countUpState: CountUpState? = null,
) {
    @Suppress("UNUSED_PARAMETER")
    fun countTextFor(size: WidgetSize, unitVisible: Boolean = true): String = countText

    fun statusDescription(context: Context): String = EventCountText.status(context,
        com.iqonli.ezcd.countdown.EventCountValue(CountMode.COUNT_DOWN, date,
            countText.toLongOrNull() ?: 0L, status))

    fun dateText(context: Context): String = EventCountText.date(context, date, CountMode.COUNT_DOWN)

    fun description(context: Context): String = listOf(
        title, statusDescription(context), dateText(context), context.getString(R.string.widget_open_event),
    ).joinToString(", ")
}

internal object WidgetEventResolver {
    fun resolveNext(events: List<CountdownEvent>, today: LocalDate): CountdownEvent? =
        CountdownEventOrder.sortedForDisplay(events, today)
            .firstOrNull { event -> !CountdownOccurrenceResolver.displayDate(event, today).isBefore(today) }

    fun resolve(selection: WidgetEventSelection, eventId: String?, events: List<CountdownEvent>, today: LocalDate): CountdownEvent? =
        when (selection) {
            WidgetEventSelection.FIXED -> eventId?.let { id -> events.firstOrNull { it.id == id } }
            WidgetEventSelection.NEXT -> resolveNext(events, today)
        }
}

internal object WidgetEventContentFactory {
    fun from(event: CountdownEvent, today: LocalDate): WidgetEventContent {
        val value = EventCountResolver.resolve(event, today)
        val unitRes = when (checkNotNull(value.countdownStatus)) {
            CountdownStatus.TODAY -> R.string.status_today
            CountdownStatus.DONE -> if (value.magnitude == 1L) R.string.widget_day_ago else R.string.widget_days_ago
            else -> R.string.widget_days_left
        }

        return WidgetEventContent(
            iconRes = EventIconPresentation.drawableRes(event.icon),
            title = event.title,
            date = value.displayDate,
            countText = value.magnitude.toString(),
            unitRes = unitRes,
            status = value.countdownStatus,
            countUpState = value.countUpState,
        )
    }
}
