// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.notification

import android.content.Context
import com.iqonli.ezcd.model.CountdownEvent
import java.time.LocalDate
import java.time.Instant

class ArrivalNotificationState(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun deliveredDate(eventId: String): LocalDate? = preferences.getString(deliveredKey(eventId), null)
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun wasDelivered(event: CountdownEvent, scheduledDate: LocalDate): Boolean =
        deliveredDate(event.id) == scheduledDate

    fun canAttempt(event: CountdownEvent, scheduledDate: LocalDate): Boolean {
        val failedDate = failedDate(event.id)
        if (failedDate != scheduledDate) return true
        return preferences.getInt(failureCountKey(event.id), 0) < MAX_DELIVERY_ATTEMPTS
    }

    fun markDelivered(event: CountdownEvent, scheduledDate: LocalDate) {
        preferences.edit()
            .putString(deliveredKey(event.id), scheduledDate.toString())
            .remove(failedDateKey(event.id))
            .remove(failureCountKey(event.id))
            .remove(failureInstantKey(event.id))
            .apply()
    }

    fun lastFailure(event: CountdownEvent, scheduledDate: LocalDate): Instant? =
        if (failedDate(event.id) != scheduledDate) null else
            preferences.getString(failureInstantKey(event.id), null)
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }

    fun recordFailure(event: CountdownEvent, scheduledDate: LocalDate, at: Instant = Instant.now()) {
        val previousDate = failedDate(event.id)
        val previousCount = if (previousDate == scheduledDate) {
            preferences.getInt(failureCountKey(event.id), 0)
        } else {
            0
        }
        preferences.edit()
            .putString(failedDateKey(event.id), scheduledDate.toString())
            .putString(failureInstantKey(event.id), at.toString())
            .putInt(
                failureCountKey(event.id),
                (previousCount + 1).coerceAtMost(MAX_DELIVERY_ATTEMPTS),
            )
            .apply()
    }

    fun remove(eventId: String) {
        preferences.edit()
            .remove(deliveredKey(eventId))
            .remove(failedDateKey(eventId))
            .remove(failureCountKey(eventId))
            .remove(failureInstantKey(eventId))
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun failedDate(eventId: String): LocalDate? =
        preferences.getString(failedDateKey(eventId), null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun failureInstantKey(eventId: String): String = "failure_instant_$eventId"
    private fun deliveredKey(eventId: String): String = "$DELIVERED_KEY_PREFIX$eventId"
    private fun failedDateKey(eventId: String): String = "$FAILED_DATE_KEY_PREFIX$eventId"
    private fun failureCountKey(eventId: String): String = "$FAILURE_COUNT_KEY_PREFIX$eventId"

    companion object {
        const val MAX_DELIVERY_ATTEMPTS = 3

        private const val PREFS_NAME = "arrival_notifications"
        private const val DELIVERED_KEY_PREFIX = "delivered_"
        private const val FAILED_DATE_KEY_PREFIX = "failed_date_"
        private const val FAILURE_COUNT_KEY_PREFIX = "failure_count_"
    }
}
