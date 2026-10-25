package com.iqonli.ezcd.widget
import android.app.Activity
import android.content.*
import android.content.pm.ProviderInfo
import android.database.*
import android.os.*
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.ui.EzcdApplication
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.*
import java.io.File
import java.time.*
import java.util.concurrent.*
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
@LooperMode(LooperMode.Mode.PAUSED)
class AutoBackupUiTest {
    class FakeDocuments: DocumentsProvider() {
        data class Doc(var name: String,val file: File)
        private val docs=linkedMapOf<String,Doc>()
        var failWrites=false
        var created=0
        override fun onCreate()=true
        override fun queryRoots(projection: Array<out String>?): Cursor=MatrixCursor(projection ?: arrayOf(DocumentsContract.Root.COLUMN_ROOT_ID))
        private fun cursor(projection: Array<out String>?): MatrixCursor=MatrixCursor(projection ?: arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_FLAGS))
        private fun add(c: MatrixCursor,id: String,name: String) {
            c.addRow(c.columnNames.map<String,Any?> { col -> when(col) {
                DocumentsContract.Document.COLUMN_DOCUMENT_ID -> id
                DocumentsContract.Document.COLUMN_DISPLAY_NAME -> name
                DocumentsContract.Document.COLUMN_MIME_TYPE -> if(id=="root") DocumentsContract.Document.MIME_TYPE_DIR else "application/json"
                DocumentsContract.Document.COLUMN_FLAGS -> DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE or DocumentsContract.Document.FLAG_SUPPORTS_WRITE or DocumentsContract.Document.FLAG_SUPPORTS_RENAME or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
                else -> null
            } }.toTypedArray())
        }
        @Synchronized override fun queryDocument(id: String,projection: Array<out String>?): Cursor=cursor(projection).apply { add(this,id,if(id=="root") "Fake drive" else checkNotNull(docs[id]).name) }
        @Synchronized override fun queryChildDocuments(parent: String,projection: Array<out String>?,sort: String?): Cursor=cursor(projection).apply { docs.forEach { (id,doc) -> add(this,id,doc.name) } }
        @Synchronized override fun createDocument(parent: String,mime: String,name: String): String {
            val id=java.util.UUID.randomUUID().toString();val folder=File(checkNotNull(context).cacheDir,"fake-provider").apply { mkdirs() }
            docs[id]=Doc(name,File(folder,id));created++;return id
        }
        @Synchronized override fun renameDocument(id: String,name: String): String { checkNotNull(docs[id]).name=name;return id }
        @Synchronized override fun deleteDocument(id: String) { docs.remove(id)?.file?.delete() }
        @Synchronized override fun openDocument(id: String,mode: String,signal: CancellationSignal?): ParcelFileDescriptor {
            if(failWrites && mode.contains('w')) throw java.io.FileNotFoundException("Revoked or offline provider")
            return ParcelFileDescriptor.open(checkNotNull(docs[id]).file,ParcelFileDescriptor.parseMode(mode))
        }
        @Synchronized override fun isChildDocument(parent: String,id: String)=parent=="root" && (id=="root" || docs.containsKey(id))
        @Synchronized fun saved(): String?=docs.values.firstOrNull { it.name==AutoBackup.FILE_NAME }?.file?.takeIf { it.exists() }?.readText()
        @Synchronized fun count()=docs.size
    }
    private val context get()=RuntimeEnvironment.getApplication() as EzcdApplication
    private fun provider(authority: String="ezcd.test.documents"): FakeDocuments {
        val p=FakeDocuments()
        p.attachInfo(context,ProviderInfo().apply { this.authority=authority;exported=true;grantUriPermissions=true;readPermission="android.permission.MANAGE_DOCUMENTS";writePermission=readPermission })
        org.robolectric.shadows.ShadowContentResolver.registerProviderInternal(authority,p)
        AutoBackupPreferences(context).selectDirectory(DocumentsContract.buildTreeDocumentUri(authority,"root"),"Fake drive")
        return p
    }
    private fun event(name: String)=CountdownEvent("auto",name,LocalDate.now(),EventType.EVENT,createdAt=Instant.EPOCH)
    private fun flush() { val latch=CountDownLatch(1);CountdownIo.execute { latch.countDown() };assertTrue(latch.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle() }
    private fun payload(name: String)=BackupArchiveCodec.encode(BackupArchive(CountdownStorageCodec.encode(listOf(event(name))),emptyList(),null))
    @Test fun documentProviderBackupsOverwriteOneFileAndWriteFailureKeepsPreviousBackup() {
        flush();val p=provider()
        AutoBackup.write(context,payload("first"));assertEquals(1,p.count())
        AutoBackup.write(context,payload("second"));assertEquals(1,p.count());assertEquals("second",CountdownStorageCodec.decode(BackupArchiveCodec.decode(p.saved()!!).eventPayload).single().title)
        p.failWrites=true
        assertThrows(Exception::class.java) { AutoBackup.write(context,payload("third")) }
        assertEquals(1,p.count());assertEquals("second",CountdownStorageCodec.decode(BackupArchiveCodec.decode(p.saved()!!).eventPayload).single().title)
    }
    @Test fun foregroundAndBackgroundExportButInternalActivityNavigationDoesNot() {
        flush();val p=provider();val settings=AutoBackupPreferences(context);settings.onOpen=true;settings.onExit=true
        CountdownRepository(context).save(listOf(event("opened")))
        val first=Robolectric.buildActivity(Activity::class.java).get();val second=Robolectric.buildActivity(Activity::class.java).get()
        context.onActivityStarted(first);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500));awaitTitle(p,"opened");awaitSuccessToast()
        org.robolectric.shadows.ShadowToast.reset()
        val afterOpen=p.created
        context.onActivityStarted(second);context.onActivityStopped(first);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1));flush();assertEquals(afterOpen,p.created)
        CountdownRepository(context).save(listOf(event("exited")))
        context.onActivityStopped(second);Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(700));awaitTitle(p,"exited");awaitSuccessToast()
        assertEquals(afterOpen+1,p.created);assertEquals(1,p.count())
    }
    @Test fun unchangedAutomaticBackupDoesNotWriteOrToastAndFailuresRemainRetryable() {
        flush();val p=provider();val settings=AutoBackupPreferences(context);settings.onOpen=true;settings.onExit=true
        CountdownRepository(context).save(listOf(event("same")))
        AutoBackup.request(context,true);settleBackup();awaitTitle(p,"same");awaitSuccessToast()
        val initial=p.created;val fingerprint=settings.lastFingerprint;assertNotNull(fingerprint)
        org.robolectric.shadows.ShadowToast.reset()
        AutoBackup.request(context,false);settleBackup()
        assertEquals(initial,p.created);assertNull(org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
        p.failWrites=true;CountdownRepository(context).save(listOf(event("changed")))
        AutoBackup.request(context,true);settleBackup()
        assertEquals(fingerprint,settings.lastFingerprint);assertNull(org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
        p.failWrites=false;AutoBackup.request(context,false);settleBackup();awaitTitle(p,"changed");awaitSuccessToast()
        val other=provider("ezcd.test.other")
        org.robolectric.shadows.ShadowToast.reset()
        AutoBackup.request(context,true);settleBackup();assertEquals(1,other.created);awaitTitle(other,"changed");awaitSuccessToast()
    }
    @Test fun aDeletedBackupIsRecreatedEvenWhenItsContentHasNotChanged() {
        flush();val p=provider();assertTrue(AutoBackup.writeIfChanged(context,payload("same")))
        assertFalse(AutoBackup.writeIfChanged(context,payload("same")))
        val children=p.queryChildDocuments("root",arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),null as String?)
        children.use { assertTrue(it.moveToFirst());p.deleteDocument(it.getString(0)) }
        assertTrue(AutoBackup.writeIfChanged(context,payload("same")));assertNotNull(p.saved())
    }
    private fun settleBackup() {
        flush()
        val field=AutoBackup::class.java.getDeclaredField("writer").apply { isAccessible=true }
        val done=CountDownLatch(1);(field.get(null) as ExecutorService).execute { done.countDown() }
        assertTrue(done.await(10,TimeUnit.SECONDS));Shadows.shadowOf(Looper.getMainLooper()).idle()
    }
    private fun awaitSuccessToast() {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(4)
        while(System.nanoTime()<until) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            if(org.robolectric.shadows.ShadowToast.getTextOfLatestToast()==context.getString(com.iqonli.ezcd.R.string.ez_export_done)) return
            Thread.sleep(10)
        };fail("Successful automatic export must display its completion toast")
    }
    private fun awaitTitle(p: FakeDocuments,title: String) {
        val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8)
        while(System.nanoTime()<until) {
            flush()
            val actual=runCatching { CountdownStorageCodec.decode(BackupArchiveCodec.decode(p.saved()!!).eventPayload).single().title }.getOrNull()
            if(actual==title) return
            Thread.sleep(10)
        };fail("Automatic export did not produce $title")
    }
}
