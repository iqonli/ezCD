// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.notification

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.countdown.CountdownTimeSnapshot
import com.iqonli.ezcd.data.CountdownLoadResult
import com.iqonli.ezcd.data.CountdownRepository
import java.time.LocalDate
import java.time.Instant
import java.time.ZonedDateTime

object ArrivalNotificationScheduler {
    const val ACTION_ARRIVAL_CHECK = "com.iqonli.ezcd.notification.ARRIVAL_CHECK"
    const val CHANNEL_ID = "countdown_arrivals"

    private var planned: ArrivalReminderTiming.Plan? = null

    @Synchronized
    fun ensureScheduled(
        context: Context,
        snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
    ) {
        if (!canPostNotifications(context)) {
            cancel(context)
            return
        }

        val events = when (val result = CountdownRepository(context).loadResult()) {
            is CountdownLoadResult.Success -> result.events
            is CountdownLoadResult.Failure -> return
        }
        val state = ArrivalNotificationState(context)
        val next = events.mapNotNull { event ->
            val date = ArrivalNotificationPolicy.nextPendingDate(
                event, snapshot.today, state::wasDelivered, state::canAttempt,
            ) ?: return@mapNotNull null
            val millis = triggerMillis(snapshot.now, date, state.lastFailure(event, date), event.reminderMinuteOfDay)
                ?: return@mapNotNull null
            ArrivalReminderTiming.Plan(millis, date, snapshot.now.zone.id)
        }.minByOrNull { it.triggerMillis } ?: run {
            cancel(context)
            return
        }
        if (ArrivalReminderTiming.keepEarlier(planned, next)) return
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                next.triggerMillis,
                pendingIntent(context),
            )
            planned = next
        }.onFailure {
            cancel(context)
        }
    }

    @Synchronized
    fun invalidatePlan() {
        planned = null
    }

    @Synchronized
    fun cancel(context: Context) {
        planned = null
        runCatching {
            context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context))
        }
    }

    fun hasNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun canPostNotifications(context: Context): Boolean {
        if (!hasNotificationPermission(context)) return false

        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false

        val channel = manager.getNotificationChannel(CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    internal fun triggerMillis(now: ZonedDateTime, eventDate: LocalDate, lastFailure: Instant? = null, reminderMinuteOfDay: Int = 9 * 60): Long? =
        runCatching { ArrivalReminderTiming.triggerTime(now, eventDate, lastFailure, reminderMinuteOfDay).toInstant().toEpochMilli() }.getOrNull()

    internal fun triggerTime(now: ZonedDateTime, eventDate: LocalDate): ZonedDateTime =
        ArrivalReminderTiming.triggerTime(now, eventDate)

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ArrivalNotificationReceiver::class.java).setAction(ACTION_ARRIVAL_CHECK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private const val REQUEST_CODE = 41_900
}
