// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName

internal object WidgetInstanceValidator {
    fun isOwnedBy(
        manager: AppWidgetManager,
        appWidgetId: Int,
        expectedProvider: ComponentName,
    ): Boolean {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return false
        val actualProvider = runCatching { manager.getAppWidgetInfo(appWidgetId)?.provider }.getOrNull()
        return matchesProvider(appWidgetId, actualProvider, expectedProvider)
    }

    internal fun matchesProvider(
        appWidgetId: Int,
        actualProvider: ComponentName?,
        expectedProvider: ComponentName,
    ): Boolean =
        appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID &&
            actualProvider == expectedProvider
}
