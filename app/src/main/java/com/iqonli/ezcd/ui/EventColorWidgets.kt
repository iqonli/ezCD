// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import android.content.Context
import android.graphics.*
import android.view.*
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.model.OpaqueColor

internal class EventColorChoices(context: Context, initial: Int, pastel: Boolean = false, private val allowAlpha: Boolean = false, isDefault: Boolean = false, custom: Boolean = false, private val defaultColor: Int = OpaqueColor.WHITE, private val onColor: (Int,Boolean) -> Unit) : HorizontalScrollView(context) {
    var customSelected=custom
        private set
    private var color = initial
    private var defaultSelected = isDefault
    private val palette = if (pastel) listOf("F4B7BD","F8CDAA","F5E6A9","C5E2BF","BFE5E2","BED5F0","D9C6EB").map { OpaqueColor.parseHex(it)!! } else OpaqueColor.presets
    private val choices = mutableListOf<FrameLayout>()
    private val row = LinearLayout(context)
    init {
        isHorizontalScrollBarEnabled = false
        addView(row, LayoutParams(-2,-2))
        val names = listOf(R.string.ez_red,R.string.ez_orange,R.string.ez_yellow,R.string.ez_green,R.string.ez_cyan,R.string.ez_blue,R.string.ez_purple,R.string.ez_custom,R.string.ez_default_color)
        repeat(9) { index ->
            val cell = FrameLayout(context).apply {
                contentDescription = context.getString(names[index]); isClickable = true; isFocusable = true
                val attr = android.util.TypedValue(); context.theme.resolveAttribute(android.R.attr.selectableItemBackground,attr,true)
                foreground = context.getDrawable(attr.resourceId)
                NativeUi.flat(this);NativeUi.rounded(this,NativeUi.dp(context,3).toFloat())
                setOnClickListener {
                    if (index == 7) ColorPickerDialog(context,color,allowAlpha) { setColor(it,false,true); onColor(it,false) }.show()
                    else { setColor(if (index == 8) defaultColor else palette[index],index == 8); onColor(color,index == 8) }
                }
            }
            cell.addView(TextView(context).apply { gravity = Gravity.CENTER; text = when(index) { 7 -> "+"; 8 -> context.getString(R.string.ez_default_color); else -> "" }; textSize = if (index == 8) 11f else 20f }, FrameLayout.LayoutParams(NativeUi.dp(context,if (index == 8) 38 else 26),NativeUi.dp(context,26),Gravity.CENTER))
            row.addView(cell,LinearLayout.LayoutParams(NativeUi.dp(context,42),NativeUi.dp(context,48))); choices.add(cell)
        }
        setColor(initial,isDefault,custom)
    }
    fun setColor(value: Int,isDefault: Boolean = false,custom: Boolean = false) {
        customSelected=custom || (!isDefault && value !in palette)
        color = value; defaultSelected=isDefault
        choices.forEachIndexed { index, cell ->
            val swatch = if (index == 8) defaultColor else palette.getOrElse(index) { value }
            val selected = if (index == 8) defaultSelected else !defaultSelected && if (index == 7) customSelected else !customSelected && value == swatch
            cell.isSelected = selected
            (cell.getChildAt(0) as TextView).apply {
                setTextColor(OpaqueColor.foreground(if (allowAlpha) com.iqonli.ezcd.widget.WidgetColor.overWhite(swatch) else swatch))
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(swatch); cornerRadius = NativeUi.dp(context,3).toFloat()
                    setStroke(NativeUi.dp(context,if (selected) 3 else 1),if (selected) 0xFF1976D2.toInt() else 0x55777777)
                }
            }
        }
    }
}

