// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Parcel
import android.widget.RemoteViews

internal object WidgetPinDiagnostics {
    private fun prefs(context: Context)=context.getSharedPreferences("widget_pin_diagnostics",Context.MODE_PRIVATE)
    @Synchronized fun note(context: Context,token: String,message: String) {
        val p=prefs(context);val old=p.getString(token,"").orEmpty()
        p.edit().putString(token,(old+"\n"+message).takeLast(24000)).commit()
    }
    fun error(context: Context,token: String,stage: String,error: Throwable) {
        note(context,token,stage+": "+error.stackTraceToString().take(10000))
    }
    fun preview(context: Context,token: String,views: RemoteViews) {
        val parcel=Parcel.obtain()
        try { views.writeToParcel(parcel,0);note(context,token,"previewBytes="+parcel.dataSize()) } finally { parcel.recycle() }
    }
    fun report(context: Context,token: String): String {
        val pm=context.packageManager
        val launcher=runCatching {
            val info=pm.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo
            if(info==null) "unknown" else info.packageName+"/"+info.name+" "+pm.getPackageInfo(info.packageName,0).versionName
        }.getOrElse { "unknown: "+it.javaClass.simpleName }
        val appVersion=runCatching { pm.getPackageInfo(context.packageName,0).versionName }.getOrNull()
        return "ezCD="+appVersion+"\nAndroid="+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")"+
            "\nDevice="+Build.MANUFACTURER+" "+Build.MODEL+"\nBuild="+Build.DISPLAY+
            "\nLauncher="+launcher+"\nProvider="+context.packageName+"/.widget.CountdownWidgetProvider"+
            "\nRequest="+token+"\n"+prefs(context).getString(token,"").orEmpty()
    }
    fun clear(context: Context,token: String) { prefs(context).edit().remove(token).apply() }
}
