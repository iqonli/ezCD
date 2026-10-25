// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.countdown.CountdownTimeSnapshot

object WidgetUpdateScheduler {
    const val ACTION_DAILY_REFRESH = "com.iqonli.ezcd.widget.DAILY_REFRESH"

    fun ensureScheduled(
        context: Context,
        snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
    ) {
        if (!hasWidgets(context)) {
            cancel(context)
            return
        }

        val nextRefreshMillis=WidgetMidnightRefresh.nextMillis(snapshot)
        val manager=context.getSystemService(AlarmManager::class.java)
        val operation=pendingIntent(context)
        // Calendar day changes are user-visible; an inexact 15-minute window is not sufficient.
        val exact=android.os.Build.VERSION.SDK_INT<31 || manager.canScheduleExactAlarms()
        if(exact) {
            try { manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,nextRefreshMillis,operation);return }
            catch (_: SecurityException) { /* Permission can be revoked between check and call. */ }
        }
        runCatching { manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,nextRefreshMillis,operation) }
            .onFailure { android.util.Log.e("ezCD","Cannot schedule widget date refresh",it) }
    }

    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        }
    }

    private fun hasWidgets(context: Context): Boolean {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, CountdownWidgetProvider::class.java)
        return manager.getAppWidgetIds(component).isNotEmpty()
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, WidgetRefreshReceiver::class.java).setAction(ACTION_DAILY_REFRESH).addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )


}

internal object WidgetMidnightRefresh {
    fun nextMillis(snapshot: CountdownTimeSnapshot)=snapshot.today.plusDays(1).atStartOfDay(snapshot.zone).plusSeconds(1).toInstant().toEpochMilli()
    fun stale(context: Context,snapshot: CountdownTimeSnapshot)=context.getSharedPreferences("widget_date",Context.MODE_PRIVATE)
        .getString("rendered",null)!=snapshot.today.toString()+"@"+snapshot.zone.id
    fun rendered(context: Context,snapshot: CountdownTimeSnapshot) { context.getSharedPreferences("widget_date",Context.MODE_PRIVATE).edit()
        .putString("rendered",snapshot.today.toString()+"@"+snapshot.zone.id).apply() }
}
