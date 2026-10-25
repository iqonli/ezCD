// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.*
import android.view.*
import android.widget.*
import com.iqonli.ezcd.R
import com.iqonli.ezcd.model.OpaqueColor
import kotlin.math.roundToInt

internal class ColorPickerDialog(context: Context, initial: Int, private val allowAlpha: Boolean = false, private val chosen: (Int) -> Unit) : FocusAwareDialog(context) {
    private var color = initial
    private var opacityByte = if (allowAlpha) initial ushr 24 else 255
    private var alphaField: EditText? = null
    private var alphaSlider: SeekBar? = null
    private var selectedHue = 0f
    private var updating = false
    private var valid = true
    private val rgb = mutableListOf<EditText>()
    private val hsl = mutableListOf<EditText>()
    private lateinit var hex: EditText
    private lateinit var swatch: TextView
    private lateinit var square: ColorPlane
    private lateinit var hue: SeekBar
    private lateinit var root: LinearLayout
    override fun onCreate(savedInstanceState: Bundle?) {
        setTitle(R.string.ez_custom_color)
        root = NativeUi.column(context).apply { setPadding(NativeUi.dp(context,20),NativeUi.dp(context,8),NativeUi.dp(context,20),NativeUi.dp(context,8)) }
        val planeRow = LinearLayout(context)
        root.addView(planeRow, LinearLayout.LayoutParams(-1,NativeUi.dp(context,185)))
        val changed: (FloatArray) -> Unit = { value -> selectedHue = value[0]; render(Color.HSVToColor(opacityByte,value), -1, value) }
        square = ColorPlane(context,false,changed)
        planeRow.addView(square, LinearLayout.LayoutParams(0,-1,1f))
        hue = HueSlider(context).apply { max=3599; contentDescription=context.getString(R.string.ez_hue) }
        root.addView(hue,LinearLayout.LayoutParams(-1,NativeUi.dp(context,40)))
        hue.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?,progress: Int,fromUser: Boolean) {
                if (fromUser) { val hsv=FloatArray(3); Color.colorToHSV(color,hsv); hsv[0]=progress/10f; changed(hsv) }
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        swatch = TextView(context).apply { gravity = Gravity.CENTER; textSize = 14f }
        root.addView(swatch,LinearLayout.LayoutParams(-1,NativeUi.dp(context,36)).apply { topMargin = NativeUi.dp(context,8) })
        addFields(listOf("R","G","B"),rgb,0)
        addFields(listOf("H","S","L"),hsl,1)
        if (allowAlpha) {
            NativeUi.label(root,context.getString(R.string.ez_alpha),13f)
            alphaField=field(false,3).apply { contentDescription=context.getString(R.string.ez_alpha) }
            root.addView(alphaField,LinearLayout.LayoutParams(-1,-2))
            alphaSlider=SeekBar(context).apply {
                max=100; contentDescription=context.getString(R.string.ez_alpha)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?,progress: Int,fromUser: Boolean) {
                        if (fromUser) { this@ColorPickerDialog.opacityByte=(progress*255f/100).roundToInt(); render((color and 0xFFFFFF) or (opacityByte shl 24),-1) }
                    }
                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            }
            root.addView(alphaSlider,LinearLayout.LayoutParams(-1,NativeUi.dp(context,40)))
        }
        val hexRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        root.addView(hexRow,LinearLayout.LayoutParams(-1,-2))
        hexRow.addView(TextView(context).apply { text = "HEX  #"; textSize = 16f; setPadding(0,0,NativeUi.dp(context,12),0) })
        hex = field(true,2)
        hexRow.addView(hex,LinearLayout.LayoutParams(0,-2,1f))
        setView(ScrollView(context).apply { addView(root) })
        setButton(AlertDialog.BUTTON_POSITIVE,context.getString(android.R.string.ok)) { _, _ -> if (valid) chosen(color) }
        setButton(AlertDialog.BUTTON_NEGATIVE,context.getString(android.R.string.cancel)) { _, _ -> }
        super.onCreate(savedInstanceState)
        render(color,-1); root.requestFocus()
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        getButton(AlertDialog.BUTTON_POSITIVE).isAllCaps = false; getButton(AlertDialog.BUTTON_NEGATIVE).isAllCaps = false
    }
    private fun field(hexadecimal: Boolean, group: Int) = EditText(context).apply {
        setSingleLine(true); minWidth = 0; textSize = 16f
        inputType = if (hexadecimal) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS else InputType.TYPE_CLASS_NUMBER
        filters = arrayOf(InputFilter { source,start,end,_,_,_ ->
            val raw = source.subSequence(start,end).toString()
            val clean = if (hexadecimal) OpaqueColor.hexDigits(raw) else OpaqueColor.digits(raw)
            if (clean == raw) null else clean
        })
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?,start: Int,count: Int,after: Int) = Unit
            override fun onTextChanged(s: CharSequence?,start: Int,before: Int,count: Int) { if (!updating) read(group) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
    }
    private fun addFields(labels: List<String>, target: MutableList<EditText>, group: Int) {
        val row = LinearLayout(context); root.addView(row,LinearLayout.LayoutParams(-1,-2))
        labels.forEachIndexed { index,label ->
            val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            row.addView(column,LinearLayout.LayoutParams(0,-2,1f).apply { if (index > 0) marginStart = NativeUi.dp(context,10) })
            column.addView(TextView(context).apply { text = label; textSize = 13f })
            val input = field(false,group).apply { contentDescription = label }
            target.add(input); column.addView(input,LinearLayout.LayoutParams(-1,-2))
        }
    }
    private fun read(group: Int) {
        val next: Int? = when (group) {
            0,1 -> {
                val fields = if (group == 0) rgb else hsl
                val max = if (group == 0) listOf(255,255,255) else listOf(360,100,100)
                val values = fields.mapIndexed { index,input ->
                    val number = input.text.toString().toIntOrNull()?.takeIf { it in 0..max[index] }
                    input.error = if (number == null) context.getString(R.string.ez_color_range,max[index]) else null
                    number
                }
                if (values.any { it == null }) null
                else if (group == 0) OpaqueColor.rgb(values[0]!!,values[1]!!,values[2]!!)
                else OpaqueColor.fromHsl(values[0]!!,values[1]!!,values[2]!!)
            }
            3 -> alphaField?.text?.toString()?.toIntOrNull()?.takeIf { it in 0..100 }?.let { opacityByte=(it*255f/100).roundToInt(); (color and 0xFFFFFF) or (opacityByte shl 24) }.also {
                alphaField?.error=if (it == null) context.getString(R.string.ez_color_range,100) else null
            }
            else -> (if (allowAlpha) com.iqonli.ezcd.widget.WidgetColor.parse(hex.text.toString()) else OpaqueColor.parseHex(hex.text.toString())).also {
                hex.error = if (it == null) context.getString(if (allowAlpha) R.string.ez_argb_error else R.string.ez_hex_error) else null
                if (it != null && allowAlpha) opacityByte=it ushr 24
            }
        }
        valid = next != null
        getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = valid
        if (next != null) {
            if (group == 1) selectedHue = hsl[0].text.toString().toFloatOrNull() ?: selectedHue
            render((next and 0xFFFFFF) or (opacityByte shl 24),group)
        }
    }
    private fun render(value: Int, source: Int, hsvOverride: FloatArray? = null) {
        color = value; updating = true; valid = true
        if (source != 0) OpaqueColor.channels(value).forEachIndexed { i,v -> rgb[i].setText(v.toString()); rgb[i].error = null }
        if (source != 1) {
            val values = OpaqueColor.hsl(value)
            if (values[1] == 0.0) values[0] = selectedHue.toDouble()
            values.forEachIndexed { i,v -> hsl[i].setText(v.roundToInt().toString()); hsl[i].error = null }
        }
        if (source != 2) { hex.setText(if (allowAlpha) com.iqonli.ezcd.widget.WidgetColor.hex(value) else OpaqueColor.hex(value)); hex.error = null }
        if (source != 3) { alphaField?.setText((opacityByte*100f/255).roundToInt().toString()); alphaField?.error=null }
        alphaSlider?.progress=(opacityByte*100f/255).roundToInt()
        val hsv = hsvOverride ?: FloatArray(3).apply {
            Color.colorToHSV(value,this)
            if (this[1] == 0f || this[2] == 0f) this[0] = selectedHue else selectedHue = this[0]
        }
        square.setHsv(hsv); hue.progress=(hsv[0]*10).roundToInt()
        swatch.background = if (allowAlpha) android.graphics.drawable.LayerDrawable(arrayOf(com.iqonli.ezcd.widget.AlphaCheckerboard(),android.graphics.drawable.ColorDrawable(value))) else android.graphics.drawable.ColorDrawable(value)
        swatch.setTextColor(OpaqueColor.foreground(if (allowAlpha) com.iqonli.ezcd.widget.WidgetColor.overWhite(value) else value)); swatch.text = "#${if (allowAlpha) com.iqonli.ezcd.widget.WidgetColor.hex(value) else OpaqueColor.hex(value)}"
        updating = false
        getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled = true
    }
}
