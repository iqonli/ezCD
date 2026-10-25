// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.ui

import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.ReminderOption
import java.time.Instant

/** A copy is a new unsaved record, never a second identity for the source. */
internal object EventCopyDraft {
    fun create(source: CountdownEvent, id: String, createdAt: Instant): CountdownEvent {
        require(id.isNotBlank() && id != source.id)
        return source.copy(id = id, createdAt = createdAt, reminder = ReminderOption.OFF)
    }
}
