// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.content.Context

data class WidgetStyleSelection(
    val appearance: WidgetAppearance = WidgetAppearance.SYSTEM,
    val background: WidgetBackground = WidgetBackground.CLASSIC,
) {
    companion object {
        val FACTORY = WidgetStyleSelection()
    }
}

internal object WidgetStyleSelectionResolver {
    fun initial(
        existing: WidgetStyleSelection?,
        defaults: WidgetStyleSelection,
    ): WidgetStyleSelection = existing ?: defaults

    fun restore(
        baseline: WidgetStyleSelection,
        appearanceName: String?,
        backgroundName: String?,
    ): WidgetStyleSelection = WidgetStyleSelection(
        appearance = WidgetAppearance.entries.firstOrNull { it.name == appearanceName }
            ?: baseline.appearance,
        background = WidgetBackground.entries.firstOrNull { it.name == backgroundName }
            ?: baseline.background,
    )
}

class WidgetDefaultsPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        FILE_NAME,
        Context.MODE_PRIVATE,
    )

    fun get(): WidgetStyleSelection = WidgetStyleSelection(
        appearance = WidgetAppearance.fromStorageKey(preferences.getString(KEY_APPEARANCE, null)),
        background = WidgetBackground.fromStorageKey(preferences.getString(KEY_BACKGROUND, null)),
    )

    fun save(selection: WidgetStyleSelection) {
        preferences.edit()
            .putString(KEY_APPEARANCE, selection.appearance.storageKey)
            .putString(KEY_BACKGROUND, selection.background.storageKey)
            .apply()
    }

    private companion object {
        const val FILE_NAME = "ezcd_widget_defaults"
        const val KEY_APPEARANCE = "appearance"
        const val KEY_BACKGROUND = "background"
    }
}
