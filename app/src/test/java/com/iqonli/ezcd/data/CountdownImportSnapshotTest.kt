package com.iqonli.ezcd.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CountdownImportSnapshotTest {
    @Test
    fun snapshotKeepsTheExactApprovedPayloadUntilCleared() {
        val directory = Files.createTempDirectory("ezcd-import").toFile()
        val snapshotFile = directory.resolve("snapshot.json")
        val snapshot = CountdownImportSnapshot(snapshotFile)
        val approved = """{"schemaVersion":5,"events":[]}"""

        try {
            snapshot.write(approved)

            assertTrue(snapshot.exists())
            assertEquals(approved, snapshot.readPayload())

            snapshot.clear()
            assertFalse(snapshot.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun replacingSnapshotReplacesTheWholePayload() {
        val directory = Files.createTempDirectory("ezcd-import-replace").toFile()
        val snapshot = CountdownImportSnapshot(directory.resolve("snapshot.json"))

        try {
            snapshot.write("first")
            snapshot.write("second")

            assertEquals("second", snapshot.readPayload())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun clearingOneSnapshotDoesNotTouchAnotherImportPayload() {
        val directory = Files.createTempDirectory("ezcd-import-isolation").toFile()
        val first = CountdownImportSnapshot(directory.resolve("snapshot-a.json"))
        val second = CountdownImportSnapshot(directory.resolve("snapshot-b.json"))

        try {
            first.write("first")
            second.write("second")

            first.clear()

            assertFalse(first.exists())
            assertTrue(second.exists())
            assertEquals("second", second.readPayload())
        } finally {
            directory.deleteRecursively()
        }
    }
}
