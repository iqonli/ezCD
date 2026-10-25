// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.data.CountdownLoadResult
import com.iqonli.ezcd.data.CountdownRepository

internal enum class WidgetPinFinalizeResult {
    APPLIED,
    ALREADY_MATCHING,
    CONFLICT,
    EVENT_MISSING,
    INVALID_WIDGET_ID,
}

internal object WidgetPinFinalizer {
    @Synchronized
    fun finalize(
        context: Context,
        appWidgetId: Int,
        snapshot: WidgetPinRequestSnapshot,
    ): WidgetPinFinalizeResult {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return WidgetPinFinalizeResult.INVALID_WIDGET_ID
        }

        val appContext = context.applicationContext
        val preferences = WidgetPreferences(appContext)
        val existing = preferences.get(appWidgetId)
        if (existing != null) {
            return if (WidgetPinResultPolicy.matchesSnapshot(existing, snapshot)) {
                WidgetDefaultsPreferences(appContext).save(snapshot.style)
                WidgetPinFinalizeResult.ALREADY_MATCHING
            } else {
                WidgetPinFinalizeResult.CONFLICT
            }
        }

        val empty=snapshot.eventId in listOf(WidgetPinning.PLACEHOLDER_ID,WidgetPinning.NO_EVENT_ID)
        val eventExists = empty || when (val result = CountdownRepository(appContext).loadResult()) {
            is CountdownLoadResult.Success -> result.events.any { it.id == snapshot.eventId }
            is CountdownLoadResult.Failure -> false
        }
        if (!WidgetPinResultPolicy.shouldApply(
                validWidgetId = true,
                eventExists = eventExists,
                existing = null,
            )
        ) {
            return WidgetPinFinalizeResult.EVENT_MISSING
        }

        // Persist the frozen layout before publishing the widget binding.
        if(snapshot.customLayout) {
            val document=WidgetPinAttemptStore.document(appContext,snapshot.requestToken) ?: return WidgetPinFinalizeResult.CONFLICT
            WidgetLayoutStore(appContext).save(appWidgetId,snapshot.eventId,document)
        }
        preferences.save(
            appWidgetId = appWidgetId,
            eventId = snapshot.eventId,
            appearance = snapshot.style.appearance,
            background = snapshot.style.background,
            eventSelection = WidgetEventSelection.FIXED,
        )
        WidgetDefaultsPreferences(appContext).save(snapshot.style)

        val manager = AppWidgetManager.getInstance(appContext)
        val provider = ComponentName(appContext, CountdownWidgetProvider::class.java)
        if (WidgetInstanceValidator.isOwnedBy(manager, appWidgetId, provider)) {
            val timeSnapshot = CountdownTime.snapshot()
            CountdownWidgetProvider.updateWidget(appContext, manager, appWidgetId, timeSnapshot)
            WidgetUpdateScheduler.ensureScheduled(appContext, timeSnapshot)
        } else {
            WidgetUpdateScheduler.ensureScheduled(appContext, CountdownTime.snapshot())
        }

        return WidgetPinFinalizeResult.APPLIED
    }
}
