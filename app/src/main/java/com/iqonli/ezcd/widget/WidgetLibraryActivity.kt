// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.app.AlertDialog
import android.content.*
import android.os.*
import android.view.*
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.data.CountdownIo
import com.iqonli.ezcd.ui.*

class WidgetLibraryActivity: BaseActivity() {
    private var entries: List<WidgetLibraryEntry> = emptyList()
    private lateinit var grid: GridView
    private var serial=0
    private data class PreviewKey(val document: WidgetLayoutDocument,val event: com.iqonli.ezcd.model.CountdownEvent?,val size: String,val environment: String,val fonts: Long)
    private val readyPreviews=android.util.LruCache<PreviewKey,RemoteViews>(12)
    private var environment=""
    private var listAdapter: BaseAdapter?=null
    private val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var empty: TextView
    private inner class Tile: LinearLayout(this@WidgetLibraryActivity) {
        var boundEntry: WidgetLibraryEntry?=null
        var boundPreview: PreviewKey?=null
        var renderPending=false
        val preview=NativeWidgetPreview(context).apply { showGuides=false }
        val label=TextView(context).apply { textSize=12f;maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END;gravity=Gravity.CENTER }
        override fun onMeasure(widthSpec: Int,heightSpec: Int) {
            val side=(MeasureSpec.getSize(widthSpec)-paddingLeft-paddingRight).coerceAtLeast(1)
            preview.layoutParams.height=side
            super.onMeasure(widthSpec,heightSpec)
        }
        init { orientation=VERTICAL;isFocusable=false;addView(preview,LayoutParams(-1,NativeUi.dp(context,104)));addView(label,LayoutParams(-1,NativeUi.dp(context,26))) }
    }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state);title=getString(R.string.ez_widget_library)
        val frame=FrameLayout(this)
        grid=GridView(this).apply {
            numColumns=GridView.AUTO_FIT;columnWidth=NativeUi.dp(context,104);stretchMode=GridView.STRETCH_COLUMN_WIDTH
            horizontalSpacing=NativeUi.dp(context,8);verticalSpacing=NativeUi.dp(context,8);setPadding(NativeUi.dp(context,8),NativeUi.dp(context,8),NativeUi.dp(context,8),NativeUi.dp(context,8))
            clipToPadding=false
            setOnItemClickListener { _,_,position,_ -> open(entries[position].id) }
            setOnItemLongClickListener { _,_,position,_ -> actions(entries[position]);true }
        }
        frame.addView(grid,FrameLayout.LayoutParams(-1,-1))
        empty=TextView(this).apply { text=getString(R.string.ez_library_empty);gravity=Gravity.CENTER }
        frame.addView(empty,FrameLayout.LayoutParams(-1,-1));grid.emptyView=empty
        setContentView(frame)
    }
    override fun onResume() { super.onResume();load() }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if(intent.getBooleanExtra(EXTRA_PICK,false)) return true
        menu.add(0,1,0,R.string.ez_add).setIcon(R.drawable.ic_action_add).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId!=1) return super.onOptionsItemSelected(item)
        val context=applicationContext
        CountdownIo.submit({ WidgetLibraryStore(context).create(WidgetLayoutStore(context).defaults()).id }) { result ->
            if(!isDestroyed) result.fold(::open) { NativeUi.message(this,getString(R.string.ez_save_error)) }
        };return true
    }
    private fun open(id: String) {
        if(intent.getBooleanExtra(EXTRA_PICK,false)) { setResult(RESULT_OK,Intent().putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,id));finish();return }
        startActivity(Intent(this,WidgetLibraryEditorActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,id)) }
    private fun load() {
        val token=++serial;val context=applicationContext
        CountdownIo.submit({ val store=WidgetLibraryStore(context);store.sync();store.entries() }) { result ->
            if(isDestroyed || token!=serial) return@submit
            result.onFailure { NativeUi.message(this,getString(R.string.ez_data_error)) }.onSuccess { loaded ->
                entries=loaded
                val pool=TextPool(this)
                environment=CountdownTime.snapshot().today.toString()+"|"+LanguageManager.currentLanguageTag(this)+"|"+TextPool.keys.joinToString { pool.get(it) }
                if(listAdapter==null) listAdapter=object: BaseAdapter() {
                    override fun getCount()=entries.size
                    override fun getItem(position: Int)=entries[position]
                    override fun getItemId(position: Int)=java.util.UUID.fromString(entries[position].id).mostSignificantBits
                    override fun hasStableIds()=true
                    override fun getView(position: Int,recycled: View?,parent: ViewGroup): View {
                        val entry=getItem(position);val tile=recycled as? Tile ?: Tile()
                        val key=PreviewKey(entry.document,entry.event,entry.previewSize,environment,if(entry.document.renderer=="bitmap") com.iqonli.ezcd.fonts.FontRuntime.revision() else 0)
                        val same=tile.boundPreview==key
                        tile.boundEntry=entry;tile.boundPreview=key
                        tile.preview.archived=entry.widgetId==null
                        tile.label.text=entry.displayName.ifEmpty { entry.event?.title ?: getString(R.string.ez_not_selected) }
                        tile.contentDescription=tile.label.text.toString()+", "+entry.createdAt.toString()+if(entry.widgetId==null) ", "+getString(R.string.ez_library_archived) else ""
                        if(same && (tile.preview.childCount>0 || tile.renderPending)) return tile
                        val renderToken=Any();tile.tag=renderToken
                        val cached=readyPreviews.get(key)
                        val size=WidgetLayoutSize.entries.first { it.key==entry.previewSize }
                        val layout=entry.document.layouts.getValue(size.key)
                        if(cached!=null) { tile.renderPending=false;tile.preview.show(cached,entry.event,layout,size.widthDp,size.heightDp);return tile }
                        tile.preview.clear()
                        val context=applicationContext.createConfigurationContext(android.content.res.Configuration(resources.configuration))
                        if (isDestroyed || executor.isShutdown) return tile
                        tile.renderPending=true
                        executor.execute {
                            runCatching { if(entry.document.renderer=="bitmap") BitmapWidgetRenderer.remoteViews(context,entry.event,CountdownTime.snapshot().today,layout,size.widthDp,size.heightDp) else NativeWidgetRenderer.remoteViews(context,entry.event,CountdownTime.snapshot().today,layout,size.widthDp,size.heightDp) }.onSuccess { views ->
                                handler.post { if(!isDestroyed) { readyPreviews.put(key,views);if(tile.tag===renderToken) { tile.renderPending=false;tile.preview.show(views,entry.event,layout,size.widthDp,size.heightDp) } } }
                            }.onFailure { handler.post { if(tile.tag===renderToken) tile.renderPending=false } }
                        }
                        return tile
                    }
                }
                if(grid.adapter!==listAdapter) grid.adapter=listAdapter else listAdapter?.notifyDataSetChanged()
            }
        }
    }
    private fun actions(entry: WidgetLibraryEntry) {
        val options=arrayOf(getString(R.string.ez_library_cycle_size),getString(R.string.ez_library_rename),getString(R.string.ez_add_widget),getString(R.string.ez_library_copy_json),getString(R.string.ez_library_duplicate),getString(R.string.ez_delete))
        com.iqonli.ezcd.ui.FontDialogBuilder(this).setItems(options) { _,index ->
            when(index) {
                0 -> mutate { WidgetLibraryStore(applicationContext).cyclePreview(entry.id) }
                1 -> rename(entry)
                2 -> startActivity(Intent(this,WidgetPinSetupActivity::class.java).putExtra(WidgetConfigActivity.EXTRA_LIBRARY_ID,entry.id))
                3 -> runCatching { getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("ezCD widget layout",WidgetLayoutCodec.encode(entry.document))) }.onFailure { NativeUi.message(this,getString(R.string.ez_save_error)) }
                4 -> mutate { WidgetLibraryStore(applicationContext).duplicate(entry.id) }
                5 -> com.iqonli.ezcd.ui.FontDialogBuilder(this).setMessage(R.string.ez_library_delete_question).setNegativeButton(android.R.string.cancel,null)
                    .setPositiveButton(R.string.ez_delete) { _,_ -> mutate { WidgetLibraryStore(applicationContext).delete(entry.id) } }.show()
            }
        }.show()
    }
    private fun rename(entry: WidgetLibraryEntry) {
        val column=NativeUi.column(this)
        val field=EditText(this).apply { setSingleLine(true);setText(entry.displayName.ifEmpty { entry.event?.title.orEmpty() });selectAll() }
        column.addView(field)
        FocusAwareDialog(this).apply {
            setTitle(R.string.ez_library_rename);setView(column)
            setButton(AlertDialog.BUTTON_NEGATIVE,getString(android.R.string.cancel)) { _,_ -> }
            setButton(AlertDialog.BUTTON_POSITIVE,getString(android.R.string.ok)) { _,_ -> mutate { WidgetLibraryStore(applicationContext).rename(entry.id,field.text.toString()) } }
            show()
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
            field.post { field.requestFocus();field.selectAll();getSystemService(android.view.inputmethod.InputMethodManager::class.java).showSoftInput(field,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT) }
        }
    }
    companion object { const val EXTRA_PICK="pickLibraryLayout" }
    private fun mutate(task: ()->Unit) { CountdownIo.submit(task) { if(!isDestroyed) { if(it.isSuccess) load() else NativeUi.message(this,getString(R.string.ez_save_error)) } } }
    override fun onDestroy() { serial++;executor.shutdownNow();handler.removeCallbacksAndMessages(null);super.onDestroy() }
}
