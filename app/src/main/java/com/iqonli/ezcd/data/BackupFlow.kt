// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.data

import android.content.Context
import android.net.Uri
import android.os.Bundle
import com.iqonli.ezcd.R

/** One screen's retained owner. Workers never retain an Activity or a document URI beyond the operation. */
class BackupFlow(context: Context, state: Bundle? = null) {
    enum class Stage { IDLE, PICK_IMPORT, VALIDATING, CONFIRM, PICK_EXPORT, PICK_EXPORT_FIRST,
        EXPORTING, EXPORTING_FIRST, APPLYING;

        fun afterProcessDeath(hasSnapshot: Boolean): Stage = when (this) {
            VALIDATING, APPLYING, EXPORTING -> IDLE
            EXPORTING_FIRST -> if (hasSnapshot) CONFIRM else IDLE
            CONFIRM, PICK_EXPORT_FIRST -> if (hasSnapshot) this else IDLE
            else -> this
        }
    }

    private val context = context.applicationContext
    private val repository = CountdownRepository(this.context)
    var stage = state?.getString("backup_stage")?.let { raw -> Stage.entries.find { it.name == raw } }
        ?: Stage.IDLE
        private set
    private var snapshot = state?.getString("backup_snapshot")?.let {
        CountdownImportSnapshot.restore(this.context, it)
    }
    var incomingCount = state?.getInt("backup_count") ?: 0
        private set
    var currentCount: Int? = state?.takeIf { it.containsKey("backup_current") }?.getInt("backup_current")
        private set
    var onChanged: (() -> Unit)? = null
    private var notice: Int? = null
    private var abandoned = false
    val busy: Boolean get() = stage in setOf(Stage.VALIDATING, Stage.EXPORTING, Stage.EXPORTING_FIRST, Stage.APPLYING)

    init {
        // A fresh process must never replay an import or a provider write. A retained instance
        // keeps its live worker instead. Only an unconsumed approval may survive process death.
        val interrupted = stage
        stage = stage.afterProcessDeath(snapshot?.exists() == true)
        if (stage == Stage.IDLE && snapshot != null) clearSnapshot()
        notice = when (interrupted) {
            Stage.VALIDATING -> R.string.backup_import_failed
            // Atomic storage may already contain the import. Do not report an unobserved
            // write failure or replay it; normal entry reconciles the actual stored data.
            Stage.APPLYING -> null
            Stage.EXPORTING, Stage.EXPORTING_FIRST -> R.string.backup_export_failed
            Stage.CONFIRM, Stage.PICK_EXPORT_FIRST -> if (stage == Stage.IDLE) R.string.backup_import_failed else null
            else -> null
        }
    }

    fun saveState(state: Bundle) {
        state.putString("backup_stage", stage.name)
        state.putString("backup_snapshot", snapshot?.identity)
        state.putInt("backup_count", incomingCount)
        currentCount?.let { state.putInt("backup_current", it) }
    }

    fun takeNotice(): Int? = notice.also { notice = null }

    fun pickImport() {
        check(stage == Stage.IDLE)
        stage = Stage.PICK_IMPORT
        changed()
    }

    fun pickExport(first: Boolean) {
        check(stage == if (first) Stage.CONFIRM else Stage.IDLE)
        stage = if (first) Stage.PICK_EXPORT_FIRST else Stage.PICK_EXPORT
        changed()
    }

    fun providerUnavailable() {
        notice = R.string.backup_provider_unavailable
        pickerResult(null)
    }

    fun pickerResult(uri: Uri?) {
        when (stage) {
            Stage.PICK_IMPORT -> if (uri == null) {
                stage = Stage.IDLE
                changed()
            } else read(uri)
            Stage.PICK_EXPORT, Stage.PICK_EXPORT_FIRST -> {
                val first = stage == Stage.PICK_EXPORT_FIRST
                if (uri == null) {
                    stage = if (first) Stage.CONFIRM else Stage.IDLE
                    changed()
                } else export(uri, first)
            }
            else -> Unit // Ignore duplicate/stale provider results.
        }
    }

    private fun read(uri: Uri) {
        // Assign ownership before dispatch: finishing the Activity cannot orphan a later result.
        val owned = CountdownImportSnapshot.create(context)
        snapshot = owned
        stage = Stage.VALIDATING
        changed()
        CountdownIo.submit(task = {
            val payload = context.contentResolver.openInputStream(uri)?.use { BackupArchiveCodec.read(it) }
                ?: error("Unable to open backup")
            val count = BackupArchiveService(context).preview(payload)
            val current = (repository.loadResult() as? CountdownLoadResult.Success)?.events?.size
            owned.write(payload)
            count to current
        }, onComplete = { result ->
            result.onSuccess { (count, current) ->
                incomingCount = count
                currentCount = current
                stage = Stage.CONFIRM
            }.onFailure {
                clearSnapshot()
                stage = Stage.IDLE
                notice = importFailure(it)
            }
            changed()
        })
    }

    private fun export(uri: Uri, first: Boolean) {
        stage = if (first) Stage.EXPORTING_FIRST else Stage.EXPORTING
        changed()
        CountdownIo.submit(task = {
            val payload = BackupArchiveService(context).export()
            context.contentResolver.openOutputStream(uri, "rwt")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(payload)
            } ?: error("Unable to open destination")
        }, onComplete = { result ->
            stage = if (first) Stage.CONFIRM else Stage.IDLE
            notice = if (result.isSuccess) R.string.backup_export_success else R.string.backup_export_failed
            changed()
        })
    }

    fun confirmImport() {
        if (stage != Stage.CONFIRM) return
        val owned = snapshot ?: return
        stage = Stage.APPLYING
        changed()
        val mutations = CountdownMutations(context)
        CountdownIo.submit(task = { mutations.applyImport(owned) }, onComplete = { result ->
            snapshot = null
            stage = Stage.IDLE
            notice = result.fold({ R.string.backup_import_success }, ::importFailure)
            changed()
        })
    }

    fun cancelConfirmation() {
        if (stage != Stage.CONFIRM) return
        clearSnapshot()
        stage = Stage.IDLE
        changed()
    }

    fun abandon() {
        onChanged = null
        abandoned = true
        if (!busy) clearSnapshot()
    }

    private fun changed() {
        if (abandoned) {
            if (!busy) clearSnapshot()
        } else onChanged?.invoke()
    }

    private fun clearSnapshot() {
        snapshot?.clear()
        snapshot = null
    }

    private fun importFailure(error: Throwable): Int = when {
        error is CountdownDataException && error.problem == CountdownDataProblem.UNSUPPORTED_SCHEMA ->
            R.string.backup_import_newer_version
        error is CountdownDataException -> R.string.backup_import_invalid
        else -> R.string.backup_import_failed
    }
}
