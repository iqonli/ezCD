// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui
import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.*
import android.view.Gravity
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.*
import java.io.File

internal object DirectoryAccess {
    fun full(context: Context)=if(Build.VERSION.SDK_INT>=30) Environment.isExternalStorageManager() else
        context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED
    fun request(activity: Activity) {
        if(Build.VERSION.SDK_INT>=30) {
            val local=Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:${activity.packageName}"))
            runCatching { activity.startActivity(local) }.onFailure { activity.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
        } else activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE),4)
    }
    @Suppress("DEPRECATION") fun downloads()=Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    fun physicalPath(uri: Uri): File? {
        if(uri.scheme=="file") return uri.path?.let(::File)
        if(uri.authority!="com.android.externalstorage.documents") return null
        val id=runCatching { if(DocumentsContract.isTreeUri(uri)) DocumentsContract.getTreeDocumentId(uri) else DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val volume=id.substringBefore(':');val relative=id.substringAfter(':',"")
        @Suppress("DEPRECATION") val root=if(volume=="primary") Environment.getExternalStorageDirectory() else File("/storage/$volume")
        val result=File(root,relative).canonicalFile
        return result.takeIf { it.path==root.canonicalPath || it.path.startsWith(root.canonicalPath+"/") }
    }
    fun display(context: Context): String {
        val prefs=AutoBackupPreferences(context)
        val file=prefs.localDirectory?.let(::File) ?: prefs.directory?.let(::physicalPath) ?: if(prefs.directory==null) downloads() else null
        return file?.absolutePath?.trimEnd('/')?.plus("/") ?: prefs.directory.toString()
    }
    fun tree(activity: Activity,request: Int) {
        val p=AutoBackupPreferences(activity)
        val i=Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        i.putExtra(DocumentsContract.EXTRA_INITIAL_URI,p.directory ?: Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload"))
        activity.startActivityForResult(i,request)
    }
    fun manager(activity: Activity,request: Int): Boolean {
        val get=Intent(Intent.ACTION_GET_CONTENT).setType("resource/folder").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        val oi=Intent("org.openintents.action.PICK_DIRECTORY").putExtra("org.openintents.extra.TITLE",activity.getString(R.string.ez_backup_directory))
        @Suppress("DEPRECATION") val supported=listOf(get,oi).filter { activity.packageManager.queryIntentActivities(it,PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty() }
        if(supported.isEmpty()) return false
        activity.startActivityForResult(Intent.createChooser(supported.first(),activity.getString(R.string.ez_file_manager)).apply {
            if(supported.size>1) putExtra(Intent.EXTRA_INITIAL_INTENTS,supported.drop(1).toTypedArray())
        },request);return true
    }
    fun browse(activity: Activity,onPick: (File)->Unit) {
        if(!full(activity)) { request(activity);return }
        val initial=AutoBackupPreferences(activity).localDirectory?.let(::File) ?: downloads()
        val body=NativeUi.column(activity)
        val path=EditText(activity).apply { setSingleLine(true);setText(initial.absolutePath) };body.addView(path)
        val list=ListView(activity);body.addView(list,LinearLayout.LayoutParams(-1,NativeUi.dp(activity,260)))
        var current=initial;var entries=emptyList<File>();var serial=0
        val dialog=FocusAwareDialog(activity).apply {
            setTitle(R.string.ez_backup_directory);setView(body)
            setButton(AlertDialog.BUTTON_NEGATIVE,activity.getString(android.R.string.cancel)) { _,_ -> }
            setButton(AlertDialog.BUTTON_NEUTRAL,activity.getString(R.string.ez_parent_directory)) { _,_ -> }
            setButton(AlertDialog.BUTTON_POSITIVE,activity.getString(R.string.ez_use_directory)) { _,_ -> }
        }
        fun load(file: File) {
            val token=++serial
            CountdownIo.submit({ val folder=file.canonicalFile;require(folder.isDirectory);folder to (folder.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name.lowercase(java.util.Locale.ROOT) } ?: error("Unreadable directory")) }) { result ->
                if(!dialog.isShowing || activity.isDestroyed || token!=serial) return@submit
                result.onSuccess { (dir,children) -> current=dir;entries=children;path.setText(dir.absolutePath);path.error=null;list.adapter=ArrayAdapter(activity,android.R.layout.simple_list_item_1,children.map { it.name+"/" }) }
                    .onFailure { path.error=activity.getString(R.string.ez_backup_error) }
            }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { load(current.parentFile ?: current) }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val file=File(path.text.toString());val target=dialog.getButton(AlertDialog.BUTTON_POSITIVE);target.isEnabled=false
                CountdownIo.submit({ file.canonicalFile.also { require(it.isDirectory && it.canWrite()) } }) { result ->
                    if(dialog.isShowing) { target.isEnabled=true;result.onSuccess { onPick(it);dialog.dismiss() }.onFailure { path.error=activity.getString(R.string.ez_backup_error) } }
                }
            }
            path.setOnEditorActionListener { _,_,_ -> load(File(path.text.toString()));true }
            list.setOnItemClickListener { _,_,position,_ -> entries.getOrNull(position)?.let(::load) }
            load(initial)
        };dialog.show()
    }
}
