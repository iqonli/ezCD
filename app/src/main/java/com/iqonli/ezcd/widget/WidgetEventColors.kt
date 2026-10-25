// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.content.Context
import android.graphics.*
import android.widget.RemoteViews
import com.iqonli.ezcd.R
import com.iqonli.ezcd.model.OpaqueColor
import kotlin.math.roundToInt

internal object WidgetEventColors {
    fun render(context: Context,color: Int,widthDp: Int,heightDp: Int): Bitmap {
        val density = context.resources.displayMetrics.density
        val w = widthDp.coerceAtLeast(1) * density; val h = heightDp.coerceAtLeast(1) * density
        val scale = minOf(1f,600f/maxOf(w,h))
        val bitmap = Bitmap.createBitmap((w*scale).roundToInt().coerceAtLeast(1),(h*scale).roundToInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        Canvas(bitmap).drawRoundRect(0f,0f,bitmap.width.toFloat(),bitmap.height.toFloat(),20f*density*scale,20f*density*scale,paint)
        return bitmap
    }
    fun apply(views: RemoteViews,color: Int,background: Bitmap) {
        val text = OpaqueColor.foreground(color)
        val secondary = if (color == OpaqueColor.WHITE) 0xFF666666.toInt() else text
        views.setImageViewBitmap(R.id.widgetBackground,background)
        views.setTextColor(R.id.widgetTitle,text)
        views.setTextColor(R.id.widgetCount,if (color == OpaqueColor.WHITE) 0xFF1976D2.toInt() else text)
        views.setTextColor(R.id.widgetUnit,secondary); views.setTextColor(R.id.widgetDate,secondary)
        views.setTextColor(R.id.widgetMilestone,secondary)
    }
}
