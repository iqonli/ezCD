package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.*
import java.time.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class EventSortOrderTest {
    private val today=LocalDate.of(2026,10,23)
    private fun event(id: String,name: String,offset: Long,created: Long,include: Boolean=false)=CountdownEvent(id,name,today.plusDays(offset),EventType.EVENT,createdAt=Instant.ofEpochSecond(created),includeToday=include)
    private val a=event("a","Apple",3,1)
    private val b=event("b","Banana",-10,2)
    private val c=event("c","Cherry",1,3)
    private fun ids(order: EventSortOrder,values: List<CountdownEvent> = listOf(b,c,a))=order.sorted(values,today,Locale.ENGLISH).map { it.id }
    @Test fun allEightOrdersAreAvailableAndIndependent() {
        assertEquals(listOf("a","b","c"),ids(EventSortOrder.TITLE_ASC))
        assertEquals(listOf("c","b","a"),ids(EventSortOrder.TITLE_DESC))
        assertEquals(listOf("b","c","a"),ids(EventSortOrder.DATE_ASC))
        assertEquals(listOf("a","c","b"),ids(EventSortOrder.DATE_DESC))
        assertEquals(listOf("c","a","b"),ids(EventSortOrder.COUNT_ASC))
        assertEquals(listOf("b","a","c"),ids(EventSortOrder.COUNT_DESC))
        assertEquals(listOf("c","b","a"),ids(EventSortOrder.CREATED_DESC))
        assertEquals(listOf("a","b","c"),ids(EventSortOrder.CREATED_ASC))
    }
    @Test fun countOrderUsesDisplayedMagnitudeIncludingIncludeToday() {
        val first=event("x","X",0,0,true); val second=event("y","Y",0,0,false)
        assertEquals(listOf("y","x"),ids(EventSortOrder.COUNT_ASC,listOf(first,second)))
    }
    @Test fun dateOrderUsesDisplayedNextOccurrenceForRepeatingEvents() {
        val recurring=event("r","Repeat",-7,0).copy(repeatRule=RepeatRule.WEEKLY)
        assertEquals(listOf("r","c","a"),ids(EventSortOrder.DATE_ASC,listOf(c,a,recurring)))
    }
    @Test fun tiesRemainDeterministicAcrossSourceOrderAndUnknownSettingsFallBack() {
        val clone=a.copy(id="z")
        EventSortOrder.entries.forEach { order -> assertEquals(ids(order,listOf(a,clone)),ids(order,listOf(clone,a))) }
        assertEquals(EventSortOrder.DATE_ASC,EventSortOrder.fromStorage("future-option"))
    }
}
