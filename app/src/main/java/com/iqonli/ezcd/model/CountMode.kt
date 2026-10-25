// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.model

enum class CountMode(val storageKey: String) {
    COUNT_DOWN("count_down"),
    COUNT_UP("count_up");

    companion object {
        fun fromStorageKey(value: String): CountMode? =
            entries.firstOrNull { it.storageKey == value }
    }
}
