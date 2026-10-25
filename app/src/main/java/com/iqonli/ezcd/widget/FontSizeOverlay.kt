// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget
import android.app.Activity
import android.graphics.*
import android.view.*
import android.util.TypedValue
internal class FontSizeOverlay(private val activity: Activity): View(activity) {
    private var text="";private var paint=Paint(Paint.ANTI_ALIAS_FLAG);private var shown=false
    init { isClickable=false;isFocusable=false;importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    fun show(text: String,weight: Int,color: Int,sp: Float,custom: android.text.TextPaint?=null) {
        this.text=text;paint=(custom?.let { android.text.TextPaint(it) } ?: NativeWidgetRenderer.paint(context,weight)).apply { this.color=color;textSize=TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,sp,resources.displayMetrics) }
        val root=activity.window.decorView as ViewGroup
        if(!shown) { root.overlay.add(this);shown=true }
        layout(0,0,root.width,root.height);invalidate()
    }
    fun hide() { if(shown) { (activity.window.decorView as ViewGroup).overlay.remove(this);shown=false } }
    override fun onDraw(canvas: Canvas) {
        val lines=text.split('\n');val fm=paint.fontMetrics;val line=fm.descent-fm.ascent
        var y=(height-lines.size*line)/2-fm.ascent
        for(s in lines) { canvas.drawText(s,(width-paint.measureText(s))/2,y,paint);y+=line }
    }
    override fun onTouchEvent(event: MotionEvent)=false
}
