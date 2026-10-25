package com.iqonli.ezcd.widget

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract as DC
import android.provider.DocumentsProvider
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.fonts.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowContentResolver
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
class FontStorageUiTest {
    private val context get() = RuntimeEnvironment.getApplication() as Application
    private val store get() = FontStore(context)
    private fun fixture(name: String = "Regular.ttf"): File = File(context.cacheDir, "input-$name").also { file ->
        javaClass.getResourceAsStream("/font-fixtures/$name")!!.use { input -> file.outputStream().use { input.copyTo(it) } }
    }
    private fun imported(name: String = "Regular.ttf"): ImportedFont {
        if (store.storage().mode == FontStorageMode.UNSELECTED) store.switchStorage(FontStorageMode.REFERENCE)
        val file = fixture(name); val report = FontImporter(context).import(listOf(Uri.fromFile(file)))
        assertEquals(report.issues.toString(), 0, report.issues.size)
        return store.fonts().first { it.metadata.subfamily == name.substringBefore('.') }
    }
    private fun disk(uri: String): File = File(checkNotNull(Uri.parse(uri).path))
    private fun directory(): String = Uri.fromFile(File(context.cacheDir, "chosen").apply { mkdirs() }).toString()
    private fun all(view: View): List<View> = listOf(view) + if (view is ViewGroup) (0 until view.childCount).flatMap { all(view.getChildAt(it)) } else emptyList()
    private fun settle() {
        repeat(3) {
            val latch = CountDownLatch(1); FontIo.submit({ latch.countDown() }) { }
            assertTrue(latch.await(15, TimeUnit.SECONDS)); Shadows.shadowOf(Looper.getMainLooper()).idle()
        }
    }
    @Before fun reset() {
        File(context.filesDir, "fonts").deleteRecursively(); File(context.filesDir, "font-files").deleteRecursively()
        File(context.cacheDir, "chosen").deleteRecursively()
    }

    @Test fun freshInstallDefaultsToReferencesAndDoesNotRetainAnImportedCopy() {
        assertEquals(FontStorageMode.UNSELECTED, store.storage().mode)
        val font = imported()
        assertEquals(Uri.fromFile(fixture()).toString(), font.source!!.uri)
        assertFalse(File(context.filesDir, "font-files").exists())
        assertEquals(listOf("catalog.json"), File(context.filesDir, "fonts").list()!!.toList())
        assertNotNull(store.typeface(font))
        fixture().delete()
        assertThrows(Exception::class.java) { store.typeface(font) }
    }

    @Test fun referenceToInternalCopiesAndInternalToReferenceDeletesManagedBytes() {
        val font = imported(); val original = disk(font.source!!.uri)
        store.switchStorage(FontStorageMode.INTERNAL)
        val copied = store.find(font.id)!!; val copy = disk(copied.source!!.uri)
        assertTrue(original.isFile); assertTrue(copy.isFile); assertNotEquals(original, copy)
        assertArrayEquals(original.readBytes(), copy.readBytes())
        val root = disk(store.storage().root!!)
        store.switchStorage(FontStorageMode.REFERENCE)
        assertTrue(store.fonts().isEmpty()); assertFalse(root.exists()); assertTrue(original.isFile)
    }

    @Test fun referenceToDirectoryCopiesWithoutMovingOriginalFiles() {
        val font = imported(); val original = disk(font.source!!.uri)
        val parent = directory(); File(disk(parent), "unrelated.txt").writeText("keep")
        store.switchStorage(FontStorageMode.DIRECTORY, parent)
        assertTrue(original.isFile); assertTrue(disk(store.find(font.id)!!.source!!.uri).isFile)
        assertEquals(parent, store.storage().directory)
        assertEquals("keep", File(disk(parent), "unrelated.txt").readText())
    }

