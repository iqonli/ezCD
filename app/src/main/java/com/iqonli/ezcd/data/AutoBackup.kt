// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.data

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

internal class AutoBackupPreferences(private val context: Context) {
    private val prefs=context.getSharedPreferences("automatic_backup",Context.MODE_PRIVATE)
    var onOpen: Boolean get()=prefs.getBoolean("onOpen",false);set(v) { prefs.edit().putBoolean("onOpen",v).apply() }
    var onExit: Boolean get()=prefs.getBoolean("onExit",false);set(v) { prefs.edit().putBoolean("onExit",v).apply() }
    val directory: Uri? get()=prefs.getString("directory",null)?.let(Uri::parse)
    val localDirectory: String? get()=prefs.getString("localDirectory",null)
    val directoryName: String? get()=prefs.getString("directoryName",null)
    var downloadFile: Uri? get()=prefs.getString("downloadFile",null)?.let(Uri::parse);set(v) { check(prefs.edit().putString("downloadFile",v?.toString()).commit()) }
    var lastFingerprint: String? get()=prefs.getString("lastFingerprint",null);set(v) { check(prefs.edit().putString("lastFingerprint",v).commit()) }
    val destinationKey: String get()=localDirectory?.let { "local:$it" } ?: directory?.let { "tree:$it" } ?: "downloads"
    var lastError: String? get()=prefs.getString("lastError",null);set(v) { prefs.edit().putString("lastError",v).apply() }
    fun useDownloads() { check(prefs.edit().remove("localDirectory").remove("directory").remove("directoryName").remove("lastError").commit()) }
    fun selectDirectory(uri: Uri,name: String) { check(prefs.edit().remove("localDirectory").putString("directory",uri.toString()).putString("directoryName",name).remove("lastError").commit()) }
    fun selectLocal(file: File) { require(file.isAbsolute && file.isDirectory);check(prefs.edit().putString("localDirectory",file.canonicalPath).remove("directory").remove("directoryName").remove("lastError").commit()) }
    fun legacyPermissionNeeded()=Build.VERSION.SDK_INT<29 && directory==null && context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED
}

