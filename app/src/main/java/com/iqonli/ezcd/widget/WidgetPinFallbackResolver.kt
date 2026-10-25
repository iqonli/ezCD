// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

internal sealed class WidgetPinFallbackOutcome {
    object None : WidgetPinFallbackOutcome()
    data class Single(val appWidgetId: Int) : WidgetPinFallbackOutcome()
    data class Multiple(val appWidgetIds: Set<Int>) : WidgetPinFallbackOutcome()
}

internal object WidgetPinFallbackResolver {
    fun resolve(
        baselineIds: Set<Int>,
        currentIds: Set<Int>,
    ): WidgetPinFallbackOutcome {
        val added = currentIds - baselineIds
        return when (added.size) {
            0 -> WidgetPinFallbackOutcome.None
            1 -> WidgetPinFallbackOutcome.Single(added.first())
            else -> WidgetPinFallbackOutcome.Multiple(added)
        }
    }
}