    @Test fun managedLocationsMoveBothWaysAndNeverDeleteTheSelectedParent() {
        store.switchStorage(FontStorageMode.INTERNAL); val font = imported()
        val internal = disk(font.source!!.uri)
        val parent = directory(); File(disk(parent), "other.txt").writeText("untouched")
        store.switchStorage(FontStorageMode.DIRECTORY, parent)
        assertFalse(internal.exists())
        val directoryRoot = disk(store.storage().root!!)
        val outside = disk(store.find(font.id)!!.source!!.uri); assertTrue(outside.isFile)
        store.switchStorage(FontStorageMode.INTERNAL)
        assertFalse(outside.exists()); assertFalse(directoryRoot.exists())
        assertTrue(disk(store.find(font.id)!!.source!!.uri).isFile)
        assertEquals("untouched", File(disk(parent), "other.txt").readText())
    }

    @Test fun directoryToReferenceKeepRetainsFilesButClearsEveryRecord() {
        val font = imported(); store.switchStorage(FontStorageMode.DIRECTORY, directory())
        val root = disk(store.storage().root!!); val before = store.find(font.id)!!
        store.switchStorage(FontStorageMode.REFERENCE, deleteDirectory = false)
        assertEquals(FontStorageMode.REFERENCE, store.storage().mode)
        assertTrue(store.fonts().isEmpty()); assertTrue(root.isDirectory)
        assertTrue(disk(before.source!!.uri).isFile)
    }

    @Test fun directoryToReferenceDeleteRemovesManagedFolderAndItsRecords() {
        imported(); val parent = directory(); File(disk(parent), "other.txt").writeText("keep")
        store.switchStorage(FontStorageMode.DIRECTORY, parent); val root = disk(store.storage().root!!)
        store.switchStorage(FontStorageMode.REFERENCE, deleteDirectory = true)
        assertFalse(root.exists()); assertTrue(store.fonts().isEmpty()); assertTrue(disk(parent).isDirectory)
        assertEquals("keep", File(disk(parent), "other.txt").readText())
    }

    @Test fun removingOneCollectionFaceDoesNotRemoveTheSharedManagedFile() {
        store.switchStorage(FontStorageMode.INTERNAL)
        val input = fixture("Family.ttc"); assertEquals(2, FontImporter(context).import(listOf(Uri.fromFile(input))).added)
        val fonts = store.fonts(); val file = disk(fonts.first().source!!.uri)
        store.delete(setOf(fonts.first().id)); assertTrue(file.isFile); assertEquals(1, store.fonts().size)
        assertNotNull(store.typeface(store.fonts().single()))
        store.delete(setOf(fonts.last().id)); assertFalse(file.exists()); assertTrue(input.isFile)
    }

    @Test fun failedCopyRollsBackTheEntireMigrationAndRemovesStagingCopies() {
        val regular = imported(); val bold = imported("Bold.ttf"); disk(bold.source!!.uri).delete()
        val parent = directory()
        assertThrows(Exception::class.java) { store.switchStorage(FontStorageMode.DIRECTORY, parent) }
        assertEquals(FontStorageMode.REFERENCE, store.storage().mode)
        assertEquals(setOf(regular.id, bold.id), store.fonts().map { it.id }.toSet())
        assertTrue(disk(regular.source!!.uri).isFile); assertTrue(disk(parent).listFiles()!!.isEmpty())
        assertNotNull(store.typeface(regular))
    }

    @Test fun zipReferencesPreviewWithoutPersistentExtractionThenCopyIntoManagedStorage() {
        store.switchStorage(FontStorageMode.REFERENCE)
        val zip = File(context.cacheDir, "referenced.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            out.putNextEntry(ZipEntry("notes.txt")); out.write("not a font".toByteArray()); out.closeEntry()
            out.putNextEntry(ZipEntry("folder/font.ttf")); out.write(fixture().readBytes()); out.closeEntry()
        }
        assertEquals(1, FontImporter(context).import(listOf(Uri.fromFile(zip))).added)
        val font = store.fonts().single(); assertEquals("folder/font.ttf", font.source!!.zipEntry)
        assertEquals(1, font.source!!.zipIndex); assertNotNull(store.typeface(font))
        assertFalse(File(context.filesDir, "font-files").exists())
        store.switchStorage(FontStorageMode.INTERNAL); assertTrue(zip.isFile)
        assertNull(store.find(font.id)!!.source!!.zipEntry)
        zip.delete(); assertNotNull(store.typeface(store.find(font.id)!!))
    }

