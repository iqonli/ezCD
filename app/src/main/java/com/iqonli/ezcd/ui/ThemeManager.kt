// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.ui
import android.app.Activity
import android.content.Context
import android.content.res.Configuration

object ThemeManager {
    // Legacy enum is kept for source compatibility; ezCD exposes only light mode.
    enum class AppTheme { SYSTEM, LIGHT, DARK }
    fun wrap(context: Context): Context {
        val configuration = Configuration(context.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
        return context.createConfigurationContext(configuration)
    }
    fun currentTheme(context: Context) = AppTheme.LIGHT
    fun setTheme(activity: Activity, theme: AppTheme) = Unit
}
