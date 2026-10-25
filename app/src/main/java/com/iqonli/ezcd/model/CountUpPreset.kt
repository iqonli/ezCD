// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.model

/** Creation shortcuts only; event JSON retains the existing type and icon keys. */
enum class CountUpPreset(val icon: EventIcon) {
    SMOKE_FREE(EventIcon.HEART),
    NEW_HABIT(EventIcon.STAR),
    TRAINING(EventIcon.FLAG),
    READING(EventIcon.STAR),
    PROJECT(EventIcon.CALENDAR),
    FRESH_START(EventIcon.GIFT),
    MILESTONE(EventIcon.PIN),
    EVENT(EventIcon.CALENDAR),
    CUSTOM(EventIcon.STAR);

    val type: EventType
        get() = if (this == EVENT) EventType.EVENT else EventType.CUSTOM

    val suggestsTitle: Boolean
        get() = this != EVENT && this != CUSTOM

    fun titleFor(currentTitle: String, suggestion: String): String =
        if (suggestsTitle && currentTitle.isBlank()) suggestion else currentTitle

    companion object {
        fun forStoredType(type: EventType): CountUpPreset? = when (type) {
            EventType.EVENT -> EVENT
            EventType.CUSTOM -> CUSTOM
            else -> null
        }
    }
}
