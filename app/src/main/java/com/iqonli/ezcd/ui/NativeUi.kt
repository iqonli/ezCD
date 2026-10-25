// ezCD modification, Copyright (c) 2026 IQ Online Studio. MIT; upstream portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.app.Activity
import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iqonli.ezcd.R

/** Framework Views with the app light Material theme and native touch feedback. */
object NativeUi {
    fun flat(view: View) { view.stateListAnimator=null;view.elevation=0f;view.translationZ=0f }
    fun rounded(view: View,radius: Float) {
        view.outlineProvider=object: android.view.ViewOutlineProvider() { override fun getOutline(v: View,o: android.graphics.Outline) { o.setRoundRect(0,0,v.width,v.height,radius) } }
        view.clipToOutline=true
    }
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun column(context: Context) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        isFocusableInTouchMode = true; descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 16))
    }
    fun page(activity: Activity): LinearLayout {
        val body = column(activity)
        activity.setContentView(ScrollView(activity).apply { isFillViewport = true; addView(body) })
        body.requestFocus()
        return body
    }
    fun label(parent: LinearLayout, text: CharSequence, size: Float = 16f): TextView = TextView(parent.context).apply {
        this.text = text; textSize = size
        setPadding(dp(context, 4), dp(context, 10), dp(context, 4), dp(context, 6))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    fun button(parent: LinearLayout, text: CharSequence, click: () -> Unit): Button = Button(parent.context).apply {
        flat(this);this.text = text; isAllCaps = false; setOnClickListener { click() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    fun spinner(parent: LinearLayout, labels: List<String>): Spinner = Spinner(parent.context, Spinner.MODE_DROPDOWN).apply {
        adapter = ArrayAdapter(context, android.R.layout.simple_spinner_item, labels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        parent.addView(this, LinearLayout.LayoutParams(-1, dp(context, 48)))
    }
    fun preference(parent: LinearLayout, text: CharSequence, click: () -> Unit): Button = Button(parent.context).apply {
        flat(this);this.text = text; isAllCaps = false; gravity = Gravity.CENTER_VERTICAL or Gravity.START
        textSize = 16f; setTextColor(0xff333333.toInt())
        val value = android.util.TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        setBackgroundResource(value.resourceId)
        setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 12))
        minHeight = dp(context, 56); setOnClickListener { click() }
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        parent.addView(View(context).apply { setBackgroundColor(0xffdddddd.toInt()) }, LinearLayout.LayoutParams(-1, dp(context, 1)))
    }
    fun eventCard(context: Context, title: String, date: String, count: String, label: String, unit: String, color: Int = -1, countColor: Int = com.iqonli.ezcd.model.OpaqueColor.BLUE): View {
        val foreground = com.iqonli.ezcd.model.OpaqueColor.foreground(color)
        val secondary = if (foreground == 0xFF000000.toInt()) 0xFF555555.toInt() else foreground
        val frame = FrameLayout(context).apply { setPadding(dp(context, 4), dp(context, 4), dp(context, 4), dp(context, 4)) }
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 14), dp(context, 8), dp(context, 12), dp(context, 8))
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(color); cornerRadius = dp(context, 4).toFloat()
            }
            flat(this); rounded(this,dp(context,4).toFloat())
        }
        frame.addView(card, FrameLayout.LayoutParams(-1, dp(context, 84)))
        val left = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        card.addView(left, LinearLayout.LayoutParams(0, -1, 1f))
        left.addView(TextView(context).apply {
            text = title; textSize = 18f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setTextColor(foreground); setTypeface(typeface, android.graphics.Typeface.BOLD)
        }, LinearLayout.LayoutParams(-1, -2))
        left.addView(TextView(context).apply {
            text = date; textSize = 14f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, dp(context, 5), 0, 0); setTextColor(secondary)
        }, LinearLayout.LayoutParams(-1, -2))
        val right = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
        card.addView(right, LinearLayout.LayoutParams(dp(context, 118), -1).apply { marginStart = dp(context, 8) })
        val number = android.text.SpannableString("$count $unit").apply {
            setSpan(android.text.style.ForegroundColorSpan(countColor), 0, count.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(android.text.style.RelativeSizeSpan(0.43f), count.length, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        right.addView(TextView(context).apply {
            text = number; gravity = Gravity.END or Gravity.CENTER_VERTICAL; setTextColor(foreground); maxLines = 1
            setAutoSizeTextTypeUniformWithConfiguration(12, 34, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
        }, LinearLayout.LayoutParams(-1, dp(context, 41)))
        right.addView(TextView(context).apply { text = label; textSize = 12f; gravity = Gravity.END; setTextColor(secondary); maxLines = 1 })
        return frame
    }
    fun eventCard(context: Context, event: com.iqonli.ezcd.model.CountdownEvent, today: java.time.LocalDate): View {
        val value = com.iqonli.ezcd.countdown.EventCountResolver.resolve(event, today)
        val label = TextPool.status(context,checkNotNull(value.countdownStatus))
        val dateText = TextPool.date(context,value.displayDate)
        return eventCard(context, event.title.ifBlank { context.getString(R.string.ez_title) }, dateText,
            value.magnitude.toString(), label, context.getString(R.string.ez_days), event.cardColor, event.countColor)
    }

    fun actionIcon(context: Context, systemName: String, fallback: Int): android.graphics.drawable.Drawable {
        val systemId = context.resources.getIdentifier(systemName,"drawable","android")
        val system = if (systemId == 0) null else runCatching { context.getDrawable(systemId) }.getOrNull()
        return checkNotNull(system ?: context.getDrawable(fallback)).mutate().apply { setTint(0xFF1976D2.toInt()) }
    }
    fun message(context: Context, text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
}
