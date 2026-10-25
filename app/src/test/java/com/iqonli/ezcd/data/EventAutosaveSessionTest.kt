package com.iqonli.ezcd.data

import com.iqonli.ezcd.model.*
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class EventAutosaveSessionTest {
    private val original = CountdownEvent("id", "Original", LocalDate.of(2026, 10, 24), EventType.EVENT, createdAt = Instant.EPOCH)
    private class Work(val task: () -> CountdownMutationResult, val done: (Result<CountdownMutationResult>) -> Unit)
    private val queue = ArrayDeque<Work>()
    private val saved = mutableListOf<Pair<CountdownEvent?, CountdownEvent>>()
    private val removed = mutableListOf<CountdownEvent>()
    private fun session(persisted: CountdownEvent? = original, draft: CountdownEvent = original) = EventAutosaveSession(
        persisted, draft, save = { previous, next -> saved.add(previous to next); CountdownMutationResult.APPLIED },
        delete = { previous -> removed.add(previous); CountdownMutationResult.APPLIED },
        dispatch = { task, done -> queue.addLast(Work(task, done)) })
    private fun complete() { val work = queue.removeFirst(); work.done(runCatching(work.task)) }

    @Test fun unchangedOrBlankNewEventsAreNotWritten() {
        session().flush(); assertTrue(queue.isEmpty())
        session(null, original.copy(title = "")).flush(); assertTrue(queue.isEmpty())
    }
    @Test fun fastEditsDuringIoAreCoalescedAndUseTheLastSavedRevision() {
        val state = session(); val first = original.copy(title = "First"); val latest = original.copy(title = "Latest", includeToday = true)
        state.update(first); state.flush(); state.update(latest); state.flush()
        assertEquals(1, queue.size); complete()
        assertEquals(1, queue.size); assertTrue(state.running); complete()
        assertEquals(listOf(original to first, first to latest), saved)
        assertEquals(latest, state.persisted); assertTrue(state.settled)
    }
    @Test fun firstAutosaveCreatesOnceThenUpdatesRatherThanCreatingDuplicates() {
        val state = session(null); state.flush(); complete()
        val second = original.copy(includeToday = true); state.update(second); state.flush(); complete()
        assertNull(saved[0].first); assertEquals(original, saved[1].first); assertEquals(second, state.persisted)
    }
    @Test fun deletingDuringASaveWaitsForTheNewRevisionAndDoesNotRecreateTheEvent() {
        val state = session(); val next = original.copy(title = "Changed")
        state.update(next); state.flush(); state.requestDelete(); complete(); complete()
        assertEquals(listOf(next), removed); assertTrue(state.deleted)
        state.update(original); state.flush(); assertTrue(queue.isEmpty())
    }
    @Test fun queueDrainsEvenWhenTheScreenObserverIsDetached() {
        val state = session(); state.onChanged = {}; state.update(original.copy(title = "One")); state.flush()
        state.update(original.copy(title = "Two")); state.onChanged = null
        complete(); complete(); assertEquals("Two", state.persisted!!.title); assertTrue(state.settled)
    }
    @Test fun failureKeepsTheDraftAndRequiresRetryWithoutClaimingItWasSaved() {
        val state = session(); val next = original.copy(title = "Pending"); state.update(next); state.flush()
        val work = queue.removeFirst(); work.done(Result.failure(java.io.IOException("Disk unavailable")))
        assertEquals(original, state.persisted); assertEquals(next, state.draft); assertNotNull(state.failure); assertFalse(state.settled)
        state.flush(); assertTrue(queue.isEmpty()); state.retry(); complete(); assertTrue(state.settled)
    }
    @Test fun invalidEditNeverReplacesTheLastValidRecord() {
        val state = session(); state.update(original.copy(title = "")); state.flush()
        assertTrue(queue.isEmpty()); assertEquals(original, state.persisted)
    }
}
