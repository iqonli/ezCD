package com.iqonli.ezcd.widget

import android.content.Intent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRefreshActionPolicyTest {
    @Test
    fun scheduledDailyRefreshActionIsAccepted() {
        assertTrue(
            WidgetRefreshActionPolicy.shouldRefresh(WidgetUpdateScheduler.ACTION_DAILY_REFRESH),
        )
    }

    @Test
    fun supportedSystemRefreshActionsRemainAccepted() {
        listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED,
        ).forEach { action ->
            assertTrue(WidgetRefreshActionPolicy.shouldRefresh(action))
        }
    }

    @Test
    fun unrelatedOrMissingActionsAreRejected() {
        assertFalse(WidgetRefreshActionPolicy.shouldRefresh(null))
        assertFalse(WidgetRefreshActionPolicy.shouldRefresh("com.example.UNRELATED"))
    }
}
