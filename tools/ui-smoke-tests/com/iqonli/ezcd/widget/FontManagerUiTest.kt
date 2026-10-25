package com.iqonli.ezcd.widget

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.fonts.*
import com.iqonli.ezcd.ui.SettingsActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FontManagerUiTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun fixture(name: String): File = File(context.cacheDir, name).also { file ->
        javaClass.getResourceAsStream("/font-fixtures/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    private fun flush() {
        repeat(3) {
            val latch = CountDownLatch(1)
            FontIo.submit({ Unit }) { latch.countDown() }
            // FontIo posts completion to the paused main loop.
            val worker = CountDownLatch(1)
            FontIo.submit({ worker.countDown() }) { }
            assertTrue(worker.await(15, TimeUnit.SECONDS))
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0L, latch.count)
        }
    }
    private fun screenshot(activity: android.app.Activity, name: String) {
        val root = activity.window.decorView
        root.measure(View.MeasureSpec.makeMeasureSpec(432, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(960, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 432, 960)
        val bitmap = android.graphics.Bitmap.createBitmap(432, 960, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bitmap))
        val directory = File(System.getProperty("ezcd.preview.dir", "build/font-previews")).apply { mkdirs() }
        File(directory, name + ".png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    @Before fun clearFonts() {
        File(context.filesDir, "fonts").deleteRecursively()
        File(context.filesDir, "font-files").deleteRecursively()
        context.getSharedPreferences("font_preview", 0).edit().clear().commit()
        FontStore(context).switchStorage(FontStorageMode.REFERENCE)
    }

    @Test fun settingsEntryImmediatelyFollowsLayoutLibraryAndOpensStandalonePage() {
        Robolectric.buildActivity(SettingsActivity::class.java).setup().use { c ->
            val buttons = all(c.get().window.decorView).filterIsInstance<Button>()
            val library = buttons.indexOfFirst { it.text == c.get().getString(R.string.ez_widget_library) }
            assertTrue(library >= 0)
            assertEquals(c.get().getString(R.string.ez_fonts), buttons[library + 1].text.toString())
            buttons[library + 1].performClick()
            assertEquals(FontManagerActivity::class.java.name, Shadows.shadowOf(c.get()).nextStartedActivity.component!!.className)
        }
    }

    @Test fun multiselectionZipSubdirectoriesAndBadFontsPreserveValidImports() {
        FontStore(context).switchStorage(FontStorageMode.INTERNAL)
        val regular = fixture("Regular.ttf")
        val bold = fixture("Bold.ttf")
        val zip = File(context.cacheDir, "fonts.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("nested/Bold.ttf")); out.write(bold.readBytes()); out.closeEntry()
            out.putNextEntry(ZipEntry("nested/broken.otf")); out.write(byteArrayOf(0, 1)); out.closeEntry()
            out.putNextEntry(ZipEntry("readme.txt")); out.write("ignored".toByteArray()); out.closeEntry()
        }
        val report = FontImporter(context).import(listOf(Uri.fromFile(regular), Uri.fromFile(zip)))
        assertEquals(2, report.added); assertEquals(1, report.issues.size)
        assertEquals(listOf(400, 700), FontCatalog.groups(FontStore(context).fonts()).single().fonts.map { it.metadata.weight })
        regular.delete(); bold.delete(); zip.delete()
        assertNotNull(FontStore(context).typeface(FontStore(context).fonts().first()))
    }

    @Test fun duplicatesDoNotCreateNewRowsEvenWhenTheFileIsRenamed() {
        val first = fixture("Regular.ttf")
        val renamed = File(context.cacheDir, "Other-name.otf").apply { writeBytes(first.readBytes()) }
        assertEquals(1, FontImporter(context).import(listOf(Uri.fromFile(first))).added)
        val report = FontImporter(context).import(listOf(Uri.fromFile(renamed)))
        assertEquals(0, report.added); assertEquals(1, report.duplicates)
        assertEquals("Regular.ttf", FontStore(context).fonts().single().fileName)
    }

    @Test fun collectionImportsEveryFaceAndArchivePathsNeverBecomeDiskPaths() {
        val ttc = fixture("Family.ttc")
        assertEquals(2, FontImporter(context).import(listOf(Uri.fromFile(ttc))).added)
        assertEquals(listOf(0, 1), FontStore(context).fonts().map { it.metadata.faceIndex })
        FontStore(context).fonts().forEach { assertNotNull(FontStore(context).typeface(it)) }
        val zip = File(context.cacheDir, "paths.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("../../outside-font.ttf")); out.write(fixture("Light.ttf").readBytes()); out.closeEntry()
        }
        assertEquals(1, FontImporter(context).import(listOf(Uri.fromFile(zip))).added)
        assertFalse(File(context.filesDir.parentFile, "outside-font.ttf").exists())
    }

    @Test fun familyAndSingleRowsUseTheRequestedMetadataAndFilenames() {
        FontImporter(context).import(listOf(Uri.fromFile(fixture("Regular.ttf")), Uri.fromFile(fixture("Bold.ttf"))))
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            flush()
            val list = all(c.get().window.decorView).filterIsInstance<ExpandableListView>().single()
            val adapter = list.expandableListAdapter
            assertEquals(1, adapter.groupCount); assertEquals(2, adapter.getChildrenCount(0))
            assertEquals("ezCD Fixture Regular", (adapter.getGroup(0) as FontFamilyGroup).label)
            val child = adapter.getChildView(0, 1, true, null, list)
            assertTrue(all(child).filterIsInstance<TextView>().any { it.text.toString() == "Bold (Bold.ttf)" })
            val parent = adapter.getGroupView(0, false, null, list)
            assertTrue(all(parent).filterIsInstance<TextView>().any { it.text.toString() == "ezCD Fixture Regular" })
            list.expandGroup(0); screenshot(c.get(), "font-manager")
        }
        File(context.filesDir, "fonts").deleteRecursively()
        FontStore(context).switchStorage(FontStorageMode.REFERENCE)
        FontImporter(context).import(listOf(Uri.fromFile(fixture("Regular.ttf"))))
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            flush()
            val list = all(c.get().window.decorView).filterIsInstance<ExpandableListView>().single()
            val adapter = list.expandableListAdapter
            assertEquals(0, adapter.getChildrenCount(0))
            assertTrue(all(adapter.getGroupView(0, false, null, list)).filterIsInstance<TextView>().any { it.text.toString() == "ezCD Fixture Regular (Regular.ttf)" })
        }
    }

    @Test fun previewHasTenSizesAndCustomRowWithEditableTextThatSurvivesRecreation() {
        FontImporter(context).import(listOf(Uri.fromFile(fixture("Regular.ttf"))))
        val font = FontStore(context).fonts().single()
        Robolectric.buildActivity(FontPreviewActivity::class.java, FontPreviewActivity.intent(context, font.id)).setup().use { c ->
            flush()
            val activity = c.get()
            val text = all(activity.window.decorView).filterIsInstance<TextView>()
            assertTrue(text.any { it.text.startsWith("abcdefghijklmnopqrstuvwxyz") })
            val expected = activity.getString(R.string.ez_font_sample)
            assertEquals(11, text.count { it.text.toString() == expected })
            screenshot(activity, "font-preview")
            val menu = android.widget.PopupMenu(activity, activity.window.decorView).menu
            activity.onCreateOptionsMenu(menu)
            activity.onOptionsItemSelected(menu.findItem(1))
            val info = ShadowAlertDialog.getLatestAlertDialog()
            val fields = all(info.window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            assertTrue("Full Name" in fields); assertTrue("ezCD Fixture Regular" in fields); assertTrue("License" in fields)
            info.dismiss()
            activity.onOptionsItemSelected(menu.findItem(2))
            val editor = ShadowAlertDialog.getLatestAlertDialog()
            val field = all(editor.window!!.decorView).filterIsInstance<EditText>().single()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, field.selectionStart); assertEquals(field.length(), field.selectionEnd)
            field.setText("Test 123 中文")
            editor.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            assertEquals(11, all(activity.window.decorView).filterIsInstance<TextView>().count { it.text.toString() == "Test 123 中文" })
            c.recreate(); flush()
            assertEquals(11, all(c.get().window.decorView).filterIsInstance<TextView>().count { it.text.toString() == "Test 123 中文" })
        }
    }

    @Test fun systemAndManagerPickersAllowMultipleFilesWithoutHidingUnknownMimeTypes() {
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            flush()
            val activity = c.get()
            val menu = android.widget.PopupMenu(activity, activity.window.decorView).menu
            activity.onCreateOptionsMenu(menu)
            activity.onOptionsItemSelected(menu.findItem(1))
            ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null, 0, 0)
            val intent = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
            assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
            assertEquals("*/*", intent.type); assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            activity.onOptionsItemSelected(menu.findItem(1))
            ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null, 1, 1)
            val chooser = Shadows.shadowOf(activity).nextStartedActivityForResult.intent
            @Suppress("DEPRECATION") val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals(Intent.ACTION_GET_CONTENT, inner.action); assertTrue(inner.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        }
    }

    @Test fun corruptCatalogIsNotReplacedByASubsequentImport() {
        val directory = File(context.filesDir, "fonts").apply { mkdirs() }
        val catalog = File(directory, "catalog.json").apply { writeText("corrupt") }
        val report = FontImporter(context).import(listOf(Uri.fromFile(fixture("Regular.ttf"))))
        assertEquals(0, report.added); assertEquals(1, report.issues.size)
        assertEquals("corrupt", catalog.readText())
    }
}
