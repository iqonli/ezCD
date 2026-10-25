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
import org.robolectric.annotation.*
import java.time.*
import java.util.concurrent.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],shadows=[WidgetConfigActivityUiTest.FrameworkTabHost::class,WidgetConfigActivityUiTest.FrameworkTabSpec::class])
@LooperMode(LooperMode.Mode.PAUSED)
class WidgetLibraryUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as android.app.Application
    private val event=CountdownEvent("history","History",LocalDate.now(),EventType.EVENT,createdAt=Instant.parse("2025-01-02T03:04:05Z"))
    private fun flush() { val latch=CountDownLatch(1);CountdownIo.execute { latch.countDown() };assertTrue(latch.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun all(v: View): List<View> = listOf(v)+if(v is ViewGroup)(0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()
    @Test fun liveCaptureUpdatesThenFreezesAndNeverDependsOnEventExistence() {
        flush();val store=WidgetLibraryStore(context);val original=WidgetLayoutStore(context).defaults()
        store.capture(201,event,original);val first=store.entries().single()
        store.capture(201,event,original);assertEquals(first,store.entries().single())
        val altered=original.copy(layouts=original.layouts+("small" to original.layouts.getValue("small").copy(background="#FF000080")))
        store.capture(201,event,altered);assertEquals(first.id,store.entries().single().id);assertEquals(first.createdAt,store.entries().single().createdAt)
        store.sync();assertNull(store.entries().single().widgetId)
        store.capture(201,null,altered,false);assertEquals(1,store.entries().size)
        CountdownRepository(context).save(emptyList());store.sync();assertEquals(event,store.entries().single().event)
        store.capture(201,event,original);assertEquals(2,store.entries().size);assertNotEquals(first.id,store.entries().first().id)
        assertEquals(altered,store.entries().last().document)
    }
    @Test fun manualEditsAreNotOverwrittenByAnUnchangedDesktopRefreshAndDeleteDoesNotResurrect() {
        flush();val store=WidgetLibraryStore(context);val original=WidgetLayoutStore(context).defaults()
        store.capture(202,event,original);val id=store.entries().single().id
        val manual=original.copy(layouts=original.layouts+("small" to original.layouts.getValue("small").copy(background="#12345678")))
        store.save(id,manual,event);store.capture(202,event,original);assertEquals(manual,store.get(id)?.document)
        val copy=store.duplicate(id);assertNull(copy.widgetId);assertNotEquals(id,copy.id)
        store.delete(id);store.capture(202,event,original);assertEquals(listOf(copy.id),store.entries().map { it.id })
        store.sync();store.capture(202,event,original,false);assertEquals(1,store.entries().size)
    }
    @Test fun portableImportPreservesDatesAndDetachesForeignIds() {
        flush();val store=WidgetLibraryStore(context);val doc=WidgetLayoutStore(context).defaults()
        val imported=WidgetLibraryEntry(java.util.UUID.randomUUID().toString(),Instant.parse("2020-01-01T00:00:00Z"),Instant.EPOCH,987,event,doc)
        val data=BackupArchiveCodec.encode(BackupArchive(CountdownStorageCodec.encode(listOf(event)),listOf(imported),com.iqonli.ezcd.countdown.EventSortOrder.CREATED_ASC))
        BackupArchiveService(context).apply(data)
        val restored=store.entries().single();assertNull(restored.widgetId);assertEquals(imported.createdAt,restored.createdAt)
        assertEquals(event.createdAt,(CountdownRepository(context).loadResult() as CountdownLoadResult.Success).events.single().createdAt)
    }
    @Test fun libraryEditorWorksAfterOriginalEventWasDeletedAndSavesOnlyToLibrary() {
        flush();val store=WidgetLibraryStore(context);val entry=store.create(WidgetLayoutStore(context).defaults(),event)
        val intent=Intent(context,WidgetLibraryEditorActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,entry.id)
        Robolectric.buildActivity(WidgetLibraryEditorActivity::class.java,intent).setup().use { controller ->
            flush();val a=controller.get();assertFalse(a.isFinishing)
            val visible=all(a.window.decorView).filterIsInstance<CheckBox>().first { it.text==a.getString(R.string.ez_layout_visible) }
            assertTrue(visible.isEnabled);visible.performClick()
            val menu=PopupMenu(a,View(a)).menu;a.onCreateOptionsMenu(menu);a.onPrepareOptionsMenu(menu)
            a.onOptionsItemSelected(menu.findItem(R.id.widget_save_action));flush();flush()
            assertTrue(a.isFinishing);assertFalse(store.get(entry.id)!!.document.layouts.getValue("small").elements.first().visible)
            assertEquals(event,store.get(entry.id)?.event);assertNull(WidgetPreferences(context).get(-1))
        }
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun galleryShowsThreeSquarePreviewsAtPhoneWidthAndOpensLibraryEditor() {
        flush();val store=WidgetLibraryStore(context)
        repeat(6) { store.create(WidgetLayoutStore(context).defaults(),event.copy(title="Layout ${it+1}")) }
        Robolectric.buildActivity(WidgetLibraryActivity::class.java).setup().use { controller ->
            flush();val a=controller.get();val decor=a.window.decorView
            fun layout() { decor.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));decor.layout(0,0,360,720) }
            layout();val grid=all(decor).filterIsInstance<GridView>().single()
            assertEquals(3,grid.numColumns);assertEquals(6,grid.adapter.count)
            assertEquals(3,(0 until grid.childCount).map { grid.getChildAt(it) }.count { it.top==grid.getChildAt(0).top })
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
            while (System.nanoTime()<until && all(decor).filterIsInstance<NativeWidgetPreview>().any { it.childCount==0 }) { Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();layout() }
            all(decor).filterIsInstance<NativeWidgetPreview>().forEach { assertEquals(it.width,it.height);assertEquals(1,it.childCount);assertFalse(it.showGuides) }
            System.getProperty("ezcd.preview.dir")?.let { dir ->
                val bitmap=android.graphics.Bitmap.createBitmap(360,720,android.graphics.Bitmap.Config.ARGB_8888);decor.draw(android.graphics.Canvas(bitmap))
                java.io.File(dir,"library-phone.png").apply { parentFile?.mkdirs() }.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
            }
            grid.performItemClick(grid.getChildAt(0),0,0)
            val intent=Shadows.shadowOf(a).nextStartedActivity
            assertEquals(WidgetLibraryEditorActivity::class.java.name,intent.component!!.className)
        }
    }
    @Test fun archivedSnapshotIsNotSilentlyReboundToAnEventChangedLater() {
        flush();val store=WidgetLibraryStore(context);val entry=store.create(WidgetLayoutStore(context).defaults(),event)
        CountdownRepository(context).save(listOf(event.copy(title="Changed later",cardColor=0xFFFF0000.toInt())))
        val intent=Intent(context,WidgetLibraryEditorActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,entry.id)
        Robolectric.buildActivity(WidgetLibraryEditorActivity::class.java,intent).setup().use { controller ->
            flush();val a=controller.get()
            val label=all(a.window.decorView).filterIsInstance<TextView>().first { it.text==a.getString(R.string.ez_widget_select) }
            val spinner=all(label.parent as View).filterIsInstance<Spinner>().single()
            assertTrue(spinner.selectedItem.toString().startsWith(event.title))
            val menu=PopupMenu(a,View(a)).menu;a.onCreateOptionsMenu(menu);a.onOptionsItemSelected(menu.findItem(R.id.widget_save_action));flush();flush()
            assertTrue(a.isFinishing);assertEquals(event,store.get(entry.id)?.event)
        }
    }
    @Test fun importJournalRestoresThePreviousWholeStateAfterInterruption() {
        flush();val store=WidgetLibraryStore(context);val doc=WidgetLayoutStore(context).defaults();val entry=store.create(doc,event)
        CountdownRepository(context).save(listOf(event))
        val before=org.json.JSONObject().put("events",CountdownRepository(context).exportPayload()).put("library",WidgetLibraryCodec.encode(store.state())).put("sort","CREATED_DESC")
        java.io.File(context.filesDir,"backup-import-rollback.json").writeText(before.toString())
        CountdownRepository(context).save(emptyList());store.delete(entry.id)
        BackupArchiveService(context).recover()
        assertEquals(event,(CountdownRepository(context).loadResult() as CountdownLoadResult.Success).events.single());assertEquals(entry,store.get(entry.id))
    }
    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun deletingOneLayoutKeepsTheAdapterAndAllOtherPreviewsWithoutRerendering() {
        flush();val store=WidgetLibraryStore(context)
        repeat(6) { store.create(WidgetLayoutStore(context).defaults(),event.copy(title="Unique $it")) }
        Robolectric.buildActivity(WidgetLibraryActivity::class.java).setup().use { c ->
            flush();val a=c.get();val root=a.window.decorView
            fun measure() { root.measure(View.MeasureSpec.makeMeasureSpec(360,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));root.layout(0,0,360,720) }
            measure();val grid=all(root).filterIsInstance<GridView>().single();val original=grid.adapter
            val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
            while(System.nanoTime()<until && all(root).filterIsInstance<NativeWidgetPreview>().any { it.childCount==0 }) { Thread.sleep(10);Shadows.shadowOf(Looper.getMainLooper()).idle();measure() }
            assertTrue(all(root).filterIsInstance<NativeWidgetPreview>().all { it.childCount==1 })
            org.robolectric.util.ReflectionHelpers.getField<java.util.concurrent.ExecutorService>(a,"executor").shutdown()
            grid.onItemLongClickListener!!.onItemLongClick(grid,grid.getChildAt(0),0,grid.adapter.getItemId(0))
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null,5,5)
            org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            repeat(4) { flush() };measure()
            assertSame(original,grid.adapter);assertEquals(5,grid.adapter.count)
            assertTrue(all(root).filterIsInstance<NativeWidgetPreview>().isNotEmpty())
            assertTrue(all(root).filterIsInstance<NativeWidgetPreview>().all { it.childCount==1 })
        }
    }
}
