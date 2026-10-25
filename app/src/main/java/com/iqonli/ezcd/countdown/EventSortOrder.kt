// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.CountdownEvent
import java.text.Collator
import java.time.LocalDate
import java.util.Locale

enum class EventSortOrder {
    TITLE_ASC, TITLE_DESC, DATE_ASC, DATE_DESC, COUNT_ASC, COUNT_DESC, CREATED_DESC, CREATED_ASC;
    fun sorted(events: List<CountdownEvent>,today: LocalDate,locale: Locale): List<CountdownEvent> {
        val collator=Collator.getInstance(locale).apply { strength=Collator.SECONDARY }
        val values=events.associate { it.id to EventCountResolver.resolve(it,today) }
        val comparator=Comparator<CountdownEvent> { a,b ->
            val primary=when(this) {
                TITLE_ASC -> collator.compare(a.title,b.title)
                TITLE_DESC -> collator.compare(b.title,a.title)
                DATE_ASC -> values.getValue(a.id).displayDate.compareTo(values.getValue(b.id).displayDate)
                DATE_DESC -> values.getValue(b.id).displayDate.compareTo(values.getValue(a.id).displayDate)
                COUNT_ASC -> values.getValue(a.id).magnitude.compareTo(values.getValue(b.id).magnitude)
                COUNT_DESC -> values.getValue(b.id).magnitude.compareTo(values.getValue(a.id).magnitude)
                CREATED_DESC -> b.createdAt.compareTo(a.createdAt)
                CREATED_ASC -> a.createdAt.compareTo(b.createdAt)
            }
            if (primary != 0) primary else a.id.compareTo(b.id)
        }
        return events.sortedWith(comparator)
    }
    companion object { fun fromStorage(raw: String?): EventSortOrder = entries.firstOrNull { it.name == raw } ?: DATE_ASC }
}
