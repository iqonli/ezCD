package com.iqonli.ezcd.countdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArrivalMoodTest {
    @Test
    fun milestoneMarkersEscalateAndCelebrate() {
        assertEquals("🙂", ArrivalMood.marker(CountdownStatus.THREE_DAYS))
        assertEquals("😬", ArrivalMood.marker(CountdownStatus.TWO_DAYS))
        assertEquals("😱", ArrivalMood.marker(CountdownStatus.TOMORROW))
        assertEquals("🎉", ArrivalMood.marker(CountdownStatus.TODAY))
    }

    @Test
    fun normalStatesDoNotShowMarker() {
        assertNull(ArrivalMood.marker(CountdownStatus.FUTURE))
        assertNull(ArrivalMood.marker(CountdownStatus.DONE))
    }
}
