// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.countdown

/** Decoration follows the resolved occurrence, never the record's title or category. */
enum class ArrivalStage {
    NONE, THREE_DAYS, TWO_DAYS, TOMORROW, TODAY;

    companion object {
        fun from(status: CountdownStatus?): ArrivalStage = when (status) {
            CountdownStatus.THREE_DAYS -> THREE_DAYS
            CountdownStatus.TWO_DAYS -> TWO_DAYS
            CountdownStatus.TOMORROW -> TOMORROW
            CountdownStatus.TODAY -> TODAY
            else -> NONE
        }
    }
}
