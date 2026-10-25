// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.data

import com.iqonli.ezcd.countdown.CountdownDateDomain
import com.iqonli.ezcd.model.CountdownEvent

/** Main-thread state; in-flight work keeps this session, never an Activity. */
class EventAutosaveSession(
    persisted: CountdownEvent?,
    draft: CountdownEvent,
    private val save: (CountdownEvent?, CountdownEvent) -> CountdownMutationResult,
    private val delete: (CountdownEvent) -> CountdownMutationResult,
    private val dispatch: (() -> CountdownMutationResult, (Result<CountdownMutationResult>) -> Unit) -> Unit,
) {
    var persisted = persisted
        private set
    var draft = draft
        private set
    var running = false
        private set
    var deleteRequested = false
        private set
    var deleted = false
        private set
    var failure: Throwable? = null
        private set
    var failureSerial = 0
        private set
    var onChanged: (() -> Unit)? = null
    val settled: Boolean get() = !running && failure == null && persisted == draft
    val valid: Boolean get() = draft.title.isNotBlank() &&
        CountdownValidation.isTitleWithinLimit(draft.title) && CountdownDateDomain.contains(draft.date)

    fun update(value: CountdownEvent) {
        if (deleteRequested || deleted || value == draft) return
        draft = value
        failure = null
    }
    fun retry() { failure = null; flush() }
    fun cancelFailedDelete() {
        if (deleteRequested && !running && failure != null) {
            deleteRequested = false; failure = null; onChanged?.invoke()
        }
    }
    fun requestDelete() { deleteRequested = true; failure = null; flush() }

    fun flush() {
        if (running || deleted || failure != null) return
        val previous = persisted
        val removing = deleteRequested
        if (removing && previous == null) { deleted = true; onChanged?.invoke(); return }
        if (!removing && (!valid || previous == draft)) return
        val snapshot = draft
        running = true
        onChanged?.invoke()
        dispatch({ if (removing) delete(checkNotNull(previous)) else save(previous, snapshot) }) { result ->
            running = false
            if (result.getOrNull() == CountdownMutationResult.APPLIED) {
                if (removing) deleted = true else persisted = snapshot
                // Edits made during disk I/O are saved against the revision that just completed.
                flush()
            } else {
                failure = result.exceptionOrNull() ?: IllegalStateException("Event revision conflict")
                failureSerial++
            }
            onChanged?.invoke()
        }
    }
}
