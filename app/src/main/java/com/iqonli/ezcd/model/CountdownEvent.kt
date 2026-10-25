// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.model

import java.time.Instant
import java.time.LocalDate

data class CountdownEvent(
    val id: String,
    val title: String,
    val date: LocalDate,
    val type: EventType,
    val icon: EventIcon = EventIcon.defaultFor(type),
    val reminder: ReminderOption = ReminderOption.OFF,
    val createdAt: Instant,
    val repeatRule: RepeatRule = RepeatRule.NONE,
    val countMode: CountMode = CountMode.COUNT_DOWN,
    val includeToday: Boolean = false,
    val cardColor: Int = OpaqueColor.WHITE,
    val countColor: Int = OpaqueColor.BLUE,
    val reminderMinuteOfDay: Int = 9 * 60,
    val cardColorDefault: Boolean = false,
    val countColorDefault: Boolean = false,
    val cardColorCustom: Boolean = false,
    val countColorCustom: Boolean = false,
)
