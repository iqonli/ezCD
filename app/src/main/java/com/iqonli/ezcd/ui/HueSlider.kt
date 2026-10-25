// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.widget.SeekBar
/** Native SeekBar interaction/accessibility, with an always-visible rainbow track. */
internal class HueSlider(context: Context): SeekBar(context) {
    init {
        splitTrack=false
        progressDrawable=object : Drawable() {
            private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
            override fun draw(canvas: Canvas) {
                val b=bounds; val half=NativeUi.dp(context,4).toFloat()
                paint.shader=LinearGradient(b.left.toFloat(),0f,b.right.toFloat(),0f,intArrayOf(Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED),null,Shader.TileMode.CLAMP)
                canvas.drawRoundRect(b.left.toFloat(),b.exactCenterY()-half,b.right.toFloat(),b.exactCenterY()+half,half,half,paint)
            }
            override fun setAlpha(alpha: Int) { paint.alpha=alpha;invalidateSelf() }
            override fun setColorFilter(filter: ColorFilter?) { paint.colorFilter=filter;invalidateSelf() }
            @Deprecated("Deprecated by Android") override fun getOpacity()=PixelFormat.TRANSLUCENT
        }
    }
}
