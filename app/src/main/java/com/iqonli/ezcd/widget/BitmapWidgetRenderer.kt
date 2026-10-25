// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.content.Context
import android.graphics.*
import android.text.*
import android.view.Gravity
import android.widget.RemoteViews
import com.iqonli.ezcd.R
import com.iqonli.ezcd.fonts.FontRuntime
import com.iqonli.ezcd.model.CountdownEvent
import java.time.LocalDate
import kotlin.math.*

internal object BitmapWidgetRenderer {
    fun budget(context: Context,variants: Int=1): Long = context.resources.displayMetrics.run {
        (widthPixels.toLong().coerceAtLeast(1)*heightPixels.coerceAtLeast(1)*1.25/variants.coerceAtLeast(1)).toLong().coerceAtLeast(1)
    }
    fun render(context: Context,event: CountdownEvent?,today: LocalDate,layout: WidgetSizeLayout,widthDp: Int,heightDp: Int,
        placeholder: String=context.getString(R.string.widget_select_event),maxPixels: Long=budget(context)): Bitmap {
        val density=context.resources.displayMetrics.density
        val rawW=(widthDp.toDouble()*density).coerceAtLeast(1.0);val rawH=(heightDp.toDouble()*density).coerceAtLeast(1.0)
        val scale=minOf(1.0,sqrt(maxPixels/(rawW*rawH)),4096/rawW,4096/rawH)
        var w=(rawW*scale).toInt().coerceAtLeast(1);var h=(rawH*scale).toInt().coerceAtLeast(1)
        if(w.toLong()*h>maxPixels) { if(w>=h) w=(maxPixels/h).toInt().coerceAtLeast(1) else h=(maxPixels/w).toInt().coerceAtLeast(1) }
        val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image);val bounds=RectF(0f,0f,w.toFloat(),h.toFloat())
        val radius=6*density*scale.toFloat()
        val clip=Path().apply { addRoundRect(bounds,radius,radius,Path.Direction.CW) };canvas.clipPath(clip)
        canvas.drawColor(NativeWidgetRenderer.resolveColor(layout.background,event?.cardColor ?: com.iqonli.ezcd.model.OpaqueColor.WHITE))
        val texts=NativeWidgetRenderer.texts(context,event,today,placeholder)
        try {
            layout.elements.filter { it.visible }.forEach { e ->
                val text=if(e.source=="custom") e.content else texts.getValue(e.source)
                if(text.isEmpty()) return@forEach
                val rect=NativeWidgetRenderer.box(e,w,h)
                val paint=FontRuntime.paint(context,e.fontId,e.weight,e.italic).apply { color=NativeWidgetRenderer.color(e,layout,event) }
                val lines=fitLayout(context,text,e,rect.width(),rect.height(),e.maxSize.toFloat()*scale.toFloat(),paint)
                val y=when { e.alignment.startsWith("top") -> rect.top.toFloat();e.alignment.startsWith("bottom") -> rect.bottom-lines.height.toFloat();else -> rect.top+(rect.height()-lines.height)/2f }
                canvas.save();canvas.clipRect(rect);canvas.translate(rect.left+(rect.width()-lines.width)/2f,y);lines.draw(canvas);canvas.restore()
            }
            return image
        } catch(error: Throwable) { image.recycle();throw error }
    }
    internal fun fitLayout(context: Context,text: String,e: WidgetTextElement,width: Int,height: Int,cap: Float,paint: TextPaint): StaticLayout {
        val alignment=when { e.alignment.endsWith("left") -> Layout.Alignment.ALIGN_NORMAL;e.alignment.endsWith("right") -> Layout.Alignment.ALIGN_OPPOSITE;else -> Layout.Alignment.ALIGN_CENTER }
        val direction=if(e.alignment.endsWith("left") || e.alignment.endsWith("right")) TextDirectionHeuristics.LTR else TextDirectionHeuristics.FIRSTSTRONG_LTR
        fun layout(size: Float): StaticLayout {
            paint.textSize=size
            val inset=if(e.italic || paint.typeface.isItalic || paint.textSkewX!=0f) ceil(size*.25f).toInt().coerceAtMost(((width-1)/2).coerceAtLeast(0)) else 0
            return StaticLayout.Builder.obtain(text,0,text.length,paint,(width-inset*2).coerceAtLeast(1))
                .setAlignment(alignment).setIncludePad(false).setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE)
                .setTextDirection(direction).apply { if(android.os.Build.VERSION.SDK_INT>=28) setUseLineSpacingFromFallbacks(true) }.build()
        }
        val paragraphs=text.count { it=='\n' || it=='\u2028' || it=='\u2029' }+1
        fun fits(value: StaticLayout) = value.height<=height && value.lineCount<=paragraphs && (0 until value.lineCount).all { value.getLineWidth(it)<=value.width+.01f }
        var low=.1f;var high=android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,cap,context.resources.displayMetrics)
        val largest=layout(high);if(fits(largest)) return largest
        repeat(11) { val middle=(low+high)/2;if(fits(layout(middle))) low=middle else high=middle }
        return layout(low)
    }
    fun remoteViews(context: Context,event: CountdownEvent?,today: LocalDate,layout: WidgetSizeLayout,widthDp: Int,heightDp: Int,
        placeholder: String=context.getString(R.string.widget_select_event),maxPixels: Long=budget(context)): RemoteViews {
        val image=render(context,event,today,layout,widthDp,heightDp,placeholder,maxPixels)
        return RemoteViews(context.packageName,R.layout.widget_bitmap).apply {
            setImageViewBitmap(R.id.widgetBitmap,image)
            setContentDescription(R.id.widgetRoot,if(event==null) "$placeholder, -" else WidgetEventContentFactory.from(event,today).description(context))
        }
    }
}
