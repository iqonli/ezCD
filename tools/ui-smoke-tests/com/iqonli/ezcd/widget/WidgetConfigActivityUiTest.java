// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.TabHost;
import com.iqonli.ezcd.data.CountdownIo;
import com.iqonli.ezcd.data.CountdownRepository;
import com.iqonli.ezcd.model.*;
import com.iqonli.ezcd.ui.EditorActivity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import kotlin.Unit;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowViewGroup;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33, shadows = {WidgetConfigActivityUiTest.FrameworkTabHost.class, WidgetConfigActivityUiTest.FrameworkTabSpec.class})
@LooperMode(LooperMode.Mode.PAUSED)
public class WidgetConfigActivityUiTest {
    // Robolectric's default ShadowTabHost skips framework tab inflation and masks constructor bugs.
    // Use Android's actual TabHost implementation while keeping the standard ViewGroup support.
    @Implements(TabHost.class)
    public static class FrameworkTabHost extends ShadowViewGroup {}
    @Implements(TabHost.TabSpec.class)
    public static class FrameworkTabSpec {}
    private ActivityController<WidgetConfigActivity> open() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        CountdownEvent event = new CountdownEvent("ui-smoke", "UI smoke", LocalDate.of(2026, 10, 24),
            EventType.EVENT, EventIcon.CALENDAR, ReminderOption.OFF, Instant.EPOCH, RepeatRule.NONE,
            CountMode.COUNT_DOWN, false, -1, 0xFF2196F3, 540, false, false, false, false);
        new CountdownRepository(context).save(Collections.singletonList(event));
        android.appwidget.AppWidgetProviderInfo info = new android.appwidget.AppWidgetProviderInfo();
        info.provider = new android.content.ComponentName(context, CountdownWidgetProvider.class);
        shadowOf(android.appwidget.AppWidgetManager.getInstance(context)).addBoundWidget(71, info);
        new WidgetPreferences(context).save(71,event.getId(),WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED);
        Intent intent = new Intent(context, WidgetConfigActivity.class)
            .putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID, 71);
        ActivityController<WidgetConfigActivity> controller = Robolectric.buildActivity(WidgetConfigActivity.class, intent).setup();
        flushIoAndMain();
        assertFalse("The editor must remain open", controller.get().isFinishing());
        return controller;
    }
    private void flushIoAndMain() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        CountdownIo.INSTANCE.execute(() -> { latch.countDown(); return Unit.INSTANCE; });
        assertTrue("Background repository load must complete", latch.await(10, TimeUnit.SECONDS));
        shadowOf(Looper.getMainLooper()).idle();
    }
    private TabHost tabs(WidgetConfigActivity activity) {
        View tabWidget = activity.findViewById(android.R.id.tabs);
        assertNotNull("The three native tabs must be attached", tabWidget);
        return (TabHost) tabWidget.getParent().getParent();
    }
    @Test public void opensFromDesktopWidgetAndBuildsAllThreeSizeTabs() throws Exception {
        try (ActivityController<WidgetConfigActivity> controller = open()) {
            TabHost host = tabs(controller.get());
            assertEquals(3, host.getTabWidget().getTabCount());
            assertEquals("small", host.getCurrentTabTag());
            assertFalse(controller.get().getCurrentFocus() instanceof EditText);
        }
    }
    @Test public void switchingEverySizeKeepsTheActivityOpen() throws Exception {
        try (ActivityController<WidgetConfigActivity> controller = open()) {
            TabHost host = tabs(controller.get());
            for (String size : new String[]{"medium", "large", "small"}) {
                host.setCurrentTabByTag(size);
                shadowOf(Looper.getMainLooper()).idle();
                assertEquals(size, host.getCurrentTabTag());
                assertFalse(controller.get().isFinishing());
            }
        }
    }
    @Test public void recreationRetainsTheSelectedSize() throws Exception {
        try (ActivityController<WidgetConfigActivity> controller = open()) {
            tabs(controller.get()).setCurrentTabByTag("large");
            controller.recreate();
            flushIoAndMain();
            assertEquals("large", tabs(controller.get()).getCurrentTabTag());
        }
    }
}
