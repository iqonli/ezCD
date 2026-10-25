// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.data.CountdownIo

class NotificationBlockStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val blocked = intent.getBooleanExtra(NotificationBlockStatePolicy.EXTRA_BLOCKED_STATE, true)
        val channelId = intent.getStringExtra(NotificationBlockStatePolicy.EXTRA_NOTIFICATION_CHANNEL_ID)

        if (!NotificationBlockStatePolicy.shouldReschedule(action, blocked, channelId)) return

        val pending = goAsync()
        val appContext = context.applicationContext
        val snapshot = CountdownTime.snapshot()
        CountdownIo.execute {
            try {
                ArrivalNotificationScheduler.ensureScheduled(appContext, snapshot)
            } finally {
                pending.finish()
            }
        }
    }
}

internal object NotificationBlockStatePolicy {
    const val ACTION_APP_BLOCK_STATE_CHANGED = "android.app.action.APP_BLOCK_STATE_CHANGED"
    const val ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED =
        "android.app.action.NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED"
    const val EXTRA_BLOCKED_STATE = "android.app.extra.BLOCKED_STATE"
    const val EXTRA_NOTIFICATION_CHANNEL_ID = "android.app.extra.NOTIFICATION_CHANNEL_ID"

    fun shouldReschedule(action: String?, blocked: Boolean, channelId: String?): Boolean = when (action) {
        ACTION_APP_BLOCK_STATE_CHANGED -> !blocked
        ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED ->
            !blocked && channelId == ArrivalNotificationScheduler.CHANNEL_ID
        else -> false
    }
}
