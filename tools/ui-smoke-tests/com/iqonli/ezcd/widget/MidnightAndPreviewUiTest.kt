package com.iqonli.ezcd.widget
import android.app.*
import android.appwidget.*
import android.content.*
import android.graphics.*
import android.os.*
import android.view.*
import android.widget.*
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.countdown.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlarmManager
import java.time.*
import java.util.concurrent.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MidnightAndPreviewUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as Application
    private fun flush() { val l=CountDownLatch(1);CountdownIo.execute { l.countDown() };assertTrue(l.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun bind(): AppWidgetManager {
        flush();val today=LocalDate.now();val e=CountdownEvent("midnight","Date",today.minusDays(2),EventType.EVENT,createdAt=Instant.EPOCH)
        CountdownRepository(context).save(listOf(e));val manager=AppWidgetManager.getInstance(context)
        Shadows.shadowOf(manager).addBoundWidget(191,AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) })
        WidgetPreferences(context).save(191,e.id,WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED)
        return manager
    }
    private fun daily()=Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.first {
        Shadows.shadowOf(it.operation).savedIntent.action==WidgetUpdateScheduler.ACTION_DAILY_REFRESH
    }
    @Test fun exactMidnightAlarmUpdatesExistingDesktopViewWithoutOpeningAnyActivity() {
        val manager=bind();ShadowAlarmManager.setCanScheduleExactAlarms(true)
        val before=CountdownTimeSnapshot(LocalDate.now().minusDays(1).atTime(23,59).atZone(ZoneId.systemDefault()))
        CountdownWidgetProvider.updateAllWidgets(context,before);WidgetUpdateScheduler.ensureScheduled(context,before)
        val alarm=daily();assertEquals(AlarmManager.RTC_WAKEUP,alarm.type);assertEquals(0L,alarm.windowLengthMs);assertTrue(alarm.allowWhileIdle)
        assertEquals(WidgetMidnightRefresh.nextMillis(before),alarm.triggerAtTime)
        assertTrue(all(Shadows.shadowOf(manager).getViewFor(191)).filterIsInstance<TextView>().any { it.text.toString()=="1" })
        Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)).fireAlarm(alarm);flush();flush()
        assertTrue(all(Shadows.shadowOf(manager).getViewFor(191)).filterIsInstance<TextView>().any { it.text.toString()=="2" })
        assertFalse(WidgetMidnightRefresh.stale(context,CountdownTime.snapshot()))
        assertTrue(daily().triggerAtTime>CountdownTime.snapshot().now.toInstant().toEpochMilli())
    }
    @Test fun deniedExactAlarmsStillScheduleAnIdleAwareFallbackAndRemovingLastWidgetCancels() {
        bind();ShadowAlarmManager.setCanScheduleExactAlarms(false)
        WidgetUpdateScheduler.ensureScheduled(context)
        assertTrue(daily().allowWhileIdle);assertNotEquals(0L,daily().windowLengthMs)
        WidgetUpdateScheduler.cancel(context)
        assertFalse(Shadows.shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.any { Shadows.shadowOf(it.operation).savedIntent.action==WidgetUpdateScheduler.ACTION_DAILY_REFRESH })
    }
    @Test fun sliderPreviewReusesTextViewsAndDrawsNativeTextAfterManyUpdates() {
        flush();val e=CountdownEvent("preview","Preview",LocalDate.now().minusDays(356),EventType.EVENT,createdAt=Instant.EPOCH)
        val layout=WidgetLayoutStore(context).defaults().layouts.getValue("small")
        val preview=NativeWidgetPreview(context)
        preview.measure(View.MeasureSpec.makeMeasureSpec(252,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(252,View.MeasureSpec.EXACTLY));preview.layout(0,0,252,252)
        preview.update(e,LocalDate.now(),layout,84,84)
        val root=preview.getChildAt(0);val count=all(root).filterIsInstance<TextView>().first { it.text.toString()=="356" }
        val times=mutableListOf<Long>()
        repeat(240) { i ->
            val modified=layout.copy(elements=layout.elements.map { if(it.source=="count") it.copy(maxSize=22.0+(i%20)) else it })
            val start=System.nanoTime();preview.update(e,LocalDate.now(),modified,84,84);times+=System.nanoTime()-start
            assertSame(root,preview.getChildAt(0));assertSame(count,all(root).filterIsInstance<TextView>().first { it.text.toString()=="356" })
        }
        val sorted=times.drop(20).sorted();println("Reused local preview: p50=${sorted[sorted.size/2]/1e6}ms p95=${sorted[(sorted.size*.95).toInt()]/1e6}ms (host test, not phone FPS)")
        val bitmap=Bitmap.createBitmap(252,252,Bitmap.Config.ARGB_8888);preview.draw(Canvas(bitmap))
        var blue=0;for(y in 0 until 252) for(x in 0 until 252) { val p=bitmap.getPixel(x,y);if(Color.blue(p)>Color.red(p)+30) blue++ }
        assertTrue(blue>50);bitmap.recycle()
        val overlay=FontSizeOverlay(Robolectric.buildActivity(Activity::class.java).setup().get())
        val event=MotionEvent.obtain(0,0,MotionEvent.ACTION_DOWN,1f,1f,0)
        assertFalse(overlay.onTouchEvent(event));event.recycle()
    }
}
