// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.iqonli.ezcd.model.CountdownEvent
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

internal data class WidgetPinRequestSnapshot(
    val requestToken: String,
    val eventId: String,
    val style: WidgetStyleSelection,
    val size: WidgetPinSize=WidgetPinSize(),
    val customLayout: Boolean=false,
) {
    fun callbackData(): String = buildString {
        append("ezcd://widget/pin/")
        append(encode(requestToken))
        append("?event=")
        append(encode(eventId))
        append("&appearance=")
        append(encode(style.appearance.storageKey))
        append("&background=")
        append(encode(style.background.storageKey))
        if(customLayout) append("&layout=1")
        if(size!=WidgetPinSize()) { append("&columns=${size.columns}&rows=${size.rows}") }
    }

    companion object {
        fun create(
            eventId: String,
            style: WidgetStyleSelection,
            requestToken: String = UUID.randomUUID().toString(),
            size: WidgetPinSize=WidgetPinSize(),
        ): WidgetPinRequestSnapshot = WidgetPinRequestSnapshot(
            requestToken = requestToken,
            eventId = eventId,
            style = style,
            size = size,
        )

        fun fromCallbackData(raw: String?): WidgetPinRequestSnapshot? {
            if (raw.isNullOrBlank()) return null
            val uri = runCatching { URI(raw) }.getOrNull() ?: return null
            if (uri.scheme != "ezcd" || uri.host != "widget") return null

            val path = uri.rawPath.orEmpty()
                .split('/')
                .filter(String::isNotEmpty)
            if (path.size != 2 || path[0] != "pin") return null

            val requestToken = decode(path[1]).takeIf(String::isNotBlank) ?: return null
            val values = uri.rawQuery.orEmpty()
                .split('&')
                .mapNotNull { pair ->
                    val separator = pair.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
                }
                .toMap()
            val eventId = values["event"]?.takeIf(String::isNotBlank) ?: return null
            val appearance = WidgetAppearance.entries.firstOrNull {
                it.storageKey == values["appearance"]
            } ?: return null
            val background = WidgetBackground.entries.firstOrNull {
                it.storageKey == values["background"]
            } ?: return null
            val columns=values["columns"]?.toIntOrNull() ?: if("columns" in values) return null else 1
            val rows=values["rows"]?.toIntOrNull() ?: if("rows" in values) return null else 1
            if(columns !in 1..WidgetPinSize.MAX_PRESET || rows !in 1..WidgetPinSize.MAX_PRESET) return null

            return WidgetPinRequestSnapshot(
                requestToken = requestToken,
                eventId = eventId,
                style = WidgetStyleSelection(
                    appearance = appearance,
                    background = background,
                ),
                size=WidgetPinSize(columns,rows),
                customLayout=values["layout"]=="1",
            )
        }

        private fun encode(value: String): String =
            URLEncoder.encode(value, StandardCharsets.UTF_8.name())

        private fun decode(value: String): String =
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }
}

object WidgetPinning {
    fun request(
        activity: Activity,
        event: CountdownEvent,
        style: WidgetStyleSelection,
    ): Boolean = request(
        activity = activity,
        event = event,
        snapshot = WidgetPinRequestSnapshot.create(event.id, style),
    )

    internal fun request(
        activity: Activity,
        event: CountdownEvent?,
        snapshot: WidgetPinRequestSnapshot,
    ): Boolean = requestInternal(activity, event, snapshot)

    internal fun callbackPendingIntent(
        context: Context,
        snapshot: WidgetPinRequestSnapshot,
    ): PendingIntent = PendingIntent.getBroadcast(
        context,
        snapshot.requestToken.hashCode(),
        Intent(context, WidgetPinResultReceiver::class.java)
            .setData(Uri.parse(snapshot.callbackData())),
        // The launcher supplies EXTRA_APPWIDGET_ID through the success callback.
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0,
    )

    private fun requestInternal(
        activity: Activity,
        event: CountdownEvent?,
        snapshot: WidgetPinRequestSnapshot,
    ): Boolean {
        val manager = AppWidgetManager.getInstance(activity)
        val supported=manager.isRequestPinAppWidgetSupported
        WidgetPinDiagnostics.note(activity,snapshot.requestToken,"pinSupported=$supported")
        if (!supported) return false

        val provider = ComponentName(activity, CountdownWidgetProvider::class.java)
        val preview = WidgetPreviewFactory.remoteViews(
            context = activity,
            event = event,
            style = snapshot.style,
            dimensions=snapshot.size.preview,
            document=if(snapshot.customLayout) WidgetPinAttemptStore.document(activity,snapshot.requestToken) else null,
            placeholder=snapshot.eventId==PLACEHOLDER_ID,
        )
        WidgetPinDiagnostics.preview(activity,snapshot.requestToken,preview)
        val extras = Bundle().apply {
            putParcelable(AppWidgetManager.EXTRA_APPWIDGET_PREVIEW, preview)
            putBundle(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS,Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,snapshot.size.preview.widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,snapshot.size.preview.widthDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,snapshot.size.preview.heightDp)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,snapshot.size.preview.heightDp)
            })
        }
        val callback = callbackPendingIntent(activity, snapshot)

        WidgetPinDiagnostics.note(activity,snapshot.requestToken,"requestPinAppWidget: calling (foreground=${activity.hasWindowFocus()})")
        WidgetPinDiagnostics.note(activity,snapshot.requestToken,"requestedCells=${snapshot.size.columns}x${snapshot.size.rows}")
        val requested = try {
            synchronized(sizeLock) {
                val change=snapshot.size!=WidgetPinSize() && Build.VERSION.SDK_INT>=28
                if(change) manager.updateAppWidgetProviderInfo(provider,snapshot.size.metadataKey)
                try { manager.requestPinAppWidget(provider, extras, callback) }
                finally { if(change) runCatching { manager.updateAppWidgetProviderInfo(provider,null) }.onFailure { WidgetPinDiagnostics.error(activity,snapshot.requestToken,"reset provider size",it) } }
            }
        } catch (error: Exception) {
            callback.cancel()
            throw error
        }
        WidgetPinDiagnostics.note(activity,snapshot.requestToken,"requestPinAppWidget returned=$requested")
        if (!requested) callback.cancel()
        return requested
    }
    internal const val PLACEHOLDER_ID="ezcd:placeholder"
    internal const val NO_EVENT_ID="ezcd:no-event"
    private val sizeLock=Any()
}
