// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import com.iqonli.ezcd.R
import com.iqonli.ezcd.data.CountdownIo
import com.iqonli.ezcd.model.CountdownEvent

internal object WidgetPinAttemptStore {
    private fun prefs(c: Context)=c.getSharedPreferences("ezcd_pin_results",Context.MODE_PRIVATE)
    @Synchronized fun record(c: Context,t: String,succeeded: Boolean) { prefs(c).edit().putString(t,if(succeeded) "ok" else "failed").commit() }
    fun result(c: Context,t: String)=prefs(c).getString(t,null)
    @Synchronized fun prepare(c: Context,t: String,ids: Set<Int>?) {
        val edit=prefs(c).edit().putString(t,"requesting")
        if(ids==null) edit.remove("base_$t") else edit.putString("base_$t",ids.joinToString(","))
        edit.commit()
    }
    @Synchronized fun recovering(c: Context,t: String) { if(result(c,t)!="ok") prefs(c).edit().putString(t,"recovering").commit() }
    @Synchronized fun accepted(c: Context,t: String,ok: Boolean) {
        if (result(c,t) !in listOf("ok","failed")) prefs(c).edit().putString(t,if(ok) "accepted" else "rejected").commit()
    }
    fun baseline(c: Context,t: String)=prefs(c).getString("base_$t",null)?.split(',')?.mapNotNull { it.toIntOrNull() }?.toSet()
    fun document(c: Context,t: String)=prefs(c).getString("layout_$t",null)?.let { WidgetLayoutCodec.decode(it) }
    fun document(c: Context,t: String,doc: WidgetLayoutDocument) { check(prefs(c).edit().putString("layout_$t",WidgetLayoutCodec.encode(doc)).commit()) }
    fun clear(c: Context,t: String) { prefs(c).edit().remove(t).remove("base_$t").remove("layout_$t").apply() }
}

