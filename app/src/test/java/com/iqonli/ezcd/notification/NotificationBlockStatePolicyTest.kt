package com.iqonli.ezcd.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBlockStatePolicyTest {
    @Test
    fun appUnblockReschedulesPendingReminders() {
        assertTrue(
            NotificationBlockStatePolicy.shouldReschedule(
                NotificationBlockStatePolicy.ACTION_APP_BLOCK_STATE_CHANGED,
                blocked = false,
                channelId = null,
            ),
        )
        assertFalse(
            NotificationBlockStatePolicy.shouldReschedule(
                NotificationBlockStatePolicy.ACTION_APP_BLOCK_STATE_CHANGED,
                blocked = true,
                channelId = null,
            ),
        )
    }

    @Test
    fun onlyTheCountAwayChannelReschedulesWhenUnblocked() {
        assertTrue(
            NotificationBlockStatePolicy.shouldReschedule(
                NotificationBlockStatePolicy.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED,
                blocked = false,
                channelId = ArrivalNotificationScheduler.CHANNEL_ID,
            ),
        )
        assertFalse(
            NotificationBlockStatePolicy.shouldReschedule(
                NotificationBlockStatePolicy.ACTION_NOTIFICATION_CHANNEL_BLOCK_STATE_CHANGED,
                blocked = false,
                channelId = "other",
            ),
        )
    }
}
