package com.iqonli.ezcd.widget
import android.app.*
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
class Color110UiTest {
 private val context get()=RuntimeEnvironment.getApplication() as Application
 private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
 private fun flush() { val l=CountDownLatch(1);CountdownIo.execute { l.countDown() };assertTrue(l.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
 @Test fun followPaletteMirrorsEventOriginAndDefaultSelectionDoesNotFollow() {
  for(kind in listOf("default","preset","custom")) {
   flush();val event=CountdownEvent("color110","Color",LocalDate.now(),EventType.EVENT,countColorDefault=kind=="default",countColorCustom=kind=="custom",createdAt=Instant.EPOCH)
   CountdownRepository(context).save(listOf(event));Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(110,AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) })
   WidgetPreferences(context).save(110,event.id,WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED)
   Robolectric.buildActivity(WidgetConfigActivity::class.java,Intent(context,WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,110)).setup().use { c ->
    flush();val a=c.get();val selector=all(a.window.decorView).filterIsInstance<Spinner>().last();selector.setSelection(1);Shadows.shadowOf(Looper.getMainLooper()).idle()
    fun palette()=all(a.window.decorView).filterIsInstance<EventColorChoices>().first()
    val expected=when(kind) { "default" -> R.string.ez_default_color;"custom" -> R.string.ez_custom;else -> R.string.ez_blue }
    assertTrue(all(palette()).first { it.contentDescription==a.getString(expected) }.isSelected)
    val label=all(a.window.decorView).filterIsInstance<TextView>().first { it.text==a.getString(R.string.ez_layout_text_color) }
    assertTrue(all(label.parent as View).filterIsInstance<CheckBox>().single().isChecked)
    all(palette()).first { it.contentDescription==a.getString(R.string.ez_default_color) }.performClick()
    val newLabel=all(a.window.decorView).filterIsInstance<TextView>().first { it.text==a.getString(R.string.ez_layout_text_color) }
    assertFalse(all(newLabel.parent as View).filterIsInstance<CheckBox>().single().isChecked)
   }
   WidgetLayoutStore(context).remove(110)
  }
 }
 @Test fun libraryRenameSelectsAllAndRequestsKeyboardWhileCardsHaveNoElevation() {
  flush();val store=WidgetLibraryStore(context);val entry=store.create(WidgetLayoutStore(context).defaults());store.rename(entry.id,"My layout")
  Robolectric.buildActivity(WidgetLibraryActivity::class.java).setup().use { c ->
   flush();WidgetLibraryActivity::class.java.getDeclaredMethod("rename",WidgetLibraryEntry::class.java).apply { isAccessible=true }.invoke(c.get(),store.get(entry.id))
   Shadows.shadowOf(Looper.getMainLooper()).idle();val d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
   val field=all(d.window!!.decorView).filterIsInstance<EditText>().single();assertTrue(field.hasFocus());assertEquals(0,field.selectionStart);assertEquals(field.length(),field.selectionEnd)
   assertTrue(Shadows.shadowOf(context.getSystemService(android.view.inputmethod.InputMethodManager::class.java)).isSoftInputVisible)
  }
  val card=NativeUi.eventCard(context,"Title","2026-10-24","1","Today","days")
  assertTrue(all(card).all { it.elevation==0f })
 }
}
