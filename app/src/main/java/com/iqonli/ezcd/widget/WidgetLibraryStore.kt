// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.AtomicFile
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.CountdownEvent
import java.io.File
import java.io.FileNotFoundException
import java.time.Instant
import java.util.UUID

/** Only source changes replace a live capture; daily refreshes do not erase library edits. */
internal class WidgetLibraryStore(context: Context) {
    private val context=context.applicationContext
    private val file=AtomicFile(File(context.filesDir,"widget-library.json"))
    fun state(): WidgetLibraryDocument = synchronized(AppDataLock) {
        try { file.openRead().use { WidgetLibraryCodec.decode(BackupArchiveCodec.read(it)) } }
        catch (error: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path+".bak").exists()) throw error
            WidgetLibraryDocument()
        }
    }
    fun entries()=state().entries.sortedWith(compareByDescending<WidgetLibraryEntry> { it.createdAt }.thenByDescending { it.id })
    fun get(id: String)=state().entries.firstOrNull { it.id == id }
    fun replace(state: WidgetLibraryDocument) = synchronized(AppDataLock) {
        val raw=WidgetLibraryCodec.encode(state);WidgetLibraryCodec.decode(raw)
        require(raw.toByteArray(Charsets.UTF_8).size <= BackupArchiveCodec.MAX_BYTES)
        val stream=file.startWrite()
        try { stream.write(raw.toByteArray(Charsets.UTF_8));file.finishWrite(stream) }
        catch (error: Exception) { file.failWrite(stream);throw error }
    }
    fun capture(widgetId: Int,event: CountdownEvent?,document: WidgetLayoutDocument,live: Boolean=true) = synchronized(AppDataLock) {
        var state=state();if (widgetId in state.ignoredWidgetIds || !live && widgetId in state.closedWidgetIds) return@synchronized
        if (live && widgetId in state.closedWidgetIds) state=state.copy(closedWidgetIds=state.closedWidgetIds-widgetId)
        val old=state.entries.firstOrNull { it.widgetId == widgetId }
        val snapshot=event ?: old?.event
        val revision=EventRevision.ofFields(WidgetLayoutCodec.encode(document),snapshot?.let(EventRevision::of).orEmpty())
        if (old != null && old.sourceRevision == revision && live) return@synchronized
        val now=Instant.now()
        val entry=if (old != null && old.sourceRevision == revision) old.copy(widgetId=null)
            else WidgetLibraryEntry(old?.id ?: UUID.randomUUID().toString(),old?.createdAt ?: now,now,if (live) widgetId else null,snapshot,document,revision,old?.displayName.orEmpty(),old?.previewSize ?: "small")
        replace(state.copy(entries=state.entries.filterNot { it.id == entry.id }+entry,closedWidgetIds=if (live) state.closedWidgetIds else state.closedWidgetIds+widgetId))
    }
    fun sync() = synchronized(AppDataLock) {
        val manager=AppWidgetManager.getInstance(context)
        val ids=manager.getAppWidgetIds(ComponentName(context,CountdownWidgetProvider::class.java)).toSet()
        val events=(CountdownRepository(context).loadResult() as? CountdownLoadResult.Success)?.events ?: error("Cannot read events")
        val preferences=WidgetPreferences(context);val layouts=WidgetLayoutStore(context)
        ids.forEach { id -> val eventId=preferences.get(id)?.eventId;capture(id,events.firstOrNull { it.id == eventId },layouts.load(id,eventId)) }
        val state=state()
        val entries=state.entries.map { if (it.widgetId != null && it.widgetId !in ids) it.copy(widgetId=null) else it }
        val ignored=state.ignoredWidgetIds.intersect(ids)
        val closed=state.closedWidgetIds+state.entries.mapNotNull { it.widgetId }.filter { it !in ids }+(state.ignoredWidgetIds-ids)
        if (entries != state.entries || ignored != state.ignoredWidgetIds || closed != state.closedWidgetIds) replace(state.copy(entries=entries,ignoredWidgetIds=ignored,closedWidgetIds=closed))
    }
    fun archive(widgetId: Int) = synchronized(AppDataLock) {
        val state=state();replace(state.copy(entries=state.entries.map { if (it.widgetId == widgetId) it.copy(widgetId=null) else it },ignoredWidgetIds=state.ignoredWidgetIds-widgetId,closedWidgetIds=state.closedWidgetIds+widgetId))
    }
    fun remap(oldId: Int,newId: Int) = synchronized(AppDataLock) {
        val state=state();replace(state.copy(entries=state.entries.map { when(it.widgetId) { oldId -> it.copy(widgetId=newId);newId -> it.copy(widgetId=null);else -> it } },ignoredWidgetIds=state.ignoredWidgetIds-oldId-newId,closedWidgetIds=state.closedWidgetIds-oldId-newId))
    }
    fun create(document: WidgetLayoutDocument,event: CountdownEvent?=null): WidgetLibraryEntry = synchronized(AppDataLock) {
        val now=Instant.now();val entry=WidgetLibraryEntry(UUID.randomUUID().toString(),now,now,null,event,document)
        val state=state();replace(state.copy(entries=state.entries+entry));entry
    }
    fun save(id: String,document: WidgetLayoutDocument,event: CountdownEvent?) = synchronized(AppDataLock) {
        val state=state();require(state.entries.any { it.id == id }) { "Library entry was deleted" }
        replace(state.copy(entries=state.entries.map { if (it.id == id) it.copy(document=document,event=event,updatedAt=Instant.now()) else it }))
    }
    fun duplicate(id: String): WidgetLibraryEntry = synchronized(AppDataLock) {
        val old=get(id) ?: error("Missing entry");val created=create(old.document,old.event)
        val copy=created.copy(displayName=old.displayName,previewSize=old.previewSize)
        val state=state();replace(state.copy(entries=state.entries.map { if(it.id==copy.id) copy else it }));copy
    }
    fun rename(id: String,name: String) = metadata(id) { it.copy(displayName=name.trim()) }
    fun cyclePreview(id: String) = metadata(id) { e ->
        val sizes=WidgetLayoutSize.entries;val next=(sizes.indexOfFirst { it.key==e.previewSize }+1)%sizes.size
        e.copy(previewSize=sizes[next].key)
    }
    private fun metadata(id: String,edit: (WidgetLibraryEntry)->WidgetLibraryEntry) = synchronized(AppDataLock) {
        val state=state();require(state.entries.any { it.id==id })
        replace(state.copy(entries=state.entries.map { if(it.id==id) edit(it).copy(updatedAt=Instant.now()) else it }))
    }
    fun delete(id: String) = synchronized(AppDataLock) {
        val state=state();val entry=state.entries.firstOrNull { it.id == id } ?: return@synchronized
        replace(state.copy(entries=state.entries.filterNot { it.id == id },ignoredWidgetIds=state.ignoredWidgetIds+listOfNotNull(entry.widgetId)))
    }
    /** Foreign widget IDs are metadata, never binding instructions on the receiving device. */
    fun mergeImported(incoming: List<WidgetLibraryEntry>) = synchronized(AppDataLock) {
        val state=state();val old=state.entries.associateBy { it.id }
        val merged=incoming.map { entry -> entry.copy(widgetId=old[entry.id]?.widgetId,sourceRevision=old[entry.id]?.sourceRevision) }
        replace(state.copy(entries=state.entries.filterNot { e -> incoming.any { it.id == e.id } }+merged))
    }
}
