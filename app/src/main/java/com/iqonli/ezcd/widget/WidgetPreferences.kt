// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.content.Context

data class WidgetConfiguration(
    val eventId: String?,
    val appearance: WidgetAppearance,
    val background: WidgetBackground = WidgetBackground.CLASSIC,
    val eventSelection: WidgetEventSelection = WidgetEventSelection.FIXED,
)

class WidgetPreferences(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun get(appWidgetId: Int): WidgetConfiguration? {
        val eventSelection = WidgetEventSelection.fromStorageKey(
            preferences.getString(selectionKey(appWidgetId), null),
        )
        val eventId = preferences.getString(eventKey(appWidgetId), null)
        if (eventSelection == WidgetEventSelection.FIXED && eventId == null) return null

        return WidgetConfiguration(
            eventId = eventId,
            appearance = WidgetAppearance.fromStorageKey(preferences.getString(appearanceKey(appWidgetId), null)),
            background = WidgetBackground.fromStorageKey(preferences.getString(backgroundKey(appWidgetId), null)),
            eventSelection = eventSelection,
        )
    }

    fun save(
        appWidgetId: Int,
        eventId: String?,
        appearance: WidgetAppearance,
        background: WidgetBackground,
        eventSelection: WidgetEventSelection,
    ) {
        check(preferences.edit()
            .putString(eventKey(appWidgetId), eventId)
            .putString(appearanceKey(appWidgetId), appearance.storageKey)
            .putString(backgroundKey(appWidgetId), background.storageKey)
            .putString(selectionKey(appWidgetId), eventSelection.storageKey)
            .commit()) { "Cannot save widget binding" }
    }

    fun remap(oldAppWidgetId: Int, newAppWidgetId: Int) {
        if (oldAppWidgetId == newAppWidgetId) return
        val configuration = get(oldAppWidgetId)
        val editor = preferences.edit()
            .remove(eventKey(newAppWidgetId))
            .remove(appearanceKey(newAppWidgetId))
            .remove(backgroundKey(newAppWidgetId))
            .remove(selectionKey(newAppWidgetId))

        configuration?.let { current ->
            editor
                .putString(eventKey(newAppWidgetId), current.eventId)
                .putString(appearanceKey(newAppWidgetId), current.appearance.storageKey)
                .putString(backgroundKey(newAppWidgetId), current.background.storageKey)
                .putString(selectionKey(newAppWidgetId), current.eventSelection.storageKey)
        }

        editor
            .remove(eventKey(oldAppWidgetId))
            .remove(appearanceKey(oldAppWidgetId))
            .remove(backgroundKey(oldAppWidgetId))
            .remove(selectionKey(oldAppWidgetId))
            .apply()
    }

    fun remove(appWidgetId: Int) {
        preferences.edit()
            .remove(eventKey(appWidgetId))
            .remove(appearanceKey(appWidgetId))
            .remove(backgroundKey(appWidgetId))
            .remove(selectionKey(appWidgetId))
            .apply()
    }

    private fun eventKey(appWidgetId: Int) = "widget_${appWidgetId}_event"
    private fun appearanceKey(appWidgetId: Int) = "widget_${appWidgetId}_appearance"
    private fun backgroundKey(appWidgetId: Int) = "widget_${appWidgetId}_background"
    private fun selectionKey(appWidgetId: Int) = "widget_${appWidgetId}_selection"

    private companion object {
        const val FILE_NAME = "ezcd_widgets"
    }
}
