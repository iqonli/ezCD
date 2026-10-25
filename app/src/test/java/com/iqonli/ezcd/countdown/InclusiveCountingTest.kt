package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.data.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class InclusiveCountingTest {
    private val today = LocalDate.of(2026, 10, 23)
    private fun event(date: LocalDate, mode: CountMode, inclusive: Boolean) = CountdownEvent(
        "id", "event", date, EventType.EVENT, createdAt = Instant.EPOCH,
        countMode = mode, includeToday = inclusive)
    @Test fun countUpIncludesOnlyStartedDays() {
        assertEquals(0L, EventCountResolver.resolve(event(today, CountMode.COUNT_UP, false), today).magnitude)
        assertEquals(1L, EventCountResolver.resolve(event(today, CountMode.COUNT_UP, true), today).magnitude)
        assertEquals(2L, EventCountResolver.resolve(event(today.minusDays(1), CountMode.COUNT_UP, true), today).magnitude)
        assertEquals(2L, EventCountResolver.resolve(event(today.plusDays(1), CountMode.COUNT_UP, true), today).magnitude)
    }
    @Test fun inclusiveCountdownKeepsTargetDateAndAddsCurrentDay() {
        val tomorrow = event(today.plusDays(1), CountMode.COUNT_DOWN, true)
        val value = EventCountResolver.resolve(tomorrow, today)
        assertEquals(2L, value.magnitude)
        assertEquals(tomorrow.date, value.displayDate)
        assertEquals(1L, EventCountResolver.resolve(tomorrow, tomorrow.date).magnitude)
        assertEquals(2L, EventCountResolver.resolve(tomorrow, tomorrow.date.plusDays(1)).magnitude)
    }
    @Test fun schemaEightRoundTripsAndSchemaSevenKeepsOldConvention() {
        val source = event(today, CountMode.COUNT_UP, true)
        val payload = CountdownStorageCodec.encode(listOf(source))
        assertEquals(source, CountdownStorageCodec.decode(payload).single())
        val old = org.json.JSONObject(payload).put("schemaVersion", 7)
        old.getJSONArray("events").getJSONObject(0).remove("includeToday")
        assertFalse(CountdownStorageCodec.decode(old.toString()).single().includeToday)
    }
    @Test fun malformedInclusiveFlagIsRejectedAndRevisionChanges() {
        val source = event(today, CountMode.COUNT_UP, true)
        assertNotEquals(EventRevision.of(source), EventRevision.of(source.copy(includeToday = false)))
        val malformed = org.json.JSONObject(CountdownStorageCodec.encode(listOf(source)))
        malformed.getJSONArray("events").getJSONObject(0).put("includeToday", "true")
        assertThrows(CountdownDataException::class.java) { CountdownStorageCodec.decode(malformed.toString()) }
    }
    @Test fun inclusiveMonthlyRecurrenceUsesTheResolvedOccurrence() {
        val source = event(LocalDate.of(2026, 1, 31), CountMode.COUNT_DOWN, true).copy(repeatRule = RepeatRule.MONTHLY)
        val value = EventCountResolver.resolve(source, LocalDate.of(2026, 2, 27))
        assertEquals(LocalDate.of(2026, 2, 28), value.displayDate)
        assertEquals(2L, value.magnitude)
    }
}
