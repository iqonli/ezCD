// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.iqonli.ezcd.R

internal class WidgetPreviewController(
    private val context: Context,
    private val container: FrameLayout,
    private val frame: FrameLayout,
) {
    private var currentSize: WidgetSize? = null
    private var currentHeightDp: Int = 0
    private var currentStyle: WidgetStyleSelection? = null
    private var currentDimensions: WidgetPreviewDimensions? = null
    private var currentContent: WidgetEventContent? = null
    private lateinit var backgroundView: ImageView
    private lateinit var iconView: ImageView
    private lateinit var titleView: TextView
    private lateinit var milestoneView: TextView
    private lateinit var countView: TextView
    private lateinit var unitView: TextView
    private lateinit var dateView: TextView

    fun renderStyle(selection: WidgetStyleSelection, dimensions: WidgetPreviewDimensions) {
        currentHeightDp = dimensions.heightDp
        currentStyle = selection
        currentDimensions = dimensions
        ensureLayout(dimensions.size)
        applyFrame(dimensions)
        val theme = WidgetThemeResolver.resolve(context, selection.appearance, selection.background)
        renderBackground()
        iconView.setColorFilter(theme.accentTextColor)
        titleView.setTextColor(theme.primaryTextColor)
        countView.setTextColor(theme.accentTextColor)
        milestoneView.setTextColor(theme.secondaryTextColor)
        unitView.setTextColor(theme.secondaryTextColor)
        dateView.setTextColor(theme.secondaryTextColor)
        currentContent?.let(::renderEvent)
    }

    private fun renderBackground() {
        val style = currentStyle ?: return
        val dimensions = currentDimensions ?: return
        val theme = WidgetThemeResolver.resolve(context, style.appearance, style.background)
        backgroundView.setImageBitmap(WidgetBackgroundRenderer.render(context.applicationContext,
            style.background, theme.dark, dimensions.widthDp, dimensions.heightDp))
    }

    fun renderEvent(content: WidgetEventContent) {
        currentContent = content
        val presentation = WidgetPresentationResolver.resolve(content, requireNotNull(currentSize),
            context.resources.configuration.fontScale, currentHeightDp)
        iconView.visibility = if (presentation.showIcon) View.VISIBLE else View.GONE
        titleView.visibility = if (presentation.showTitle) View.VISIBLE else View.GONE
        titleView.maxLines = presentation.titleMaxLines
        iconView.setImageResource(content.iconRes)
        titleView.text = content.title
        countView.text = presentation.countText
        unitView.text = presentation.unitRes?.let(context::getString).orEmpty()
        milestoneView.text = presentation.milestone.orEmpty()
        dateView.text = content.dateText(context)
        milestoneView.visibility = if (presentation.showMilestone) View.VISIBLE else View.GONE
        unitView.visibility = if (presentation.showUnit) View.VISIBLE else View.GONE
        dateView.visibility = if (presentation.showDate) View.VISIBLE else View.GONE
        frame.findViewById<View>(R.id.widgetRoot).contentDescription = content.description(context)
    }

    fun renderPlaceholder(
        title: String,
        unit: String,
        countText: String = context.getString(R.string.widget_preview_sample_count),
        iconRes: Int = R.drawable.ic_event_calendar,
    ) {
        currentContent = null
        val presentation = WidgetPresentationResolver.placeholder(requireNotNull(currentSize),
            context.resources.configuration.fontScale, currentHeightDp)
        iconView.visibility = if (presentation.showIcon) View.VISIBLE else View.GONE
        titleView.visibility = if (presentation.showTitle) View.VISIBLE else View.GONE
        titleView.maxLines = presentation.titleMaxLines
        iconView.setImageResource(iconRes)
        titleView.text = title
        countView.text = countText
        unitView.text = unit
        milestoneView.text = ""
        dateView.text = ""
        milestoneView.visibility = View.GONE
        dateView.visibility = View.GONE
        unitView.visibility = if (presentation.showUnit) View.VISIBLE else View.GONE
        frame.findViewById<View>(R.id.widgetRoot).contentDescription = "$title, $unit"
    }

    private fun ensureLayout(size: WidgetSize) {
        if (currentSize == size && frame.childCount > 0) return
        frame.removeAllViews()
        LayoutInflater.from(context).inflate(WidgetLayoutResolver.layoutRes(size), frame, true)
        backgroundView = frame.findViewById(R.id.widgetBackground)
        iconView = frame.findViewById(R.id.widgetIcon)
        titleView = frame.findViewById(R.id.widgetTitle)
        milestoneView = frame.findViewById(R.id.widgetMilestone)
        countView = frame.findViewById(R.id.widgetCount)
        if (size != WidgetSize.SHORT) {
            // The attached preview resizes after inflation. Give autosizing the final
            // row width instead of retaining a wrap-content width from its small frame.
            countView.layoutParams = countView.layoutParams.apply {
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
            }
        }
        unitView = frame.findViewById(R.id.widgetUnit)
        dateView = frame.findViewById(R.id.widgetDate)
        currentSize = size
    }

    private fun applyFrame(dimensions: WidgetPreviewDimensions) {
        container.post {
            val fitted = WidgetPreviewSizing.fit(container.width, container.height, dimensions)
            frame.layoutParams = FrameLayout.LayoutParams(fitted.width, fitted.height, Gravity.CENTER)
        }
    }
}
