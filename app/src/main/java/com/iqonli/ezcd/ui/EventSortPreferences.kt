// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import android.content.Context
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.*
import com.iqonli.ezcd.model.CountdownEvent

internal class EventSortPreferences(private val context: Context) {
    private val prefs=context.getSharedPreferences("event_list_order",Context.MODE_PRIVATE)
    fun get()=EventSortOrder.fromStorage(prefs.getString("order",null))
    fun save(order: EventSortOrder) { prefs.edit().putString("order",order.name).apply() }
    fun saveDurable(order: EventSortOrder) { check(prefs.edit().putString("order",order.name).commit()) }
    fun sorted(events: List<CountdownEvent>)=get().sorted(events,CountdownTime.snapshot().today,context.resources.configuration.locales[0])
    fun labels()=listOf(R.string.ez_sort_az,R.string.ez_sort_za,R.string.ez_sort_date_asc,R.string.ez_sort_date_desc,
        R.string.ez_sort_count_asc,R.string.ez_sort_count_desc,R.string.ez_sort_newest,R.string.ez_sort_oldest).map { context.getString(it) }
    fun eventLabel(event: CountdownEvent): String = context.getString(R.string.ez_event_days,event.title,
        EventCountResolver.resolve(event,CountdownTime.snapshot().today).magnitude.toString())
}
