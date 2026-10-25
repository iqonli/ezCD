// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import com.iqonli.ezcd.data.CountdownStorageCodec
import com.iqonli.ezcd.model.CountdownEvent
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

internal data class WidgetLibraryEntry(
    val id: String, val createdAt: Instant, val updatedAt: Instant,
    val widgetId: Int?, val event: CountdownEvent?, val document: WidgetLayoutDocument,
    val sourceRevision: String? = null,
    val displayName: String="",val previewSize: String="small",
)
internal data class WidgetLibraryDocument(val entries: List<WidgetLibraryEntry> = emptyList(),val ignoredWidgetIds: Set<Int> = emptySet(),val closedWidgetIds: Set<Int> = emptySet())

internal object WidgetLibraryCodec {
    fun encode(document: WidgetLibraryDocument): String = JSONObject().put("version",1)
        .put("entries",JSONArray().apply { document.entries.forEach { put(entry(it)) } })
        .put("ignoredWidgetIds",JSONArray(document.ignoredWidgetIds.sorted())).put("closedWidgetIds",JSONArray(document.closedWidgetIds.sorted())).toString()
    private fun entry(e: WidgetLibraryEntry)=JSONObject().put("id",e.id).put("createdAt",e.createdAt.toString())
        .put("updatedAt",e.updatedAt.toString()).put("widgetId",e.widgetId ?: JSONObject.NULL)
        .put("event",e.event?.let { JSONObject(CountdownStorageCodec.encode(listOf(it))) } ?: JSONObject.NULL)
        .put("displayName",e.displayName).put("previewSize",e.previewSize)
        .put("layout",JSONObject(WidgetLayoutCodec.encode(e.document))).put("sourceRevision",e.sourceRevision ?: JSONObject.NULL)
    fun decode(raw: String): WidgetLibraryDocument {
        StrictLayoutJson.validate(raw)
        val root=JSONObject(raw);require(root.get("version") == 1)
        val array=root.getJSONArray("entries")
        val entries=(0 until array.length()).map { i ->
            val e=array.getJSONObject(i);val id=e.getString("id");require(UUID.fromString(id).toString() == id)
            val widget=if (e.isNull("widgetId")) null else e.get("widgetId").also { require(it is Int && it >= 0) } as Int
            val events=if (e.isNull("event")) null else CountdownStorageCodec.decode(e.getJSONObject("event").toString()).also { require(it.size == 1) }.single()
            val revision=if (e.isNull("sourceRevision")) null else e.getString("sourceRevision").also { require(it.matches(Regex("[0-9a-f]{64}"))) }
            val name=if(e.has("displayName")) e.get("displayName").also { require(it is String) } as String else ""
            val previewSize=if(e.has("previewSize")) e.get("previewSize").also { require(it is String && WidgetLayoutSize.entries.any { size -> size.key==it }) } as String else "small"
            WidgetLibraryEntry(id,Instant.parse(e.getString("createdAt")),Instant.parse(e.getString("updatedAt")),widget,events,
                WidgetLayoutCodec.decode(e.getJSONObject("layout").toString()),revision,name,previewSize)
        }
        require(entries.map { it.id }.distinct().size == entries.size)
        require(entries.mapNotNull { it.widgetId }.distinct().size == entries.mapNotNull { it.widgetId }.size)
        val ignored=root.optJSONArray("ignoredWidgetIds") ?: JSONArray()
        val ids=(0 until ignored.length()).map { ignored.get(it).also { n -> require(n is Int && n >= 0) } as Int }.toSet()
        val closed=root.optJSONArray("closedWidgetIds") ?: JSONArray()
        val closedIds=(0 until closed.length()).map { closed.get(it).also { n -> require(n is Int && n >= 0) } as Int }.toSet()
        return WidgetLibraryDocument(entries,ids,closedIds)
    }
}
