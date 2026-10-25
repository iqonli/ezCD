// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.share

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownStatus
import com.iqonli.ezcd.countdown.EventCountResolver
import com.iqonli.ezcd.model.CountMode
import com.iqonli.ezcd.model.EventIcon
import com.iqonli.ezcd.model.RepeatRule
import com.iqonli.ezcd.ui.EventCountText
import com.iqonli.ezcd.ui.EventIconPresentation
import com.iqonli.ezcd.ui.ThemeManager
import com.iqonli.ezcd.widget.WidgetBackground
import com.iqonli.ezcd.widget.WidgetBackgroundRenderer
import com.iqonli.ezcd.widget.WidgetPaletteResolver
import java.time.LocalDate
import java.time.format.FormatStyle

internal data class ShareCardContent(
    val iconRes: Int,
    val title: String,
    val primaryText: String,
    val secondaryText: String?,
    val dateText: String,
    val recurrenceText: String?,
    val date: LocalDate,
)

internal object ShareCardContentFactory {
    fun create(
        context: Context,
        title: String,
        date: LocalDate,
        icon: EventIcon,
        repeatRule: RepeatRule,
        today: LocalDate,
        countMode: CountMode = CountMode.COUNT_DOWN,
    ): ShareCardContent {
        val value = EventCountResolver.resolve(date, repeatRule, countMode, today)
        val primaryText = value.magnitude.toString()
        val secondaryText = when (value.countdownStatus) {
            CountdownStatus.TODAY -> context.getString(R.string.status_today)
            CountdownStatus.DONE -> context.getString(if (value.magnitude == 1L) R.string.widget_day_ago else R.string.widget_days_ago)
            else -> context.getString(R.string.widget_days_left)
        }
        return ShareCardContent(
            iconRes = EventIconPresentation.drawableRes(icon),
            title = title,
            primaryText = primaryText,
            secondaryText = com.iqonli.ezcd.ui.TextPool(context).get(when(value.countdownStatus) {
                CountdownStatus.TODAY -> "today";CountdownStatus.DONE -> "passed";else -> "left"
            }).ifEmpty { secondaryText },
            dateText = EventCountText.date(context, value.displayDate, countMode, FormatStyle.LONG),
            recurrenceText = repeatLabelRes(repeatRule)?.let(context::getString),
            date = value.displayDate,
        )
    }

    fun text(context: Context, title: String, date: LocalDate, repeatRule: RepeatRule,
        today: LocalDate, countMode: CountMode = CountMode.COUNT_DOWN): String {
        val value = EventCountResolver.resolve(date, repeatRule, countMode, today)
        return context.getString(R.string.share_countdown_format, title,
            EventCountText.status(context, value), EventCountText.date(context, value.displayDate, countMode, FormatStyle.LONG))
    }

    private fun repeatLabelRes(repeatRule: RepeatRule): Int? = when (repeatRule) {
        RepeatRule.NONE -> null
        RepeatRule.WEEKLY -> R.string.repeat_weekly
        RepeatRule.MONTHLY -> R.string.repeat_monthly
        RepeatRule.YEARLY -> R.string.repeat_yearly
    }
}

internal object ShareCardRenderer {
    private const val DESIGN_SIZE_PX = 1080f
    const val SIZE_PX = 768

    /** Freeze the effective Activity configuration without retaining the Activity. */
    fun captureContext(context: Context): Context = context.applicationContext
        .createConfigurationContext(Configuration(context.resources.configuration))

