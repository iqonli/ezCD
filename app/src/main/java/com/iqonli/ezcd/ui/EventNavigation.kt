// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import android.content.Context
import android.content.Intent

/** External event entry always reuses the home task and clears previous editors above it. */
object EventNavigation {
    const val EXTRA_OPEN_EVENT = "open_event_from_home"
    fun fromHome(context: Context, eventId: String): Intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_OPEN_EVENT, eventId)
}