/** HSV square and hue strip; the linked numeric fields also expose RGB and HSL. */
internal class ColorPlane(context: Context, private val hueOnly: Boolean, private val changed: (FloatArray) -> Unit) : View(context) {
    private val hsv = floatArrayOf(0f,1f,1f)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true; isFocusable = true; contentDescription = context.getString(if (hueOnly) R.string.ez_hue else R.string.ez_saturation_value) }
    fun setColor(color: Int) {
        val oldHue = hsv[0]
        Color.colorToHSV(color, hsv)
        // Keep the selected hue while traversing grayscale instead of jumping to red.
        if (hsv[1] == 0f) hsv[0] = oldHue
        invalidate()
    }
    fun setHsv(value: FloatArray) { value.copyInto(hsv); invalidate() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = NativeUi.dp(context,5).toFloat(); val box = RectF(inset,inset,width-inset,height-inset)
        if (box.width() <= 0 || box.height() <= 0) return
        if (hueOnly) {
            val colors = intArrayOf(Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED)
            paint.shader = LinearGradient(0f,box.top,0f,box.bottom,colors,null,Shader.TileMode.CLAMP)
            canvas.drawRect(box,paint); paint.shader = null
            val y = box.top + hsv[0] / 360 * box.height()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = NativeUi.dp(context,3).toFloat(); paint.color = Color.WHITE
            canvas.drawRect(box.left-2,y-3,box.right+2,y+3,paint)
            paint.strokeWidth = 1f; paint.color = Color.BLACK; canvas.drawRect(box.left-3,y-4,box.right+3,y+4,paint)
        } else {
            paint.shader = LinearGradient(box.left,0f,box.right,0f,Color.WHITE,Color.HSVToColor(floatArrayOf(hsv[0],1f,1f)),Shader.TileMode.CLAMP)
            canvas.drawRect(box,paint)
            paint.shader = LinearGradient(0f,box.top,0f,box.bottom,Color.TRANSPARENT,Color.BLACK,Shader.TileMode.CLAMP)
            canvas.drawRect(box,paint); paint.shader = null
            val x = box.left + hsv[1]*box.width(); val y = box.top + (1-hsv[2])*box.height()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = NativeUi.dp(context,2).toFloat(); paint.color = Color.WHITE
            canvas.drawCircle(x,y,NativeUi.dp(context,6).toFloat(),paint)
            paint.strokeWidth = 1f; paint.color = Color.BLACK; canvas.drawCircle(x,y,NativeUi.dp(context,7).toFloat(),paint)
        }
        paint.shader = null; paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = 0xFF777777.toInt(); canvas.drawRect(box,paint)
        paint.style = Paint.Style.FILL
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked !in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP)) return true
        parent.requestDisallowInterceptTouchEvent(true)
        val inset = NativeUi.dp(context,5).toFloat()
        if (hueOnly) hsv[0] = ((event.y-inset)/(height-2*inset).coerceAtLeast(1f)).coerceIn(0f,1f)*359.999f
        else {
            hsv[1] = ((event.x-inset)/(width-2*inset).coerceAtLeast(1f)).coerceIn(0f,1f)
            hsv[2] = 1-((event.y-inset)/(height-2*inset).coerceAtLeast(1f)).coerceIn(0f,1f)
        }
        changed(hsv.clone()); invalidate()
        if (event.actionMasked == MotionEvent.ACTION_UP) { performClick(); parent.requestDisallowInterceptTouchEvent(false) }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> if (hueOnly) hsv[0] = (hsv[0]-2+360)%360 else hsv[2] = (hsv[2]+.02f).coerceAtMost(1f)
            KeyEvent.KEYCODE_DPAD_DOWN -> if (hueOnly) hsv[0] = (hsv[0]+2)%360 else hsv[2] = (hsv[2]-.02f).coerceAtLeast(0f)
            KeyEvent.KEYCODE_DPAD_LEFT -> hsv[1] = (hsv[1]-.02f).coerceAtLeast(0f)
            KeyEvent.KEYCODE_DPAD_RIGHT -> hsv[1] = (hsv[1]+.02f).coerceAtMost(1f)
            else -> return super.onKeyDown(keyCode,event)
        }
        changed(hsv.clone()); invalidate(); return true
    }
}
