// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.countdown.CountdownTimeSnapshot
import com.iqonli.ezcd.data.CountdownIo
import com.iqonli.ezcd.data.CountdownLoadResult
import com.iqonli.ezcd.data.CountdownRepository
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.ui.EditorActivity
import com.iqonli.ezcd.ui.LanguageManager

class ArrivalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CountdownIo.execute {
            try {
                val snapshot = CountdownTime.snapshot()
                ArrivalNotificationScheduler.invalidatePlan()
                ArrivalNotifier.notifyDueEvents(appContext, snapshot)
                ArrivalNotificationScheduler.ensureScheduled(appContext, snapshot)
            } finally {
                pending.finish()
            }
        }
    }
}

object ArrivalNotifier {
    fun notifyDueEvents(
        context: Context,
        snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
    ) {
        if (!ArrivalNotificationScheduler.hasNotificationPermission(context)) return

        val displayContext = LanguageManager.localizedContext(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ArrivalNotificationScheduler.CHANNEL_ID,
                displayContext.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        if (!ArrivalNotificationScheduler.canPostNotifications(context)) return

        val events = when (val result = CountdownRepository(context).loadResult()) {
            is CountdownLoadResult.Success -> result.events
            is CountdownLoadResult.Failure -> return
        }
        val state = ArrivalNotificationState(context)
        val dueEvents = events.mapNotNull { event ->
            val scheduledDate = ArrivalNotificationPolicy.scheduledDate(event, snapshot.today)
                ?: return@mapNotNull null
            if (
                ArrivalNotificationPolicy.isDue(
                    event,
                    snapshot.today,
                    state.deliveredDate(event.id),
                ) &&
                state.canAttempt(event, scheduledDate) &&
                !ArrivalReminderTiming.triggerTime(snapshot.now, scheduledDate,
                    state.lastFailure(event, scheduledDate), event.reminderMinuteOfDay).isAfter(snapshot.now)
            ) {
                event to scheduledDate
            } else {
                null
            }
        }
        if (dueEvents.isEmpty()) return

        dueEvents.forEach { (event, scheduledDate) ->
            val daysBefore = event.reminder.daysBefore ?: return@forEach
            val title: String
            val body: String
            if (daysBefore == 0) {
                title = displayContext.getString(R.string.notification_arrival_title)
                body = displayContext.getString(R.string.notification_arrival_body, event.title)
            } else {
                title = displayContext.getString(R.string.notification_upcoming_title)
                body = displayContext.resources.getQuantityString(
                    R.plurals.notification_upcoming_body,
                    daysBefore,
                    daysBefore,
                    event.title,
                )
            }

            val notification = Notification.Builder(context, ArrivalNotificationScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_event_star)
                .setColor(context.getColor(R.color.accent))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(Notification.BigTextStyle().bigText(body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(editPendingIntent(context, event.id))
                .build()

            runCatching {
                manager.notify(
                    ArrivalNotificationIdentity.tag(event.id),
                    ArrivalNotificationIdentity.ID,
                    notification,
                )
            }.onSuccess {
                if (ArrivalNotificationScheduler.canPostNotifications(context)) {
                    state.markDelivered(event, scheduledDate)
                } else {
                    state.recordFailure(event, scheduledDate, snapshot.now.toInstant())
                }
            }.onFailure {
                state.recordFailure(event, scheduledDate, snapshot.now.toInstant())
            }
        }
    }

    fun cancelEvent(context: Context, eventId: String) {
        runCatching {
            context.getSystemService(NotificationManager::class.java).cancel(
                ArrivalNotificationIdentity.tag(eventId),
                ArrivalNotificationIdentity.ID,
            )
        }
    }

    fun cancelAllEventNotifications(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching {
            manager.activeNotifications
                .filter { notification -> notification.id == ArrivalNotificationIdentity.ID }
                .forEach { notification -> manager.cancel(notification.tag, notification.id) }
        }
    }

    fun reconcileVisibleNotifications(
        context: Context,
        events: List<CountdownEvent>,
        snapshot: CountdownTimeSnapshot = CountdownTime.snapshot(),
    ) {
        val state = ArrivalNotificationState(context)
        val keepTags = events.mapNotNullTo(HashSet()) { event ->
            val scheduledDate = ArrivalNotificationPolicy.scheduledDate(event, snapshot.today)
            if (
                scheduledDate == snapshot.today &&
                state.deliveredDate(event.id) == scheduledDate
            ) {
                ArrivalNotificationIdentity.tag(event.id)
            } else {
                null
            }
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        runCatching {
            manager.activeNotifications
                .filter { notification ->
                    notification.id == ArrivalNotificationIdentity.ID &&
                        notification.tag !in keepTags
                }
                .forEach { notification -> manager.cancel(notification.tag, notification.id) }
        }
    }

    private fun editPendingIntent(context: Context, eventId: String): PendingIntent {
        val intent = com.iqonli.ezcd.ui.EventNavigation.fromHome(context, eventId)
            .setData(Uri.parse("ezcd://arrival/$eventId"))
        return PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