    fun render(context: Context, content: ShareCardContent, dark: Boolean): Bitmap {
        val bitmap = WidgetBackgroundRenderer.renderShareRidge(dark = dark, sizePx = SIZE_PX)
        val canvas = Canvas(bitmap)
        val palette = WidgetPaletteResolver.resolve(WidgetBackground.MONOGRAM, dark)
        val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        val bold = Typeface.create("sans-serif", Typeface.BOLD)
        val scale = SIZE_PX / DESIGN_SIZE_PX
        canvas.save()
        canvas.scale(scale, scale)
        drawBrandWordmark(context, canvas)
        drawIcon(context, canvas, content.iconRes, palette.accentTextColor, 882, 54, 122)
        val titlePaint = textPaint(palette.primaryTextColor, 70f, medium)
        drawTwoLineText(canvas, content.title, titlePaint, 76f, 226f, 900f, 82f)
        val primaryMaxSize = if (content.secondaryText == null) 166f else 270f
        val primaryPaint = textPaint(palette.accentTextColor, primaryMaxSize, bold)
        fitText(primaryPaint, content.primaryText, 900f, minSize = 92f)
        canvas.drawText(content.primaryText, 76f, 590f, primaryPaint)
        content.secondaryText?.let { secondary ->
            val secondaryPaint = textPaint(palette.secondaryTextColor, 58f, medium)
            fitText(secondaryPaint, secondary, 900f, minSize = 32f)
            canvas.drawText(secondary, 84f, 666f, secondaryPaint)
        }
        val datePaint = textPaint(palette.primaryTextColor, 50f, medium)
        canvas.drawText(ellipsize(content.dateText, datePaint, 900f), 76f, 770f, datePaint)
        content.recurrenceText?.let { recurrence ->
            val recurrencePaint = textPaint(palette.secondaryTextColor, 39f, medium)
            canvas.drawText(ellipsize(recurrence, recurrencePaint, 760f), 78f, 828f, recurrencePaint)
        }
        canvas.restore()
        return bitmap
    }

    fun resolveDark(context: Context): Boolean {
        val systemDark = context.applicationContext.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return when (ThemeManager.currentTheme(context)) {
            ThemeManager.AppTheme.SYSTEM -> systemDark
            ThemeManager.AppTheme.LIGHT -> false
            ThemeManager.AppTheme.DARK -> true
        }
    }

    private fun drawBrandWordmark(context: Context, canvas: Canvas) {
        val drawable = context.getDrawable(R.drawable.brand_logo)?.mutate() ?: return
        drawable.setBounds(28, 0, 348, 200)
        drawable.draw(canvas)
    }

    private fun drawIcon(context: Context, canvas: Canvas, iconRes: Int, tint: Int?, left: Int, top: Int, size: Int) {
        val drawable = context.getDrawable(iconRes)?.mutate() ?: return
        tint?.let(drawable::setTint)
        drawable.setBounds(left, top, left + size, top + size)
        drawable.draw(canvas)
    }

    private fun drawTwoLineText(canvas: Canvas, text: String, paint: TextPaint, x: Float,
        firstBaseline: Float, maxWidth: Float, lineHeight: Float) {
        if (!text.contains('\n') && !text.contains('\r') &&
            !java.text.Bidi.requiresBidi(text.toCharArray(), 0, text.length) &&
            paint.measureText(text) <= maxWidth) {
            canvas.drawText(text, x, firstBaseline, paint)
            return
        }
        val layout = titleLayout(text, paint, maxWidth.toInt(), lineHeight)
        canvas.save()
        canvas.translate(x, firstBaseline - layout.getLineBaseline(0))
        layout.draw(canvas)
        canvas.restore()
    }

    internal fun titleLayout(text: String, paint: TextPaint, width: Int, lineHeight: Float): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_LTR)
            .setIncludePad(false)
            .setLineSpacing(lineHeight - paint.fontSpacing, 1f)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setMaxLines(2)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setEllipsizedWidth(width)
            .build()

    private fun fitText(paint: TextPaint, text: String, maxWidth: Float, minSize: Float) {
        while (paint.textSize > minSize && paint.measureText(text) > maxWidth) paint.textSize -= 4f
    }

    private fun ellipsize(text: String, paint: TextPaint, maxWidth: Float): String =
        TextUtils.ellipsize(text, paint, maxWidth, TextUtils.TruncateAt.END).toString()

    private fun textPaint(color: Int, size: Float, typeface: Typeface): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = size
        this.typeface = typeface
    }
}
