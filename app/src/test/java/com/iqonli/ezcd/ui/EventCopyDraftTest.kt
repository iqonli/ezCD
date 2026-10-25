package com.iqonli.ezcd.ui

import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventIcon
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.ReminderOption
import com.iqonli.ezcd.model.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class EventCopyDraftTest {
    private val source = CountdownEvent(id = "source", title = "My event", date = LocalDate.of(2026, 10, 5),
        type = EventType.CUSTOM, icon = EventIcon.BOOK, reminder = ReminderOption.ONE_DAY,
        createdAt = Instant.EPOCH, repeatRule = RepeatRule.YEARLY)

    @Test fun copyPreservesFieldsButHasANewIdentityAndNoReminder() {
        val created = Instant.parse("2026-09-30T00:00:00Z")
        assertEquals(source.copy(id = "copy", createdAt = created, reminder = ReminderOption.OFF),
            EventCopyDraft.create(source, "copy", created))
        assertEquals(ReminderOption.ONE_DAY, source.reminder)
    }

    @Test fun countUpAndHistoricalTitlesAreNotNormalized() {
        val historical = source.copy(title = "Long title ".repeat(40), countMode = CountMode.COUNT_UP,
            repeatRule = RepeatRule.NONE, reminder = ReminderOption.OFF)
        val copy = EventCopyDraft.create(historical, "copy", Instant.EPOCH)
        assertEquals(historical.title, copy.title)
        assertEquals(historical.date, copy.date)
        assertEquals(CountMode.COUNT_UP, copy.countMode)
        assertEquals(EventIcon.BOOK, copy.icon)
    }

    @Test(expected = IllegalArgumentException::class)
    fun copyingTheSourceIdentityIsRejected() { EventCopyDraft.create(source, source.id, Instant.EPOCH) }
}
