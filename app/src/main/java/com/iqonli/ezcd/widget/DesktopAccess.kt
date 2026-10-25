// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import java.util.concurrent.*

internal enum class DesktopPermission { ALLOWED,DENIED,UNKNOWN }
internal data class DesktopState(val grid: DesktopGrid?=null,val permission: DesktopPermission=DesktopPermission.UNKNOWN,val miui: Boolean=false)

/** OEM probes are optional and run separately from event saving and font work. */
internal object DesktopAccess {
    val executor=ThreadPoolExecutor(0,2,30,TimeUnit.SECONDS,SynchronousQueue(),ThreadFactory { Thread(it,"ezCD-desktop").apply { isDaemon=true } })
    fun detect(context: Context): DesktopState {
        @Suppress("DEPRECATION")
        val home=runCatching { context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName }.getOrNull()
        val miui=home=="com.miui.home" || Build.MANUFACTURER.lowercase() in setOf("xiaomi","redmi","poco")
        // MIUI's readable setting is optional. A third-party launcher may use a different grid.
        val grid=if(home=="com.miui.home") runCatching { DesktopGrid.parse(Settings.System.getString(context.contentResolver,"miui_home_screen_cells_size")) }.getOrNull() else null
        val permission=if(miui) runCatching {
            val ops=context.getSystemService(AppOpsManager::class.java)
            val method=AppOpsManager::class.java.getMethod("checkOpNoThrow",Integer.TYPE,Integer.TYPE,String::class.java)
            permissionMode(method.invoke(ops,10017,android.os.Process.myUid(),context.packageName) as Int)
        }.getOrDefault(DesktopPermission.UNKNOWN) else DesktopPermission.UNKNOWN
        return DesktopState(grid,permission,miui)
    }
    fun permissionMode(mode: Int)=when(mode) {
        AppOpsManager.MODE_ALLOWED -> DesktopPermission.ALLOWED
        AppOpsManager.MODE_IGNORED,AppOpsManager.MODE_ERRORED -> DesktopPermission.DENIED
        else -> DesktopPermission.UNKNOWN
    }
    fun settings(context: Context) {
        val editor=Intent("miui.intent.action.APP_PERM_EDITOR").setPackage("com.miui.securitycenter")
            .putExtra("extra_pkgname",context.packageName).putExtra("extra_package_uid",android.os.Process.myUid())
        runCatching { context.startActivity(editor) }.onFailure {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}")))
        }
    }
}
