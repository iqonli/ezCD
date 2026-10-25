// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.fonts

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import com.iqonli.ezcd.R
import java.util.WeakHashMap

/** UI style remains normal/bold/italic; a chosen family supplies its nearest available face. */
internal object AppFonts {
    private data class Applied(var original: Typeface?,var last: Typeface?,var skew: Float,var fake: Boolean)
    private val applied=WeakHashMap<TextView,Applied>()
    private val windows=WeakHashMap<Window,Boolean>()
    private var selected: String?=null
    private data class Face(val typeface: Typeface,val skew: Boolean,val fakeBold: Boolean)
    private var loaded=emptyMap<Int,Face>()
    private var generation=0
    private data class Request(val root: String,val id: String?,val revision: Long)
    private var request: Request?=null
    fun selected(context: Context): String? = context.getSharedPreferences("app_font",0).getString("fontId",null)
    fun choose(context: Context,id: String?) {
        context.getSharedPreferences("app_font",0).edit().putString("fontId",id).apply()
        refresh(context)
    }
    fun watch(window: Window?) {
        if(window==null) return
        if(windows.put(window,true)==null) window.decorView.viewTreeObserver.addOnGlobalLayoutListener { apply(window.decorView) }
        apply(window.decorView)
    }
    fun refresh(context: Context) {
        val id=selected(context);val app=context.applicationContext
        val wanted=Request(app.filesDir.path,id,FontRuntime.revision())
        if(request==wanted) return
        request=wanted
        val token=++generation
        if(id==null) { selected=null;loaded=emptyMap();windows.keys.toList().forEach { apply(it.decorView) };return }
        FontIo.submit({
            val fonts=FontRuntime.fonts(app)
            val family=FontRuntime.family(fonts,id)
            if(family.isEmpty()) emptyMap() else {
                val normal=FontStyle.weight(FontStyle.standard(family))
                val boldWeight=maxOf(700,(normal+300).coerceAtMost(1000))
                val regularMatch=FontRuntime.match(family,normal,false)
                (0..3).associateWith { style ->
                    val bold=style and Typeface.BOLD!=0;val italic=style and Typeface.ITALIC!=0
                    val weight=if(bold) boldWeight else normal
                    val match=FontRuntime.match(family,weight,italic)
                    val resolved=FontRuntime.resolve(app,id,weight,italic)
                    Face(resolved.first,resolved.second,bold && match!=null && (match.weight<600 ||
                        match.font.id==regularMatch?.font?.id && match.variations==regularMatch?.variations))
                }
            }
        }) { result ->
            if(token!=generation) return@submit
            if(result.isFailure) request=null // A transient provider error may be retried on resume.
            selected=id;loaded=result.getOrDefault(emptyMap());windows.keys.toList().forEach { apply(it.decorView) }
        }
    }
    fun apply(view: View) {
        if(selected==null && applied.isEmpty()) return
        if(view.getTag(R.id.font_ui_skip)==true) return
        if(view is TextView) {
            val old=applied[view]
            if(selected==null || loaded.isEmpty()) {
                if(old!=null) { if(view.typeface===old.last) { view.typeface=old.original;view.paint.textSkewX=old.skew;view.paint.isFakeBoldText=old.fake;view.requestLayout();view.invalidate() };applied.remove(view) }
            } else {
                val state=old ?: Applied(view.typeface,null,view.paint.textSkewX,view.paint.isFakeBoldText).also { applied[view]=it }
                if(state.last!=null && view.typeface!==state.last) { state.original=view.typeface;state.skew=view.paint.textSkewX;state.fake=view.paint.isFakeBoldText }
                val style=state.original?.style ?: Typeface.NORMAL
                loaded[style]?.let { (face,skew,fake) ->
                    if(view.typeface!==face) view.typeface=face
                    val wanted=if(skew) -.2f else state.skew
                    if(view.paint.textSkewX!=wanted) { view.paint.textSkewX=wanted;view.requestLayout();view.invalidate() }
                    if(view.paint.isFakeBoldText!=(fake || state.fake)) { view.paint.isFakeBoldText=fake || state.fake;view.requestLayout();view.invalidate() }
                    state.last=face
                }
            }
        }
        if(view is ViewGroup) for(i in 0 until view.childCount) apply(view.getChildAt(i))
    }
}
