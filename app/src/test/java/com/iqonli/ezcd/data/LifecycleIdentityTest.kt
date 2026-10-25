package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventType
import com.iqonli.ezcd.model.ReminderOption
import com.iqonli.ezcd.model.RepeatRule
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate

class LifecycleIdentityTest {
    private val event = CountdownEvent("id", "Original", LocalDate.of(2026, 12, 1),
        EventType.EVENT, createdAt = Instant.parse("2026-01-01T00:00:00Z"))

    @Test fun revisionDetectsEveryPersistedFieldAndIsStableForCopies() {
        val revision = EventRevision.of(event)
        assertEquals(revision, EventRevision.of(event.copy()))
        listOf(event.copy(id = "other"), event.copy(title = "Other"),
            event.copy(date = event.date.plusDays(1)), event.copy(type = EventType.TRIP),
            event.copy(icon = com.iqonli.ezcd.model.EventIcon.defaultFor(EventType.BIRTHDAY)),
            event.copy(reminder = ReminderOption.ON_DAY), event.copy(createdAt = event.createdAt.plusSeconds(1)),
            event.copy(repeatRule = RepeatRule.YEARLY), event.copy(countMode = CountMode.COUNT_UP)).forEach {
            assertNotEquals(revision, EventRevision.of(it))
        }
    }

    @Test fun revisionHandlesHistoricalLongStringsAndAmbiguousSeparators() {
        assertEquals(64, EventRevision.of(event.copy(id = "I".repeat(100_000), title = "é".repeat(100_000))).length)
        assertNotEquals(EventRevision.ofFields("a|b", "c"), EventRevision.ofFields("a", "b|c"))
        assertNotEquals(EventRevision.ofFields("ab", "c"), EventRevision.ofFields("a", "bc"))
    }

    @Test fun processRestorationNeverReplaysWorkAndPreservesTheExportFirstPicker() {
        for (stage in BackupFlow.Stage.entries) {
            val recovered = stage.afterProcessDeath(true)
            assertFalse(recovered in setOf(BackupFlow.Stage.APPLYING, BackupFlow.Stage.VALIDATING,
                BackupFlow.Stage.EXPORTING, BackupFlow.Stage.EXPORTING_FIRST))
        }
        assertEquals(BackupFlow.Stage.PICK_EXPORT_FIRST,
            BackupFlow.Stage.PICK_EXPORT_FIRST.afterProcessDeath(true))
        assertEquals(BackupFlow.Stage.CONFIRM, BackupFlow.Stage.EXPORTING_FIRST.afterProcessDeath(true))
        assertEquals(BackupFlow.Stage.IDLE, BackupFlow.Stage.PICK_EXPORT_FIRST.afterProcessDeath(false))
        assertEquals(BackupFlow.Stage.IDLE, BackupFlow.Stage.CONFIRM.afterProcessDeath(false))
    }

    @Test fun approvalIsConsumedBeforeImportAndCannotBeRecoveredFromStaleUi() {
        val directory = Files.createTempDirectory("ezcd-claim").toFile()
        val first = CountdownImportSnapshot(directory.resolve("first.json"))
        val other = CountdownImportSnapshot(directory.resolve("other.json"))
        try {
            first.write("approved")
            other.write("independent")
            assertEquals("approved", first.claimPayload())
            assertFalse(first.exists())
            assertTrue(directory.resolve("first.json.applying").isFile)
            assertTrue(runCatching { first.claimPayload() }.isFailure)
            first.clear()
            assertFalse(directory.resolve("first.json.applying").exists())
            assertEquals("independent", other.readPayload())
        } finally { directory.deleteRecursively() }
    }
}
