// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.*
import com.iqonli.ezcd.data.*
import com.iqonli.ezcd.model.*
import com.iqonli.ezcd.widget.WidgetPinSetupActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

class EditorActivity : BaseActivity() {
    private enum class NextAction { NONE, LEAVE, PIN }
    private class Session {
        var saver: EventAutosaveSession? = null
        var nextAction = NextAction.NONE
    }
    private lateinit var session: Session
    private lateinit var body: LinearLayout
    private lateinit var nameInput: EditText
    private lateinit var repeat: Spinner
    private lateinit var reminder: Spinner
    private lateinit var include: CheckBox
    private lateinit var dateButton: Button
    private lateinit var timeButton: Button
    private lateinit var colors: EventColorChoices
    private lateinit var foregroundColors: EventColorChoices
    private var countColor = OpaqueColor.BLUE
    private var cardColor = OpaqueColor.WHITE
    private var cardColorDefault = true
    private var countColorDefault = true
    private var reminderMinuteOfDay = 9 * 60
    private lateinit var preview: FrameLayout
    private lateinit var pinButton: Button
    private lateinit var temporal: TemporalInvalidationController
    private val handler = Handler(Looper.getMainLooper())
    private val saveLater = Runnable { session.saver?.flush() }
    private val changedObserver: () -> Unit = { consume() }
    private var date = LocalDate.now()
    private var loading = true
    private var generation = 0
    private var notificationRequested = false
    private var resumed = false
    private var lastFailureShown = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = lastNonConfigurationInstance as? Session ?: Session()
        notificationRequested = savedInstanceState?.getBoolean("notificationRequested") ?: false
        title = getString(if (intent.hasExtra(EXTRA_EVENT_ID)) R.string.ez_edit else R.string.ez_add)
        body = NativeUi.page(this)
        preview = FrameLayout(this)
        body.addView(preview, LinearLayout.LayoutParams(-1, -2))
        NativeUi.label(body, getString(R.string.ez_title))
        nameInput = EditText(this).apply { id = R.id.event_title_input; setSingleLine(true); hint = getString(R.string.ez_title) }
        body.addView(nameInput, LinearLayout.LayoutParams(-1, -2))
        val dateRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        body.addView(dateRow, LinearLayout.LayoutParams(-1, -2))
        include = CheckBox(this).apply { id=R.id.event_include_input;text=getString(R.string.ez_include);textSize=14f;minWidth=0;setPadding(0,0,0,0) }
        dateRow.addView(include,LinearLayout.LayoutParams(-2,-2))
        dateButton = Button(this).apply { isAllCaps=false;minWidth=0;textSize=14f;setOnClickListener { inputDate() } }
        dateRow.addView(dateButton,LinearLayout.LayoutParams(0,-2,1f))
        dateRow.addView(Button(this).apply {
            text=getString(R.string.ez_choose_date);isAllCaps=false;minWidth=0;textSize=14f;setOnClickListener { chooseDate() }
        },LinearLayout.LayoutParams(-2,-2))
        NativeUi.label(body, getString(R.string.ez_count_color))
        foregroundColors = EventColorChoices(this, countColor,isDefault=countColorDefault,defaultColor=OpaqueColor.BLUE) { value,default -> countColor = value; countColorDefault=default; changed() }
        body.addView(foregroundColors, LinearLayout.LayoutParams(-1,-2))
        NativeUi.label(body, getString(R.string.ez_card_color))
        colors = EventColorChoices(this, cardColor, pastel = true,isDefault=cardColorDefault,defaultColor=OpaqueColor.WHITE) { value,default -> cardColor = value; cardColorDefault=default; changed() }
        body.addView(colors, LinearLayout.LayoutParams(-1,-2))
        NativeUi.label(body, getString(R.string.ez_repeat))
        repeat = NativeUi.spinner(body, listOf(R.string.ez_none, R.string.ez_weekly, R.string.ez_monthly, R.string.ez_yearly).map(::getString)).apply { id = R.id.event_repeat_input }
        NativeUi.label(body, getString(R.string.ez_reminder))
        val reminderRow = LinearLayout(this)
        body.addView(reminderRow, LinearLayout.LayoutParams(-1,-2))
        reminder = NativeUi.spinner(reminderRow, listOf(R.string.ez_none, R.string.ez_on_day, R.string.ez_before_1, R.string.ez_before_3, R.string.ez_before_7).map(::getString)).apply {
            id = R.id.event_reminder_input; layoutParams = LinearLayout.LayoutParams(0,NativeUi.dp(this@EditorActivity,48),1f)
        }
        timeButton = Button(this).apply { isAllCaps = false; setOnClickListener { chooseTime() } }
        reminderRow.addView(timeButton,LinearLayout.LayoutParams(-2,-2))
        pinButton = NativeUi.button(body, getString(R.string.ez_add_widget)) { requestAction(NextAction.PIN) }
        temporal = TemporalInvalidationController(this) { renderPreview() }
        enable(false)
        if (session.saver != null) bind() else load(savedInstanceState)
    }
    override fun onResume() {
        super.onResume(); resumed = true; temporal.start(); consume(); renderPreview()
    }
    override fun onPause() {
        handler.removeCallbacks(saveLater)
        session.saver?.flush()
        resumed = false; temporal.stop(); super.onPause()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null); generation++
        session.saver?.let { if (it.onChanged === changedObserver) it.onChanged = null }
        super.onDestroy()
    }
    override fun onRetainNonConfigurationInstance(): Any = session
    override fun onSaveInstanceState(out: Bundle) {
        session.saver?.let { saver ->
            out.putString("draftId", saver.draft.id); out.putString("draftCreated", saver.draft.createdAt.toString())
            out.putString("draftTitle", nameInput.text.toString()); out.putString("draftDate", date.toString())
            out.putInt("draftRepeat", repeat.selectedItemPosition); out.putInt("draftReminder", reminder.selectedItemPosition)
            out.putBoolean("draftInclude", include.isChecked)
            out.putBoolean("draftCountCustom",foregroundColors.customSelected);out.putBoolean("draftCardCustom",colors.customSelected);out.putBoolean("draftCountDefault",countColorDefault); out.putBoolean("draftCardDefault",cardColorDefault); out.putInt("draftCountColor", countColor); out.putInt("draftColor", cardColor); out.putInt("draftTime", reminderMinuteOfDay)
        }
        out.putBoolean("notificationRequested", notificationRequested);
        super.onSaveInstanceState(out)
    }
    override fun navigateBack() = requestAction(NextAction.LEAVE)
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, DELETE, 0, R.string.ez_delete).setIcon(NativeUi.actionIcon(this,"ic_menu_delete_material",R.drawable.ic_action_delete))
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(DELETE)?.apply {
            isVisible = !loading && session.saver?.persisted != null
            isEnabled = session.saver?.deleteRequested == false
        }
        return super.onPrepareOptionsMenu(menu)
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> { requestAction(NextAction.LEAVE); true }
        DELETE -> { confirmDelete(); true }
        else -> super.onOptionsItemSelected(item)
    }
    private fun load(state: Bundle?) {
        val token = ++generation
        val repository = CountdownRepository(applicationContext)
        CountdownIo.submit({ repository.loadResult() }) { result ->
            if (isDestroyed || token != generation) return@submit
            val events = (result.getOrNull() as? CountdownLoadResult.Success)?.events
            if (events == null) { NativeUi.message(this, getString(R.string.ez_data_error)); finish(); return@submit }
            val id = intent.getStringExtra(EXTRA_EVENT_ID) ?: state?.getString("draftId")
            val original = events.firstOrNull { it.id == id }
            if (intent.hasExtra(EXTRA_EVENT_ID) && original == null) { finish(); return@submit }
            val seed = original ?: CountdownEvent(id = id ?: UUID.randomUUID().toString(), title = "", date = date,
                type = EventType.EVENT,cardColorDefault=true,countColorDefault=true, createdAt = state?.getString("draftCreated")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: Instant.now())
            val draft = if (state?.containsKey("draftTitle") == true) seed.copy(
                title = state.getString("draftTitle").orEmpty(),
                date = state.getString("draftDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: seed.date,
                repeatRule = RepeatRule.entries.getOrElse(state.getInt("draftRepeat")) { seed.repeatRule },
                reminder = ReminderOption.entries.getOrElse(state.getInt("draftReminder")) { seed.reminder },
                includeToday = state.getBoolean("draftInclude"),
                countColorCustom=state.getBoolean("draftCountCustom",seed.countColorCustom),cardColorCustom=state.getBoolean("draftCardCustom",seed.cardColorCustom),countColorDefault=state.getBoolean("draftCountDefault",seed.countColorDefault), cardColorDefault=state.getBoolean("draftCardDefault",seed.cardColorDefault),
                countColor = if (state.containsKey("draftCountColor")) state.getInt("draftCountColor") else seed.countColor,
                cardColor = if (state.containsKey("draftColor")) state.getInt("draftColor") else seed.cardColor,
                reminderMinuteOfDay = if (state.containsKey("draftTime")) state.getInt("draftTime") else seed.reminderMinuteOfDay) else seed
            val mutations = CountdownMutations(applicationContext)
            session.saver = EventAutosaveSession(original, draft,
                save = { previous, value -> mutations.save(previous?.let(EventRevision::of), previous, value) },
                delete = { previous -> mutations.delete(previous.id, EventRevision.of(previous)) },
                dispatch = { task, done -> CountdownIo.submit(task, done) })
            bind()
        }
    }
    private fun bind() {
        val saver = checkNotNull(session.saver); val value = saver.draft
        date = value.date; dateButton.text = date.toString(); nameInput.setText(value.title)
        countColor = value.countColor; countColorDefault=value.countColorDefault; foregroundColors.setColor(countColor,countColorDefault,value.countColorCustom)
        cardColor = value.cardColor; cardColorDefault=value.cardColorDefault; colors.setColor(cardColor,cardColorDefault,value.cardColorCustom); reminderMinuteOfDay = value.reminderMinuteOfDay; renderTime()
        include.isChecked = value.includeToday
        repeat.setSelection(RepeatRule.entries.indexOf(value.repeatRule))
        reminder.setSelection(ReminderOption.entries.indexOf(value.reminder))
        loading = false; saver.onChanged = changedObserver
        body.requestFocus()
        repeat.onItemSelectedListener = SimpleItemSelectedListener { changed() }
        reminder.onItemSelectedListener = SimpleItemSelectedListener {
            changed()
            if (resumed && reminder.selectedItemPosition > 0 && Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED && !notificationRequested) {
                notificationRequested = true; requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 9)
            }
        }
        include.setOnCheckedChangeListener { _, _ -> changed() }
        nameInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        enable(!saver.deleteRequested); updateControls(); renderPreview(); consume()
        if (!saver.settled && saver.valid) saver.flush()
    }
    private fun changed() {
        if (loading) return
        val saver = checkNotNull(session.saver)
        saver.update(saver.draft.copy(title = nameInput.text.toString().trim(), date = date,
            countMode = CountMode.COUNT_DOWN, repeatRule = RepeatRule.entries[repeat.selectedItemPosition.coerceAtLeast(0)],
            reminder = ReminderOption.entries[reminder.selectedItemPosition.coerceAtLeast(0)], includeToday = include.isChecked,
            countColorCustom=foregroundColors.customSelected,cardColorCustom=colors.customSelected,countColor = countColor, cardColor = cardColor, countColorDefault=countColorDefault,cardColorDefault=cardColorDefault, reminderMinuteOfDay = reminderMinuteOfDay))
        if (nameInput.text.isNotBlank()) nameInput.error = if (CountdownValidation.isTitleWithinLimit(saver.draft.title)) null else getString(R.string.ez_title_error)
        renderPreview(); renderTime(); timeButton.isEnabled = reminder.selectedItemPosition > 0
        handler.removeCallbacks(saveLater); handler.postDelayed(saveLater, 450)
    }
    private fun renderPreview() {
        if (loading) return
        preview.removeAllViews()
        preview.addView(NativeUi.eventCard(this, checkNotNull(session.saver).draft, CountdownTime.snapshot().today))
        dateButton.text = date.toString()
    }
    private fun setDate(value: LocalDate) { date = value; changed() }
    private fun inputDate() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER; setSingleLine(true); filters = arrayOf(InputFilter.LengthFilter(8))
            hint = getString(R.string.ez_date_example); setText(CompactDateInput.format(date)); selectAll()
        }
        val padded=NativeUi.column(this).apply {
            setPadding(NativeUi.dp(context,24),NativeUi.dp(context,12),NativeUi.dp(context,24),NativeUi.dp(context,8))
            addView(TextView(context).apply { text=getString(R.string.ez_date_example);textSize=16f;setPadding(0,0,0,NativeUi.dp(context,12)) })
            addView(input,LinearLayout.LayoutParams(-1,-2))
        }
        val dialog = FocusAwareDialog(this).apply {
            setTitle(R.string.ez_input_date); setView(padded)
            setButton(AlertDialog.BUTTON_NEGATIVE,getString(android.R.string.cancel)) { _, _ -> }
            setButton(AlertDialog.BUTTON_NEUTRAL,getString(R.string.ez_choose_date)) { _,_ -> dismiss();chooseDate() }
            setButton(AlertDialog.BUTTON_POSITIVE,getString(android.R.string.ok)) { _, _ -> }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val parsed = CompactDateInput.parse(input.text.toString())
                if (parsed == null) input.error = getString(R.string.ez_date_error)
                else { setDate(parsed); dialog.dismiss() }
            }
            input.requestFocus(); dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }
    private fun chooseDate() {
        DatePickerDialog(this, { _, year, month, day -> setDate(LocalDate.of(year, month + 1, day)) }, date.year, date.monthValue - 1, date.dayOfMonth).apply {
            datePicker.minDate = CountdownDateDomain.MIN_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            datePicker.maxDate = CountdownDateDomain.MAX_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.also { it.show();DialogUi.apply(it) }
    }
    private fun renderTime() {
        val pattern = if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm" else "hh:mm a"
        val text = java.time.LocalTime.of(reminderMinuteOfDay / 60,reminderMinuteOfDay % 60)
            .format(java.time.format.DateTimeFormatter.ofPattern(pattern,resources.configuration.locales[0]))
        timeButton.text = text; timeButton.contentDescription = getString(R.string.ez_reminder_time_value,text)
    }
    private fun chooseTime() {
        val field=EditText(this).apply { inputType=InputType.TYPE_CLASS_NUMBER;setSingleLine(true);setText("%02d%02d".format(java.util.Locale.ROOT,reminderMinuteOfDay/60,reminderMinuteOfDay%60));selectAll() }
        val body=NativeUi.column(this).apply {
            setPadding(NativeUi.dp(context,24),NativeUi.dp(context,12),NativeUi.dp(context,24),NativeUi.dp(context,8))
            addView(TextView(context).apply { text=getString(R.string.ez_time_example);textSize=16f;setPadding(0,0,0,NativeUi.dp(context,12)) })
            addView(field,LinearLayout.LayoutParams(-1,-2))
        }
        val dialog=FocusAwareDialog(this).apply {
            setTitle(R.string.ez_input_time);setView(body)
            setButton(AlertDialog.BUTTON_NEGATIVE,getString(android.R.string.cancel)) { _,_ -> }
            setButton(AlertDialog.BUTTON_NEUTRAL,getString(R.string.ez_choose_date)) { _,_ -> dismiss();chooseSystemTime() }
            setButton(AlertDialog.BUTTON_POSITIVE,getString(android.R.string.ok)) { _,_ -> }
        }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val minute=CompactTimeInput.parse(field.text.toString())
                if(minute==null) field.error=getString(R.string.ez_time_error)
                else { reminderMinuteOfDay=minute;changed();dialog.dismiss() }
            }
            field.requestFocus();dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        };dialog.show()
    }
    private fun chooseSystemTime() {
        TimePickerDialog(this,{ _,hour,minute -> reminderMinuteOfDay=hour*60+minute;changed() },
            reminderMinuteOfDay/60,reminderMinuteOfDay%60,android.text.format.DateFormat.is24HourFormat(this)).also { it.show();DialogUi.apply(it) }
    }
    private fun requestAction(action: NextAction) {
        if (loading) { if (action == NextAction.LEAVE) finish(); return }
        val saver = checkNotNull(session.saver)
        if (saver.deleteRequested) return
        if (!saver.valid) {
            if (action == NextAction.LEAVE && saver.persisted == null && saver.draft.title.isBlank()) { finish(); return }
            nameInput.error = getString(R.string.ez_title_error); nameInput.requestFocus(); return
        }
        session.nextAction = action; handler.removeCallbacks(saveLater); saver.flush(); consume()
    }
    private fun consume() {
        if (loading || !resumed || isFinishing || isDestroyed) return
        val saver = session.saver ?: return
        updateControls()
        if (saver.deleted) { session.nextAction = NextAction.NONE; finish(); return }
        if (saver.failure != null && saver.failureSerial != lastFailureShown) {
            lastFailureShown = saver.failureSerial; session.nextAction = NextAction.NONE
            com.iqonli.ezcd.ui.FontDialogBuilder(this).setMessage(R.string.ez_save_error)
                .setPositiveButton(R.string.ez_retry) { _, _ -> saver.retry() }
                .setNegativeButton(android.R.string.cancel) { _, _ -> saver.cancelFailedDelete() }
                .setOnCancelListener { saver.cancelFailedDelete() }.show()
            return
        }
        if (!saver.settled) return
        when (session.nextAction.also { session.nextAction = NextAction.NONE }) {
            NextAction.LEAVE -> finish()
            NextAction.PIN -> startActivity(Intent(this,WidgetPinSetupActivity::class.java).putExtra(WidgetPinSetupActivity.EXTRA_EVENT_ID,checkNotNull(saver.persisted).id))
            NextAction.NONE -> Unit
        }
    }
    private fun updateControls() {
        val saver = session.saver ?: return
        enable(!saver.deleteRequested)
        timeButton.isEnabled = !saver.deleteRequested && reminder.selectedItemPosition > 0
        pinButton.isEnabled = !saver.deleteRequested && session.nextAction == NextAction.NONE
        invalidateOptionsMenu()
    }
    private fun confirmDelete() {
        val saver = session.saver ?: return
        com.iqonli.ezcd.ui.FontDialogBuilder(this).setTitle(R.string.ez_delete).setMessage(R.string.ez_delete_question)
            .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.ez_delete) { _, _ ->
                handler.removeCallbacks(saveLater); session.nextAction = NextAction.NONE; saver.requestDelete()
            }.show()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 9 && grants.firstOrNull() != PackageManager.PERMISSION_GRANTED) NativeUi.message(this, getString(R.string.ez_notifications_denied))
    }
    private fun enable(enabled: Boolean) {
        fun visit(view: View) { view.isEnabled = enabled; if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i)) }
        visit(body)
    }
    companion object {
        private const val DELETE = 201
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_DUPLICATE_EVENT_ID = "duplicate_event_id"
        const val EXTRA_SOURCE_REVISION = "source_revision"
    }
}
