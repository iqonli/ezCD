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
@Config(sdk=[33],shadows=[WidgetConfigActivityUiTest.FrameworkTabHost::class,WidgetConfigActivityUiTest.FrameworkTabSpec::class,Editor108UiTest.Storage::class])
@LooperMode(LooperMode.Mode.PAUSED)
class Editor108UiTest {
    @Implements(android.os.Environment::class)
    class Storage: org.robolectric.shadows.ShadowEnvironment() {
        companion object {
            var allowed=false
            @JvmStatic @Implementation fun isExternalStorageManager()=allowed
            @JvmStatic @Resetter fun resetPermission() { allowed=false }
        }
    }
    private val context get()=RuntimeEnvironment.getApplication() as Application
    private val event=CountdownEvent("editor108","Event",LocalDate.now(),EventType.EVENT,createdAt=Instant.EPOCH)
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    private fun flush() { val l=CountDownLatch(1);CountdownIo.execute { l.countDown() };assertTrue(l.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun open(): org.robolectric.android.controller.ActivityController<WidgetConfigActivity> {
        flush();CountdownRepository(context).save(listOf(event))
        Shadows.shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(108,AppWidgetProviderInfo().apply { provider=ComponentName(context,CountdownWidgetProvider::class.java) })
        WidgetPreferences(context).save(108,event.id,WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED)
        return Robolectric.buildActivity(WidgetConfigActivity::class.java,Intent(context,WidgetConfigActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,108)).setup().also { flush() }
    }
    private fun textSelector(a: Activity): Spinner {
        val label=all(a.window.decorView).filterIsInstance<TextView>().first { it.text==a.getString(R.string.ez_layout_text) }
        return all(label.parent as View).filterIsInstance<Spinner>().single()
    }
    @Test @Suppress("DEPRECATION") fun unchangedAndRevertedLayoutsReturnWithoutConfirmation() {
        open().use { c -> c.get().onBackPressed();assertTrue(c.get().isFinishing);assertNull(org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()) }
        open().use { c ->
            val a=c.get();val v=all(a.window.decorView).filterIsInstance<CheckBox>().first { it.text==a.getString(R.string.ez_layout_visible) }
            v.performClick();v.performClick();a.onBackPressed();assertTrue(a.isFinishing)
        }
    }
    @Test fun deletingNewTextReturnsToThePreviouslyEditedCountAndItsWeightRowWorks() {
        open().use { c ->
            val a=c.get();textSelector(a).setSelection(1);Shadows.shadowOf(Looper.getMainLooper()).idle()
            all(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.ez_layout_add_text) }.performClick()
            all(a.window.decorView).filterIsInstance<Button>().first { it.tag=="delete_text" }.performClick()
            assertEquals(a.getString(R.string.ez_layout_count),textSelector(a).selectedItem.toString())
            val field=all(a.window.decorView).filterIsInstance<EditText>().first { it.contentDescription==a.getString(R.string.ez_layout_weight) }
            val old=field.text.toString().toInt()
            all(a.window.decorView).filterIsInstance<Button>().first { it.text=="+100" }.performClick();assertEquals((old+100).coerceAtMost(1000).toString(),field.text.toString())
            all(a.window.decorView).filterIsInstance<Button>().first { it.text=="-100" }.performClick();assertEquals(old.toString(),field.text.toString())
            val slider=all(a.window.decorView).filterIsInstance<SeekBar>().first { it.contentDescription==a.getString(R.string.ez_layout_weight) }
            field.setText("523");assertEquals(522,slider.progress)
            assertSame(field.parent,all(a.window.decorView).filterIsInstance<Button>().first { it.text=="+100" }.parent)
        }
    }
    @Test fun libraryMetadataPersistsAndImportOnlyReplacesLayout() {
        open().use { c ->
            val store=WidgetLibraryStore(context);val entry=store.create(WidgetLayoutStore(context).defaults().let { d -> d.copy(layouts=d.layouts+("small" to d.layouts.getValue("small").copy(background="#12345680"))) })
            store.rename(entry.id,"Artwork");store.cyclePreview(entry.id);assertEquals("medium",store.get(entry.id)!!.previewSize)
            val copy=store.duplicate(entry.id);assertEquals("Artwork",copy.displayName);assertEquals("medium",copy.previewSize)
            val a=c.get();all(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.ez_layout_import) }.performClick()
            val dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            dialog.listView.performItemClick(dialog.listView.getChildAt(0),0,0)
            val intent=Shadows.shadowOf(a).nextStartedActivityForResult.intent
            assertTrue(intent.getBooleanExtra(WidgetLibraryActivity.EXTRA_PICK,false))
            Shadows.shadowOf(a).receiveResult(intent,Activity.RESULT_OK,Intent().putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,entry.id));flush()
            val binding=all(a.window.decorView).filterIsInstance<TextView>().first { it.text==a.getString(R.string.ez_widget_select) }
            assertTrue(all(binding.parent as View).filterIsInstance<Spinner>().single().selectedItem.toString().startsWith(event.title))
            val menu=PopupMenu(a,View(a)).menu;a.onCreateOptionsMenu(menu);a.onOptionsItemSelected(menu.findItem(R.id.widget_save_action));flush();flush()
            assertEquals(event.id,WidgetPreferences(context).get(108)?.eventId)
            assertEquals("#12345680",WidgetLayoutStore(context).load(108,event.id).layouts.getValue("small").background)
        }
    }
    @Test fun dateAndTimeExamplesAreInTheBodyAndSystemTimeChooserClosesTheInputDialog() {
        Robolectric.buildActivity(EditorActivity::class.java).setup().use { c ->
            flush();val a=c.get();val include=a.findViewById<CheckBox>(R.id.event_include_input)
            val row=include.parent as LinearLayout;(row.getChildAt(1) as Button).performClick()
            val date=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(all(date.window!!.decorView).filterIsInstance<TextView>().any { it.text==a.getString(R.string.ez_date_example) && it.id!=android.R.id.message })
            date.dismiss()
            val method=EditorActivity::class.java.getDeclaredMethod("chooseTime");method.isAccessible=true;method.invoke(a)
            val time=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertEquals("0900",all(time.window!!.decorView).filterIsInstance<EditText>().single().text.toString())
            time.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertFalse(time.isShowing);assertTrue(org.robolectric.shadows.ShadowDialog.getLatestDialog() is TimePickerDialog)
        }
    }
    @Test fun storageDialogShowsAPathAndHidesFullAccessAfterGrant() {
        Storage.allowed=false
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { c ->
            val a=c.get();all(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.ez_auto_backup) }.performClick()
            val dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(all(dialog.window!!.decorView).filterIsInstance<Button>().any { it.text.toString()==DirectoryAccess.downloads().absolutePath+"/" })
            assertEquals(a.getString(R.string.ez_full_access),dialog.getButton(AlertDialog.BUTTON_NEUTRAL).text.toString());dialog.dismiss()
            Storage.allowed=true
            all(a.window.decorView).filterIsInstance<Button>().first { it.text==a.getString(R.string.ez_auto_backup) }.performClick()
            assertEquals(View.GONE,org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).visibility)
        }
    }
    @Test fun textPoolSurvivesLanguageChangesAndIsIncludedInBackup() {
        flush();assertEquals("\"left\"",context.getString(R.string.ez_pool_left));val pool=TextPool(context);val custom=mapOf("date" to "%yyyy%/%mm%/%dd%","left" to "还有","today" to "当日","passed" to "已用")
        pool.save(custom)
        val english=context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(java.util.Locale.ENGLISH) })
        assertEquals("还有",TextPool.status(english,com.iqonli.ezcd.countdown.CountdownStatus.FUTURE))
        assertEquals("2026/01/02",TextPool.date(english,LocalDate.of(2026,1,2)))
        val backup=BackupArchiveService(context).export();pool.save(TextPool.keys.associateWith { "" });BackupArchiveService(context).apply(backup)
        assertEquals(custom,TextPool.keys.associateWith(pool::get))
    }
}
