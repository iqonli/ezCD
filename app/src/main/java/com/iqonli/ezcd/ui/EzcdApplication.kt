// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import android.app.Activity
import android.app.Application
import android.os.*
import com.iqonli.ezcd.data.*

/** Internal navigation and rotations are not exits. Background transitions are debounced. */
class EzcdApplication: Application(),Application.ActivityLifecycleCallbacks {
    private val handler=Handler(Looper.getMainLooper())
    private var started=0
    private var foreground=false
    private val leave=Runnable { if(started==0 && foreground) { foreground=false;AutoBackup.request(this,false) } }
    private val enter=Runnable { if(started>0) AutoBackup.request(this,true) }
    override fun onCreate() {
        super.onCreate()
        com.iqonli.ezcd.fonts.FontIo.submit({ com.iqonli.ezcd.fonts.FontCache.clean(this) }) { }
        CountdownIo.execute { runCatching { com.iqonli.ezcd.share.ShareImageStore.prune(this) } }
        CountdownIo.execute { runCatching { BackupArchiveService(this).recover();com.iqonli.ezcd.widget.WidgetLibraryStore(this).sync() }.onFailure { android.util.Log.e("ezCD","Backup recovery failed",it) } }
        val clock=com.iqonli.ezcd.widget.WidgetRefreshReceiver()
        val filter=android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK);addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_SCREEN_ON);addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        if(Build.VERSION.SDK_INT>=33) registerReceiver(clock,filter,RECEIVER_NOT_EXPORTED) else registerReceiver(clock,filter)
        CountdownIo.execute { runCatching {
            com.iqonli.ezcd.widget.CountdownWidgetProvider.updateAllWidgets(this)
            com.iqonli.ezcd.widget.WidgetUpdateScheduler.ensureScheduled(this)
        }.onFailure { android.util.Log.e("ezCD","Cannot initialize widget date refresh",it) } }
        registerActivityLifecycleCallbacks(this)
    }
    override fun onActivityStarted(activity: Activity) {
        started++;handler.removeCallbacks(leave)
        if(!foreground) { foreground=true;handler.removeCallbacks(enter);handler.postDelayed(enter,500) }
    }
    override fun onActivityStopped(activity: Activity) {
        started=(started-1).coerceAtLeast(0)
        if(started==0) {
            handler.removeCallbacks(enter)
            if(!activity.isChangingConfigurations) handler.postDelayed(leave,700)
        }
    }
    override fun onActivityCreated(a: Activity,b: Bundle?)=Unit
    override fun onActivityResumed(a: Activity)=Unit
    override fun onActivityPaused(a: Activity)=Unit
    override fun onActivitySaveInstanceState(a: Activity,b: Bundle)=Unit
    override fun onActivityDestroyed(a: Activity)=Unit
}
