// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.annotation.TargetApi
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.countdown.CountdownTimeSnapshot
import com.iqonli.ezcd.data.CountdownIo
import com.iqonli.ezcd.data.CountdownRepository
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.ui.EditorActivity
import com.iqonli.ezcd.ui.LanguageManager
import java.time.LocalDate
import kotlin.math.roundToInt

class CountdownWidgetProvider : AppWidgetProvider() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateScheduler.ensureScheduled(context.applicationContext, CountdownTime.snapshot())
    }

    override fun onDisabled(context: Context) {
        WidgetUpdateScheduler.cancel(context.applicationContext)
        super.onDisabled(context)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val ids = appWidgetIds.clone()
        val snapshot = CountdownTime.snapshot()
        CountdownIo.execute {
            try {
                updateWidgets(appContext, manager, ids, snapshot)
                WidgetUpdateScheduler.ensureScheduled(appContext, snapshot)
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val appContext=context.applicationContext;val ids=appWidgetIds.clone();val pending=goAsync()
        CountdownIo.execute {
            try {
                val preferences=WidgetPreferences(appContext);val layouts=WidgetLayoutStore(appContext);val library=WidgetLibraryStore(appContext)
                val events=(CountdownRepository(appContext).loadResult() as? com.iqonli.ezcd.data.CountdownLoadResult.Success)?.events.orEmpty()
                ids.forEach { id ->
                    val eventId=preferences.get(id)?.eventId
                    runCatching { library.capture(id,events.firstOrNull { it.id == eventId },layouts.load(id,eventId),false);library.archive(id) }.onFailure { android.util.Log.e("ezCD","Cannot archive deleted widget",it) }
                    preferences.remove(id);layouts.remove(id)
                }
                WidgetUpdateScheduler.ensureScheduled(appContext,CountdownTime.snapshot())
            } finally { pending.finish() }
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        val appContext = context.applicationContext
        val preferences = WidgetPreferences(appContext)
        val manager = AppWidgetManager.getInstance(appContext)
        val pairCount = minOf(oldWidgetIds.size, newWidgetIds.size)

        for (index in 0 until pairCount) {
            preferences.remap(oldWidgetIds[index], newWidgetIds[index])
            WidgetLayoutStore(appContext).remap(oldWidgetIds[index],newWidgetIds[index])
            CountdownIo.execute { WidgetLibraryStore(appContext).remap(oldWidgetIds[index],newWidgetIds[index]) }
        }
        for (index in pairCount until newWidgetIds.size) {
            preferences.remove(newWidgetIds[index])
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            newWidgetIds.forEach { appWidgetId ->
                runCatching {
                    manager.updateAppWidgetOptions(appWidgetId, Bundle().apply {
                        putBoolean(AppWidgetManager.OPTION_APPWIDGET_RESTORE_COMPLETED, true)
                    })
                }
            }
        }

        WidgetUpdateScheduler.ensureScheduled(appContext, CountdownTime.snapshot())
        super.onRestored(context, oldWidgetIds, newWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        val pending = goAsync()
        val appContext = context.applicationContext
        val snapshot = CountdownTime.snapshot()
        CountdownIo.execute {
            try {
                updateWidget(appContext, manager, appWidgetId, snapshot)
            } finally {
                pending.finish()
            }
        }
        super.onAppWidgetOptionsChanged(context, manager, appWidgetId, newOptions)
    }

    companion object {
        fun updateAllWidgets(context: Context, snapshot: CountdownTimeSnapshot = CountdownTime.snapshot()) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val component = ComponentName(appContext, CountdownWidgetProvider::class.java)
            updateWidgets(appContext, manager, manager.getAppWidgetIds(component), snapshot)
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int,
            snapshot: CountdownTimeSnapshot = CountdownTime.snapshot()) {
            updateWidgets(context.applicationContext, manager, intArrayOf(appWidgetId), snapshot)
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray,
            snapshot: CountdownTimeSnapshot) {
            runCatching { WidgetLibraryStore(context).sync() }.onFailure { android.util.Log.e("ezCD","Cannot capture widget library",it) }
            if (appWidgetIds.isEmpty()) { WidgetMidnightRefresh.rendered(context,snapshot);return }
            val displayContext = LanguageManager.localizedContext(context)
            val preferences = WidgetPreferences(context)
            val renderData = WidgetRenderData.from(CountdownRepository(context).loadResult(), snapshot.today)
            appWidgetIds.forEach { appWidgetId ->
                val views = buildRemoteViews(context, displayContext, manager, appWidgetId,
                    snapshot.today, preferences.get(appWidgetId), renderData)
                manager.updateAppWidget(appWidgetId, views)
            }
            WidgetMidnightRefresh.rendered(context,snapshot)
        }

        private fun buildRemoteViews(
            context: Context,
            displayContext: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
            today: LocalDate,
            configuration: WidgetConfiguration?,
            renderData: WidgetRenderData,
        ): RemoteViews {
            val options = manager.getAppWidgetOptions(appWidgetId)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val responsive = responsiveViews(context, displayContext, appWidgetId, today,
                    configuration, renderData, options)
                if (responsive != null) return responsive
            }
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, DEFAULT_MIN_WIDTH_DP)
                .takeIf { it > 0 } ?: DEFAULT_MIN_WIDTH_DP
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, DEFAULT_MIN_HEIGHT_DP)
                .takeIf { it > 0 } ?: DEFAULT_MIN_HEIGHT_DP
            val maxWidth=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,widthDp).coerceAtLeast(widthDp)
            val maxHeight=options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,heightDp).coerceAtLeast(heightDp)
            val portrait=renderForSize(context,displayContext,appWidgetId,today,configuration,renderData,widthDp,maxHeight)
            val landscape=renderForSize(context,displayContext,appWidgetId,today,configuration,renderData,maxWidth,heightDp)
            return RemoteViews(landscape,portrait)
        }

        @TargetApi(Build.VERSION_CODES.S)
        @Suppress("DEPRECATION")
        private fun responsiveViews(
            context: Context,
            displayContext: Context,
            appWidgetId: Int,
            today: LocalDate,
            configuration: WidgetConfiguration?,
            renderData: WidgetRenderData,
            options: Bundle,
        ): RemoteViews? {
            val sizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES)
                .orEmpty().filter { it.width.isFinite() && it.height.isFinite() && it.width>0 && it.height>0 }.distinct().take(16)
            if (sizes.isEmpty()) return null
            val mapping = LinkedHashMap<SizeF, RemoteViews>(sizes.size)
            sizes.forEach { size ->
                mapping[size] = renderForSize(context, displayContext, appWidgetId, today,
                    configuration, renderData,
                    size.width.roundToInt().coerceAtLeast(MIN_RENDER_DIMENSION_DP),
                    size.height.roundToInt().coerceAtLeast(MIN_RENDER_DIMENSION_DP),BitmapWidgetRenderer.budget(context,sizes.size))
            }
            return RemoteViews(mapping)
        }

        private fun renderForSize(
            context: Context,
            displayContext: Context,
            appWidgetId: Int,
            today: LocalDate,
            configuration: WidgetConfiguration?,
            renderData: WidgetRenderData,
            widthDp: Int,
            heightDp: Int,
            bitmapBudget: Long=BitmapWidgetRenderer.budget(context,2),
        ): RemoteViews {
            if(configuration?.eventId==WidgetPinning.PLACEHOLDER_ID) return RemoteViews(context.packageName,R.layout.widget_placeholder).apply {
                setOnClickPendingIntent(R.id.widgetRoot,configurePendingIntent(context,appWidgetId))
            }
            val event = (renderData as? WidgetRenderData.Ready)?.resolve(configuration)
            val doc = WidgetLayoutStore(context).load(appWidgetId,event?.id)
            val layout = doc.layouts.getValue(WidgetLayoutSize.fromDimensions(widthDp,heightDp).key)
            val title = if (renderData is WidgetRenderData.Failure) displayContext.getString(R.string.widget_data_error) else displayContext.getString(R.string.widget_select_event)
            return (if(doc.renderer=="bitmap") BitmapWidgetRenderer.remoteViews(displayContext,event,today,layout,widthDp,heightDp,title,bitmapBudget)
                else NativeWidgetRenderer.remoteViews(displayContext,event,today,layout,widthDp,heightDp,title)).apply {
                setOnClickPendingIntent(R.id.widgetRoot,if (event != null) editPendingIntent(context,appWidgetId,event.id) else if(configuration?.eventId==WidgetPinning.NO_EVENT_ID) configurePendingIntent(context,appWidgetId) else openAppPendingIntent(context,appWidgetId))
            }
        }

        private fun editPendingIntent(context: Context, appWidgetId: Int, eventId: String): PendingIntent {
            val intent = com.iqonli.ezcd.ui.EventNavigation.fromHome(context, eventId)
                .setData(Uri.parse("ezcd://widget/$appWidgetId/event/$eventId"))
            return PendingIntent.getActivity(context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun configurePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = Intent(context, WidgetConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .setData(Uri.parse("ezcd://widget/$appWidgetId/configure"))
            return PendingIntent.getActivity(context, CONFIG_REQUEST_CODE_OFFSET + appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun openAppPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, WidgetConfigActivity::class.java)
            intent.setData(Uri.parse("ezcd://widget/$appWidgetId/data-error"))
            return PendingIntent.getActivity(context, DATA_ERROR_REQUEST_CODE_OFFSET + appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private const val DEFAULT_MIN_WIDTH_DP = 56
        private const val DEFAULT_MIN_HEIGHT_DP = 50
        private const val MIN_RENDER_DIMENSION_DP = 1
        private const val CONFIG_REQUEST_CODE_OFFSET = 100_000
        private const val DATA_ERROR_REQUEST_CODE_OFFSET = 200_000
    }

}
