package com.iqonli.ezcd.data
import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.widget.*
import com.iqonli.ezcd.countdown.EventSortOrder
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import java.io.File
import java.time.*
import java.util.UUID
class BackupArchiveCodecTest {
    private fun layout()=WidgetLayoutCodec.decode(listOf(File("src/main/assets/widget-layout-default.json"),File("app/src/main/assets/widget-layout-default.json")).first { it.isFile }.readText())
    private fun event(id: String,created: String)=CountdownEvent(id,id,LocalDate.of(2026,10,24),EventType.EVENT,createdAt=Instant.parse(created))
    @Test fun completeArchivePreservesEventAndLibraryCreationChronologyAndAlpha() {
        val events=listOf(event("later","2026-10-20T01:00:00Z"),event("earlier","2025-01-02T03:04:05.123456789Z"))
        val doc=layout();val small=doc.layouts.getValue("small").copy(background="#12345678")
        val entry=WidgetLibraryEntry(UUID.randomUUID().toString(),Instant.parse("2025-03-04T00:00:00Z"),Instant.now(),44,events.last(),doc.copy(layouts=doc.layouts+("small" to small)))
        val data=BackupArchive(CountdownStorageCodec.encode(events),listOf(entry),EventSortOrder.CREATED_DESC)
        val restored=BackupArchiveCodec.decode(BackupArchiveCodec.encode(data))
        assertEquals(events,CountdownStorageCodec.decode(restored.eventPayload));assertEquals(listOf(entry),restored.library);assertEquals(data.sort,restored.sort)
    }
    @Test fun legacyEventBackupsRemainReadableAndDoNotRequestLibraryDeletion() {
        val payload=CountdownStorageCodec.encode(listOf(event("e","2024-01-01T00:00:00Z")))
        val archive=BackupArchiveCodec.decode(payload)
        assertNull(archive.library);assertNull(archive.sort);assertEquals(payload,archive.eventPayload)
    }
    @Test fun damagedLayoutIsRejectedBeforeAnArchiveCanBeApplied() {
        val entry=WidgetLibraryEntry(UUID.randomUUID().toString(),Instant.EPOCH,Instant.EPOCH,null,null,layout())
        val raw=JSONObject(BackupArchiveCodec.encode(BackupArchive(CountdownStorageCodec.encode(emptyList()),listOf(entry),EventSortOrder.TITLE_ASC)))
        raw.getJSONObject("widgetLibrary").getJSONArray("entries").getJSONObject(0).getJSONObject("layout").put("version",999)
        assertThrows(Exception::class.java) { BackupArchiveCodec.decode(raw.toString()) }
    }
    @Test fun duplicateRecordIdsAndInvalidTimesAreRejected() {
        val entry=WidgetLibraryEntry(UUID.randomUUID().toString(),Instant.EPOCH,Instant.EPOCH,null,null,layout())
        assertThrows(Exception::class.java) { WidgetLibraryCodec.decode(WidgetLibraryCodec.encode(WidgetLibraryDocument(listOf(entry,entry)))) }
        val root=JSONObject(WidgetLibraryCodec.encode(WidgetLibraryDocument(listOf(entry))))
        root.getJSONArray("entries").getJSONObject(0).put("createdAt","yesterday")
        assertThrows(Exception::class.java) { WidgetLibraryCodec.decode(root.toString()) }
    }
    @Test fun libraryNameSizeAndTextPoolRoundTripWithFullBackup() {
        val entry=WidgetLibraryEntry(UUID.randomUUID().toString(),Instant.EPOCH,Instant.EPOCH,null,null,layout(),displayName="我的透明排版",previewSize="large")
        val pool=mapOf("date" to "%yyyy%年%mm%月%dd%日","left" to "还有","today" to "当日","passed" to "已用")
        val archive=BackupArchive(CountdownStorageCodec.encode(emptyList()),listOf(entry),EventSortOrder.CREATED_DESC,pool)
        val result=BackupArchiveCodec.decode(BackupArchiveCodec.encode(archive))
        assertEquals(entry,result.library!!.single());assertEquals(pool,result.textPool)
    }
    @Test fun oldLibraryEntriesDefaultToUnnamedSmallAndInvalidSizeIsRejected() {
        val entry=WidgetLibraryEntry(UUID.randomUUID().toString(),Instant.EPOCH,Instant.EPOCH,null,null,layout())
        val root=JSONObject(WidgetLibraryCodec.encode(WidgetLibraryDocument(listOf(entry))))
        val item=root.getJSONArray("entries").getJSONObject(0);item.remove("displayName");item.remove("previewSize")
        assertEquals(entry,WidgetLibraryCodec.decode(root.toString()).entries.single())
        item.put("previewSize","huge")
        assertThrows(Exception::class.java) { WidgetLibraryCodec.decode(root.toString()) }
    }
    @Test fun legacyFullBackupLeavesExistingTextPoolUntouched() {
        val archive=BackupArchive(CountdownStorageCodec.encode(emptyList()),emptyList(),EventSortOrder.CREATED_DESC)
        assertNull(BackupArchiveCodec.decode(BackupArchiveCodec.encode(archive)).textPool)
        val raw=JSONObject(BackupArchiveCodec.encode(archive)).put("textPool",JSONObject().put("left",123))
        assertThrows(Exception::class.java) { BackupArchiveCodec.decode(raw.toString()) }
    }
}
