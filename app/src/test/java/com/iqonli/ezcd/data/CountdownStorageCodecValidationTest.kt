package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.EventType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDate

class CountdownStorageCodecValidationTest {
    @Test
    fun duplicateIdsAreRejected() {
        val error = assertThrows(CountdownDataException::class.java) {
            CountdownValidation.validateStoredEvents(
                listOf(
                    event("same", "First"),
                    event("same", "Second"),
                ),
            )
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun blankIdsAndTitlesAreRejected() {
        listOf(
            event("", "Valid title"),
            event("valid-id", "   "),
        ).forEach { invalidEvent ->
            val error = assertThrows(CountdownDataException::class.java) {
                CountdownValidation.validateStoredEvents(listOf(invalidEvent))
            }
            assertEquals(CountdownDataProblem.CORRUPT, error.problem)
        }
    }

    @Test
    fun historicalOversizedFieldsRemainImportableForSelfBackupCompatibility() {
        val legacyEvent = event(
            "i".repeat(512),
            "t".repeat(CountdownValidation.MAX_TITLE_LENGTH + 1),
        )

        CountdownValidation.validateStoredEvents(listOf(legacyEvent))
        CountdownValidation.validateImportedEvents(listOf(legacyEvent))
        assertFalse(CountdownValidation.isTitleWithinLimit(legacyEvent.title))
    }

    @Test
    fun datesOutsideTheProductDomainAreRejected() {
        listOf(
            LocalDate.of(1899, 12, 31),
            LocalDate.of(2101, 1, 1),
        ).forEach { invalidDate ->
            val error = assertThrows(CountdownDataException::class.java) {
                CountdownValidation.validateImportedEvents(
                    listOf(event("event", "Title", invalidDate)),
                )
            }
            assertEquals(CountdownDataProblem.CORRUPT, error.problem)
        }

        CountdownValidation.validateImportedEvents(
            listOf(event("min", "Minimum", LocalDate.of(1900, 1, 1))),
        )
        CountdownValidation.validateImportedEvents(
            listOf(event("max", "Maximum", LocalDate.of(2100, 12, 31))),
        )
    }

    @Test
    fun payloadSizeValidationRejectsOutputAboveStorageLimit() {
        CountdownValidation.validatePayloadSize("a".repeat(CountdownValidation.MAX_PAYLOAD_BYTES))

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownValidation.validatePayloadSize("a".repeat(CountdownValidation.MAX_PAYLOAD_BYTES + 1))
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun oversizedPayloadIsRejectedBeforeParsing() {
        val payload = " ".repeat(CountdownValidation.MAX_PAYLOAD_BYTES + 1)

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.decode(payload)
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun oversizedStreamIsRejectedBeforeMaterializingWholePayload() {
        val input = ByteArrayInputStream(
            ByteArray(CountdownValidation.MAX_PAYLOAD_BYTES + 1) { ' '.code.toByte() },
        )

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.readUtf8Payload(input)
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun invalidUtf8IsRejectedInsteadOfBeingSilentlyReplaced() {
        val input = ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28))

        val error = assertThrows(CountdownDataException::class.java) {
            CountdownStorageCodec.readUtf8Payload(input)
        }

        assertEquals(CountdownDataProblem.CORRUPT, error.problem)
    }

    @Test
    fun currentEditorTitleLimitRemainsIndependentFromBackupCompatibility() {
        assertTrue(CountdownValidation.isTitleWithinLimit("t".repeat(CountdownValidation.MAX_TITLE_LENGTH)))
        assertFalse(
            CountdownValidation.isTitleWithinLimit(
                "t".repeat(CountdownValidation.MAX_TITLE_LENGTH + 1),
            ),
        )
    }

    private fun event(
        id: String,
        title: String,
        date: LocalDate = LocalDate.of(2026, 12, 1),
    ): CountdownEvent = CountdownEvent(
        id = id,
        title = title,
        date = date,
        type = EventType.TRIP,
        createdAt = Instant.parse("2026-08-23T12:00:00Z"),
    )
}
