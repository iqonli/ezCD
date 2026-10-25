// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import android.graphics.*
import android.graphics.drawable.Drawable
internal class AlphaCheckerboard : Drawable() {
    private val paint=Paint()
    override fun draw(canvas: Canvas) {
        val step=(bounds.width()/24).coerceAtLeast(8)
        for (y in bounds.top until bounds.bottom step step) for (x in bounds.left until bounds.right step step) {
            paint.color=if (((x-bounds.left)/step+(y-bounds.top)/step)%2 == 0) 0xFFF5F5F5.toInt() else 0xFFE5E5E5.toInt()
            canvas.drawRect(x.toFloat(),y.toFloat(),(x+step).coerceAtMost(bounds.right).toFloat(),(y+step).coerceAtMost(bounds.bottom).toFloat(),paint)
        }
    }
    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Drawable pixel format API") override fun getOpacity()=PixelFormat.OPAQUE
}
