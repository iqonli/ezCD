package com.iqonli.ezcd.countdown

import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.RepeatRule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class ArrivalStageTest {
    private val today = LocalDate.of(2026, 12, 31)
    private val event = CountdownEvent(id = "test", title = "A date", date = today,
        type = EventType.EVENT, createdAt = Instant.EPOCH)

    @Test fun finalDaysFollowResolvedCountdownAndClearAfterArrival() {
        val expected = listOf(ArrivalStage.NONE, ArrivalStage.THREE_DAYS, ArrivalStage.TWO_DAYS,
            ArrivalStage.TOMORROW, ArrivalStage.TODAY, ArrivalStage.NONE)
        assertEquals(expected, listOf(4L, 3L, 2L, 1L, 0L, -1L).map {
            ArrivalStage.from(EventCountResolver.resolve(event.copy(date = today.plusDays(it)), today).countdownStatus)
        })
    }

    @Test fun legacyModeDoesNotChangeCalendarStage() {
        for (offset in -4L..4L) assertEquals(ArrivalStage.from(CountdownCalculator.value(today, today.plusDays(offset)).status), ArrivalStage.from(
            EventCountResolver.resolve(event.copy(date = today.plusDays(offset), countMode = CountMode.COUNT_UP), today).countdownStatus))
    }

    @Test fun recurringOccurrencesUseTheirNextDateRatherThanTheOriginalDate() {
        val yearly = event.copy(date = LocalDate.of(2020, 1, 1), repeatRule = RepeatRule.YEARLY)
        assertEquals(ArrivalStage.TOMORROW, ArrivalStage.from(EventCountResolver.resolve(yearly, today).countdownStatus))
        assertEquals(ArrivalStage.TODAY, ArrivalStage.from(EventCountResolver.resolve(yearly, today.plusDays(1)).countdownStatus))
        assertEquals(ArrivalStage.NONE, ArrivalStage.from(EventCountResolver.resolve(yearly, today.plusDays(2)).countdownStatus))
    }
}
