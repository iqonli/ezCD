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
class EditorControlsUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as android.app.Application
    private fun all(v: View): List<View> = listOf(v)+if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun flush() { val latch=CountDownLatch(1);CountdownIo.execute { latch.countDown() }; assertTrue(latch.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    @Test fun explicitWhiteIsCustomAndResetIsAnIndependentFinalChoice() {
        var isDefault=false
        val choices=EventColorChoices(context,-1,defaultColor=-1) { _,default -> isDefault=default }
        val row=choices.getChildAt(0) as LinearLayout
        assertEquals(9,row.childCount)
        assertTrue(row.getChildAt(7).isSelected);assertFalse(row.getChildAt(8).isSelected)
        row.getChildAt(8).performClick();assertTrue(isDefault);assertTrue(row.getChildAt(8).isSelected)
        choices.setColor(-1,false);assertTrue(row.getChildAt(7).isSelected);assertFalse(row.getChildAt(8).isSelected)
    }
    @Test fun eventEditorHasNoDuplicateLayoutEntryOrIncludeExplanation() {
        Robolectric.buildActivity(EditorActivity::class.java).setup().use { controller ->
            flush()
            val a=controller.get();val texts=all(a.window.decorView).filterIsInstance<TextView>()
            assertFalse(texts.any { it.text == a.getString(R.string.ez_layout_editor) })
            assertFalse(texts.any { it.text == a.getString(R.string.ez_include_help) })
            assertEquals(1,texts.count { it.text == a.getString(R.string.ez_add_widget) })
            assertFalse(a.currentFocus is EditText)
        }
    }
    @Test fun jsonPasteReplacesEverythingAndTheWholeEditorScrollsInAPlatformScrollView() {
        val event=CountdownEvent("json","Event",LocalDate.now(),EventType.EVENT,createdAt=Instant.EPOCH)
        CountdownRepository(context).save(listOf(event))
        val info=AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) }
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(75,info)
        WidgetPreferences(context).save(75,event.id,WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED)
        val intent=Intent(context,WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,75)
        Robolectric.buildActivity(WidgetConfigActivity::class.java,intent).setup().use { controller ->
            flush();val a=controller.get()
            all(a.window.decorView).filterIsInstance<Button>().first { it.text == a.getString(R.string.ez_layout_edit_json) }.performClick()
            val dialog=org.robolectric.shadows.ShadowDialog.getLatestDialog()
            val views=all(dialog.window!!.decorView)
            val input=views.filterIsInstance<EditText>().single()
            assertTrue(input.parent is ScrollView);assertEquals(Int.MAX_VALUE,input.maxLines)
            val clipboard=context.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("layout","replacement"))
            input.setText("old text");input.setSelection(2)
            views.filterIsInstance<Button>().first { it.text == a.getString(R.string.ez_layout_paste) }.performClick()
            assertEquals("replacement",input.text.toString())
            views.filterIsInstance<Button>().first { it.text == a.getString(R.string.ez_layout_confirm) }.performClick()
            assertTrue(dialog.isShowing);assertNotNull(input.error)
            assertFalse(views.filterIsInstance<TextView>().any { it.text == a.getString(R.string.ez_layout_json_help) })
            dialog.dismiss()
        }
    }
}
