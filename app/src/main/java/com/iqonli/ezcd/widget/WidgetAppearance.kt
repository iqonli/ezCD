// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

enum class WidgetAppearance(val storageKey: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    fun resolveDark(systemDark: Boolean): Boolean = false

    companion object {
        fun fromStorageKey(value: String?): WidgetAppearance =
            entries.firstOrNull { it.storageKey == value } ?: SYSTEM
    }
}
