// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iqonli.ezcd.data.CountdownIo

class WidgetPinResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val callbackIntent = intent ?: return
        val snapshot = WidgetPinRequestSnapshot.fromCallbackData(callbackIntent.dataString) ?: return
        val appWidgetId = appWidgetIdFrom(callbackIntent)

        val pending = goAsync()
        CountdownIo.execute {
            try {
                WidgetPinDiagnostics.note(context,snapshot.requestToken,"callback widgetId=$appWidgetId")
                val result = runCatching { WidgetPinFinalizer.finalize(
                    context = context.applicationContext,
                    appWidgetId = appWidgetId,
                    snapshot = snapshot,
                ) }.onFailure { WidgetPinDiagnostics.error(context,snapshot.requestToken,"callback binding",it) }.getOrNull()
                WidgetPinDiagnostics.note(context,snapshot.requestToken,"callback binding result=$result")
                if(result==WidgetPinFinalizeResult.INVALID_WIDGET_ID) {
                    WidgetPinAttemptStore.recovering(context,snapshot.requestToken)
                    val baseline=WidgetPinAttemptStore.baseline(context,snapshot.requestToken)
                    if(baseline!=null) {
                        val manager=AppWidgetManager.getInstance(context)
                        val added=manager.getAppWidgetIds(android.content.ComponentName(context,CountdownWidgetProvider::class.java)).toSet()-baseline
                        if(added.size==1) {
                            val recovered=WidgetPinFinalizer.finalize(context,added.single(),snapshot)
                            WidgetPinDiagnostics.note(context,snapshot.requestToken,"invalid-ID recovery: id=${added.single()}, result=$recovered")
                            if(recovered in listOf(WidgetPinFinalizeResult.APPLIED,WidgetPinFinalizeResult.ALREADY_MATCHING)) WidgetPinAttemptStore.record(context,snapshot.requestToken,true)
                        }
                    }
                    return@execute
                }
                WidgetPinAttemptStore.record(context, snapshot.requestToken,
                    result == WidgetPinFinalizeResult.APPLIED || result == WidgetPinFinalizeResult.ALREADY_MATCHING)
            } finally {
                pending.finish()
            }
        }
    }

    internal companion object {
        fun appWidgetIdFrom(intent: Intent?): Int =
            intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }
}