    @Test fun versionOneCatalogPreservesInternalLocationAndExistingFontOnUpgrade() {
        store.switchStorage(FontStorageMode.INTERNAL); val font = imported()
        val oldFile = File(context.filesDir, "fonts/${font.blob}.font")
        disk(font.source!!.uri).copyTo(oldFile)
        val catalog = File(context.filesDir, "fonts/catalog.json")
        val current = JSONObject(catalog.readText()); current.put("version", 1)
        val root = disk(store.storage().root!!); root.deleteRecursively()
        catalog.writeText(current.toString())
        assertEquals(FontStorageMode.INTERNAL, store.storage().mode)
        assertNotNull(store.typeface(store.fonts().single()))
        store.switchStorage(FontStorageMode.REFERENCE)
        assertFalse(oldFile.exists()); assertTrue(catalog.isFile); assertTrue(store.fonts().isEmpty())
    }

    @Test fun storageMenuIsBeforePlusAndExistingReferencesAndCancelDoesNotChangeStorage() {
        store.switchStorage(FontStorageMode.REFERENCE)
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle(); val a = c.get(); val menu = PopupMenu(a, a.window.decorView).menu; a.onCreateOptionsMenu(menu)
            assertEquals(2, menu.getItem(0).itemId); assertEquals(1, menu.getItem(1).itemId)
            a.onOptionsItemSelected(menu.findItem(2))
            val sheet = ShadowAlertDialog.getLatestAlertDialog(); assertEquals(2, sheet.listView.checkedItemPosition)
            assertEquals(listOf(a.getString(R.string.ez_font_choose_directory),a.getString(R.string.ez_font_internal),a.getString(R.string.ez_font_reference)), (0..2).map { sheet.listView.adapter.getItem(it).toString() })
            sheet.listView.performItemClick(null, 1, 1)
            val confirm = ShadowAlertDialog.getLatestAlertDialog()
            assertTrue(all(confirm.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString() == a.getString(R.string.ez_font_reference_to_internal) })
            confirm.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick(); Shadows.shadowOf(Looper.getMainLooper()).idle(); settle()
            assertEquals(FontStorageMode.REFERENCE, store.storage().mode)
        }
    }

    private fun referenceConfirmation(activity: FontManagerActivity): android.app.AlertDialog {
        val menu = PopupMenu(activity, activity.window.decorView).menu
        activity.onCreateOptionsMenu(menu); activity.onOptionsItemSelected(menu.findItem(2))
        ShadowAlertDialog.getLatestAlertDialog().listView.performItemClick(null, 2, 2)
        return ShadowAlertDialog.getLatestAlertDialog()
    }

    @Test fun directoryReferenceDialogHasThreeButtonsAndCancelPreservesEverythingThenKeepClearsRecords() {
        imported(); store.switchStorage(FontStorageMode.DIRECTORY, directory())
        val before = store.fonts(); val root = disk(store.storage().root!!)
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle(); val a = c.get(); var dialog = referenceConfirmation(a)
            assertEquals(a.getString(android.R.string.cancel), dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).text.toString())
            assertEquals(a.getString(R.string.ez_delete), dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).text.toString())
            assertEquals(a.getString(R.string.ez_font_keep), dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).text.toString())
            dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick(); Shadows.shadowOf(Looper.getMainLooper()).idle(); settle()
            assertEquals(FontStorageMode.DIRECTORY, store.storage().mode); assertEquals(before,store.fonts()); assertTrue(root.isDirectory)
            dialog = referenceConfirmation(a)
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick(); Shadows.shadowOf(Looper.getMainLooper()).idle(); settle()
            assertEquals(FontStorageMode.REFERENCE,store.storage().mode); assertTrue(store.fonts().isEmpty()); assertTrue(root.isDirectory)
        }
    }

    @Test fun directoryReferenceDeleteButtonClearsRecordsAndDeletesOwnedDirectory() {
        imported(); store.switchStorage(FontStorageMode.DIRECTORY,directory()); val root=disk(store.storage().root!!)
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle(); referenceConfirmation(c.get()).getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick()
            Shadows.shadowOf(Looper.getMainLooper()).idle(); settle()
            assertTrue(store.fonts().isEmpty()); assertFalse(root.exists()); assertTrue(disk(directory()).isDirectory)
        }
    }

    @Test fun allSixChineseConfirmationMessagesMatchTheRequestedText() {
        val c = context.createConfigurationContext(android.content.res.Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag("zh-Hans")) })
        val cases = listOf(
            Triple(FontStorageMode.DIRECTORY,FontStorageMode.INTERNAL,"此操作将会移动所有字体文件到应用内部，是否继续？"),
            Triple(FontStorageMode.INTERNAL,FontStorageMode.DIRECTORY,"此操作将会移动所有字体文件到选择的目录，是否继续？"),
            Triple(FontStorageMode.DIRECTORY,FontStorageMode.REFERENCE,"此操作将清除所有字体记录。是否保留原字体文件目录？"),
            Triple(FontStorageMode.REFERENCE,FontStorageMode.DIRECTORY,"此操作将会复制所有字体文件到选择的目录，是否继续？"),
            Triple(FontStorageMode.INTERNAL,FontStorageMode.REFERENCE,"此操作将清除所有字体记录，并删除应用内部所有字体文件，是否继续？"),
            Triple(FontStorageMode.REFERENCE,FontStorageMode.INTERNAL,"此操作将会复制所有字体文件到应用内部，是否继续？"))
        cases.forEach { (from,to,text) -> assertEquals(text,c.getString(FontStoragePolicy.confirmation(from,to))) }
    }

    @Test fun singletonIsBoldAndFamilyArrowIsAnImageRatherThanText() {
        imported()
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle(); val list = all(c.get().window.decorView).filterIsInstance<ExpandableListView>().single()
            val row = list.expandableListAdapter.getGroupView(0,false,null,list)
            val title = all(row).filterIsInstance<TextView>().first { it.text.toString().startsWith("ezCD Fixture") }
            assertTrue(title.typeface.isBold)
        }
        imported("Bold.ttf")
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle(); val list = all(c.get().window.decorView).filterIsInstance<ExpandableListView>().single()
            val row = list.expandableListAdapter.getGroupView(0,false,null,list)
            assertTrue(all(row).filterIsInstance<ImageView>().any { it.visibility == View.VISIBLE && it.drawable != null })
            assertTrue(all(row).filterIsInstance<TextView>().none { it.text.contains('▾') || it.text.contains('▸') })
        }
    }

    @Test fun longPressSelectsFamilyThenDeleteRemovesReferencesAndKeepsOriginals() {
        val aFont = imported(); val bFont = imported("Bold.ttf")
        Robolectric.buildActivity(FontManagerActivity::class.java).setup().use { c ->
            settle(); val a = c.get(); val list = all(a.window.decorView).filterIsInstance<ExpandableListView>().single()
            assertTrue(list.onItemLongClickListener!!.onItemLongClick(list,null,0,0))
            val menu = org.robolectric.util.ReflectionHelpers.getField<android.view.ActionMode>(a,"actionMode")
            assertNotNull(menu); assertTrue(menu.title.toString().contains("2"))
            val method = FontManagerActivity::class.java.getDeclaredMethod("deleteSelected").apply { isAccessible=true }; method.invoke(a)
            val dialog=ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()
            Shadows.shadowOf(Looper.getMainLooper()).idle();settle()
            assertTrue(store.fonts().isEmpty());assertTrue(disk(aFont.source!!.uri).isFile);assertTrue(disk(bFont.source!!.uri).isFile)
        }
    }

    class Drive : DocumentsProvider() {
        data class Item(val parent:String?,val name:String,val file:File)
        val docs=linkedMapOf<String,Item>();var failWrites=false;var failDeletes=false
        override fun onCreate()=true
        private fun cursor(projection:Array<out String>?)=MatrixCursor(projection ?: arrayOf(DC.Document.COLUMN_DOCUMENT_ID,DC.Document.COLUMN_DISPLAY_NAME,DC.Document.COLUMN_MIME_TYPE,DC.Document.COLUMN_FLAGS))
        private fun row(c:MatrixCursor,id:String,item:Item) { c.addRow(c.columnNames.map<String, Any?> { key -> when(key) {
            DC.Document.COLUMN_DOCUMENT_ID -> id;DC.Document.COLUMN_DISPLAY_NAME -> item.name
            DC.Document.COLUMN_MIME_TYPE -> if(item.file.isDirectory) DC.Document.MIME_TYPE_DIR else "application/octet-stream"
            DC.Document.COLUMN_FLAGS -> DC.Document.FLAG_DIR_SUPPORTS_CREATE or DC.Document.FLAG_SUPPORTS_WRITE or DC.Document.FLAG_SUPPORTS_DELETE
            else -> null
        } }.toTypedArray()) }
        override fun queryRoots(projection:Array<out String>?) :Cursor=MatrixCursor(projection ?: arrayOf(DC.Root.COLUMN_ROOT_ID))
        override fun queryDocument(id:String,projection:Array<out String>?):Cursor=cursor(projection).apply { docs[id]?.let { row(this,id,it) } }
        override fun queryChildDocuments(parent:String,projection:Array<out String>?,sort:String?):Cursor=cursor(projection).apply { docs.filterValues { it.parent==parent }.forEach { (id,item) -> row(this,id,item) } }
        override fun createDocument(parent:String,mime:String,name:String):String {
            if(failWrites) throw java.io.FileNotFoundException("offline")
            val id=java.util.UUID.randomUUID().toString();val file=File(docs.getValue(parent).file,name)
            if(mime==DC.Document.MIME_TYPE_DIR) check(file.mkdir()) else check(file.createNewFile())
            docs[id]=Item(parent,name,file);return id
        }
        override fun openDocument(id:String,mode:String,signal:CancellationSignal?):ParcelFileDescriptor {
            if(failWrites && mode.contains('w')) throw java.io.FileNotFoundException("offline")
            return ParcelFileDescriptor.open(docs.getValue(id).file,ParcelFileDescriptor.parseMode(mode))
        }
        override fun deleteDocument(id:String) {
            if(failDeletes) throw java.io.FileNotFoundException("deletion denied")
            val item=docs[id] ?: return;check(item.file.delete());docs.remove(id)
        }
        override fun isChildDocument(parent:String,id:String)=id==parent || generateSequence(docs[id]?.parent) { docs[it]?.parent }.any { it==parent }
    }
    private fun drive():Pair<Drive,String> {
        val p=Drive();val authority="ezcd.font.test.documents"
        p.attachInfo(context,ProviderInfo().apply { this.authority=authority;exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission=readPermission })
        p.docs["root"]=Drive.Item(null,"Drive",File(context.cacheDir,"document-drive").apply { deleteRecursively();mkdirs() })
        ShadowContentResolver.registerProviderInternal(authority,p)
        return p to DC.buildTreeDocumentUri(authority,"root").toString()
    }

    @Test fun contentReferenceRequiresDurableGrantAndSurvivesANewStoreInstance() {
        store.switchStorage(FontStorageMode.REFERENCE)
        val (provider,parent)=drive()
        val id=provider.createDocument("root","font/ttf","chosen.ttf")
        provider.docs.getValue(id).file.writeBytes(fixture().readBytes())
        val uri=DC.buildDocumentUriUsingTree(Uri.parse(parent),id)
        val denied=FontImporter(context).import(listOf(uri));assertEquals(0,denied.added);assertEquals(1,denied.issues.size)
        context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val allowed=FontImporter(context).import(listOf(uri));assertEquals(allowed.issues.toString(),1,allowed.added)
        val font=FontStore(context).fonts().single();assertEquals("chosen.ttf",font.fileName)
        assertNotNull(FontStore(context).typeface(font));assertFalse(File(context.filesDir,"font-files").exists())
    }

    @Test fun failedStagingCleanupMustFinishBeforeAnotherMigrationCanReplaceItsRecoveryPointer() {
        val (provider,parent)=drive();imported();val missing=imported("Bold.ttf");disk(missing.source!!.uri).delete()
        provider.failDeletes=true
        assertThrows(Exception::class.java) { store.switchStorage(FontStorageMode.DIRECTORY,parent) }
        val catalog=File(context.filesDir,"fonts/catalog.json");val staging=JSONObject(catalog.readText()).getString("staging")
        assertThrows(Exception::class.java) { store.switchStorage(FontStorageMode.INTERNAL) }
        assertEquals(staging,JSONObject(catalog.readText()).getString("staging"))
        provider.failDeletes=false;fixture("Bold.ttf");store.switchStorage(FontStorageMode.INTERNAL)
        assertEquals(1,provider.docs.size);assertEquals(2,store.fonts().size)
    }

    @Test fun reimportingACollectionRestoresADeletedFaceWithoutLeakingASecondManagedFile() {
        store.switchStorage(FontStorageMode.INTERNAL)
        val uri=Uri.fromFile(fixture("Family.ttc"));FontImporter(context).import(listOf(uri))
        val face=store.fonts().first();val original=face.source!!.uri
        store.delete(setOf(face.id));val report=FontImporter(context).import(listOf(uri))
        assertEquals(1,report.added);assertEquals(1,report.duplicates);assertEquals(2,store.fonts().size)
        assertTrue(store.fonts().all { it.source!!.uri==original })
        assertEquals(2,disk(store.storage().root!!).listFiles()!!.size)
    }

    @Test fun safDirectoryStoresAndPreviewsFontsThenMovesThemBackIntoInternalStorage() {
        val (provider,parent)=drive();val font=imported()
        store.switchStorage(FontStorageMode.DIRECTORY,parent)
        val outside=store.find(font.id)!!;assertEquals("content",Uri.parse(outside.source!!.uri).scheme)
        assertNotNull(store.typeface(outside))
        store.switchStorage(FontStorageMode.INTERNAL)
        assertEquals(1,provider.docs.size);assertNotNull(store.typeface(store.find(font.id)!!))
    }

    @Test fun safFailurePreservesOldStorageAndDeferredDeletionRetriesWithoutLosingNewCopy() {
        val (provider,parent)=drive();val font=imported()
        provider.failWrites=true
        assertThrows(Exception::class.java) { store.switchStorage(FontStorageMode.DIRECTORY,parent) }
        assertEquals(FontStorageMode.REFERENCE,store.storage().mode);assertNotNull(store.typeface(font))
        provider.failWrites=false;store.switchStorage(FontStorageMode.DIRECTORY,parent)
        provider.failDeletes=true
        val result=store.switchStorage(FontStorageMode.INTERNAL)
        assertTrue(result.pendingCleanup>0);assertNotNull(store.typeface(store.find(font.id)!!))
        provider.failDeletes=false;store.fonts();assertEquals(1,provider.docs.size)
    }
}
