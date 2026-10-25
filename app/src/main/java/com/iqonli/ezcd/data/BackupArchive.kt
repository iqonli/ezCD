// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.data

import android.content.Context
import android.util.AtomicFile
import com.iqonli.ezcd.countdown.EventSortOrder
import com.iqonli.ezcd.ui.EventSortPreferences
import com.iqonli.ezcd.widget.*
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.CodingErrorAction

internal data class BackupArchive(val eventPayload: String,val library: List<WidgetLibraryEntry>?,val sort: EventSortOrder?,val textPool: Map<String,String>?=null)
internal object BackupArchiveCodec {
    const val MAX_BYTES=64*1024*1024
    fun read(input: InputStream,maxBytes: Int=MAX_BYTES): String {
        val output=java.io.ByteArrayOutputStream();val bytes=ByteArray(8192)
        while (true) { val n=input.read(bytes);if (n<0) break;require(output.size()+n <= maxBytes) { "Backup too large" };output.write(bytes,0,n) }
        return Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(output.toByteArray())).toString()
    }
    fun decode(payload: String): BackupArchive {
        require(payload.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        StrictLayoutJson.validate(payload)
        val root=JSONObject(payload)
        if (!root.has("format")) { CountdownStorageCodec.decodeForImport(payload);return BackupArchive(payload,null,null) }
        require(root.getString("format")=="ezcd-backup" && root.get("version")==1)
        val events=root.getJSONObject("eventData").toString();CountdownStorageCodec.decodeForImport(events)
        val library=WidgetLibraryCodec.decode(root.getJSONObject("widgetLibrary").toString()).entries
        val sort=EventSortOrder.entries.firstOrNull { it.name == root.getString("sortOrder") } ?: error("Unknown sort order")
        val pool=if(!root.has("textPool")) null else root.getJSONObject("textPool").let { data ->
            com.iqonli.ezcd.ui.TextPool.keys.associateWith { key -> if(data.has(key)) data.get(key).also { require(it is String) } as String else "" }
        }
        return BackupArchive(events,library,sort,pool)
    }
    fun encode(archive: BackupArchive): String {
        val root=JSONObject().put("format","ezcd-backup").put("version",1)
            .put("eventData",JSONObject(archive.eventPayload))
            .put("widgetLibrary",JSONObject(WidgetLibraryCodec.encode(WidgetLibraryDocument(archive.library.orEmpty()))))
            .put("sortOrder",(archive.sort ?: EventSortOrder.DATE_ASC).name)
        archive.textPool?.let { root.put("textPool",JSONObject(it)) }
        val result=root.toString();decode(result);return result
    }
}

/** Import has a rollback journal; process death cannot leave half of a backup applied. */
internal class BackupArchiveService(context: Context) {
    private val context=context.applicationContext
    private val repository=CountdownRepository(this.context)
    private val library=WidgetLibraryStore(this.context)
    private val sort=EventSortPreferences(this.context)
    private val pool=com.iqonli.ezcd.ui.TextPool(this.context)
    private fun poolValues()=com.iqonli.ezcd.ui.TextPool.keys.associateWith(pool::get)
    private val journal=AtomicFile(File(context.filesDir,"backup-import-rollback.json"))
    fun export(): String = synchronized(AppDataLock) {
        recover();library.sync()
        BackupArchiveCodec.encode(BackupArchive(repository.exportPayload(),library.entries(),sort.get(),poolValues()))
    }
    fun preview(payload: String): Int = CountdownStorageCodec.decodeForImport(BackupArchiveCodec.decode(payload).eventPayload).size
    fun apply(payload: String): Int = synchronized(AppDataLock) {
        val incoming=BackupArchiveCodec.decode(payload);recover()
        val previous=JSONObject().put("events",repository.exportPayload()).put("library",WidgetLibraryCodec.encode(library.state())).put("sort",sort.get().name).put("textPool",JSONObject(poolValues())).toString()
        val stream=journal.startWrite()
        try { stream.write(previous.toByteArray(Charsets.UTF_8));journal.finishWrite(stream) }
        catch (error: Exception) { journal.failWrite(stream);throw error }
        try {
            val count=repository.importPayload(incoming.eventPayload)
            incoming.library?.let { library.mergeImported(it) }
            incoming.sort?.let { sort.saveDurable(it) }
            incoming.textPool?.let { pool.save(it) }
            journal.delete();count
        } catch (error: Exception) { runCatching { recover() };throw error }
    }
    fun recover() = synchronized(AppDataLock) {
        if (!journal.baseFile.exists() && !File(journal.baseFile.path+".bak").exists()) return@synchronized
        val old=journal.openRead().use { JSONObject(BackupArchiveCodec.read(it,BackupArchiveCodec.MAX_BYTES*3)) }
        val events=old.getString("events");val entries=WidgetLibraryCodec.decode(old.getString("library"))
        val order=EventSortOrder.valueOf(old.getString("sort"))
        CountdownStorageCodec.decode(events)
        repository.importPayload(events);library.replace(entries);sort.saveDurable(order)
        if(old.has("textPool")) { val data=old.getJSONObject("textPool");pool.save(com.iqonli.ezcd.ui.TextPool.keys.associateWith { data.getString(it) }) }
        journal.delete()
    }
}
