// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.view.MotionEvent
import android.view.WindowManager

abstract class BaseActivity : Activity() {
    private var backCallback: Any? = null
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(ThemeManager.wrap(LanguageManager.wrap(newBase))) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LanguageManager.syncPlatformLocale(this)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        actionBar?.setDisplayHomeAsUpEnabled(javaClass != MainActivity::class.java)
        if (Build.VERSION.SDK_INT >= 33) {
            val callback = android.window.OnBackInvokedCallback { navigateBack() }
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, callback)
            backCallback = callback
        }
    }
    override fun onResume() { super.onResume();com.iqonli.ezcd.fonts.AppFonts.watch(window);com.iqonli.ezcd.fonts.AppFonts.refresh(this) }
    override fun onContentChanged() {
        super.onContentChanged()
        fun visit(v: android.view.View) {
            if(v is android.widget.Button) NativeUi.flat(v)
            if(v is android.view.ViewGroup) for(i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        window?.decorView?.let(::visit)
        com.iqonli.ezcd.fonts.AppFonts.watch(window)
    }
    protected open fun navigateBack() { finish() }
    @Deprecated("Platform back compatibility for Android 12 and below.")
    override fun onBackPressed() = navigateBack()
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) { navigateBack(); return true }
        return super.onOptionsItemSelected(item)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        FocusTools.touch(window, this, event)
        return super.dispatchTouchEvent(event)
    }
    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 33) (backCallback as? android.window.OnBackInvokedCallback)?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
        super.onDestroy()
    }
}
