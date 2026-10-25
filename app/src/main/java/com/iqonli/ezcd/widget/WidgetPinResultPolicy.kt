// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

internal object WidgetPinResultPolicy {
    fun shouldApply(
        validWidgetId: Boolean,
        eventExists: Boolean,
        existing: WidgetConfiguration?,
    ): Boolean = validWidgetId && eventExists && existing == null

    fun matchesSnapshot(
        existing: WidgetConfiguration,
        snapshot: WidgetPinRequestSnapshot,
    ): Boolean =
        existing.eventSelection == WidgetEventSelection.FIXED &&
            existing.eventId == snapshot.eventId &&
            existing.appearance == snapshot.style.appearance &&
            existing.background == snapshot.style.background
}
