package com.iqonli.ezcd.widget

import android.app.Application
import android.content.*
import android.content.pm.ProviderInfo
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.iqonli.ezcd.fonts.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.*
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.*
import java.nio.charset.Charset
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class FontZipCompatibilityUiTest {
    private val context get()=RuntimeEnvironment.getApplication() as Application
    private val store get()=FontStore(context)
    private fun bytes(name: String="Regular.ttf")=javaClass.getResourceAsStream("/font-fixtures/$name")!!.use { it.readBytes() }
    @Before fun reset() {
        File(context.filesDir,"fonts").deleteRecursively();File(context.filesDir,"font-files").deleteRecursively();FontRuntime.invalidate()
    }
    @Test fun legacyChineseNamesImportInEveryStorageModeAndRemainReadableAfterMigration() {
        val zip=File(context.cacheDir,"中文字体包.zip")
        ZipOutputStream(zip.outputStream(),Charset.forName("GB18030")).use { output ->
            output.putNextEntry(ZipEntry("字体/细分目录/常规.ttf"));output.write(bytes());output.closeEntry()
            output.putNextEntry(ZipEntry("字体/粗体.OTF"));output.write(bytes("Bold.ttf"));output.closeEntry()
        }
        // This is a real legacy-name archive, not a renamed UTF-8 archive.
        assertThrows(Exception::class.java) { ZipInputStream(zip.inputStream()).use { it.nextEntry } }
        for(mode in listOf(FontStorageMode.REFERENCE,FontStorageMode.INTERNAL,FontStorageMode.DIRECTORY)) {
            reset();val parent=File(context.cacheDir,"zip-parent-${mode.name}").apply { mkdirs() }
            store.switchStorage(mode,if(mode==FontStorageMode.DIRECTORY) Uri.fromFile(parent).toString() else null)
            val progress=mutableListOf<Int>();val report=FontImporter(context).import(listOf(Uri.fromFile(zip)),progress::add)
            assertEquals(report.issues.toString(),0,report.failures);assertEquals(2,report.added);assertEquals(progress.sorted(),progress)
            assertEquals(setOf("常规.ttf","粗体.OTF"),store.fonts().map { it.fileName }.toSet())
            store.fonts().forEach { assertNotNull(store.typeface(it)) }
            if(mode==FontStorageMode.REFERENCE) { store.switchStorage(FontStorageMode.INTERNAL);store.fonts().forEach { assertNull(it.source!!.zipEntry);assertNotNull(store.typeface(it)) } }
            assertFalse(File(context.cacheDir,"font-work").exists())
        }
    }
    private fun storedDescriptor(corrupt: Boolean=false): ByteArray {
        val name="目录/常规.ttf".toByteArray();val data=bytes();val crc=CRC32().apply { update(data) }.value
        val out=ByteArrayOutputStream()
        fun word(value: Long,n: Int) { repeat(n) { out.write((value ushr (it*8)).toInt() and 255) } }
        word(0x04034b50,4);word(20,2);word(0x808,2);word(0,2);word(0,4);word(0,4);word(0,4);word(0,4);word(name.size.toLong(),2);word(0,2);out.write(name);out.write(data)
        word(0x08074b50,4);word(crc,4);word(data.size.toLong(),4);word(data.size.toLong(),4)
        val start=out.size()
        word(0x02014b50,4);word(20,2);word(20,2);word(0x808,2);word(0,2);word(0,4);word(if(corrupt) crc xor 1 else crc,4);word(data.size.toLong(),4);word(data.size.toLong(),4)
        word(name.size.toLong(),2);word(0,2);word(0,2);word(0,2);word(0,2);word(0,4);word(0,4);out.write(name)
        val length=out.size()-start
        word(0x06054b50,4);word(0,2);word(0,2);word(1,2);word(1,2);word(length.toLong(),4);word(start.toLong(),4);word(0,2)
        return out.toByteArray()
    }
    @Test fun storedEntriesWithDataDescriptorsWorkAndZipSignatureDoesNotRequireAnExtension() {
        val data=storedDescriptor()
        assertThrows(ZipException::class.java) { ZipInputStream(ByteArrayInputStream(data)).use { it.nextEntry } }
        val file=File(context.cacheDir,"download-without-extension").apply { writeBytes(data) }
        store.switchStorage(FontStorageMode.REFERENCE)
        val report=FontImporter(context).import(listOf(Uri.fromFile(file)))
        assertEquals(report.issues.toString(),0,report.failures);assertEquals(1,report.added);assertNotNull(store.typeface(store.fonts().single()))
    }
    @Test fun crcFailuresNeverInstallAFontOrLeaveWorkFiles() {
        val file=File(context.cacheDir,"bad-crc.zip").apply { writeBytes(storedDescriptor(true)) }
        store.switchStorage(FontStorageMode.INTERNAL)
        val report=FontImporter(context).import(listOf(Uri.fromFile(file)))
        assertEquals(0,report.added);assertEquals(1,report.failures);assertTrue(store.fonts().isEmpty());assertFalse(File(context.cacheDir,"font-work").exists())
    }
    class PipeProvider: ContentProvider() {
        var data=byteArrayOf();var opens=0
        override fun onCreate()=true
        override fun getType(uri: Uri)="application/octet-stream"
        override fun query(uri: Uri,projection: Array<out String>?,s: String?,args: Array<out String>?,sort: String?)=MatrixCursor(projection ?: arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE)).apply {
            addRow(columnNames.map { if(it==OpenableColumns.DISPLAY_NAME) "download" else null })
        }
        override fun openFile(uri: Uri,mode: String): ParcelFileDescriptor {
            opens++;check(opens==1) { "Provider permits one stream" }
            val pair=ParcelFileDescriptor.createPipe()
            Thread { ParcelFileDescriptor.AutoCloseOutputStream(pair[1]).use { it.write(data) } }.start()
            return pair[0]
        }
        override fun insert(uri: Uri,v: ContentValues?): Uri?=null
        override fun delete(uri: Uri,s: String?,a: Array<out String>?)=0
        override fun update(uri: Uri,v: ContentValues?,s: String?,a: Array<out String>?)=0
    }
    @Test fun providerPipeWithNoLengthAndNoExtensionIsReadOnceAndStagingIsRemoved() {
        val authority="ezcd.zip.pipe";val provider=PipeProvider().apply { data=storedDescriptor() }
        provider.attachInfo(context,ProviderInfo().apply { this.authority=authority;exported=true })
        ShadowContentResolver.registerProviderInternal(authority,provider);store.switchStorage(FontStorageMode.INTERNAL)
        val report=FontImporter(context).import(listOf(Uri.parse("content://$authority/download")))
        assertEquals(report.issues.toString(),0,report.failures);assertEquals(1,report.added);assertEquals(1,provider.opens)
        assertNotNull(store.typeface(store.fonts().single()));assertFalse(File(context.cacheDir,"font-work").exists())
    }
}
