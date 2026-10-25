package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.notification.ArrivalNotificationPolicy
import java.time.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ColorAndTimeStorageTest {
    private val event = CountdownEvent("id","Event",LocalDate.of(2026,10,24),EventType.EVENT,createdAt = Instant.EPOCH,
        includeToday = true,cardColor = 0xFF0022AA.toInt(),reminderMinuteOfDay = 18*60+45)
    @Test fun newFieldsSurviveBackupRoundTrip() {
        val payload = CountdownStorageCodec.encode(listOf(event))
        assertEquals(11,JSONObject(payload).getInt("schemaVersion"))
        assertEquals(event,CountdownStorageCodec.decode(payload).single())
        assertEquals("0022AA",JSONObject(payload).getJSONArray("events").getJSONObject(0).getString("cardColor"))
    }
    @Test fun explicitWhiteAndDefaultWhiteRemainDistinctAcrossBackupAndAutosave() {
        val explicit=event.copy(cardColor=-1,cardColorDefault=false,countColorDefault=false)
        val default=explicit.copy(cardColorDefault=true,countColorDefault=true)
        assertEquals(explicit,CountdownStorageCodec.decode(CountdownStorageCodec.encode(listOf(explicit))).single())
        assertEquals(default,CountdownStorageCodec.decode(CountdownStorageCodec.encode(listOf(default))).single())
        assertNotEquals(EventRevision.of(explicit),EventRevision.of(default))
        val old=JSONObject(CountdownStorageCodec.encode(listOf(explicit))).put("schemaVersion",10)
        val node=old.getJSONArray("events").getJSONObject(0); node.remove("cardColorDefault");node.remove("countColorDefault")
        assertFalse(CountdownStorageCodec.decode(old.toString()).single().cardColorDefault)
    }
    @Test fun schemaEightDefaultsToWhiteAndNineWithoutChangingExistingCountRules() {
        val json = JSONObject(CountdownStorageCodec.encode(listOf(event))).put("schemaVersion",8)
        json.getJSONArray("events").getJSONObject(0).remove("cardColor")
        json.getJSONArray("events").getJSONObject(0).remove("reminderMinuteOfDay")
        val restored = CountdownStorageCodec.decode(json.toString()).single()
        assertEquals(-1,restored.cardColor); assertEquals(9*60,restored.reminderMinuteOfDay)
        assertTrue(restored.includeToday); assertEquals(event.date,restored.date)
    }
    @Test fun invalidNewFieldsCannotEnterStorage() {
        for ((key,value) in listOf("cardColor" to "0022AAFF","cardColor" to 123,"reminderMinuteOfDay" to -1,
            "reminderMinuteOfDay" to 1440,"reminderMinuteOfDay" to 540.5,"reminderMinuteOfDay" to "540")) {
            val json = JSONObject(CountdownStorageCodec.encode(listOf(event)))
            json.getJSONArray("events").getJSONObject(0).put(key,value)
            assertThrows(CountdownDataException::class.java) { CountdownStorageCodec.decode(json.toString()) }
        }
        assertThrows(CountdownDataException::class.java) { CountdownStorageCodec.encode(listOf(event.copy(cardColor = 0x112233))) }
    }
    @Test fun autosaveRevisionTracksBothNewFields() {
        assertNotEquals(EventRevision.of(event),EventRevision.of(event.copy(cardColor = -1)))
        assertNotEquals(EventRevision.of(event),EventRevision.of(event.copy(reminderMinuteOfDay = 9*60)))
    }
    @Test fun colorChangesDoNotResendNotificationsButTimeChangesReschedule() {
        assertFalse(ArrivalNotificationPolicy.shouldResetDeliveryState(event,event.copy(cardColor = -1)))
        assertTrue(ArrivalNotificationPolicy.shouldResetDeliveryState(event,event.copy(reminderMinuteOfDay = 9*60)))
    }
}
