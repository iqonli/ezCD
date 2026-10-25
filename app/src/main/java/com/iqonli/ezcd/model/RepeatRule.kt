// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.model

enum class RepeatRule(val storageKey: String) {
    NONE("none"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly");

    companion object {
        fun fromStorageKey(value: String): RepeatRule? =
            entries.firstOrNull { it.storageKey == value }
    }
}
