// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.data.CountdownIo
import com.iqonli.ezcd.data.CountdownLoadResult
import com.iqonli.ezcd.data.CountdownRepository
import com.iqonli.ezcd.notification.ArrivalNotificationScheduler
import com.iqonli.ezcd.notification.ArrivalNotifier

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!WidgetRefreshActionPolicy.shouldRefresh(intent?.action)) return

        if(intent?.action in setOf(Intent.ACTION_TIME_TICK,Intent.ACTION_SCREEN_ON,Intent.ACTION_USER_PRESENT) &&
            !WidgetMidnightRefresh.stale(context,CountdownTime.snapshot())) return
        val pending = goAsync()
        val appContext = context.applicationContext


        CountdownIo.execute {
            try {
                val snapshot=CountdownTime.snapshot()
                CountdownWidgetProvider.updateAllWidgets(appContext,snapshot)
                WidgetUpdateScheduler.ensureScheduled(appContext,snapshot)
                when (val result = CountdownRepository(appContext).loadResult()) {
                    is CountdownLoadResult.Success ->
                        ArrivalNotifier.reconcileVisibleNotifications(appContext, result.events, snapshot)
                    is CountdownLoadResult.Failure -> Unit
                }
                if (intent?.action in setOf(Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
                        Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) {
                    ArrivalNotificationScheduler.invalidatePlan()
                }
                ArrivalNotificationScheduler.ensureScheduled(appContext, snapshot)
            } catch(error: Exception) {
                android.util.Log.e("ezCD","Widget date refresh failed",error)
            } finally {
                runCatching { WidgetUpdateScheduler.ensureScheduled(appContext,CountdownTime.snapshot()) }
                pending.finish()
            }
        }
    }
}

internal object WidgetRefreshActionPolicy {
    private val refreshActions = setOf(
        WidgetUpdateScheduler.ACTION_DAILY_REFRESH,
        android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        Intent.ACTION_TIME_TICK,Intent.ACTION_SCREEN_ON,Intent.ACTION_USER_PRESENT,
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_DATE_CHANGED,
        Intent.ACTION_LOCALE_CHANGED,
    )

    fun shouldRefresh(action: String?): Boolean = action in refreshActions
}
