package com.iqonli.ezcd.widget
import android.appwidget.*
import android.content.*
import android.graphics.drawable.ColorDrawable
import android.os.*
import android.view.*
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.ui.*
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
class WidgetEditorLayoutUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as android.app.Application
    private fun flush() { val latch=CountDownLatch(1);CountdownIo.execute { latch.countDown() };assertTrue(latch.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun open(): org.robolectric.android.controller.ActivityController<WidgetConfigActivity> {
        flush();val event=CountdownEvent("layout-ui","Layout",LocalDate.now(),EventType.EVENT,createdAt=Instant.EPOCH)
        CountdownRepository(context).save(listOf(event))
        val info=AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) }
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(82,info)
        WidgetPreferences(context).save(82,event.id,WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED)
        return Robolectric.buildActivity(WidgetConfigActivity::class.java,Intent(context,WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,82)).setup().also { flush() }
    }
    private fun hasScrollAncestor(view: View): Boolean {
        var parent=view.parent
        while(parent is View) { if(parent is ScrollView) return true;parent=parent.parent };return false
    }
    @Test fun editorDateControlsShareOneRowInTheRequestedOrder() {
        Robolectric.buildActivity(EditorActivity::class.java).setup().use { c ->
            flush();val a=c.get();val include=a.findViewById<CheckBox>(R.id.event_include_input)
            val row=include.parent as LinearLayout
            assertEquals(3,row.childCount);assertSame(include,row.getChildAt(0));assertTrue(row.getChildAt(1) is Button)
            assertEquals(a.getString(R.string.ez_choose_date),(row.getChildAt(2) as Button).text.toString())
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun previewPinsBelowTabsAndAdjustmentControlsRemainHiddenUntilRequested() {
        open().use { c ->
            val a=c.get();val nodes=all(a.window.decorView)
            val pin=nodes.filterIsInstance<ImageButton>().first { it.contentDescription==a.getString(R.string.ez_preview_pin) }
            val adjust=nodes.filterIsInstance<ImageButton>().first { it.contentDescription==a.getString(R.string.ez_preview_adjust) }
            val preview=nodes.filterIsInstance<NativeWidgetPreview>().first()
            val width=nodes.filterIsInstance<SeekBar>().first { it.contentDescription==a.getString(R.string.ez_preview_width) }
            assertFalse(width.isShown);assertTrue(hasScrollAncestor(preview))
            adjust.performClick();assertTrue(width.isShown)
            val decor=a.window.decorView
            decor.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));decor.layout(0,0,360,720)
            System.getProperty("ezcd.preview.dir")?.let { dir ->
                val bitmap=android.graphics.Bitmap.createBitmap(360,720,android.graphics.Bitmap.Config.ARGB_8888);decor.draw(android.graphics.Canvas(bitmap))
                java.io.File(dir,"editor-phone.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            val handle=nodes.filterIsInstance<PreviewResizeHandle>().single();assertTrue(handle.isShown)
            pin.performClick();assertFalse(hasScrollAncestor(preview));assertTrue(hasScrollAncestor(width))
            c.recreate();flush()
            assertFalse(hasScrollAncestor(all(c.get().window.decorView).filterIsInstance<NativeWidgetPreview>().first()))
        }
    }
    @Test fun selectorsAndActionsAreCompactAndTextSelectorUsesLayoutColors() {
        open().use { c ->
            val a=c.get();val views=all(a.window.decorView)
            val binding=views.filterIsInstance<TextView>().first { it.text==a.getString(R.string.ez_widget_select) }
            assertEquals(1,(binding.parent as LinearLayout).childCount-1)
            val textLabel=views.filterIsInstance<TextView>().first { it.text==a.getString(R.string.ez_layout_text) }
            val row=textLabel.parent as LinearLayout;val spinner=(0 until row.childCount).map { row.getChildAt(it) }.filterIsInstance<Spinner>().single()
            val selected=spinner.adapter.getView(1,null,row) as TextView
            assertEquals(OpaqueColor.BLUE,selected.currentTextColor);assertEquals(-1,(selected.background as ColorDrawable).color)
            val visible=views.filterIsInstance<CheckBox>().first { it.text==a.getString(R.string.ez_layout_visible) }
            val actions=visible.parent as LinearLayout
            assertEquals(3,actions.childCount);assertSame(visible,actions.getChildAt(0))
            assertEquals(a.getString(R.string.ez_layout_add_text),(actions.getChildAt(1) as Button).text.toString())
            val menu=PopupMenu(a,View(a)).menu;a.onCreateOptionsMenu(menu);assertNotNull(menu.findItem(R.id.widget_save_action).icon)
        }
    }
}
