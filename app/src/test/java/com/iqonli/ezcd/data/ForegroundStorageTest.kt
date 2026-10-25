package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.*
import java.time.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ForegroundStorageTest {
    private val event=CountdownEvent("e","Name",LocalDate.of(2026,10,23),EventType.EVENT,createdAt=Instant.EPOCH,countColor=0xFF6A1599.toInt())
    @Test fun foregroundAndBackgroundAreIndependentAndBackedUp() {
        val saved=event.copy(cardColor=0xFFF0DDEE.toInt())
        assertEquals(saved,CountdownStorageCodec.decode(CountdownStorageCodec.encode(listOf(saved))).single())
        assertNotEquals(EventRevision.of(event),EventRevision.of(event.copy(countColor=OpaqueColor.BLUE)))
    }
    @Test fun versionNineUpgradesToClassicBlueForeground() {
        val root=JSONObject(CountdownStorageCodec.encode(listOf(event))).put("schemaVersion",9)
        root.getJSONArray("events").getJSONObject(0).remove("countColor")
        assertEquals(OpaqueColor.BLUE,CountdownStorageCodec.decode(root.toString()).single().countColor)
    }
    @Test fun invalidForegroundCannotEnterBackups() {
        val root=JSONObject(CountdownStorageCodec.encode(listOf(event)))
        root.getJSONArray("events").getJSONObject(0).put("countColor","FF000080")
        assertThrows(CountdownDataException::class.java) { CountdownStorageCodec.decode(root.toString()) }
        assertThrows(CountdownDataException::class.java) { CountdownStorageCodec.encode(listOf(event.copy(countColor=0))) }
    }
}
