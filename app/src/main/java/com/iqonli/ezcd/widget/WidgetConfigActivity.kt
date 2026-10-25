// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.Gravity
import android.view.View
import android.widget.*
import com.iqonli.ezcd.fonts.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownTime
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.model.OpaqueColor
import com.iqonli.ezcd.ui.*
import java.util.UUID
import kotlin.math.roundToInt

/** Native tabs and native form controls; the preview never accepts editing gestures. */
open class WidgetConfigActivity : BaseActivity() {
    private data class Saved(val document: WidgetLayoutDocument,val eventId: String?)
    private data class Loaded(val events: List<CountdownEvent>,val selectedId: String?,val document: WidgetLayoutDocument,val originalDocument: WidgetLayoutDocument,val originalEventId: String?,val snapshotEvent: CountdownEvent?=null)
    private class Session {
        var fonts=emptyList<ImportedFont>()
        var pendingFontElement: String?=null
        var pendingFontSize: String?=null
        var document: WidgetLayoutDocument? = null
        var selectedId: String? = null
        var events: List<CountdownEvent> = emptyList()
        var size = WidgetLayoutSize.SMALL
        var elementId = "title"
        var dirty = false
        var originalSnapshot: CountdownEvent?=null
        val textHistory=WidgetLayoutSize.entries.associate { it.key to mutableListOf<String>() }
        var originalDocument: WidgetLayoutDocument? = null
        var originalEventId: String? = null
        val save = RetainedOperation<Saved>()
        var draftToken = UUID.randomUUID().toString()
        var pendingExport: String? = null
        var snapshotEvent: CountdownEvent? = null
        var previewPinned=false
        var adjustPreview=false
        var previewFrameDp=200
        val previewSizes = WidgetLayoutSize.entries.associate { it.key to (it.widthDp to it.heightDp) }.toMutableMap()
    }
    private lateinit var session: Session
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var configuring = false
    private var libraryId: String?=null
    private var selectionRestored=false
    private var stateEvent: CountdownEvent?=null
    private lateinit var pinnedContainer: LinearLayout
    private lateinit var previewBlock: LinearLayout
    private lateinit var previewFrame: FrameLayout
    private lateinit var dimensionsControls: LinearLayout
    private lateinit var dragHandle: View
    private lateinit var scroll: ScrollView
    private var loading = true
    private lateinit var body: LinearLayout
    private lateinit var preview: NativeWidgetPreview
    private lateinit var eventSelector: Spinner
    private lateinit var tabHost: TabHost
    private lateinit var properties: LinearLayout
    private lateinit var selector: Spinner
    private lateinit var backgroundControls: LinearLayout
    private val handler = Handler(Looper.getMainLooper())
    private var selectorPalette=""
    private var previewScheduled=false
    private val previewFrameCallback=android.view.Choreographer.FrameCallback { previewScheduled=false;renderPreview() }
    private var fontOverlay: FontSizeOverlay?=null
    private val saveLater = Runnable { stashDraft() }
    private lateinit var temporal: TemporalInvalidationController
    private var generation = 0
    private var fontRevision=-1L
    private var fontLoadPending=false
    private var exitDialog: AlertDialog? = null
    override fun onCreate(state: Bundle?) {
        super.onCreate(state); setResult(RESULT_CANCELED)
        title = getString(R.string.ez_layout_editor)
        actionBar?.setDisplayHomeAsUpEnabled(false)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)
        configuring = widgetId != AppWidgetManager.INVALID_APPWIDGET_ID
        libraryId=if (this is WidgetLibraryEditorActivity) intent.getStringExtra(EXTRA_LIBRARY_ID) else null
        if (!configuring && libraryId==null) { finish(); return }
        selectionRestored=state?.containsKey("event")==true
        stateEvent=state?.getString("snapshotEvent")?.let { runCatching { CountdownStorageCodec.decode(it).single() }.getOrNull() }
        if (configuring && !WidgetInstanceValidator.isOwnedBy(AppWidgetManager.getInstance(applicationContext),widgetId,ComponentName(this,CountdownWidgetProvider::class.java))) { finish(); return }
        session = lastNonConfigurationInstance as? Session ?: Session().apply {
            draftToken = state?.getString("draftToken")?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: draftToken
            selectedId = if (state?.containsKey("event") == true) state.getString("event") else WidgetPreferences(this@WidgetConfigActivity).get(widgetId)?.eventId
            size = WidgetLayoutSize.entries.getOrElse(state?.getInt("size") ?: 0) { WidgetLayoutSize.SMALL }
            elementId = state?.getString("element") ?: "title"
            previewPinned=state?.getBoolean("previewPinned") ?: false
            adjustPreview=state?.getBoolean("adjustPreview") ?: false
            previewFrameDp=(state?.getInt("previewFrameDp",200) ?: 200).coerceIn(80,600)
            WidgetLayoutSize.entries.forEach { entry ->
                state?.getStringArrayList("history_${entry.key}")?.let { textHistory.getValue(entry.key).addAll(it.takeLast(32)) }
                val dimensions=state?.getIntArray("preview_${entry.key}")
                if (dimensions?.size == 2 && dimensions.all { it in 32..480 }) previewSizes[entry.key]=dimensions[0] to dimensions[1]
            }
        }
        session.pendingFontElement=state?.getString("pendingFontElement") ?: session.pendingFontElement
        session.pendingFontSize=state?.getString("pendingFontSize") ?: session.pendingFontSize
        session.save.onChanged = { consumeSave() }
        temporal = TemporalInvalidationController(this) { refreshEvents() }
        if (session.document != null) { loading=false; render() } else load()
    }
    override fun onRetainNonConfigurationInstance(): Any = session
    override fun onSaveInstanceState(out: Bundle) {
        out.putString("pendingFontElement",session.pendingFontElement);out.putString("pendingFontSize",session.pendingFontSize)
        out.putString("draftToken",session.draftToken); out.putString("event",session.selectedId); out.putInt("size",session.size.ordinal); out.putString("element",session.elementId)
        session.textHistory.forEach { (key,value) -> out.putStringArrayList("history_$key",ArrayList(value)) }
        session.previewSizes.forEach { (key,value) -> out.putIntArray("preview_$key",intArrayOf(value.first,value.second)) }
        out.putBoolean("previewPinned",session.previewPinned);out.putBoolean("adjustPreview",session.adjustPreview);out.putInt("previewFrameDp",session.previewFrameDp)
        session.events.firstOrNull { it.id == session.selectedId }?.let { out.putString("snapshotEvent",CountdownStorageCodec.encode(listOf(it))) }
        if (!loading) stashDraft()
        super.onSaveInstanceState(out)
    }
    override fun onResume() { super.onResume(); if(::session.isInitialized) loadFonts(); if (::temporal.isInitialized) temporal.start(); if (!loading) { consumeSave(); if (!isFinishing) refreshEvents() } }
    override fun onPause() {
        fontOverlay?.hide()
        handler.removeCallbacks(saveLater)
        if (!loading) stashDraft()
        if (::temporal.isInitialized) temporal.stop()
        super.onPause()
    }
    override fun onDestroy() { fontOverlay?.hide();android.view.Choreographer.getInstance().removeFrameCallback(previewFrameCallback);exitDialog?.dismiss(); if (::session.isInitialized) session.save.onChanged=null; generation++; handler.removeCallbacksAndMessages(null); if (isFinishing && ::session.isInitialized) { val file=draftFile(); CountdownIo.execute { android.util.AtomicFile(file).delete() } }; super.onDestroy() }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0,R.id.widget_save_action,0,R.string.ez_save).setIcon(R.drawable.ic_action_save).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.widget_save_action)?.isEnabled=!loading && (libraryId != null || session.selectedId != null) && !session.save.running
        return super.onPrepareOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.widget_save_action) { persist(); return true }
        return super.onOptionsItemSelected(item)
    }
    override fun navigateBack() {
        if (loading) { finish(); return }
        if (session.save.running) return
        updateDirty()
        if (!session.dirty && !hasInvalidFields(body)) { finish();return }
        if (exitDialog?.isShowing == true) return
        exitDialog=com.iqonli.ezcd.ui.FontDialogBuilder(this).setMessage(R.string.ez_save_changes)
            .setNegativeButton(R.string.ez_dont_save) { _,_ -> finish() }
            .setPositiveButton(R.string.ez_save) { _,_ -> persist() }
            .create().apply {
                setOnDismissListener { exitDialog=null }
                show()
                getButton(AlertDialog.BUTTON_POSITIVE).isEnabled=libraryId != null || session.selectedId != null
            }
    }
    private fun refreshEvents() {
        val token=++generation; val context=applicationContext
        CountdownIo.submit({ CountdownRepository(context).loadResult() }) { result ->
            if (isDestroyed || token != generation) return@submit
            val loaded=result.getOrNull() as? CountdownLoadResult.Success ?: return@submit
            session.events=withSnapshot(EventSortPreferences(this).sorted(loaded.events),session.snapshotEvent)
            if (session.events.none { it.id == session.selectedId }) session.selectedId=null
            if (::body.isInitialized) { buildEventSelector(); buildBody() }
        }
    }
    private fun load() {
        val token=++generation; val context=applicationContext; val selected=session.selectedId
        val draft=draftFile()
        CountdownIo.submit({
            val loaded=CountdownRepository(context).loadResult() as? CountdownLoadResult.Success ?: error("Cannot read events")
            val entry=libraryId?.let { WidgetLibraryStore(context).get(it) ?: error("Library entry deleted") }
            val snapshot=if (libraryId!=null) stateEvent ?: entry?.event else null
            val ordered=withSnapshot(EventSortPreferences(this).sorted(loaded.events),snapshot)
            val chosen=if (libraryId!=null && !selectionRestored) entry?.event?.id else selected
            val id=ordered.firstOrNull { it.id == chosen }?.id
            val boundId=entry?.event?.id ?: WidgetPreferences(context).get(widgetId)?.eventId
            val originalId=ordered.firstOrNull { it.id == boundId }?.id
            val original=entry?.document ?: WidgetLayoutStore(context).load(widgetId,originalId)
            Loaded(ordered,id,runCatching { android.util.AtomicFile(draft).openRead().bufferedReader().use { WidgetLayoutCodec.decode(it.readText()) } }.getOrNull() ?: original,original,originalId,snapshot)
        }) { result ->
            if (isDestroyed || token != generation) return@submit
            val data=result.getOrNull()
            if (data == null) { android.util.Log.e("ezCD","Cannot load widget configuration",result.exceptionOrNull()); NativeUi.message(this,getString(R.string.ez_data_error)); finish(); return@submit }
            session.snapshotEvent=data.snapshotEvent;session.originalSnapshot=data.snapshotEvent; session.events=data.events; session.selectedId=data.selectedId; session.document=data.document; session.originalDocument=data.originalDocument; session.originalEventId=data.originalEventId; updateDirty(); loading=false; render(); invalidateOptionsMenu(); consumeSave()
        }
    }
    private fun withSnapshot(events: List<CountdownEvent>,snapshot: CountdownEvent?): List<CountdownEvent> {
        if (libraryId==null || snapshot==null) return events
        // Library records keep their captured appearance even when the original event changes later.
        val captured=if (events.any { it.id==snapshot.id }) events.map { if (it.id==snapshot.id) snapshot else it } else events+snapshot
        return EventSortPreferences(this).sorted(captured)
    }
    private fun loadFonts() {
        val revision=FontRuntime.revision()
        if(fontLoadPending || fontRevision==revision) return
        fontLoadPending=true
        FontIo.submit({ FontRuntime.fonts(applicationContext) }) { result ->
            if(isDestroyed || !::session.isInitialized) return@submit
            fontLoadPending=false
            result.onSuccess { fonts ->
                val changed=session.fonts!=fonts
                session.fonts=fonts;fontRevision=revision
                if(!loading && ::properties.isInitialized && session.document?.renderer=="bitmap") {
                    if(changed) { buildProperties();setControlsEnabled() }
                    schedulePreview()
                }
            }
        }
    }
    private fun current(): WidgetSizeLayout = checkNotNull(session.document).layouts.getValue(session.size.key)
    private fun element(): WidgetTextElement = current().elements.firstOrNull { it.id == session.elementId } ?: current().elements.first()
    private fun replaceLayout(layout: WidgetSizeLayout) {
        session.document=checkNotNull(session.document).copy(layouts=checkNotNull(session.document).layouts + (session.size.key to layout)); changed()
    }
    private fun updateElement(edit: (WidgetTextElement)->WidgetTextElement) {
        val id=element().id; replaceLayout(current().copy(elements=current().elements.map { if (it.id == id) edit(it) else it }))
    }
    private fun updateDirty() {
        session.dirty=session.document != session.originalDocument || session.selectedId != session.originalEventId ||
            (libraryId!=null && session.events.firstOrNull { it.id==session.selectedId } != session.originalSnapshot)
    }
    private fun selectText(id: String) {
        if(id==session.elementId) return
        val history=session.textHistory.getValue(session.size.key)
        history.remove(session.elementId);history.add(session.elementId)
        if(history.size>32) history.removeAt(0)
        session.elementId=id
    }
    private fun previousText(): String {
        val history=session.textHistory.getValue(session.size.key)
        while(history.isNotEmpty()) { val id=history.removeAt(history.lastIndex);if(current().elements.any { it.id==id }) return id }
        return current().elements.first().id
    }
    private fun schedulePreview() {
        if(!previewScheduled) { previewScheduled=true;android.view.Choreographer.getInstance().postFrameCallback(previewFrameCallback) }
    }
    private fun changed() {
        updateDirty()
        val palette=current().background+"|"+current().elements.joinToString { it.color }+"|"+session.selectedId
        if(selectorPalette!=palette) {
            selectorPalette=palette
            (if (::selector.isInitialized) selector.adapter else null)?.let { (it as? ArrayAdapter<*>)?.notifyDataSetChanged() }
        }
        schedulePreview()
        handler.removeCallbacks(saveLater); handler.postDelayed(saveLater,500)
    }
    private fun render() {
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; isFocusableInTouchMode=true }
        // The one-argument constructor leaves the tab indicator layout at resource ID 0.
        // Enter the styled constructor even though no XML attributes are supplied.
        val eventColumn=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(NativeUi.dp(this@WidgetConfigActivity,12),0,NativeUi.dp(this@WidgetConfigActivity,12),0) }
        eventColumn.addView(TextView(this).apply { text=getString(R.string.ez_widget_select);textSize=14f },LinearLayout.LayoutParams(-2,-2))
        eventSelector=NativeUi.spinner(eventColumn,emptyList()).apply { layoutParams=LinearLayout.LayoutParams(0,NativeUi.dp(this@WidgetConfigActivity,48),1f) }
        root.addView(eventColumn)
        val host=TabHost(this,null); tabHost=host
        val tabColumn=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        val tabs=TabWidget(this).apply { id=android.R.id.tabs }
        tabColumn.addView(tabs,LinearLayout.LayoutParams(-1,-2))
        tabColumn.addView(FrameLayout(this).apply { id=android.R.id.tabcontent },LinearLayout.LayoutParams(-1,0))
        host.addView(tabColumn); host.setup()
        WidgetLayoutSize.entries.forEach { size -> host.addTab(host.newTabSpec(size.key).setIndicator(size.name.lowercase().replaceFirstChar { it.uppercase() }).setContent { FrameLayout(this) }) }
        for (i in 0 until tabs.childCount) tabs.getChildAt(i).findViewById<TextView>(android.R.id.title)?.isAllCaps=false
        root.addView(host,LinearLayout.LayoutParams(-1,-2))
        pinnedContainer=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;setPadding(NativeUi.dp(this@WidgetConfigActivity,12),0,NativeUi.dp(this@WidgetConfigActivity,12),0) }
        root.addView(pinnedContainer,LinearLayout.LayoutParams(-1,-2))
        body=NativeUi.column(this).apply { setPadding(NativeUi.dp(this@WidgetConfigActivity,12),0,NativeUi.dp(this@WidgetConfigActivity,12),NativeUi.dp(this@WidgetConfigActivity,16)) }
        scroll=ScrollView(this).apply { isFillViewport=true;addView(body) }
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        host.currentTab=session.size.ordinal
        host.setOnTabChangedListener { key ->
            session.size=WidgetLayoutSize.entries.first { it.key == key }; buildBody()
        }
        buildEventSelector(); buildBody(); root.requestFocus()
    }
    private fun buildBody() {
        body.removeAllViews()
        pinnedContainer.removeAllViews()
        previewBlock=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        previewFrame=FrameLayout(this)
        preview=NativeWidgetPreview(this).apply { contentDescription=getString(R.string.ez_layout_preview) }
        previewFrame.addView(preview,FrameLayout.LayoutParams(-1,-1))
        val tools=LinearLayout(this)
        fun tool(icon: Int,label: Int,click: (ImageButton)->Unit): ImageButton {
            val button=ImageButton(this).apply {
                setImageResource(icon);contentDescription=getString(label);background=getDrawable(android.R.drawable.list_selector_background)
                setPadding(NativeUi.dp(context,9),NativeUi.dp(context,9),NativeUi.dp(context,9),NativeUi.dp(context,9))
                setOnClickListener { click(this) }
            }
            tools.addView(button,LinearLayout.LayoutParams(NativeUi.dp(this,40),NativeUi.dp(this,40)));return button
        }
        tool(R.drawable.ic_action_pin,R.string.ez_preview_pin) { button ->
            session.previewPinned=!session.previewPinned;button.isSelected=session.previewPinned;button.imageAlpha=if(session.previewPinned) 255 else 130;placePreview()
        }.apply { isSelected=session.previewPinned;imageAlpha=if(session.previewPinned) 255 else 130 }
        tool(R.drawable.ic_action_adjust,R.string.ez_preview_adjust) { button ->
            session.adjustPreview=!session.adjustPreview;button.isSelected=session.adjustPreview;dimensionsControls.visibility=if(session.adjustPreview) View.VISIBLE else View.GONE;dragHandle.visibility=dimensionsControls.visibility
        }.isSelected=session.adjustPreview
        previewFrame.addView(tools,FrameLayout.LayoutParams(-2,-2,Gravity.TOP or Gravity.END))
        previewBlock.addView(previewFrame,LinearLayout.LayoutParams(-1,NativeUi.dp(this,session.previewFrameDp)))
        dragHandle=PreviewResizeHandle(this) { delta ->
            val max=(resources.displayMetrics.heightPixels/resources.displayMetrics.density*.6f).toInt().coerceAtLeast(120)
            session.previewFrameDp=(session.previewFrameDp+delta).coerceIn(80,max)
            previewFrame.layoutParams=previewFrame.layoutParams.apply { height=NativeUi.dp(this@WidgetConfigActivity,session.previewFrameDp) }
        }.apply { visibility=if(session.adjustPreview) View.VISIBLE else View.GONE }
        previewBlock.addView(dragHandle,LinearLayout.LayoutParams(-1,NativeUi.dp(this,22)))
        dimensionsControls=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL;visibility=if(session.adjustPreview) View.VISIBLE else View.GONE }
        previewSlider(R.string.ez_preview_width,true);previewSlider(R.string.ez_preview_height,false)
        placePreview()
        body.addView(dimensionsControls)
        val rendering=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL };body.addView(rendering)
        rendering.addView(TextView(this).apply { text=getString(R.string.ez_widget_renderer);textSize=14f },LinearLayout.LayoutParams(0,-2,1f))
        NativeUi.button(rendering,getString(if(session.document?.renderer=="bitmap") R.string.ez_widget_bitmap else R.string.ez_widget_native)) {
            session.document=checkNotNull(session.document).copy(renderer=if(session.document?.renderer=="bitmap") "native" else "bitmap");changed();buildBody()
        }.apply { tag="widget_renderer";layoutParams=LinearLayout.LayoutParams(NativeUi.dp(this@WidgetConfigActivity,96),-2) }
        val textRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL };body.addView(textRow)
        textRow.addView(TextView(this).apply { text=getString(R.string.ez_layout_text);textSize=14f })
        selector=NativeUi.spinner(textRow,current().elements.map(::elementName)).apply { layoutParams=LinearLayout.LayoutParams(0,NativeUi.dp(this@WidgetConfigActivity,48),1f) }
        selector.adapter=object : ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,current().elements.map(::elementName)) {
            init { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            private fun colors(view: View,position: Int): View {
                val e=current().elements.getOrNull(position) ?: return view
                val event=session.events.firstOrNull { it.id == session.selectedId }
                val bg=NativeWidgetRenderer.resolveColor(current().background,event?.cardColor ?: OpaqueColor.WHITE)
                (view as? TextView)?.setTextColor(NativeWidgetRenderer.color(e,current(),event));view.setBackgroundColor(bg);return view
            }
            override fun getView(position: Int,recycled: View?,parent: android.view.ViewGroup): View=colors(super.getView(position,recycled,parent),position)
            override fun getDropDownView(position: Int,recycled: View?,parent: android.view.ViewGroup): View=colors(super.getDropDownView(position,recycled,parent),position)
        }
        selector.setSelection(current().elements.indexOfFirst { it.id == session.elementId }.coerceAtLeast(0))
        selector.onItemSelectedListener=SimpleItemSelectedListener {
            val id=current().elements[selector.selectedItemPosition.coerceAtLeast(0)].id
            if (id != session.elementId) { selectText(id); buildProperties() }
        }
        val actions=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }; body.addView(actions)
        actions.addView(CheckBox(this).apply { tag="visible_text";text=getString(R.string.ez_layout_visible);textSize=14f },LinearLayout.LayoutParams(0,-2,1f))
        NativeUi.button(actions,getString(R.string.ez_layout_add_text)) {
            val e=WidgetTextElement(UUID.randomUUID().toString(),"custom",content=getString(R.string.ez_layout_new_text),top=40.0)
            replaceLayout(current().copy(elements=current().elements+e)); selectText(e.id); buildBody()
        }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        NativeUi.button(actions,getString(R.string.ez_delete)) {
            val e=element(); if (e.source == "custom") { replaceLayout(current().copy(elements=current().elements.filterNot { it.id == e.id })); session.elementId=previousText(); buildBody() }
        }.apply { layoutParams=LinearLayout.LayoutParams(0,-2,1f); isEnabled=element().source == "custom"; tag="delete_text" }
        properties=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; body.addView(properties)
        backgroundControls=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; body.addView(backgroundControls)
        buildBackground()
        NativeUi.button(body,getString(R.string.ez_layout_reset)) {
            com.iqonli.ezcd.ui.FontDialogBuilder(this).setMessage(R.string.ez_layout_reset_question).setNegativeButton(android.R.string.cancel,null)
                .setPositiveButton(android.R.string.ok) { _,_ ->
                    replaceLayout(WidgetLayoutStore(this).defaults().layouts.getValue(session.size.key)); session.elementId="title"; buildBody()
                }.show()
        }
        val files=LinearLayout(this); body.addView(files)
        NativeUi.button(files,getString(R.string.ez_layout_import)) { importLayout() }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        NativeUi.button(files,getString(R.string.ez_layout_export)) {
            session.pendingExport=WidgetLayoutCodec.encode(checkNotNull(session.document))
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json").putExtra(Intent.EXTRA_TITLE,"ezcd-widget-layout.json"),EXPORT)
        }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        NativeUi.button(body,getString(R.string.ez_layout_edit_json)) { editJson() }
        buildProperties(); renderPreview(); setControlsEnabled(); body.requestFocus()
    }
    private fun buildEventSelector() {
        eventSelector.onItemSelectedListener=null
        eventSelector.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,listOf(getString(R.string.ez_not_selected))+session.events.map { EventSortPreferences(this).eventLabel(it) }).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        eventSelector.setSelection(session.events.indexOfFirst { it.id == session.selectedId }+1)
        eventSelector.onItemSelectedListener=SimpleItemSelectedListener {
            val id=session.events.getOrNull(eventSelector.selectedItemPosition-1)?.id
            if (id != session.selectedId) { session.selectedId=id; if(libraryId!=null && id!=null) session.snapshotEvent=session.events.firstOrNull { it.id == id };changed(); buildBody() }
        }
    }
    private fun setControlsEnabled() {
        val enabled=(libraryId != null || session.selectedId != null) && !session.save.running
        fun set(view: View) { view.isEnabled=enabled; if (view is android.view.ViewGroup) for (i in 0 until view.childCount) set(view.getChildAt(i)) }
        set(body);set(pinnedContainer);set(tabHost.tabWidget)
        eventSelector.isEnabled=!session.save.running
        invalidateOptionsMenu()
        body.alpha=if (enabled) 1f else .38f;pinnedContainer.alpha=body.alpha
        if (enabled) {
            body.findViewWithTag<Button>("delete_text")?.isEnabled=element().source == "custom"
            body.findViewWithTag<CheckBox>("widget_italic")?.let { if(it.getTag(R.id.font_italic_fixed)==true) it.isEnabled=false }
        }
    }
    private fun previewSlider(label: Int,horizontal: Boolean) {
        val (w,h)=session.previewSizes.getValue(session.size.key)
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL };dimensionsControls.addView(row)
        row.addView(TextView(this).apply { text=getString(label);textSize=14f;setTextColor(0xFF757575.toInt()) },LinearLayout.LayoutParams(0,-2,1f))
        val field=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine(true);setText((if(horizontal) w else h).toString());contentDescription=getString(label) }
        row.addView(field,LinearLayout.LayoutParams(NativeUi.dp(this,70),-2))
        val slider=SeekBar(this).apply { max=448;progress=(if(horizontal) w else h)-32;contentDescription=getString(label) }
        dimensionsControls.addView(slider,LinearLayout.LayoutParams(-1,NativeUi.dp(this,36)))
        var updating=false
        fun apply(n: Int) { val old=session.previewSizes.getValue(session.size.key);session.previewSizes[session.size.key]=if(horizontal) n to old.second else old.first to n;schedulePreview() }
        watch(field) {
            if(!updating) { val n=it.toIntOrNull();field.error=if(n==null || n !in 32..480) "32–480" else null
                if(n!=null && n in 32..480) { slider.progress=n-32;apply(n) } }
        }
        slider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?,p: Int,user: Boolean) { if(user) { updating=true;field.setText((p+32).toString());field.error=null;updating=false;apply(p+32) } }
            override fun onStartTrackingTouch(b: SeekBar?)=Unit
            override fun onStopTrackingTouch(b: SeekBar?)=Unit
        })
    }
    private fun placePreview() {
        (previewBlock.parent as? android.view.ViewGroup)?.removeView(previewBlock)
        if(session.previewPinned) pinnedContainer.addView(previewBlock) else body.addView(previewBlock,0)
    }
    private fun elementName(e: WidgetTextElement): String = when(e.source) {
        "title" -> getString(R.string.ez_title); "count" -> getString(R.string.ez_layout_count)
        "status" -> getString(R.string.ez_layout_status); "date" -> getString(R.string.ez_layout_date)
        else -> e.content.lineSequence().firstOrNull().orEmpty().take(32).ifBlank { getString(R.string.ez_layout_new_text) }
    }
    private fun buildProperties() {
        if (!::properties.isInitialized) return
        properties.removeAllViews(); val e=element()
        body.findViewWithTag<Button>("delete_text")?.isEnabled=e.source == "custom"
        body.findViewWithTag<CheckBox>("visible_text")?.apply { setOnCheckedChangeListener(null);isChecked=e.visible;setOnCheckedChangeListener { _,v -> updateElement { it.copy(visible=v) } } }
        if (e.source == "custom") {
            parameterLabel(properties,getString(R.string.ez_layout_content))
            val input=EditText(this).apply { setText(e.content); inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines=2 }
            properties.addView(input,LinearLayout.LayoutParams(-1,-2))
            watch(input) { value ->
                updateElement { it.copy(content=value) }
                @Suppress("UNCHECKED_CAST")
                (selector.adapter as? ArrayAdapter<String>)?.apply { setNotifyOnChange(false); clear(); addAll(current().elements.map(::elementName)); notifyDataSetChanged() }
            }
        }
        val bitmap=session.document?.renderer=="bitmap"
        if(bitmap) {
            val fonts=LinearLayout(this);properties.addView(fonts)
            val name=session.fonts.firstOrNull { it.id==e.fontId }?.metadata?.family ?: getString(R.string.ez_font_system)
            NativeUi.button(fonts,name) {
                session.pendingFontElement=element().id;session.pendingFontSize=session.size.key
                startActivityForResult(FontSelectorActivity.intent(this),PICK_FONT)
            }.apply { tag="widget_font";layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
            NativeUi.button(fonts,getString(R.string.ez_font_restore)) { updateElement { it.copy(fontId="") };buildProperties() }
                .apply { tag="widget_font_restore";layoutParams=LinearLayout.LayoutParams(0,-2,1f) }
        }
        val family=FontRuntime.family(session.fonts,e.fontId)
        val match=FontRuntime.match(family,e.weight,e.italic)
        val axis=match?.font?.metadata?.axes?.firstOrNull { it.tag=="wght" }
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL };properties.addView(row)
        val canUpright=family.isEmpty() || family.any { f -> !FontStyle.italic(f) || f.metadata.axes.any { it.tag in setOf("ital","slnt") && 0f in it.minimum..it.maximum } }
        if(bitmap) row.addView(CheckBox(this).apply {
            text=getString(if(e.italic && match?.syntheticItalic==true) R.string.ez_font_italic_synthetic else R.string.ez_font_italic)
            textSize=14f;isChecked=e.italic || !canUpright;tag="widget_italic"
            setTag(R.id.font_italic_fixed,!canUpright);isEnabled=canUpright
            setOnCheckedChangeListener { _,value -> updateElement { it.copy(italic=value) };buildProperties() }
        },LinearLayout.LayoutParams(0,-2,1f))
        row.addView(TextView(this).apply { text=getString(R.string.ez_layout_weight);textSize=14f;setTextColor(0xFF757575.toInt()) },LinearLayout.LayoutParams(if(bitmap) -2 else 0,-2,if(bitmap) 0f else 1f))
        if(bitmap && family.isNotEmpty() && axis==null) {
            val candidates=family.filter { FontStyle.italic(it)==e.italic }.ifEmpty { family }.distinctBy { FontStyle.weight(it) }.sortedBy { FontStyle.weight(it) }
            val weights=candidates.map { FontStyle.weight(it) }
            if(weights.size==1) row.addView(TextView(this).apply { text=weights.single().toString();contentDescription=getString(R.string.ez_layout_weight) },LinearLayout.LayoutParams(NativeUi.dp(this,72),-2))
            else NativeUi.spinner(row,candidates.map { "${FontStyle.weight(it)} ${it.metadata.subfamily}" }).apply {
                tag="widget_weight_options";layoutParams=LinearLayout.LayoutParams(NativeUi.dp(this@WidgetConfigActivity,170),NativeUi.dp(this@WidgetConfigActivity,48))
                setSelection(weights.indices.minBy { kotlin.math.abs(weights[it]-e.weight) })
                onItemSelectedListener=SimpleItemSelectedListener {
                    val n=weights[selectedItemPosition.coerceAtLeast(0)]
                    if(n!=element().weight) {
                        updateElement { it.copy(weight=n) }
                        row.findViewWithTag<CheckBox>("widget_italic")?.text=getString(if(element().italic && FontRuntime.match(family,n,true)?.syntheticItalic==true) R.string.ez_font_italic_synthetic else R.string.ez_font_italic)
                        if(FontRuntime.match(family,n,element().italic)?.font?.metadata?.axes?.any { it.tag=="wght" }==true) buildProperties()
                    }
                }
            }
        } else {
            val minW=if(bitmap) axis?.minimum?.toInt()?.coerceIn(1,1000) ?: 1 else 1
            val maxW=if(bitmap) axis?.maximum?.toInt()?.coerceIn(minW,1000) ?: 1000 else 1000
            val initial=e.weight.coerceIn(minW,maxW)
            val weight=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine(true);setText(initial.toString());contentDescription=getString(R.string.ez_layout_weight) }
            val minus=NativeUi.button(row,"-100") { weight.setText(((weight.text.toString().toIntOrNull() ?: element().weight)-100).coerceAtLeast(minW).toString()) }
            minus.layoutParams=LinearLayout.LayoutParams(NativeUi.dp(this,64),-2);minus.minWidth=0;minus.setPadding(0,0,0,0)
            row.addView(weight,LinearLayout.LayoutParams(NativeUi.dp(this,66),-2))
            NativeUi.button(row,"+100") { weight.setText(((weight.text.toString().toIntOrNull() ?: element().weight)+100).coerceAtMost(maxW).toString()) }.apply { layoutParams=LinearLayout.LayoutParams(NativeUi.dp(context,64),-2);minWidth=0;setPadding(0,0,0,0) }
            val weightSlider=SeekBar(this).apply { max=maxW-minW;progress=initial-minW;contentDescription=getString(R.string.ez_layout_weight) }
            properties.addView(weightSlider,LinearLayout.LayoutParams(-1,NativeUi.dp(this,40)))
            var changingWeight=false
            watch(weight) { raw -> if(!changingWeight) {
                val n=raw.toIntOrNull();weight.error=if(n==null || n !in minW..maxW) "$minW–$maxW" else null
                if(n!=null && n in minW..maxW) { weightSlider.progress=n-minW;updateElement { it.copy(weight=n) } }
            } }
            weightSlider.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(b: SeekBar?,p: Int,user: Boolean) { if(user) { changingWeight=true;weight.setText((p+minW).toString());weight.error=null;changingWeight=false;updateElement { it.copy(weight=p+minW) } } }
                override fun onStartTrackingTouch(b: SeekBar?)=Unit
                override fun onStopTrackingTouch(b: SeekBar?)=Unit
            })
        }
        val event=session.events.firstOrNull { it.id==session.selectedId }
        val follows=e.color=="auto"
        val colorRow=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL };properties.addView(colorRow)
        parameterLabel(colorRow,getString(R.string.ez_layout_text_color)).layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        colorRow.addView(CheckBox(this).apply {
            text=getString(R.string.ez_layout_auto_color);textSize=14f;isChecked=follows
            setOnCheckedChangeListener { _,v -> updateElement { it.copy(color=if(v) "auto" else "#${WidgetColor.hex(resolvedElementColor())}") };buildProperties() }
        })
        properties.addView(EventColorChoices(this,resolvedElementColor(),allowAlpha=true,
            isDefault=if(follows) event?.countColorDefault ?: true else e.color=="default",
            custom=follows && event?.countColorCustom==true,defaultColor=defaultElementColor()) { color,default ->
            updateElement { it.copy(color=if(default) "default" else "#${WidgetColor.hex(color)}") };buildProperties()
        },LinearLayout.LayoutParams(-1,-2))
        geometry(R.string.ez_max_font_size,e.maxSize,1.0,512.0,"sp") { n -> updateElement { it.copy(maxSize=n) } }
        geometry(R.string.ez_layout_top,e.top,0.0,200.0) { n -> updateElement { it.copy(top=n) } }
        geometry(R.string.ez_layout_width,e.width,.1) { n -> updateElement { it.copy(width=n) } }
        geometry(R.string.ez_layout_height,e.height,.1) { n -> updateElement { it.copy(height=n) } }
        parameterLabel(properties,getString(R.string.ez_layout_alignment))
        val labels=listOf(R.string.ez_align_tl,R.string.ez_align_tc,R.string.ez_align_tr,R.string.ez_align_ml,R.string.ez_align_mc,R.string.ez_align_mr,R.string.ez_align_bl,R.string.ez_align_bc,R.string.ez_align_br)
        repeat(3) { r ->
            val line=LinearLayout(this); properties.addView(line)
            repeat(3) { c ->
                val index=r*3+c; val alignment=WidgetLayoutCodec.alignments[index]
                line.addView(ToggleButton(this).apply {
                    NativeUi.flat(this);isAllCaps=false; textOn=getString(labels[index]); textOff=textOn; text=textOn
                    isChecked=element().alignment == alignment
                    setOnClickListener { updateElement { it.copy(alignment=alignment) }; buildProperties() }
                },LinearLayout.LayoutParams(0,NativeUi.dp(this,48),1f))
            }
        }
    }
    private fun geometry(label: Int,value: Double,min: Double,maxValue: Double=100.0,unit: String="%",change: (Double)->Unit) {
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL }; properties.addView(row)
        parameterLabel(row,getString(label)).layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        val field=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setSingleLine(true); setText(format(value)); contentDescription=getString(label) }
        row.addView(field,LinearLayout.LayoutParams(NativeUi.dp(this,85),-2))
        row.addView(TextView(this).apply { text=unit })
        val slider=SeekBar(this).apply { max=(maxValue*10).roundToInt(); progress=(value*10).roundToInt();contentDescription=getString(label) }
        properties.addView(slider,LinearLayout.LayoutParams(-1,NativeUi.dp(this,40)))
        var updating=false
        watch(field) { raw ->
            if (!updating) {
                val number=raw.toDoubleOrNull()?.takeIf { it.isFinite() && it in min..maxValue }
                field.error=if (number == null) "${format(min)}–${format(maxValue)}" else null
                if (number != null) { slider.progress=(number*10).roundToInt(); change(number) }
            }
        }
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,progress: Int,fromUser: Boolean) {
                if (fromUser) { val n=(progress/10.0).coerceAtLeast(min); updating=true; field.setText(format(n)); field.error=null; updating=false; change(n);if(label==R.string.ez_max_font_size) showFontPreview() }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) { if(label==R.string.ez_max_font_size) showFontPreview() }
            override fun onStopTrackingTouch(bar: SeekBar?) { if(label==R.string.ez_max_font_size) fontOverlay?.hide() }
        })
    }
    private fun format(n: Double): String = java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString()
    private fun parameterLabel(parent: LinearLayout,text: String): TextView = NativeUi.label(parent,text,14f).apply { setTextColor(0xFF757575.toInt()) }
    private fun defaultElementColor(): Int = if(element().source=="count") OpaqueColor.BLUE else OpaqueColor.foreground(NativeWidgetRenderer.resolveColor(current().background,session.events.firstOrNull { it.id==session.selectedId }?.cardColor ?: OpaqueColor.WHITE))
    private fun resolvedElementColor(): Int = NativeWidgetRenderer.color(element(),current(),session.events.firstOrNull { it.id==session.selectedId })
    private fun buildBackground() {
        backgroundControls.removeAllViews()
        val event=session.events.firstOrNull { it.id==session.selectedId }
        val follows=current().background=="auto"
        val row=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL };backgroundControls.addView(row)
        parameterLabel(row,getString(R.string.ez_card_color)).layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        row.addView(CheckBox(this).apply {
            text=getString(R.string.ez_layout_follow_background);textSize=14f;isChecked=follows
            setOnCheckedChangeListener { _,checked ->
                replaceLayout(current().copy(background=if(checked) "auto" else "#${WidgetColor.hex(event?.cardColor ?: OpaqueColor.WHITE)}"));buildBackground()
            }
        })
        backgroundControls.addView(EventColorChoices(this,NativeWidgetRenderer.resolveColor(current().background,event?.cardColor ?: OpaqueColor.WHITE),pastel=true,allowAlpha=true,
            isDefault=if(follows) event?.cardColorDefault ?: true else current().background=="default",
            custom=follows && event?.cardColorCustom==true,defaultColor=OpaqueColor.WHITE) { color,default ->
            replaceLayout(current().copy(background=if(default) "default" else "#${WidgetColor.hex(color)}"));buildBackground()
        },LinearLayout.LayoutParams(-1,-2))
    }
    private fun renderPreview() {
        if(loading || !::preview.isInitialized) return
        val (w,h)=session.previewSizes.getValue(session.size.key)
        preview.update(session.events.firstOrNull { it.id==session.selectedId },CountdownTime.snapshot().today,current(),w,h,session.document?.renderer=="bitmap")
    }
    private fun showFontPreview() {
        val event=session.events.firstOrNull { it.id==session.selectedId };val e=element()
        val text=if(e.source=="custom") e.content else NativeWidgetRenderer.texts(this,event,CountdownTime.snapshot().today).getValue(e.source)
        val overlay=fontOverlay ?: FontSizeOverlay(this).also { fontOverlay=it }
        val custom=if(session.document?.renderer=="bitmap") {
            if(e.fontId.isEmpty()) FontRuntime.paint(this,null,e.weight,e.italic) else FontRuntime.cachedPaint(e.fontId,e.weight,e.italic)
        } else null
        if(session.document?.renderer=="bitmap" && e.fontId.isNotEmpty() && custom==null) { overlay.hide();return }
        overlay.show(text.ifEmpty { getString(R.string.ez_layout_count) },e.weight,NativeWidgetRenderer.color(e,current(),event),e.maxSize.toFloat(),custom)
    }
    private fun importLayout() {
        com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(R.string.ez_layout_import)
            .setItems(arrayOf(getString(R.string.ez_layout_from_library),getString(R.string.ez_layout_from_file))) { _,which ->
                if(which==0) startActivityForResult(Intent(this,WidgetLibraryActivity::class.java).putExtra(WidgetLibraryActivity.EXTRA_PICK,true),PICK_LIBRARY)
                else FilePickers.open(this,IMPORT)
            }.show()
    }
    private fun draftFile()=java.io.File(cacheDir,"widget-layout-draft-${session.draftToken}.json")
    private fun stashDraft() {
        val document=session.document ?: return
        val raw=WidgetLayoutCodec.encode(document); val file=draftFile()
        CountdownIo.execute {
            val atomic=android.util.AtomicFile(file); var stream: java.io.FileOutputStream?=null
            try { stream=atomic.startWrite(); stream.write(raw.toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
            catch (_: Exception) { atomic.failWrite(stream) }
        }
    }
    private fun persist() {
        if (loading || session.save.running) return
        val id=session.selectedId
        if (libraryId==null && id==null) return
        val document=session.document ?: return
        if (hasInvalidFields(body)) { NativeUi.message(this,getString(R.string.ez_layout_fix_fields)); return }
        val context=applicationContext; val target=widgetId;val targetLibrary=libraryId;val event=session.events.firstOrNull { it.id == id }
        // Retain the pending result across recreation and close only after a successful save.
        session.save.start {
            if (targetLibrary != null) WidgetLibraryStore(context).save(targetLibrary,document,event)
            else {
                check(WidgetInstanceValidator.isOwnedBy(AppWidgetManager.getInstance(context),target,ComponentName(context,CountdownWidgetProvider::class.java)))
                WidgetLayoutStore(context).save(target,checkNotNull(id),document)
                WidgetPreferences(context).save(target,id,WidgetAppearance.LIGHT,WidgetBackground.CLASSIC,WidgetEventSelection.FIXED)
                WidgetLibraryStore(context).capture(target,event,document)
                CountdownWidgetProvider.updateAllWidgets(context);WidgetUpdateScheduler.ensureScheduled(context)
            }
            Saved(document,id)
        }
        setControlsEnabled()
    }
    private fun consumeSave() {
        if (loading || !::body.isInitialized) return
        setControlsEnabled()
        val result=session.save.takeResult() ?: return
        result.fold({ saved ->
            session.originalDocument=saved.document; session.originalEventId=saved.eventId; updateDirty()
            setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,widgetId)); finish()
        },{ NativeUi.message(this,getString(R.string.ez_save_error)) })
    }
    private fun editJson() {
        val column=NativeUi.column(this)
        val input=EditText(this).apply {
            typeface=android.graphics.Typeface.MONOSPACE; textSize=12f; minLines=8; isVerticalScrollBarEnabled=false
            gravity=Gravity.TOP or Gravity.START; inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setText(WidgetLayoutCodec.encode(checkNotNull(session.document)))
        }
        val scroll=ScrollView(this).apply { isFillViewport=true; addView(input,FrameLayout.LayoutParams(-1,-2)) }
        column.addView(scroll,LinearLayout.LayoutParams(-1,NativeUi.dp(this,300)))
        val buttons=LinearLayout(this); column.addView(buttons)
        val dialog=FocusAwareDialog(this).apply { setTitle(R.string.ez_layout_edit_json); setView(column) }
        val clipboard=getSystemService(ClipboardManager::class.java)
        NativeUi.button(buttons,getString(R.string.ez_layout_copy)) { clipboard.setPrimaryClip(ClipData.newPlainText("ezCD widget layout",input.text.toString())) }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        NativeUi.button(buttons,getString(R.string.ez_layout_paste)) {
            val pasted=clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() ?: return@button
            if (pasted.toByteArray(Charsets.UTF_8).size > WidgetLayoutCodec.MAX_BYTES) input.error=getString(R.string.ez_layout_too_large) else { input.text.clear(); input.append(pasted); input.setSelection(0); input.error=null; scroll.scrollTo(0,0) }
        }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        NativeUi.button(buttons,getString(R.string.ez_layout_confirm)) {
            try { val parsed=WidgetLayoutCodec.decode(input.text.toString()); session.document=parsed; changed(); dialog.dismiss(); buildBody() }
            catch (error: Exception) { input.error=getString(R.string.ez_layout_invalid,error.message.orEmpty()) }
        }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        dialog.show(); column.requestFocus(); dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    }
    @Deprecated("Framework activity result API is intentionally used without AndroidX")
    override fun onActivityResult(request: Int,result: Int,data: Intent?) {
        super.onActivityResult(request,result,data)
        if(request==PICK_FONT) {
            val target=session.pendingFontElement;val size=session.pendingFontSize
            session.pendingFontElement=null;session.pendingFontSize=null
            if(result==RESULT_OK) data?.getStringExtra(FontSelectorActivity.EXTRA_FONT_ID)?.let { id ->
                FontIo.submit({ FontRuntime.fonts(applicationContext) }) { loaded ->
                    if(isDestroyed || session.document==null) return@submit
                    session.fonts=loaded.getOrDefault(emptyList())
                    val chosen=session.fonts.firstOrNull { it.id==id } ?: return@submit
                    val doc=checkNotNull(session.document);val old=doc.layouts[size] ?: return@submit
                    session.document=doc.copy(layouts=doc.layouts+(checkNotNull(size) to old.copy(elements=old.elements.map { if(it.id==target) it.copy(fontId=id,weight=FontStyle.weight(chosen),italic=FontStyle.italic(chosen)) else it })))
                    changed();buildProperties();setControlsEnabled()
                }
            }
            return
        }
        if (result != RESULT_OK) return
        if(request==PICK_LIBRARY) {
            val id=data?.getStringExtra(EXTRA_LIBRARY_ID) ?: return
            CountdownIo.submit({ WidgetLibraryStore(applicationContext).get(id)?.document ?: error("Missing library entry") }) {
                if(!isDestroyed) it.fold({ doc -> session.document=doc;changed();buildBody() },{ NativeUi.message(this,getString(R.string.ez_data_error)) })
            };return
        }
        val uri=data?.data ?: return; val resolver=applicationContext.contentResolver
        if (request == IMPORT) CountdownIo.submit({
            resolver.openInputStream(uri)?.use { input ->
                val output=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
                while (true) { val count=input.read(buffer); if (count < 0) break; require(output.size()+count <= WidgetLayoutCodec.MAX_BYTES) { "Layout exceeds 1 MiB" }; output.write(buffer,0,count) }
                val bytes=output.toByteArray()
                require(bytes.size <= WidgetLayoutCodec.MAX_BYTES) { "Layout exceeds 1 MiB" }
                val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                WidgetLayoutCodec.decode(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString())
            } ?: error("Cannot open document")
        }) { result ->
            if (!isDestroyed) result.fold({ session.document=it; changed(); buildBody() },{ NativeUi.message(this,getString(R.string.ez_layout_invalid,it.message.orEmpty())) })
        }
        if (request == EXPORT) {
            val raw=session.pendingExport ?: session.document?.let(WidgetLayoutCodec::encode) ?: return
            CountdownIo.submit({ resolver.openOutputStream(uri,"wt")?.use { it.write(raw.toByteArray(Charsets.UTF_8)) } ?: error("Cannot write document") }) {
                if (!isDestroyed) NativeUi.message(this,getString(if (it.isSuccess) R.string.ez_layout_exported else R.string.ez_save_error))
            }
        }
    }
    private fun hasInvalidFields(view: View): Boolean = when(view) {
        is EditText -> view.error != null
        is android.view.ViewGroup -> (0 until view.childCount).any { hasInvalidFields(view.getChildAt(it)) }
        else -> false
    }
    private fun watch(field: EditText,changed: (String)->Unit) { field.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) = Unit
        override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { changed(s.toString()) }
        override fun afterTextChanged(s: Editable?) = Unit
    }) }
    companion object { const val EXTRA_LIBRARY_ID="libraryEntryId";private const val PICK_FONT=504;private const val PICK_LIBRARY=503;private const val IMPORT=501;private const val EXPORT=502 }
}
