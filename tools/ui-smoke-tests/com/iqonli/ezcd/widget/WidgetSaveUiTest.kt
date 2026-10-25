// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget
import android.app.AlertDialog
import android.appwidget.*
import android.content.*
import android.content.pm.ActivityInfo
import android.os.*
import android.view.*
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.time.*
import java.util.concurrent.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],shadows=[WidgetConfigActivityUiTest.FrameworkTabHost::class,WidgetConfigActivityUiTest.FrameworkTabSpec::class])
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetSaveUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as android.app.Application
    private val event=CountdownEvent("save-test","Event",LocalDate.now(),EventType.EVENT,createdAt=Instant.EPOCH)
    private fun flush() { val latch=CountDownLatch(1);CountdownIo.execute { latch.countDown() };assertTrue(latch.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun open(): org.robolectric.android.controller.ActivityController<WidgetConfigActivity> {
        CountdownRepository(context).save(listOf(event))
        val info=AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) }
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(81,info)
        WidgetPreferences(context).save(81,event.id,WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED)
        return Robolectric.buildActivity(WidgetConfigActivity::class.java,Intent(context,WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,81)).setup().also { flush() }
    }
    private fun change(activity: WidgetConfigActivity) {
        all(activity.window.decorView).filterIsInstance<CheckBox>().first { it.text == activity.getString(R.string.ez_layout_visible) }.performClick()
    }
    private fun saveMenu(activity: WidgetConfigActivity): MenuItem {
        val menu=PopupMenu(activity,View(activity)).menu;activity.onCreateOptionsMenu(menu);activity.onPrepareOptionsMenu(menu)
        return menu.findItem(R.id.widget_save_action)
    }
    @Suppress("DEPRECATION")
    @Test fun backConfirmsChangesAndDiscardLeavesStoredLayoutAlone() {
        open().use { controller ->
            val a=controller.get();val original=WidgetLayoutStore(context).load(81,event.id)
            assertTrue(saveMenu(a).isEnabled)
            assertFalse(all(a.window.decorView).filterIsInstance<Button>().any { it.text == a.getString(R.string.ez_layout_apply) })
            change(a);a.onBackPressed();val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(a.isFinishing);assertEquals(original,WidgetLayoutStore(context).load(81,event.id))
        }
    }
    @Suppress("DEPRECATION")
    @Test fun backSaveWritesChangesAndOnlyThenFinishes() {
        open().use { c ->
            val a=c.get();change(a);a.onBackPressed()
            val d=org.robolectric.shadows.ShadowDialog.getLatestDialog() as AlertDialog
            d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();flush();flush()
            assertTrue(a.isFinishing)
            assertFalse(WidgetLayoutStore(context).load(81,event.id).layouts.getValue("small").elements.first().visible)
        }
    }
    @Test fun savingFromTopBarSurvivesRecreation() {
        open().use { c ->
            change(c.get());c.get().onOptionsItemSelected(saveMenu(c.get()));c.recreate();flush();flush()
            assertTrue(c.get().isFinishing)
            assertFalse(WidgetLayoutStore(context).load(81,event.id).layouts.getValue("small").elements.first().visible)
        }
    }
    @Test fun editorUsesANormalActivityAndCanAppearInRecentTasks() {
        open().use { c ->
            val a=c.get();val info=context.packageManager.getActivityInfo(ComponentName(context,WidgetConfigActivity::class.java),0)
            assertEquals(0,info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS)
            val out=android.util.TypedValue();a.theme.resolveAttribute(android.R.attr.windowIsFloating,out,true)
            assertEquals(0,out.data)
        }
    }
}
