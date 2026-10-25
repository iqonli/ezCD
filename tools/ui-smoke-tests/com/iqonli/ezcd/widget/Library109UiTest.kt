package com.iqonli.ezcd.widget
import android.app.Application
import android.appwidget.*
import android.content.*
import android.content.res.Configuration
import android.os.Looper
import android.view.*
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.CountdownIo
import com.iqonli.ezcd.ui.SettingsActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.util.Locale
import java.util.concurrent.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
@LooperMode(LooperMode.Mode.PAUSED)
class Library109UiTest {
    private val context get()=RuntimeEnvironment.getApplication() as Application
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun flush() { val done=CountDownLatch(1);CountdownIo.execute { done.countDown() };assertTrue(done.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    @Test fun localizedPoolLabelsRetainExactQuotesAndLowercaseEnglish() {
        val cases=mapOf("zh-Hans" to listOf("“剩余”","“今天”","“已过”","日期显示 可用%yyyy% %mm% %dd%"),
            "zh-Hant" to listOf("“剩餘”","“今天”","“已過”","日期顯示 可用%yyyy% %mm% %dd%"),
            "en" to listOf("\"left\"","\"today\"","\"passed\"","Date display Available %yyyy% %mm% %dd%"))
        cases.forEach { (tag,expected) ->
            val c=context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
            assertEquals(expected,listOf(R.string.ez_pool_left,R.string.ez_pool_today,R.string.ez_pool_passed,R.string.ez_pool_date).map(c::getString))
        }
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { controller ->
            val a=controller.get();all(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.ez_text_pool) }.performClick()
            val dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            val labels=all(dialog.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue(a.getString(R.string.ez_pool_date) in labels);assertFalse("%yyyy% %mm% %dd%" in labels)
        }
    }
    @Test fun onlyArchivedTilesShowTheDecorativeSaveMarkAndSizesStillCycle() {
        flush();val manager=AppWidgetManager.getInstance(context)
        Shadows.shadowOf(manager).addBoundWidget(109,AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) })
        val store=WidgetLibraryStore(context);val archived=store.create(WidgetLayoutStore(context).defaults());store.sync()
        Robolectric.buildActivity(WidgetLibraryActivity::class.java).setup().use { c ->
            flush();val grid=all(c.get().window.decorView).filterIsInstance<GridView>().single();val adapter=grid.adapter
            for(i in 0 until adapter.count) {
                val entry=adapter.getItem(i) as WidgetLibraryEntry
                val tile=adapter.getView(i,null,grid)
                val preview=all(tile).filterIsInstance<NativeWidgetPreview>().single()
                assertEquals(entry.widgetId==null,preview.archived);assertFalse(preview.isClickable);assertFalse(preview.isFocusable)
            }
            assertNotNull(store.get(archived.id));store.cyclePreview(archived.id);assertEquals("medium",store.get(archived.id)!!.previewSize)
            assertEquals("切换展示尺寸",context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("zh-Hans")) }).getString(R.string.ez_library_cycle_size))
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun archivalSaveMarkRendersAtTheCanvasCorner() {
        val preview=NativeWidgetPreview(context).apply { showGuides=false }
        val layout=WidgetLayoutStore(context).defaults().layouts.getValue("medium")
        preview.update(null,java.time.LocalDate.now(),layout,200,100)
        val spec=View.MeasureSpec.makeMeasureSpec(200,View.MeasureSpec.EXACTLY);preview.measure(spec,spec);preview.layout(0,0,200,200)
        fun pixels(): android.graphics.Bitmap = android.graphics.Bitmap.createBitmap(200,200,android.graphics.Bitmap.Config.ARGB_8888).also { preview.draw(android.graphics.Canvas(it)) }
        val before=pixels();preview.archived=true;val after=pixels()
        val rect=android.graphics.Rect(200,200,0,0);var changed=0
        for(y in 0 until 200) for(x in 0 until 200) if(before.getPixel(x,y)!=after.getPixel(x,y)) { rect.union(x,y,x+1,y+1);changed++ }
        assertTrue("The archival save icon must actually draw",changed>10)
        assertTrue("Save icon stays at the canvas top: $rect",rect.top>=0 && rect.bottom<=com.iqonli.ezcd.ui.NativeUi.dp(context,24))
        assertTrue("Save icon stays at the right inside the rendered preview: $rect",rect.left>=100 && rect.right<=200)
        preview.archived=false;val cleared=pixels()
        for(y in 0 until 200) for(x in 0 until 200) assertEquals(before.getPixel(x,y),cleared.getPixel(x,y))
    }

}
