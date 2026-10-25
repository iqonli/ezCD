// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.ui

import com.iqonli.ezcd.R
import com.iqonli.ezcd.model.EventIcon
import com.iqonli.ezcd.model.EventType

object EventTypePresentation {
    fun labelRes(type: EventType): Int = when (type) {
        EventType.TRIP -> R.string.event_type_trip
        EventType.EXAM -> R.string.event_type_exam
        EventType.PARTY -> R.string.event_type_party
        EventType.BIRTHDAY -> R.string.event_type_birthday
        EventType.ANNIVERSARY -> R.string.event_type_anniversary
        EventType.CONCERT -> R.string.event_type_concert
        EventType.DEADLINE -> R.string.event_type_deadline
        EventType.EVENT -> R.string.event_type_event
        EventType.CUSTOM -> R.string.event_type_custom
    }

    fun iconRes(type: EventType): Int = EventIconPresentation.drawableRes(EventIcon.defaultFor(type))
}

object EventIconPresentation {
    fun drawableRes(icon: EventIcon): Int = when (icon) {
        EventIcon.AIRPLANE -> R.drawable.ic_event_airplane
        EventIcon.BOOK -> R.drawable.ic_event_book
        EventIcon.CONFETTI -> R.drawable.ic_event_confetti
        EventIcon.CAKE -> R.drawable.ic_event_cake
        EventIcon.HEART -> R.drawable.ic_event_heart
        EventIcon.MUSIC -> R.drawable.ic_event_music
        EventIcon.HOURGLASS -> R.drawable.ic_event_hourglass
        EventIcon.CALENDAR -> R.drawable.ic_event_calendar
        EventIcon.STAR -> R.drawable.ic_event_star
        EventIcon.GIFT -> R.drawable.ic_event_gift
        EventIcon.FLAG -> R.drawable.ic_event_flag
        EventIcon.PIN -> R.drawable.ic_event_pin
    }

    fun labelRes(icon: EventIcon): Int = when (icon) {
        EventIcon.AIRPLANE -> R.string.event_icon_airplane
        EventIcon.BOOK -> R.string.event_icon_book
        EventIcon.CONFETTI -> R.string.event_icon_confetti
        EventIcon.CAKE -> R.string.event_icon_cake
        EventIcon.HEART -> R.string.event_icon_heart
        EventIcon.MUSIC -> R.string.event_icon_music
        EventIcon.HOURGLASS -> R.string.event_icon_hourglass
        EventIcon.CALENDAR -> R.string.event_icon_calendar
        EventIcon.STAR -> R.string.event_icon_star
        EventIcon.GIFT -> R.string.event_icon_gift
        EventIcon.FLAG -> R.string.event_icon_flag
        EventIcon.PIN -> R.string.event_icon_pin
    }
}
