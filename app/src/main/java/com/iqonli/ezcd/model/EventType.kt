// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.model

enum class EventType(val storageKey: String) {
    TRIP("trip"),
    EXAM("exam"),
    PARTY("party"),
    BIRTHDAY("birthday"),
    ANNIVERSARY("anniversary"),
    CONCERT("concert"),
    DEADLINE("deadline"),
    EVENT("event"),
    CUSTOM("custom");

    companion object {
        fun fromStorageKey(value: String): EventType? = when (value) {
            "first_flight" -> TRIP
            else -> entries.firstOrNull { it.storageKey == value }
        }

        fun fromLegacyName(value: String): EventType? = when (value) {
            "FIRST_FLIGHT" -> TRIP
            else -> entries.firstOrNull { it.name == value }
        }
    }
}
