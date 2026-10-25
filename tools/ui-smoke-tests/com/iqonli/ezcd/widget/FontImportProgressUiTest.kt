package com.iqonli.ezcd.widget

import android.app.Application
import android.app.AlertDialog
import android.content.res.Configuration
import android.net.Uri
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.fonts.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.util.ReflectionHelpers
import java.io.File
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontImportProgressUiTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application
    private val store get() = FontStore(context)
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun bytes(name: String = "Regular.ttf") = javaClass.getResourceAsStream("/font-fixtures/$name")!!.use { it.readBytes() }
    private fun settle() {
        repeat(3) { val latch=CountDownLatch(1); FontIo.submit({ latch.countDown() }) { }; assertTrue(latch.await(15,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    }
    private fun importOne() {
        store.switchStorage(FontStorageMode.REFERENCE)
        val file=File(context.cacheDir,"original.ttf").apply { writeBytes(bytes()) }
        assertEquals(1,FontImporter(context).import(listOf(Uri.fromFile(file))).added)
    }
    private fun screenshot(activity: android.app.Activity, name: String, bottom: Boolean) {
        val root=activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(432,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(960,View.MeasureSpec.EXACTLY));root.layout(0,0,432,960)
        if(bottom) all(root).filterIsInstance<ScrollView>().first().fullScroll(View.FOCUS_DOWN)
        val bitmap=android.graphics.Bitmap.createBitmap(432,960,android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        val folder=File(System.getProperty("ezcd.preview.dir","build/font-previews")).apply { mkdirs() }
        File(folder,"$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
    }
    @Before fun reset() {
        File(context.filesDir,"fonts").deleteRecursively();File(context.filesDir,"font-files").deleteRecursively()
        context.getSharedPreferences("font_preview",0).edit().clear().commit()
    }
    @Test fun noStorageIsSelectedAndPlusShowsTheRequiredDialogWithoutStartingAPicker() {
        assertEquals(FontStorageMode.UNSELECTED,store.storage().mode)
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val a=c.get();val menu=PopupMenu(a,a.window.decorView).menu;a.onCreateOptionsMenu(menu)
            a.onOptionsItemSelected(menu.findItem(1));val message=ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(all(message.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString()==a.getString(R.string.ez_font_storage_required) })
            assertNull(Shadows.shadowOf(a).nextStartedActivityForResult)
            message.dismiss();a.onOptionsItemSelected(menu.findItem(2))
            val choices=ShadowAlertDialog.getLatestAlertDialog();assertEquals(-1,choices.listView.checkedItemPosition)
            choices.listView.performItemClick(null,2,2);Shadows.shadowOf(Looper.getMainLooper()).idle();settle()
            assertEquals(FontStorageMode.REFERENCE,store.storage().mode)
            a.onOptionsItemSelected(menu.findItem(1));assertEquals(3,ShadowAlertDialog.getLatestAlertDialog().listView.adapter.count)
        }
    }
    @Test fun importerAlsoRejectsAnUnsetLocationAndDoesNotCreateRecordsOrCopies() {
        val file=File(context.cacheDir,"unset.ttf").apply { writeBytes(bytes()) }
        val report=FontImporter(context).import(listOf(Uri.fromFile(file)))
        assertEquals(0,report.added);assertEquals(1,report.failures);assertTrue(store.fonts().isEmpty())
        assertEquals(FontStorageMode.UNSELECTED,store.storage().mode);assertFalse(File(context.filesDir,"font-files").exists())
    }
    @Test fun zipTraversesEveryNestedFontBeyondTheOldLimitCountsAllFailuresAndReportsMonotonicProgress() {
        store.switchStorage(FontStorageMode.REFERENCE)
        val zip=File(context.cacheDir,"many-fonts.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            val names=listOf("Bold.ttf","Medium.ttf","Regular.ttf","Light.ttf")
            repeat(300) { i -> out.putNextEntry(ZipEntry("nested/$i/${names[i.coerceAtMost(3)]}"));out.write(bytes(names[i.coerceAtMost(3)]));out.closeEntry() }
            repeat(45) { i -> out.putNextEntry(ZipEntry("invalid/$i.otf"));out.write(byteArrayOf(0,1));out.closeEntry() }
            out.putNextEntry(ZipEntry("readme.txt"));out.write("ignore me".toByteArray());out.closeEntry()
        }
        val progress=mutableListOf<Int>();val report=FontImporter(context).import(listOf(Uri.fromFile(zip)),progress::add)
        assertEquals(4,report.added);assertEquals(296,report.duplicates);assertEquals(45,report.failures);assertEquals(40,report.issues.size)
        assertEquals(listOf(300,400,500,700),FontCatalog.groups(store.fonts()).single().fonts.map { it.metadata.weight })
        assertEquals(0,progress.first());assertEquals(100,progress.last());assertTrue(progress.any { it in 1..99 })
        assertEquals(progress.sorted(),progress);assertFalse(File(context.cacheDir,"font-work").exists())
        store.fonts().forEach { assertNotNull(store.typeface(it)) };assertFalse(File(context.cacheDir,"font-work").exists())
    }
    @Test fun summaryUsesOnlyNonzeroCountsAndTheFullFailureTotal() {
        val c=context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("zh-Hans")) })
        assertEquals("导入成功5个，跳过重复3个，导入失败1个",FontImportSummary.text(c,FontImportReport(5,3,emptyList(),1)))
        assertEquals("导入成功5个",FontImportSummary.text(c,FontImportReport(5,0,emptyList())))
        assertEquals("跳过重复3个",FontImportSummary.text(c,FontImportReport(0,3,emptyList())))
        assertEquals("导入失败45个",FontImportSummary.text(c,FontImportReport(0,0,emptyList(),45)))
        assertEquals("",FontImportSummary.text(c,FontImportReport(0,0,emptyList())))
    }
    @Test fun cacheCleanupPreservesActiveFontWorkAndUnrelatedDrafts() {
        val stale=File(context.cacheDir,"font-123456.tmp").apply { writeText("aborted") }
        val draft=File(context.cacheDir,"widget-layout-draft-example.json").apply { writeText("draft") }
        FontCache.withFile(context) { active ->
            active.writeText("in use");FontCache.clean(context);assertTrue(active.isFile);assertFalse(stale.exists());assertTrue(draft.isFile)
        }
        assertFalse(File(context.cacheDir,"font-work").exists());assertEquals("draft",draft.readText())
        assertThrows(IllegalStateException::class.java) { FontCache.withFile(context) { it.writeText("failed");error("abort") } }
        assertFalse(File(context.cacheDir,"font-work").exists())
    }
    @Test fun processingBarKeepsActualProgressAcrossActivityRecreation() {
        store.switchStorage(FontStorageMode.REFERENCE)
        val entered=CountDownLatch(1);val finish=CountDownLatch(1)
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle();val session=ReflectionHelpers.getField<Any>(c.get(),"session")
            val task=ReflectionHelpers.getField<FontTask<Any>>(session,"task")
            try {
                task.start { progress -> progress(50);entered.countDown();check(finish.await(15,TimeUnit.SECONDS));error("test finished") }
                assertTrue(entered.await(15,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle()
                var bar=all(c.get().window.decorView).filterIsInstance<ProgressBar>().single()
                assertEquals(50,bar.progress);assertFalse(bar.isIndeterminate)
                assertTrue(c.get().window.decorView.alpha<1f)
                assertFalse(all(c.get().window.decorView).filterIsInstance<ExpandableListView>().single().isEnabled)
                val menu=PopupMenu(c.get(),c.get().window.decorView).menu;c.get().onCreateOptionsMenu(menu);c.get().onPrepareOptionsMenu(menu)
                assertFalse(menu.findItem(1).isEnabled);assertFalse(menu.findItem(2).isEnabled)
                assertEquals(1,all(c.get().window.decorView).filterIsInstance<TextView>().count { it.text.toString()==c.get().getString(R.string.ez_font_working) })
                screenshot(c.get(), "font-progress", false)
                c.recreate();Shadows.shadowOf(Looper.getMainLooper()).idle()
                bar=all(c.get().window.decorView).filterIsInstance<ProgressBar>().single();assertEquals(50,bar.progress)
                assertEquals(View.VISIBLE,bar.parent.let { it as View }.visibility)
            } finally { finish.countDown();settle();assertEquals(1f,c.get().window.decorView.alpha,0f) }
        }
    }
    @Test fun inlineCustomSizeUsesASeparateRowValidatesAndRestoresDraft() {
        importOne()
        Robolectric.buildActivity(FontPreviewActivity::class.java,FontPreviewActivity.intent(context,store.fonts().single().id)).setup().use { c ->
            settle();var a=c.get()
            assertEquals(listOf(12,18,24,36,48,60,72,80,88,96,100),all(a.window.decorView).filterIsInstance<TextView>().filter { it.tag is Int }.map { it.tag as Int })
            var field=all(a.window.decorView).filterIsInstance<EditText>().single()
            val rendered=all(a.window.decorView).filterIsInstance<TextView>().single { it.tag==100 }
            val body=field.parent.parent as ViewGroup
            assertEquals(body.indexOfChild(field.parent as View)+1,body.indexOfChild(rendered.parent as View))
            field.setText("0");assertNotNull(field.error);assertEquals(100,rendered.tag)
            field.setText("128");assertEquals(128,rendered.tag)
            c.recreate();settle();a=c.get();field=all(a.window.decorView).filterIsInstance<EditText>().single()
            assertEquals("128",field.text.toString());assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.tag==128 })
            field.setText("0");c.recreate();settle();a=c.get();field=all(a.window.decorView).filterIsInstance<EditText>().single()
            assertEquals("0",field.text.toString());assertNotNull(field.error)
            assertTrue(all(a.window.decorView).filterIsInstance<TextView>().any { it.tag==128 })
        }
    }
}
