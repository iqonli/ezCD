// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

enum class WidgetEventSelection(val storageKey: String) {
    FIXED("fixed"),
    NEXT("next");

    companion object {
        fun fromStorageKey(value: String?): WidgetEventSelection =
            entries.firstOrNull { it.storageKey == value } ?: FIXED
    }
}
