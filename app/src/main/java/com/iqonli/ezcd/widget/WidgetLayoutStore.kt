// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.content.Context

internal class WidgetLayoutStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("widget_custom_layouts",Context.MODE_PRIVATE)
    fun load(widgetId: Int, eventId: String?): WidgetLayoutDocument {
        val raw = (if (widgetId >= 0) prefs.getString("widget_$widgetId",null) else null)
            ?: eventId?.let { prefs.getString("event_$it",null) }
        val loaded = raw?.let { runCatching { WidgetLayoutCodec.decode(it) }.getOrNull() } ?: return defaults()
        if (org.json.JSONObject(raw).optInt("version") != 1) return loaded
        val old = WidgetLayoutCodec.decode(context.assets.open("widget-layout-v1-default.json").bufferedReader().use { it.readText() })
        val fresh = defaults()
        return loaded.copy(layouts=loaded.layouts.mapValues { (key,layout) ->
            if (layout.elements == old.layouts.getValue(key).elements) fresh.layouts.getValue(key).copy(background=layout.background) else layout
        })
    }
    fun defaults(): WidgetLayoutDocument = WidgetLayoutCodec.decode(context.assets.open("widget-layout-default.json").bufferedReader().use { it.readText() })
    /** Synchronous commit is called on CountdownIo; failure never reports a successful save. */
    fun save(widgetId: Int,eventId: String,document: WidgetLayoutDocument) {
        val raw = WidgetLayoutCodec.encode(document); WidgetLayoutCodec.decode(raw)
        check(prefs.edit().putString(if (widgetId >= 0) "widget_$widgetId" else "event_$eventId",raw).commit()) { "Cannot save widget layout" }
    }
    fun remove(widgetId: Int) { prefs.edit().remove("widget_$widgetId").apply() }
    fun remap(oldId: Int,newId: Int) {
        val raw = prefs.getString("widget_$oldId",null)
        prefs.edit().apply { remove("widget_$oldId"); remove("widget_$newId"); if (raw != null) putString("widget_$newId",raw) }.apply()
    }
}
