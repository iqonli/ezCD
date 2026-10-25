// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import android.app.Activity
import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

internal object FocusTools {
    fun clear(window: Window, context: Context) {
        val focused = window.currentFocus
        focused?.clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)
            ?.hideSoftInputFromWindow(focused?.windowToken ?: window.decorView.windowToken, 0)
        val root = window.decorView.findViewById<View>(android.R.id.content)
        root?.apply { isFocusableInTouchMode = true; requestFocus() }
    }
    fun touch(window: Window, context: Context, event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN || window.currentFocus !is EditText) return
        if (!editableAt(window.decorView, event.rawX.toInt(), event.rawY.toInt())) clear(window, context)
    }
    private fun editableAt(view: View, x: Int, y: Int): Boolean {
        if (view.visibility != View.VISIBLE) return false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val rect = Rect(location[0],location[1],location[0]+view.width,location[1]+view.height)
        if (!rect.contains(x,y)) return false
        if (view is EditText) return true
        return view is ViewGroup && (0 until view.childCount).any { editableAt(view.getChildAt(it), x, y) }
    }
}
internal open class FocusAwareDialog(context: Context) : android.app.AlertDialog(context) {
    override fun onStart() {
        super.onStart()
        DialogUi.apply(this)
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        window?.let { FocusTools.touch(it, context, event) }
        return super.dispatchTouchEvent(event)
    }
}
