// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.appwidget.*
import android.content.*
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],shadows=[WidgetConfigActivityUiTest.FrameworkTabHost::class,WidgetConfigActivityUiTest.FrameworkTabSpec::class])
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@LooperMode(LooperMode.Mode.PAUSED)
class NativeWidgetUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as android.app.Application
    private val event=CountdownEvent("native","Event",LocalDate.of(2026,10,24),EventType.EVENT,createdAt=Instant.EPOCH)
    private fun flush() {
        val latch=CountDownLatch(1);CountdownIo.execute { latch.countDown() };assertTrue(latch.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
    private fun all(v: View): List<View> = listOf(v)+if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun apply(rv: RemoteViews,w: Int,h: Int): View {
        val parcel=Parcel.obtain()
        val copy=try { rv.writeToParcel(parcel,0);parcel.setDataPosition(0);RemoteViews.CREATOR.createFromParcel(parcel) } finally { parcel.recycle() }
        return copy.apply(context,FrameLayout(context)).apply {
            measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));layout(0,0,w,h)
            all(this).forEach { it.viewTreeObserver.dispatchOnPreDraw() }
        }
    }
    @Test fun nativeViewsSurviveIpcAndKeepAlphaWeightOverlapAndCenterGeometry() {
        val e=WidgetTextElement("count","count",weight=523,color="#12345680",top=50.0,width=80.0,height=40.0,alignment="bottom_right",maxSize=38.0)
        val layout=WidgetSizeLayout("#FFFFFF40",listOf(e,e.copy(id="custom1",source="custom",content="overlay",color="#FF00007F")))
        val rv=NativeWidgetRenderer.remoteViews(context,event,LocalDate.of(2026,10,23),layout,160,100)
        val root=apply(rv,160,100)
        val texts=all(root).filterIsInstance<TextView>()
        assertEquals(2,texts.size);assertEquals("1",texts[0].text.toString())
        assertEquals(0x80123456.toInt(),texts[0].currentTextColor)
        assertEquals(Gravity.BOTTOM or Gravity.RIGHT,texts[0].gravity)
        val density=context.resources.displayMetrics.density
        val box=NativeWidgetRenderer.box(e,(160*density).toInt(),(100*density).toInt())
        assertEquals(box.width(),texts[0].width)
        assertEquals(box.top,(texts[0].parent as View).paddingTop)
        assertEquals(64,root.findViewById<ImageView>(R.id.nativeBackground).imageAlpha)
    }
    @Test fun requestedWeightSurvivesIpcAndIsAppliedToTheTextPaint() {
        for (weight in listOf(1,100,400,523,700,900,1000)) {
            val e=WidgetTextElement("count","count",weight=weight,top=50.0,height=80.0,maxSize=40.0)
            val root=apply(NativeWidgetRenderer.remoteViews(context,event,LocalDate.of(2026,10,23),WidgetSizeLayout(elements=listOf(e)),160,100),160,100)
            val view=all(root).filterIsInstance<TextView>().single()
            val paint=android.text.TextPaint(view.paint); val text=view.text as android.text.Spanned
            text.getSpans(0,text.length,android.text.style.MetricAffectingSpan::class.java).forEach { it.updateMeasureState(paint) }
            val span=text.getSpans(0,text.length,android.text.style.TextAppearanceSpan::class.java).single()
            assertEquals("Requested weight axis", "'wght' $weight",span.fontVariationSettings)
            println("weight $weight -> paintWeight=${paint.typeface.weight}, axis=${paint.fontVariationSettings}")
            assertTrue("Font family must also be present for native rendering",!span.family.isNullOrBlank())
            assertFalse(paint.isFakeBoldText)
        }
    }
    @Test fun thinAndHeavyWeightsProduceDifferentVisiblePixelsAfterIpc() {
        fun pixels(weight: Int): Int {
            val e=WidgetTextElement("count","count",weight=weight,top=50.0,height=80.0,maxSize=40.0)
            val root=apply(NativeWidgetRenderer.remoteViews(context,event,LocalDate.of(2026,10,23),WidgetSizeLayout(elements=listOf(e)),160,100),160,100)
            val bitmap=android.graphics.Bitmap.createBitmap(160,100,android.graphics.Bitmap.Config.ARGB_8888)
            root.draw(android.graphics.Canvas(bitmap))
            var colored=0
            for (y in 0 until 100) for (x in 0 until 160) {
                val p=bitmap.getPixel(x,y)
                if (android.graphics.Color.blue(p) > android.graphics.Color.red(p)+30) colored++
            }
            System.getProperty("ezcd.preview.dir")?.let { directory ->
                val dir=java.io.File(directory).apply { mkdirs() }
                java.io.File(dir,"weight-$weight.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            }
            bitmap.recycle();return colored
        }
        val thin=pixels(100); val heavy=pixels(900)
        assertTrue("Thin text must draw",thin > 10)
        assertTrue("Heavy text must visibly differ: $thin vs $heavy",heavy > thin*1.3)
    }
    @Test fun shortCountsAreCappedAndLongCountsShrinkRatherThanWrap() {
        val e=WidgetTextElement("count","count",maxSize=38.0)
        val cap=38*context.resources.displayMetrics.scaledDensity
        assertTrue(NativeWidgetRenderer.fit(context,"1",e,400,200) <= cap)
        assertTrue(NativeWidgetRenderer.fit(context,"123456789",e,40,40) < NativeWidgetRenderer.fit(context,"1",e,40,40))
    }
    @Test fun allDefaultSizesKeepTheirCountReadableWithoutInflatingIt() {
        val output=System.getProperty("ezcd.preview.dir")?.let { java.io.File(it).apply { mkdirs() } }
        for (size in WidgetLayoutSize.entries) {
            val layout=WidgetLayoutStore(context).defaults().layouts.getValue(size.key)
            for (count in listOf(1,356)) {
                val date=LocalDate.of(2026,10,23)
                val sample=event.copy(title="ezCD",date=date.minusDays(count.toLong()))
                val w=size.widthDp;val h=size.heightDp
                val root=apply(NativeWidgetRenderer.remoteViews(context,sample,date,layout,w,h),w,h)
                val number=all(root).filterIsInstance<TextView>().first { it.text.toString() == count.toString() }
                assertTrue(number.textSize >= 20f)
                assertTrue(number.textSize <= layout.elements.first { it.source == "count" }.maxSize*context.resources.displayMetrics.scaledDensity)
                if (output != null) {
                    val bitmap=android.graphics.Bitmap.createBitmap(w,h,android.graphics.Bitmap.Config.ARGB_8888)
                    root.draw(android.graphics.Canvas(bitmap))
                    assertTrue("The native text layout must be constrained to its box",number.layout.width <= number.width)
                    var bluePixels=0
                    for (y in 0 until h) for (x in 0 until w) {
                        val pixel=bitmap.getPixel(x,y)
                        if (android.graphics.Color.blue(pixel) > android.graphics.Color.red(pixel)+30) bluePixels++
                    }
                    assertTrue("Native counts must actually draw visible pixels",bluePixels > 10)
                    java.io.File(output,"${size.key}-$count.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
                }
            }
        }
    }
    @Test fun emptyPlaceholderUsesAsciiMinusAndNoBitmap() {
        val l=WidgetLayoutStore(context).defaults().layouts.getValue("small")
        val root=apply(NativeWidgetRenderer.remoteViews(context,null,LocalDate.now(),l,84,84),84,84)
        assertTrue(all(root).filterIsInstance<TextView>().any { it.text.toString() == "-" })
        assertFalse(all(root).filterIsInstance<ImageView>().any { it.drawable is android.graphics.drawable.BitmapDrawable })
    }
    @Test fun newDesktopConfigurationStartsUnselectedDisablesSettingsAndCanBind() {
        CountdownRepository(context).save(listOf(event))
        val manager=AppWidgetManager.getInstance(context)
        val info=AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) }
        Shadows.shadowOf(manager).addBoundWidget(72,info)
        val intent=Intent(context,WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,72)
        Robolectric.buildActivity(WidgetConfigActivity::class.java,intent).setup().use { controller ->
            flush()
            val activity=controller.get();assertFalse(activity.isFinishing)
            val views=all(activity.window.decorView)
            val events=views.filterIsInstance<Spinner>().first { it.adapter?.getItem(0) == activity.getString(R.string.ez_not_selected) }
            assertEquals(0,events.selectedItemPosition)
            assertTrue(events.isEnabled)
            assertTrue(views.filterIsInstance<SeekBar>().all { !it.isEnabled })
            val tabs=activity.findViewById<TabWidget>(android.R.id.tabs);assertFalse(tabs.isEnabled)
            assertTrue(events.top <= tabs.top || (events.parent as View).top <= (tabs.parent as View).top)
            events.setSelection(1);Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertTrue(all(activity.window.decorView).filterIsInstance<SeekBar>().all { it.isEnabled })
            val menu=PopupMenu(activity,View(activity)).menu
            activity.onCreateOptionsMenu(menu);activity.onPrepareOptionsMenu(menu)
            assertTrue(menu.findItem(R.id.widget_save_action).isEnabled)
            activity.onOptionsItemSelected(menu.findItem(R.id.widget_save_action))
            flush();flush()
            assertEquals(event.id,WidgetPreferences(context).get(72)?.eventId)
        }
    }
    @Test fun pinCallbackBindsTheReportedWidgetAndDoesNotTouchOtherInstances() {
        CountdownRepository(context).save(listOf(event))
        val info=AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) }
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(73,info)
        val snapshot=WidgetPinRequestSnapshot.create(event.id,WidgetStyleSelection(WidgetAppearance.LIGHT,WidgetBackground.CLASSIC))
        val pi=WidgetPinning.callbackPendingIntent(context,snapshot)
        pi.send(context,0,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,73))
        flush();flush()
        assertEquals(event.id,WidgetPreferences(context).get(73)?.eventId)
        assertNull(WidgetPreferences(context).get(74))
    }
}
