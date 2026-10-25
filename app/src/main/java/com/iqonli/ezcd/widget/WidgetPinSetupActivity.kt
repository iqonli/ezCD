// Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.os.*
import android.text.*
import android.view.Gravity
import android.view.View
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.ui.*

class WidgetPinSetupActivity : BaseActivity() {
    private var event: CountdownEvent? = null
    private var loaded=false
    private var library: WidgetLibraryEntry?=null
    private lateinit var placeholder: Button
    private var previewGeneration=0
    private var generation=0
    private var desktopGeneration=0
    private var desktop=DesktopState()
    private lateinit var feedback: WidgetPinFeedback
    private lateinit var add: Button
    private lateinit var columns: CellInput
    private lateinit var rows: CellInput
    private lateinit var note: TextView
    private val handler=Handler(Looper.getMainLooper())
    private inner class CellInput(parent: LinearLayout,label: Int,draft: String,tag: String) {
        val field=EditText(this@WidgetPinSetupActivity).apply {
            this.tag=tag;inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine(true);setSelectAllOnFocus(true)
            filters=arrayOf(InputFilter.LengthFilter(3));gravity=Gravity.CENTER;minWidth=0;setText(draft)
            contentDescription=getString(label)
        }
        val minus: Button
        val plus: Button
        init {
            val row=LinearLayout(this@WidgetPinSetupActivity).apply { gravity=Gravity.CENTER_VERTICAL }
            parent.addView(row,LinearLayout.LayoutParams(-1,-2))
            row.addView(TextView(this@WidgetPinSetupActivity).apply { text=getString(label);textSize=16f },LinearLayout.LayoutParams(0,-2,1f))
            minus=NativeUi.button(row,"-1") { change(-1) }.apply { layoutParams=LinearLayout.LayoutParams(NativeUi.dp(context,48),-2);minWidth=0;setPadding(0,0,0,0) }
            row.addView(field,LinearLayout.LayoutParams(NativeUi.dp(row.context,64),-2))
            plus=NativeUi.button(row,"+1") { change(1) }.apply { layoutParams=LinearLayout.LayoutParams(NativeUi.dp(context,48),-2);minWidth=0;setPadding(0,0,0,0) }
            row.addView(TextView(this@WidgetPinSetupActivity).apply { text=getString(R.string.ez_pin_cells);textSize=16f;setPadding(NativeUi.dp(context,8),0,0,0) })
            field.addTextChangedListener(object: TextWatcher {
                override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int)=Unit
                override fun afterTextChanged(s: Editable?)=Unit
                override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { if(::add.isInitialized) updateControls() }
            })
        }
        private fun maximum()=if(Build.VERSION.SDK_INT<28) 1 else minOf(WidgetPinSize.MAX_PRESET,if(this===columns) desktop.grid?.columns ?: WidgetPinSize.MAX_PRESET else desktop.grid?.rows ?: WidgetPinSize.MAX_PRESET)
        private fun change(delta: Int) { field.setText(((field.text.toString().toIntOrNull() ?: 1)+delta).coerceIn(1,maximum()).toString()) }
        fun value()=field.text.toString().toIntOrNull()?.takeIf { it in 1..maximum() }
        fun refresh() {
            val n=field.text.toString().toIntOrNull();val enabled=!feedback.pending && Build.VERSION.SDK_INT>=28
            field.isEnabled=enabled
            minus.isEnabled=enabled && (n==null || n>1);plus.isEnabled=enabled && (n==null || n<maximum())
            field.error=if(value()==null) getString(R.string.ez_pin_range,1,maximum()) else null
        }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val id=intent.getStringExtra(EXTRA_EVENT_ID)
        val libraryId=intent.getStringExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID)
        if(id.isNullOrBlank() && libraryId.isNullOrBlank()) { finish();return }
        title=getString(R.string.ez_add_widget)
        val body=NativeUi.page(this)
        val summary=NativeUi.label(body,"",20f).apply { tag="pin_summary" }
        val preview=if(libraryId!=null) NativeWidgetPreview(this).apply {
            showGuides=false;tag="pin_layout_preview"
            NativeUi.label(body,getString(R.string.ez_pin_layout_preview))
            body.addView(this,LinearLayout.LayoutParams(-1,NativeUi.dp(context,180)))
        } else null
        val selector=if(libraryId!=null) NativeUi.spinner(body,emptyList()) else null
        fun showPreview() {
            val entry=library ?: return
            val selected=event;val token=++previewGeneration
            val size=WidgetLayoutSize.entries.first { it.key==entry.previewSize }
            val layout=entry.document.layouts.getValue(size.key);val context=LanguageManager.localizedContext(applicationContext)
            CountdownIo.submit({
                if(entry.document.renderer=="bitmap") BitmapWidgetRenderer.remoteViews(context,selected,com.iqonli.ezcd.countdown.CountdownTime.snapshot().today,layout,size.widthDp,size.heightDp)
                else NativeWidgetRenderer.remoteViews(context,selected,com.iqonli.ezcd.countdown.CountdownTime.snapshot().today,layout,size.widthDp,size.heightDp)
            }) { result -> if(!isDestroyed && token==previewGeneration) result.onSuccess { preview?.show(it,selected,layout,size.widthDp,size.heightDp) } }
        }
        NativeUi.label(body,getString(R.string.ez_pin_custom_size))
        columns=CellInput(body,R.string.ez_pin_width,state?.getString("columns") ?: "1","pin_columns")
        rows=CellInput(body,R.string.ez_pin_height,state?.getString("rows") ?: "1","pin_rows")
        add=NativeUi.button(body,getString(R.string.ez_add_widget)) {
            request(false)
        }.apply { tag="pin_add";isEnabled=false }
        note=NativeUi.label(body,"",14f).apply { tag="pin_note" }
        NativeUi.label(body,getString(R.string.ez_pin_compat_help),14f)
        NativeUi.label(body,getString(R.string.ez_pin_placeholder_help),14f)
        placeholder=NativeUi.button(body,getString(R.string.ez_pin_placeholder)) { request(true) }.apply { tag="pin_placeholder";isEnabled=false }
        feedback=WidgetPinFeedback(this) { updateControls() }.apply { restore(state) }
        updateControls()
        val token=++generation
        CountdownIo.submit({
            val events=(CountdownRepository(applicationContext).loadResult() as? CountdownLoadResult.Success)?.events ?: error("Cannot load events")
            val entry=libraryId?.let { WidgetLibraryStore(applicationContext).get(it) ?: error("Library entry deleted") }
            events to entry
        }) { result ->
            if(isDestroyed || token!=generation) return@submit
            val data=result.getOrNull()
            if(data==null) { NativeUi.message(this,getString(R.string.ez_data_error));finish();return@submit }
            val events=data.first;library=data.second
            val selected=state?.getString("selectedEvent") ?: library?.event?.id ?: id
            event=events.firstOrNull { it.id==selected }
            if(libraryId==null && event==null) { NativeUi.message(this,getString(R.string.ez_data_error));finish();return@submit }
            loaded=true
            fun refreshSummary() { summary.text=event?.let { EventSortPreferences(this).eventLabel(it) } ?: getString(R.string.ez_not_selected);showPreview();updateControls() }
            if(selector!=null) {
                selector.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,listOf(getString(R.string.ez_not_selected))+events.map { EventSortPreferences(this).eventLabel(it) }).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                selector.setSelection(events.indexOfFirst { it.id==event?.id }+1)
                selector.onItemSelectedListener=object: AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?)=Unit
                    override fun onItemSelected(parent: AdapterView<*>?,view: View?,position: Int,rowId: Long) { event=events.getOrNull(position-1);refreshSummary() }
                }
            }
            refreshSummary()
        }
    }
    private fun request(isPlaceholder: Boolean) {
        if(!loaded || feedback.pending) return
        val width=columns.value() ?: return;val height=rows.value() ?: return
        if(desktop.permission==DesktopPermission.DENIED) {
            FontDialogBuilder(this).setMessage(R.string.ez_pin_permission_denied).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(R.string.ez_pin_open_permissions) { _,_ -> runCatching { DesktopAccess.settings(this) }.onFailure { NativeUi.message(this,getString(R.string.ez_widget_pin_denied)) } }.show()
        } else feedback.request(event,WidgetPinSize(width,height),if(isPlaceholder) null else library?.document,isPlaceholder)
    }

    private fun updateControls() {
        if(!::feedback.isInitialized) return
        columns.refresh();rows.refresh()
        add.isEnabled=loaded && !feedback.pending && columns.value()!=null && rows.value()!=null
        placeholder.isEnabled=add.isEnabled
        note.text=when {
            desktop.permission==DesktopPermission.DENIED -> getString(R.string.ez_pin_permission_denied)
            Build.VERSION.SDK_INT<28 -> getString(R.string.ez_pin_old_system)
            desktop.grid!=null -> getString(R.string.ez_pin_grid,desktop.grid!!.columns,desktop.grid!!.rows)
            else -> getString(R.string.ez_pin_grid_unknown)
        }
    }
    private fun detectDesktop() {
        val token=++desktopGeneration
        // An inaccessible launcher probe never holds the add button disabled indefinitely.
        handler.postDelayed({ if(token==desktopGeneration && !isDestroyed) updateControls() },1500)
        try { DesktopAccess.executor.execute {
            val result=runCatching { DesktopAccess.detect(applicationContext) }.getOrDefault(DesktopState())
            handler.post { if(token==desktopGeneration && !isDestroyed) { desktop=result;updateControls() } }
        } } catch(_: java.util.concurrent.RejectedExecutionException) { updateControls() }
    }
    override fun onResume() { super.onResume();if(::feedback.isInitialized) { feedback.onResume();detectDesktop() } }
    override fun onPause() { if(::feedback.isInitialized) feedback.onPause();super.onPause() }
    override fun onWindowFocusChanged(hasFocus: Boolean) { super.onWindowFocusChanged(hasFocus);if(::feedback.isInitialized) feedback.onWindowFocusChanged(hasFocus) }
    override fun onSaveInstanceState(out: Bundle) { if(::feedback.isInitialized) { feedback.save(out);out.putString("selectedEvent",event?.id ?: "");out.putString("columns",columns.field.text.toString());out.putString("rows",rows.field.text.toString()) };super.onSaveInstanceState(out) }
    override fun onDestroy() { generation++;desktopGeneration++;previewGeneration++;handler.removeCallbacksAndMessages(null);if(::feedback.isInitialized) feedback.onDestroy();super.onDestroy() }
    companion object { const val EXTRA_EVENT_ID="event_id" }
}
