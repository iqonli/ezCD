// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.countdown

object ArrivalMood {
    fun marker(status: CountdownStatus): String? = when (status) {
        CountdownStatus.THREE_DAYS -> "🙂"
        CountdownStatus.TWO_DAYS -> "😬"
        CountdownStatus.TOMORROW -> "😱"
        CountdownStatus.TODAY -> "🎉"
        CountdownStatus.FUTURE,
        CountdownStatus.DONE,
        -> null
    }
}
