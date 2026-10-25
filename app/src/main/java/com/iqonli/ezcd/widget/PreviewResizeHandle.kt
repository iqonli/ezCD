// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget
import android.content.Context
import android.graphics.*
import android.view.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.ui.NativeUi
internal class PreviewResizeHandle(context: Context,private val change: (Int)->Unit): View(context) {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xFF2196F3.toInt() }
    private var lastY=0f
    init { isFocusable=true;isClickable=true;contentDescription=context.getString(R.string.ez_preview_resize) }
    override fun onDraw(canvas: Canvas) {
        val half=NativeUi.dp(context,20).toFloat();val y=height/2f;val h=NativeUi.dp(context,2).toFloat()
        canvas.drawRoundRect(width/2f-half,y-h,width/2f+half,y+h,h,h,paint)
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { lastY=event.rawY;parent.requestDisallowInterceptTouchEvent(true) }
            MotionEvent.ACTION_MOVE -> { val dp=((event.rawY-lastY)/resources.displayMetrics.density).toInt();if(dp!=0) { change(dp);lastY=event.rawY } }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false);if(event.actionMasked==MotionEvent.ACTION_UP) performClick() }
        };return true
    }
    override fun performClick(): Boolean { super.performClick();return true }
    override fun onKeyDown(code: Int,event: KeyEvent): Boolean=when(code) { KeyEvent.KEYCODE_DPAD_UP -> { change(-8);true };KeyEvent.KEYCODE_DPAD_DOWN -> { change(8);true };else -> super.onKeyDown(code,event) }
}
class WidgetLibraryEditorActivity: WidgetConfigActivity()
