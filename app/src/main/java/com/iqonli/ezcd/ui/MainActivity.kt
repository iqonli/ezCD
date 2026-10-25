// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.*
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.CountdownEvent
import com.iqonli.ezcd.widget.WidgetPinSetupActivity

class MainActivity : BaseActivity() {
    private var events: List<CountdownEvent> = emptyList()
    private lateinit var list: ListView
    private lateinit var temporal: TemporalInvalidationController
    private var generation = 0
    private var language = ""
    private var environment=""
    private val stableIds=HashMap<String,Long>()
    private data class CardKey(val event: CountdownEvent,val environment: String)
    private val adapter=object: BaseAdapter() {
        override fun getCount()=events.size
        override fun getItem(position: Int)=events[position]
        override fun getItemId(position: Int)=stableIds.getOrPut(getItem(position).id) { stableIds.size.toLong() }
        override fun hasStableIds()=true
        override fun getView(position: Int,recycled: View?,parent: ViewGroup): View {
            val key=CardKey(getItem(position),environment)
            if(recycled?.tag==key) return recycled
            return NativeUi.eventCard(this@MainActivity,key.event,CountdownTime.snapshot().today).apply { tag=key }
        }
    }
    private lateinit var todayLabel: TextView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        title = "ezCD"
        language = LanguageManager.currentLanguageTag(this)
        val frame = FrameLayout(this)
        val root = NativeUi.column(this).apply { setPadding(6, 0, 6, 0) }
        todayLabel = NativeUi.label(root, TextPool.date(this,java.time.LocalDate.now(),java.time.format.FormatStyle.LONG), 16f)
        todayLabel.gravity = android.view.Gravity.CENTER
        list = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
            dividerHeight = NativeUi.dp(this@MainActivity, 3)
            clipToPadding = false; setPadding(0, 0, 0, NativeUi.dp(this@MainActivity, 88))
        }
        list.adapter=adapter
        val empty = NativeUi.label(root, getString(R.string.ez_empty))
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.emptyView = empty
        list.setOnItemClickListener { _, _, index, _ -> open(events[index]) }
        list.setOnItemLongClickListener { _, _, index, _ -> actions(events[index]); true }
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        val fab = TextView(this).apply {
            text = "+"; textSize = 32f; gravity = android.view.Gravity.CENTER; setTextColor(-1)
            contentDescription = getString(R.string.ez_add); isClickable = true; isFocusable = true
            val circle = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(0xff2196f3.toInt()) }
            background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x55ffffff), circle, null)
            elevation = NativeUi.dp(this@MainActivity, 6).toFloat();NativeUi.rounded(this,NativeUi.dp(this@MainActivity,28).toFloat())
            setOnClickListener { open(null) }
        }
        frame.addView(fab, FrameLayout.LayoutParams(NativeUi.dp(this, 56), NativeUi.dp(this, 56), android.view.Gravity.BOTTOM or android.view.Gravity.END).apply {
            setMargins(0, 0, NativeUi.dp(this@MainActivity, 20), NativeUi.dp(this@MainActivity, 20))
        })
        setContentView(frame)
        temporal = TemporalInvalidationController(this) { load() }
        openRoutedEvent(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent); setIntent(intent); openRoutedEvent(intent)
    }
    private fun openRoutedEvent(source: Intent) {
        val id = source.getStringExtra(EventNavigation.EXTRA_OPEN_EVENT) ?: return
        source.removeExtra(EventNavigation.EXTRA_OPEN_EVENT)
        if (id.isNotBlank()) startActivity(Intent(this, EditorActivity::class.java).putExtra(EditorActivity.EXTRA_EVENT_ID, id))
    }
    override fun onResume() {
        super.onResume()
        if (language != LanguageManager.currentLanguageTag(this)) { recreate(); return }
        temporal.start(); load()
    }
    override fun onPause() { temporal.stop(); generation++; super.onPause() }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 2, 0, R.string.ez_sort).setIcon(NativeUi.actionIcon(this,"ic_menu_sort_by_size_material",R.drawable.ic_action_sort)).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        menu.add(0, 1, 0, R.string.ez_settings).setIcon(NativeUi.actionIcon(this,"ic_settings_material",R.drawable.ic_action_settings)).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 2) {
            val preferences=EventSortPreferences(this)
            com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(R.string.ez_sort)
                .setSingleChoiceItems(preferences.labels().toTypedArray(),preferences.get().ordinal) { dialog,index ->
                    preferences.save(EventSortOrder.entries[index]); dialog.dismiss(); load()
                }.setNegativeButton(android.R.string.cancel,null).show()
            return true
        }
        if (item.itemId == 1) { startActivity(Intent(this, SettingsActivity::class.java)); return true }
        return super.onOptionsItemSelected(item)
    }
    private fun open(event: CountdownEvent?) {
        startActivity(Intent(this, EditorActivity::class.java).apply { event?.let { putExtra(EditorActivity.EXTRA_EVENT_ID, it.id) } })
    }
    private fun load() {
        todayLabel.text = TextPool.date(this,java.time.LocalDate.now(),java.time.format.FormatStyle.LONG)
        val token = ++generation
        val repository = CountdownRepository(applicationContext)
        val order=EventSortPreferences(this).get();val today=CountdownTime.snapshot().today;val locale=resources.configuration.locales[0]
        CountdownIo.submit({ repository.loadResult().let { if(it is CountdownLoadResult.Success) it.copy(events=order.sorted(it.events,today,locale)) else it } }) { result ->
            if (token != generation || isDestroyed || isFinishing) return@submit
            when (val loaded = result.getOrNull()) {
                is CountdownLoadResult.Success -> {
                    val nextEnvironment=today.toString()+"|"+locale.toLanguageTag()+"|"+TextPool.keys.joinToString("|") { TextPool(this).get(it) }
                    if(events!=loaded.events || environment!=nextEnvironment) {
                        val first=list.firstVisiblePosition;val id=events.getOrNull(first)?.id;val top=list.getChildAt(0)?.top ?: 0
                        events=loaded.events;environment=nextEnvironment;adapter.notifyDataSetChanged()
                        if(events.isNotEmpty()) {
                            val index=events.indexOfFirst { it.id==id }.takeIf { it>=0 } ?: first.coerceIn(events.indices)
                            list.setSelectionFromTop(index,top-list.paddingTop)
                        }
                    }
                }
                else -> NativeUi.message(this, getString(R.string.ez_data_error))
            }
        }
    }
    private fun actions(event: CountdownEvent) {
        com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(event.title)
            .setItems(arrayOf(getString(R.string.ez_edit), getString(R.string.ez_add_widget))) { _, which ->
                if (which == 0) open(event)
                else startActivity(Intent(this, WidgetPinSetupActivity::class.java).putExtra(WidgetPinSetupActivity.EXTRA_EVENT_ID, event.id))
            }.show()
    }
}
