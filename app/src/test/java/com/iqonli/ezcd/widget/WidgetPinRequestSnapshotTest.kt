package com.iqonli.ezcd.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetPinRequestSnapshotTest {
    @Test
    fun callbackDataRoundTripPreservesExactRequest() {
        val snapshot = WidgetPinRequestSnapshot.create(
            eventId = "trip / buenos aires?2026",
            style = WidgetStyleSelection(
                appearance = WidgetAppearance.DARK,
                background = WidgetBackground.MONOGRAM,
            ),
            requestToken = "request / token?one",
        )

        assertEquals(
            snapshot,
            WidgetPinRequestSnapshot.fromCallbackData(snapshot.callbackData()),
        )
    }

    @Test
    fun differentRequestsRemainIsolated() {
        val first = WidgetPinRequestSnapshot.create(
            eventId = "event-a",
            style = WidgetStyleSelection(
                appearance = WidgetAppearance.LIGHT,
                background = WidgetBackground.FOREST,
            ),
            requestToken = "request-a",
        )
        val second = WidgetPinRequestSnapshot.create(
            eventId = "event-b",
            style = WidgetStyleSelection(
                appearance = WidgetAppearance.DARK,
                background = WidgetBackground.PULSE,
            ),
            requestToken = "request-b",
        )

        assertNotEquals(first.callbackData(), second.callbackData())
        assertEquals(first, WidgetPinRequestSnapshot.fromCallbackData(first.callbackData()))
        assertEquals(second, WidgetPinRequestSnapshot.fromCallbackData(second.callbackData()))
    }

    @Test
    fun malformedOrIncompleteCallbackDataIsRejected() {
        assertNull(WidgetPinRequestSnapshot.fromCallbackData(null))
        assertNull(WidgetPinRequestSnapshot.fromCallbackData(""))
        assertNull(WidgetPinRequestSnapshot.fromCallbackData("https://widget/pin/request"))
        assertNull(
            WidgetPinRequestSnapshot.fromCallbackData(
                "ezcd://widget/pin/request?event=event-a&appearance=light",
            ),
        )
    }
}