/** Request pinning directly from the visible activity; optional queries never gate the request. */
internal class WidgetPinFeedback(private val activity: Activity,private val onChanged: ()->Unit={}) {
    private val context=activity.applicationContext
    private val handler=Handler(Looper.getMainLooper())
    private var snapshot: WidgetPinRequestSnapshot?=null
    private var started=0L
    private var resumed=false
    private var focused=false
    private var checking=false
    private var baseline: Set<Int>?=null
    private var baselineLoading=false
    val pending get()=snapshot != null
    private val hardDeadline=Runnable { if(pending) snapshot?.let { if(WidgetPinAttemptStore.result(context,it.requestToken)=="ok") end() else report(it,R.string.ez_widget_pin_unconfirmed) } }
    private val poll=Runnable { checkResult() }
    private val deadline=Runnable {
        val attempt=snapshot
        if(attempt!=null) when(WidgetPinAttemptStore.result(context,attempt.requestToken)) {
            "ok" -> end()
            "failed" -> report(attempt,R.string.ez_widget_pin_failed)
            else -> report(attempt,R.string.ez_widget_pin_unconfirmed)
        }
    }
    fun restore(state: Bundle?) { snapshot=WidgetPinRequestSnapshot.fromCallbackData(state?.getString("pinSnapshot"));started=state?.getLong("pinStarted") ?: 0L }
    fun save(out: Bundle) { snapshot?.let { out.putString("pinSnapshot",it.callbackData());out.putLong("pinStarted",started) } }
    fun onResume() {
        if(!resumed && pending) started=SystemClock.elapsedRealtime()
        resumed=true;if(pending) { handler.removeCallbacks(hardDeadline);handler.postDelayed(hardDeadline,15000) };schedule()
        if(!pending && !baselineLoading) {
            baselineLoading=true
            submit({ currentIds() }) { result -> baselineLoading=false;if(!pending && resumed && !activity.isDestroyed) baseline=result.getOrNull() }
        }
    }
    fun onPause() { handler.removeCallbacks(hardDeadline);resumed=false;baseline=null;handler.removeCallbacks(poll);handler.removeCallbacks(deadline) }
    fun onWindowFocusChanged(value: Boolean) { if(value && !focused && pending) started=SystemClock.elapsedRealtime();focused=value;schedule() }
    fun onDestroy() { resumed=false;handler.removeCallbacksAndMessages(null) }
    fun request(event: CountdownEvent?,size: WidgetPinSize=WidgetPinSize(),document: WidgetLayoutDocument?=null,placeholder: Boolean=false) {
        if (pending) return
        val attempt=WidgetPinRequestSnapshot.create(if(placeholder) WidgetPinning.PLACEHOLDER_ID else event?.id ?: WidgetPinning.NO_EVENT_ID,WidgetStyleSelection(WidgetAppearance.LIGHT,WidgetBackground.CLASSIC),size=size).copy(customLayout=document!=null)
        if(document!=null) {
            val saved=runCatching { WidgetPinAttemptStore.document(context,attempt.requestToken,document) }
            if(saved.isFailure) { com.iqonli.ezcd.ui.NativeUi.message(activity,activity.getString(R.string.ez_save_error));return }
        }
        snapshot=attempt;started=SystemClock.elapsedRealtime();onChanged();handler.postDelayed(hardDeadline,15000)
        val known=baseline;baseline=null
        WidgetPinDiagnostics.note(context,attempt.requestToken,"baseline="+(known?.sorted()?.joinToString(",") ?: "unknown"))
        CountdownIo.execute { WidgetPinAttemptStore.prepare(context,attempt.requestToken,known) }
        // Use the actual foreground activity and original direct launcher request path.
        // Keep the 1.0.5 preview contract; optional baseline queries must not gate the request.
        val result=runCatching { WidgetPinning.request(activity,event,attempt) }
        CountdownIo.execute { result.onSuccess { WidgetPinAttemptStore.accepted(context,attempt.requestToken,it) } }
        if(result.isFailure || result.getOrNull()==false) {
            result.exceptionOrNull()?.let { WidgetPinDiagnostics.error(context,attempt.requestToken,"request",it) }
            report(attempt,if(result.exceptionOrNull() is SecurityException) R.string.ez_widget_pin_denied else R.string.ez_widget_pin_failed)
        }
        schedule()
    }
    private fun <T> submit(task: ()->T,done: (Result<T>)->Unit) {
        try { launcherIo.execute { val result=runCatching(task);handler.post { done(result) } } }
        catch (error: java.util.concurrent.RejectedExecutionException) { handler.post { done(Result.failure(error)) } }
    }
    private companion object {
        val launcherIo=java.util.concurrent.ThreadPoolExecutor(0,4,30,java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.SynchronousQueue(),java.util.concurrent.ThreadFactory { r -> Thread(r,"ezCD-launcher").apply { isDaemon=true } })
    }
    private fun currentIds()=AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context,CountdownWidgetProvider::class.java)).toSet()
    private fun schedule() {
        handler.removeCallbacks(poll);handler.removeCallbacks(deadline)
        if (!pending || !resumed || !focused) return
        val remaining=(8000-(SystemClock.elapsedRealtime()-started)).coerceAtLeast(0)
        handler.postDelayed(deadline,remaining)
        if (!checking) handler.postDelayed(poll,500)
    }
    private fun checkResult() {
        val attempt=snapshot ?: return
        if (!resumed || !focused || checking || activity.isFinishing || activity.isDestroyed) return
        checking=true
        submit({
            val status=WidgetPinAttemptStore.result(context,attempt.requestToken)
            if (status=="ok" || status=="failed" || status=="rejected") status
            else {
                val baseline=WidgetPinAttemptStore.baseline(context,attempt.requestToken)
                val added=if (baseline==null || status=="requesting") emptySet() else currentIds()-baseline
                if(added.isNotEmpty()) WidgetPinDiagnostics.note(context,attempt.requestToken,"newWidgetIds="+added.sorted().joinToString(","))
                if (added.size==1) {
                    val applied=WidgetPinFinalizer.finalize(context,added.first(),attempt)
                    WidgetPinDiagnostics.note(context,attempt.requestToken,"recovered binding=$applied")
                    if (applied in listOf(WidgetPinFinalizeResult.APPLIED,WidgetPinFinalizeResult.ALREADY_MATCHING)) "ok" else "unknown"
                } else "unknown"
            }
        }) { result ->
            checking=false
            if (activity.isDestroyed || snapshot?.requestToken != attempt.requestToken) return@submit
            result.exceptionOrNull()?.let { WidgetPinDiagnostics.error(context,attempt.requestToken,"check result",it) }
            when(result.getOrNull()) {
                "rejected","failed" -> report(attempt,R.string.ez_widget_pin_failed)
                "ok" -> end()
                else -> if (SystemClock.elapsedRealtime()-started >= 8000) report(attempt,R.string.ez_widget_pin_unconfirmed) else schedule()
            }
        }
    }
    private fun end(clearDiagnostics: Boolean=true) { snapshot?.let { CountdownIo.execute { WidgetPinAttemptStore.clear(context,it.requestToken) };if(clearDiagnostics) WidgetPinDiagnostics.clear(context,it.requestToken) };snapshot=null;checking=false;handler.removeCallbacks(poll);handler.removeCallbacks(deadline);handler.removeCallbacks(hardDeadline);onChanged() }
    private fun report(attempt: WidgetPinRequestSnapshot,message: Int) {
        WidgetPinDiagnostics.note(context,attempt.requestToken,"status="+WidgetPinAttemptStore.result(context,attempt.requestToken)+", elapsedMs="+(SystemClock.elapsedRealtime()-started))
        end(false)
        submit({ WidgetPinDiagnostics.report(context,attempt.requestToken) }) { result ->
            val detail=activity.getString(message)+"\n\n"+result.getOrElse { "diagnostics: "+it.stackTraceToString() }
            WidgetPinDiagnostics.clear(context,attempt.requestToken)
            if(activity.isFinishing || activity.isDestroyed) return@submit
            val text=android.widget.TextView(activity).apply {
                this.text=detail;setTextIsSelectable(true);textSize=13f
                typeface=android.graphics.Typeface.MONOSPACE
                val pad=com.iqonli.ezcd.ui.NativeUi.dp(context,20);setPadding(pad,pad,pad,pad)
            }
            val scroll=android.widget.ScrollView(activity).apply { addView(text) }
            com.iqonli.ezcd.ui.FontDialogBuilder(activity).setTitle(R.string.ez_add_widget).setView(scroll).setPositiveButton(android.R.string.ok,null)
                .setNeutralButton(android.R.string.copy) { _,_ ->
                    activity.getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("ezCD widget diagnostics",detail))
                }.setNegativeButton(R.string.ez_app_settings) { _,_ -> runCatching { activity.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${activity.packageName}"))) } }.show()
        }
    }
}
