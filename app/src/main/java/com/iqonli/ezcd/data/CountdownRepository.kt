// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.data

import android.content.Context
import android.util.AtomicFile
import com.iqonli.ezcd.model.CountdownEvent
import java.io.File
import java.io.FileNotFoundException

sealed interface CountdownLoadResult {
    data class Success(val events: List<CountdownEvent>) : CountdownLoadResult
    data class Failure(val problem: CountdownDataProblem) : CountdownLoadResult
}

enum class CountdownMutationResult {
    APPLIED,
    CONFLICT,
}

class CountdownRepository(context: Context) {
    private val baseFile = File(context.filesDir, FILE_NAME)
    private val atomicFile = AtomicFile(baseFile)

    fun loadResult(): CountdownLoadResult = synchronized(FILE_LOCK) {
        loadResultLocked()
    }

    fun exportPayload(): String = synchronized(FILE_LOCK) {
        CountdownStorageCodec.encode(loadEventsOrThrowLocked())
    }

    fun previewImport(payload: String): Int = CountdownStorageCodec.decodeForImport(payload).size

    fun importPayload(payload: String): Int = synchronized(FILE_LOCK) {
        val events = CountdownStorageCodec.decodeForImport(payload)
        saveLocked(events)
        events.size
    }

    fun save(events: List<CountdownEvent>) = synchronized(FILE_LOCK) {
        saveLocked(events)
    }

    fun saveEvent(
        expectedEvent: CountdownEvent?,
        replacement: CountdownEvent,
    ): CountdownMutationResult = saveEventRevision(expectedEvent?.let(EventRevision::of), replacement)

    fun saveEventRevision(
        expectedRevision: String?,
        replacement: CountdownEvent,
    ): CountdownMutationResult = synchronized(FILE_LOCK) {
        val events = loadEventsOrThrowLocked().toMutableList()
        val currentIndex = events.indexOfFirst { it.id == replacement.id }

        if (expectedRevision == null) {
            if (currentIndex >= 0) return@synchronized CountdownMutationResult.CONFLICT
            events.add(replacement)
        } else {
            if (currentIndex < 0 || EventRevision.of(events[currentIndex]) != expectedRevision) {
                return@synchronized CountdownMutationResult.CONFLICT
            }
            events[currentIndex] = replacement
        }

        saveLocked(events)
        CountdownMutationResult.APPLIED
    }

    fun deleteEvent(expectedEvent: CountdownEvent): CountdownMutationResult =
        deleteEventRevision(expectedEvent.id, EventRevision.of(expectedEvent))

    fun deleteEventRevision(id: String, expectedRevision: String): CountdownMutationResult = synchronized(FILE_LOCK) {
        val events = loadEventsOrThrowLocked().toMutableList()
        val currentIndex = events.indexOfFirst { it.id == id }
        if (currentIndex < 0 || EventRevision.of(events[currentIndex]) != expectedRevision) {
            return@synchronized CountdownMutationResult.CONFLICT
        }

        events.removeAt(currentIndex)
        saveLocked(events)
        CountdownMutationResult.APPLIED
    }

    private fun loadResultLocked(): CountdownLoadResult = try {
        val payload = atomicFile.openRead().use(CountdownStorageCodec::readUtf8Payload)
        CountdownLoadResult.Success(CountdownStorageCodec.decode(payload))
    } catch (_: FileNotFoundException) {
        if (storageArtifactsExist()) {
            CountdownLoadResult.Failure(CountdownDataProblem.CORRUPT)
        } else {
            CountdownLoadResult.Success(emptyList())
        }
    } catch (error: CountdownDataException) {
        CountdownLoadResult.Failure(error.problem)
    } catch (_: Exception) {
        CountdownLoadResult.Failure(CountdownDataProblem.CORRUPT)
    }

    private fun loadEventsOrThrowLocked(): List<CountdownEvent> = when (val result = loadResultLocked()) {
        is CountdownLoadResult.Success -> result.events
        is CountdownLoadResult.Failure -> throw CountdownDataException(result.problem)
    }

    private fun saveLocked(events: List<CountdownEvent>) {
        val payload = CountdownStorageCodec.encode(events).toByteArray(Charsets.UTF_8)
        val stream = atomicFile.startWrite()
        try {
            stream.write(payload)
            stream.flush()
            atomicFile.finishWrite(stream)
        } catch (error: Exception) {
            atomicFile.failWrite(stream)
            throw error
        }
    }

    private fun storageArtifactsExist(): Boolean =
        baseFile.exists() ||
            File(baseFile.path + LEGACY_BACKUP_SUFFIX).exists() ||
            File(baseFile.path + NEW_FILE_SUFFIX).exists()

    private companion object {
        val FILE_LOCK = AppDataLock
        const val FILE_NAME = "ezcds.json"
        const val LEGACY_BACKUP_SUFFIX = ".bak"
        const val NEW_FILE_SUFFIX = ".new"
    }
}