internal object AutoBackup {
    const val FILE_NAME="ezCD-backup.json"
    private val writer=Executors.newSingleThreadExecutor { r -> Thread(r,"ezCD-backup").apply { isDaemon=true } }
    fun request(context: Context,opening: Boolean) {
        val app=context.applicationContext;val preferences=AutoBackupPreferences(app)
        if (!(if(opening) preferences.onOpen else preferences.onExit)) return
        // Snapshot after preceding autosaves; a slow cloud provider never blocks event storage.
        CountdownIo.submit({ BackupArchiveService(app).export() }) { result ->
            result.onSuccess { payload -> writer.execute { runCatching { writeIfChanged(app,payload) }.onSuccess { changed ->
                if(changed) Handler(Looper.getMainLooper()).post { android.widget.Toast.makeText(app,com.iqonli.ezcd.R.string.ez_export_done,android.widget.Toast.LENGTH_SHORT).show() }
            }.onFailure { preferences.lastError=it.javaClass.simpleName;android.util.Log.e("ezCD","Automatic backup failed",it) } } }
                .onFailure { preferences.lastError=it.javaClass.simpleName;android.util.Log.e("ezCD","Cannot prepare automatic backup",it) }
        }
    }
    @Synchronized fun writeIfChanged(context: Context,payload: String): Boolean {
        BackupArchiveCodec.decode(payload)
        val settings=AutoBackupPreferences(context)
        val fingerprint=BackupContentFingerprint.of(settings.destinationKey,payload)
        if(settings.lastFingerprint==fingerprint && backupExists(context,settings)) {
            settings.lastError=null;return false
        }
        write(context,payload)
        settings.lastFingerprint=fingerprint
        return true
    }
    private fun backupExists(context: Context,settings: AutoBackupPreferences): Boolean = runCatching {
        settings.localDirectory?.let { return@runCatching File(it,FILE_NAME).isFile }
        settings.directory?.let { tree ->
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
            return@runCatching context.contentResolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),Bundle.EMPTY,null)?.use { c ->
                var found=false;while(c.moveToNext()) if(c.getString(1)==FILE_NAME) { found=true;break };found
            } == true
        }
        if(Build.VERSION.SDK_INT>=29) {
            settings.downloadFile?.let { uri -> context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),null,null,null)?.use { it.moveToFirst() && it.getString(0)==FILE_NAME } } == true
        } else {
            @Suppress("DEPRECATION") val folder=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            File(folder,FILE_NAME).isFile
        }
    }.getOrDefault(false)
    fun write(context: Context,payload: String) {
        BackupArchiveCodec.decode(payload)
        val settings=AutoBackupPreferences(context)
        val local=settings.localDirectory
        if(local!=null) {
            check(com.iqonli.ezcd.ui.DirectoryAccess.full(context)) { "Full storage access required" }
            writeLocal(File(local),payload)
        } else settings.directory?.let { writeTree(context,it,payload) } ?: if (Build.VERSION.SDK_INT>=29) writeDownloads(context,settings,payload) else writeLegacy(context,payload)
        settings.lastError=null
    }
    private fun output(context: Context,uri: Uri,payload: String) {
        checkNotNull(context.contentResolver.openOutputStream(uri,"rwt")).use { stream ->
            stream.write(payload.toByteArray(Charsets.UTF_8));stream.flush()
            if(stream is FileOutputStream) runCatching { stream.fd.sync() }
        }
    }
    private fun writeLegacy(context: Context,payload: String) {
        check(!AutoBackupPreferences(context).legacyPermissionNeeded()) { "Storage permission required" }
        @Suppress("DEPRECATION") val folder=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        check(folder.isDirectory || folder.mkdirs())
        writeLocal(folder,payload)
    }
    private fun writeLocal(folder: File,payload: String) {
        require(folder.isDirectory)
        val file=AtomicFile(File(folder,FILE_NAME));val stream=file.startWrite()
        try { stream.write(payload.toByteArray(Charsets.UTF_8));file.finishWrite(stream) }
        catch(error: Exception) { file.failWrite(stream);throw error }
    }
    @android.annotation.TargetApi(29)
    private fun writeDownloads(context: Context,settings: AutoBackupPreferences,payload: String) {
        val resolver=context.contentResolver
        val old=settings.downloadFile?.takeIf { uri ->
            runCatching { resolver.query(uri,arrayOf(MediaStore.MediaColumns.RELATIVE_PATH),null,null,null)?.use { it.moveToFirst() && it.getString(0).trimEnd('/')==Environment.DIRECTORY_DOWNLOADS } == true }.getOrDefault(false)
        }
        val values=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"ezCD-backup.pending.json");put(MediaStore.MediaColumns.MIME_TYPE,"application/json")
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS);put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val fresh=checkNotNull(resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
        var oldRenamed=false;var published=false
        try {
            output(context,fresh,payload)
            if(old!=null) { check(resolver.update(old,ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,"ezCD-backup.previous.json") },null,null)>0);oldRenamed=true }
            check(resolver.update(fresh,ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,FILE_NAME);put(MediaStore.MediaColumns.IS_PENDING,0) },null,null)>0)
            settings.downloadFile=fresh;published=true
            if(old!=null) runCatching { resolver.delete(old,null,null) }
        } finally {
            if(!published) {
                runCatching { resolver.delete(fresh,null,null) }
                if(oldRenamed && old!=null) runCatching { resolver.update(old,ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME,FILE_NAME) },null,null) }
            }
        }
    }
    private fun writeTree(context: Context,tree: Uri,payload: String) {
        require(tree.scheme=="content" && DocumentsContract.isTreeUri(tree))
        val resolver=context.contentResolver
        val parent=DocumentsContract.buildDocumentUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,DocumentsContract.getTreeDocumentId(tree))
        var old: Uri?=null
        resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),Bundle.EMPTY,null)?.use { cursor ->
            while(cursor.moveToNext()) if(cursor.getString(1)==FILE_NAME) { old=DocumentsContract.buildDocumentUriUsingTree(tree,cursor.getString(0));break }
        } ?: error("Cannot read selected directory")
        val temporary=checkNotNull(DocumentsContract.createDocument(resolver,parent,"application/json","ezCD-backup.pending.json"))
        var renamedOld: Uri?=null;var published=false;var temporaryNow=temporary
        try {
            output(context,temporary,payload)
            try {
                if(old!=null) renamedOld=DocumentsContract.renameDocument(resolver,checkNotNull(old),"ezCD-backup.previous.json") ?: error("Rename unsupported")
                temporaryNow=DocumentsContract.renameDocument(resolver,temporary,FILE_NAME) ?: error("Rename unsupported")
                published=true
                renamedOld?.let { runCatching { DocumentsContract.deleteDocument(resolver,it) } }
            } catch(error: Exception) {
                // Providers without rename can still overwrite a stable document after validating the temporary copy.
                val target=if(renamedOld!=null) {
                    DocumentsContract.renameDocument(resolver,checkNotNull(renamedOld),FILE_NAME) ?: throw error
                } else old ?: checkNotNull(DocumentsContract.createDocument(resolver,parent,"application/json",FILE_NAME))
                renamedOld=null;output(context,target,payload);published=true
                runCatching { DocumentsContract.deleteDocument(resolver,temporary) }
            }
        } finally {
            if(!published) {
                runCatching { DocumentsContract.deleteDocument(resolver,temporaryNow) }
                renamedOld?.let { runCatching { DocumentsContract.renameDocument(resolver,it,FILE_NAME) } }
            }
        }
    }
}
