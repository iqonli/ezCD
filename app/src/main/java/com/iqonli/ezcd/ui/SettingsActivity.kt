// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.*

class SettingsActivity : BaseActivity() {
    private class Session {
        val operation = RetainedOperation<String>()
        var pendingPayload: String? = null
        var pendingCount = 0
        var showAuto=false
        var permissionToggle: String?=null
    }
    private lateinit var session: Session
    private var dialog: AlertDialog? = null
    private var autoDialog: AlertDialog?=null
    private var autoFullAccess=false
    private val controls = mutableListOf<Button>()
    private lateinit var languageButton: Button
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        session = lastNonConfigurationInstance as? Session ?: Session()
        session.operation.onChanged = { consume() }
        title = getString(R.string.ez_settings)
        val body = NativeUi.page(this)
        NativeUi.label(body, "ezCD", 36f)
        @Suppress("DEPRECATION")
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
        NativeUi.label(body, getString(R.string.ez_version, version), 14f)
        languageButton = NativeUi.preference(body, getString(R.string.ez_language)) { languages() }
        controls += languageButton
        controls += NativeUi.preference(body,getString(R.string.ez_text_pool)) { editTextPool() }
        controls += NativeUi.preference(body, getString(R.string.ez_import)) {
            FilePickers.open(this,2)
        }
        controls += NativeUi.preference(body, getString(R.string.ez_export)) {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "ezcd-backup-${java.time.LocalDate.now()}.json")
            }, 1)
        }
        controls += NativeUi.preference(body,getString(R.string.ez_auto_backup)) { showAutoBackup() }
        controls += NativeUi.preference(body,getString(R.string.ez_widget_library)) { startActivity(Intent(this,com.iqonli.ezcd.widget.WidgetLibraryActivity::class.java)) }
        controls += NativeUi.preference(body,getString(R.string.ez_fonts)) { startActivity(com.iqonli.ezcd.fonts.FontManagerActivity.intent(this)) }
        NativeUi.preference(body, getString(R.string.ez_about)) { startActivity(Intent(this, AboutActivity::class.java)) }
        consume(); confirmImport()
    }
    override fun onResume() { super.onResume(); consume(); refreshEnabled();if(session.showAuto) {
            if(autoDialog?.isShowing==true && autoFullAccess!=DirectoryAccess.full(this)) { autoDialog?.setOnDismissListener(null);autoDialog?.dismiss();autoDialog=null }
            showAutoBackup()
        } }
    override fun onDestroy() { autoDialog?.setOnDismissListener(null);autoDialog?.dismiss();dialog?.dismiss(); session.operation.onChanged = null; super.onDestroy() }
    override fun onRetainNonConfigurationInstance(): Any = session
    private fun refreshEnabled() { controls.forEach { it.isEnabled = !session.operation.running } }
    private fun showAutoBackup() {
        if(autoDialog?.isShowing==true) return
        session.showAuto=true
        val prefs=AutoBackupPreferences(this)
        val column=NativeUi.column(this)
        NativeUi.label(column,getString(R.string.ez_backup_directory))
        NativeUi.button(column,DirectoryAccess.display(this)) { chooseDirectory() }
        fun toggle(label: Int,key: String,value: Boolean) {
            column.addView(android.widget.CheckBox(this).apply {
                text=getString(label);isChecked=value
                setOnCheckedChangeListener { button,checked ->
                    if(checked && prefs.legacyPermissionNeeded()) {
                        button.isChecked=false;session.permissionToggle=key
                        requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),4)
                    } else if(key=="open") prefs.onOpen=checked else prefs.onExit=checked
                }
            })
        }
        toggle(R.string.ez_backup_on_open,"open",prefs.onOpen);toggle(R.string.ez_backup_on_exit,"exit",prefs.onExit)
        if(prefs.lastError!=null) NativeUi.label(column,getString(R.string.ez_auto_backup_failed),13f)
        autoFullAccess=DirectoryAccess.full(this)
        val builder=com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(R.string.ez_auto_backup).setView(android.widget.ScrollView(this).apply { addView(column) })
            .setPositiveButton(android.R.string.ok,null)
        if(!autoFullAccess) builder.setNeutralButton(R.string.ez_full_access) { _,_ ->
            session.showAuto=true;runCatching { DirectoryAccess.request(this) }.onFailure { NativeUi.message(this,getString(R.string.ez_backup_error)) }
        }
        autoDialog=builder.create().apply {
            setOnDismissListener { session.showAuto=false }
            show()
            getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
                session.showAuto=true;runCatching { DirectoryAccess.request(this@SettingsActivity) }.onFailure { NativeUi.message(this@SettingsActivity,getString(R.string.ez_backup_error)) }
            }
        }
    }
    private fun chooseDirectory() {
        com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(R.string.ez_backup_directory)
            .setItems(arrayOf(getString(R.string.ez_system_directory),getString(R.string.ez_file_manager),getString(R.string.ez_local_directory))) { _,which ->
                when(which) {
                    0 -> runCatching { DirectoryAccess.tree(this,3) }.onFailure { NativeUi.message(this,getString(R.string.ez_backup_error)) }
                    1 -> if(!DirectoryAccess.manager(this,5)) DirectoryAccess.browse(this,::selectedLocal)
                    2 -> DirectoryAccess.browse(this,::selectedLocal)
                }
            }.show()
    }
    private fun selectedLocal(file: java.io.File) {
        val context=applicationContext
        CountdownIo.submit({ AutoBackupPreferences(context).selectLocal(file) }) {
            if(!isDestroyed) { if(it.isFailure) NativeUi.message(this,getString(R.string.ez_backup_error));autoDialog?.dismiss();showAutoBackup() }
        }
    }
    private fun acceptDirectory(data: Intent) {
        val uri=data.data ?: listOf("path","directory","selected_path").firstNotNullOfOrNull { data.getStringExtra(it) }?.let { android.net.Uri.fromFile(java.io.File(it)) } ?: return
        if(uri.scheme=="file") {
            val file=uri.path?.let { java.io.File(it) } ?: return
            if(!DirectoryAccess.full(this)) { DirectoryAccess.request(this);return }
            selectedLocal(file);return
        }
        if(!android.provider.DocumentsContract.isTreeUri(uri)) {
            val local=DirectoryAccess.physicalPath(uri)
            if(local!=null && DirectoryAccess.full(this)) { selectedLocal(local);return }
            NativeUi.message(this,getString(R.string.ez_backup_error));return
        }
        val context=applicationContext
        val flags=data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        CountdownIo.submit({
            val required=Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            require(uri.scheme=="content" && android.provider.DocumentsContract.isTreeUri(uri) && flags and required == required)
            context.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            val document=android.provider.DocumentsContract.buildDocumentUriUsingTree(uri,android.provider.DocumentsContract.getTreeDocumentId(uri))
            val name=context.contentResolver.query(document,arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME),Bundle.EMPTY,null)?.use { if(it.moveToFirst()) it.getString(0) else null } ?: uri.toString()
            AutoBackupPreferences(context).selectDirectory(uri,name)
        }) { result ->
            if(isDestroyed) return@submit
            if(result.isFailure) NativeUi.message(this,getString(R.string.ez_backup_error))
            autoDialog?.dismiss();showAutoBackup()
        }
    }
    @Deprecated("Native permission API")
    override fun onRequestPermissionsResult(request: Int,permissions: Array<out String>,results: IntArray) {
        super.onRequestPermissionsResult(request,permissions,results)
        if(request==4) {
            val key=session.permissionToggle;session.permissionToggle=null
            if(results.firstOrNull()==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val prefs=AutoBackupPreferences(this);if(key=="open") prefs.onOpen=true else if(key=="exit") prefs.onExit=true
            } else if(key=="existing") {
                val prefs=AutoBackupPreferences(this);prefs.onOpen=false;prefs.onExit=false
            }
            autoDialog?.dismiss();showAutoBackup()
        }
    }
    private fun editTextPool() {
        val pool=TextPool(this);val body=NativeUi.column(this)
        val labels=listOf(R.string.ez_pool_date,R.string.ez_pool_left,R.string.ez_pool_today,R.string.ez_pool_passed)
        val fields=TextPool.keys.mapIndexed { i,key ->
            NativeUi.label(body,getString(labels[i]))
            android.widget.EditText(this).apply { setSingleLine(true);setText(pool.get(key));body.addView(this) }
        }
        FocusAwareDialog(this).apply {
            setTitle(R.string.ez_text_pool);setView(android.widget.ScrollView(this@SettingsActivity).apply { addView(body) })
            setButton(AlertDialog.BUTTON_NEGATIVE,getString(android.R.string.cancel)) { _,_ -> }
            setButton(AlertDialog.BUTTON_POSITIVE,getString(android.R.string.ok)) { _,_ ->
                val values=TextPool.keys.zip(fields.map { it.text.toString() }).toMap();val context=applicationContext
                CountdownIo.submit({ TextPool(context).save(values);CountdownMutations(context).reconcile() }) {
                    if(it.isFailure && !isDestroyed) NativeUi.message(this@SettingsActivity,getString(R.string.ez_save_error))
                }
            };show()
        }
    }
    private fun languages() {
        val tags = arrayOf("", "zh-Hans", "zh-Hant", "en")
        val current = if (LanguageManager.isFollowingSystem(this)) "" else LanguageManager.currentLanguageTag(this)
        com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(R.string.ez_language)
            .setSingleChoiceItems(arrayOf(getString(R.string.ez_system_language), "简体中文 (zhs)", "繁體中文 (zht)", "English (en)"), tags.indexOf(current)) { d, index ->
                d.dismiss()
                if (index == 0) LanguageManager.useSystemLanguage(this) else LanguageManager.setLanguage(this, tags[index])
                val context = applicationContext
                CountdownIo.execute { CountdownMutations(context).reconcile() }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    @Deprecated("Platform activity result API is intentional; no AndroidX runtime dependency.")
    override fun onActivityResult(request: Int, result: Int, data: Intent?) {
        super.onActivityResult(request, result, data)
        if(request==3 || request==5) { if(result==RESULT_OK && data?.data!=null) acceptDirectory(data);return }
        if (result != RESULT_OK || session.operation.running) return
        val uri = data?.data ?: return
        val context = applicationContext
        val resolver = context.contentResolver
        val target = session
        session.operation.start {
            val repository = CountdownRepository(context)
            if (request == 1) {
                val bytes = BackupArchiveService(context).export().toByteArray(Charsets.UTF_8)
                checkNotNull(resolver.openOutputStream(uri, "wt")).use { it.write(bytes) }
                "exported"
            } else {
                val payload = checkNotNull(resolver.openInputStream(uri)).use { BackupArchiveCodec.read(it) }
                target.pendingCount = BackupArchiveService(context).preview(payload)
                target.pendingPayload = payload
                "preview"
            }
        }
        refreshEnabled()
    }
    private fun consume() {
        val completed = session.operation.takeResult()
        refreshEnabled()
        if (completed != null) {
            if (completed.isFailure) NativeUi.message(this, getString(R.string.ez_backup_error))
            else when (completed.getOrNull()) {
                "exported" -> NativeUi.message(this, getString(R.string.ez_export_done))
                "imported" -> NativeUi.message(this, getString(R.string.ez_import_done))
            }
        }
        confirmImport()
    }
    private fun confirmImport() {
        if (session.pendingPayload == null || session.operation.running || dialog?.isShowing == true) return
        dialog = com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(R.string.ez_import)
            .setMessage(getString(R.string.ez_import_confirm, session.pendingCount))
            .setNegativeButton(android.R.string.cancel) { _, _ -> session.pendingPayload = null }
            .setOnCancelListener { session.pendingPayload = null }
            .setPositiveButton(R.string.ez_replace) { _, _ ->
                val payload = session.pendingPayload ?: return@setPositiveButton
                session.pendingPayload = null
                val context = applicationContext
                session.operation.start {
                    val snapshot = CountdownImportSnapshot.create(context)
                    try { snapshot.write(payload); CountdownMutations(context).applyImport(snapshot) }
                    finally { snapshot.clear() }
                    "imported"
                }
                refreshEnabled()
            }.create()
        dialog?.show()
    }
}
