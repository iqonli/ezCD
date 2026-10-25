// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.notification

object ArrivalNotificationIdentity {
    const val ID = 1

    fun tag(eventId: String): String = eventId
}
