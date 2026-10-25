// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.widget

import android.view.View
import android.widget.RemoteViews
import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.ArrivalMood
import kotlin.math.ceil

internal data class WidgetPresentation(
    val countText: String,
    val unitRes: Int?,
    val milestone: String?,
    val showUnit: Boolean,
    val showMilestone: Boolean,
    val showDate: Boolean,
    val showIcon: Boolean,
    val showTitle: Boolean,
    val titleMaxLines: Int,
)

internal data class WidgetPlaceholderPresentation(
    val showIcon: Boolean,
    val showTitle: Boolean,
    val showUnit: Boolean,
    val titleMaxLines: Int,
)

internal object WidgetPresentationResolver {
    fun placeholder(
        size: WidgetSize,
        fontScale: Float,
        heightDp: Int = WidgetPreviewSizing.representative(size).heightDp,
    ) = WidgetPlaceholderPresentation(
        showIcon = false,
        showTitle = size != WidgetSize.COMPACT || fontScale < 1.3f,
        showUnit = when (size) {
            WidgetSize.STANDARD -> fontScale < 1.3f
            WidgetSize.LARGE -> fontScale < 1.75f
            else -> false
        },
        titleMaxLines = if (size == WidgetSize.SHORT) shortTitleLines(heightDp, fontScale) else 1,
    )

    fun resolve(
        content: WidgetEventContent,
        size: WidgetSize,
        fontScale: Float,
        heightDp: Int = WidgetPreviewSizing.representative(size).heightDp,
    ): WidgetPresentation {
        val largeFont = fontScale >= LARGE_FONT_SCALE
        val milestone = content.status?.let(ArrivalMood::marker)
        val showUnit = (size == WidgetSize.STANDARD || size == WidgetSize.LARGE) && !largeFont
        val showMilestone = milestone != null &&
            size != WidgetSize.SHORT && size != WidgetSize.COMPACT && fontScale < 1.3f
        val showDate = size == WidgetSize.LARGE && fontScale < DATE_HIDE_FONT_SCALE
        val compactCountHeight = ceil(28 * fontScale).toInt()
        val compactLineHeight = ceil(14 * fontScale).toInt()
        val compactIconFits = false
        val compactTitleLines = ((heightDp - 8 - compactCountHeight -
            if (compactIconFits) 16 else 0) / compactLineHeight).coerceIn(0, 3)
        val showIcon = false /* icons disabled; interface retained */ && when (size) {
            WidgetSize.COMPACT -> compactIconFits
            WidgetSize.SHORT -> fontScale < LARGE_FONT_SCALE || content.countTextFor(WidgetSize.SHORT, false).length <= 5
            else -> fontScale < 1.3f && milestone == null
        }
        // Reserve space for the count and visible details before allowing another title line.
        // Width alone cannot tell a minimum-size widget from a narrow, tall launcher cell.
        val titleMaxLines = when (size) {
            WidgetSize.COMPACT -> compactTitleLines.coerceAtLeast(1)
            WidgetSize.SHORT -> shortTitleLines(heightDp, fontScale)
            WidgetSize.STANDARD, WidgetSize.LARGE -> {
                val isLarge = size == WidgetSize.LARGE
                val padding = if (isLarge) 28 else 20
                val count = ceil((if (isLarge) 22 else 20) * fontScale).toInt()
                val icon = if (showIcon) { if (isLarge) 35 else 25 } else 0
                // Both detail TextViews allow two lines (including localized labels/dates).
                // Reserve their full height before giving the title another line.
                val unit = if (showUnit) 2 * ceil((if (isLarge) 20 else 16) * fontScale).toInt() else 0
                val marker = if (showMilestone) ceil((if (isLarge) 17 else 14) * fontScale).toInt() else 0
                val date = if (showDate) 2 * ceil(20 * fontScale).toInt() + 4 else 0
                val line = ceil((if (isLarge) 22 else 18) * fontScale).toInt()
                ((heightDp - padding - count - icon - unit - marker - date) / line).coerceIn(1, 2)
            }
        }

        return WidgetPresentation(
            countText = content.countTextFor(size, showUnit),
            unitRes = content.unitRes,
            milestone = null,
            showUnit = showUnit,
            showMilestone = false,
            showDate = showDate,
            showIcon = false,
            showTitle = size != WidgetSize.COMPACT || compactTitleLines > 0,
            titleMaxLines = titleMaxLines,
        )
    }

    private const val LARGE_FONT_SCALE = 1.75f
    private const val DATE_HIDE_FONT_SCALE = 1.50f

    private fun shortTitleLines(heightDp: Int, fontScale: Float): Int =
        ((heightDp - 6) / ceil(14 * fontScale).toInt()).coerceIn(1, 2)
}

internal object WidgetLayoutResolver {
    fun layoutRes(size: WidgetSize): Int = when (size) {
        WidgetSize.COMPACT -> R.layout.widget_countdown_compact
        WidgetSize.SHORT -> R.layout.widget_countdown_short
        WidgetSize.STANDARD -> R.layout.widget_countdown_standard
        WidgetSize.LARGE -> R.layout.widget_countdown_large
    }
}

internal object WidgetRemoteViewsPresentation {
    fun applyPlaceholder(
        views: RemoteViews,
        size: WidgetSize,
        fontScale: Float,
        title: String,
        action: String,
        countText: String,
        heightDp: Int = WidgetPreviewSizing.representative(size).heightDp,
    ) {
        val presentation = WidgetPresentationResolver.placeholder(size, fontScale, heightDp)
        views.setImageViewResource(R.id.widgetIcon, R.drawable.ic_event_calendar)
        views.setTextViewText(R.id.widgetTitle, title)
        views.setTextViewText(R.id.widgetCount, countText)
        views.setTextViewText(R.id.widgetUnit, action)
        views.setTextViewText(R.id.widgetDate, "")
        views.setTextViewText(R.id.widgetMilestone, "")
        views.setInt(R.id.widgetTitle, "setMaxLines", presentation.titleMaxLines)
        views.setViewVisibility(R.id.widgetIcon, if (presentation.showIcon) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetTitle, if (presentation.showTitle) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetUnit, if (presentation.showUnit) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetDate, View.GONE)
        views.setViewVisibility(R.id.widgetMilestone, View.GONE)
    }

    fun applyEvent(
        context: android.content.Context,
        views: RemoteViews,
        content: WidgetEventContent,
        size: WidgetSize,
        fontScale: Float,
        heightDp: Int = WidgetPreviewSizing.representative(size).heightDp,
    ) {
        val presentation = WidgetPresentationResolver.resolve(content, size, fontScale, heightDp)
        views.setViewVisibility(R.id.widgetIcon, if (presentation.showIcon) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetTitle, if (presentation.showTitle) View.VISIBLE else View.GONE)
        views.setInt(R.id.widgetTitle, "setMaxLines", presentation.titleMaxLines)
        views.setImageViewResource(R.id.widgetIcon, content.iconRes)
        views.setTextViewText(R.id.widgetTitle, content.title)
        views.setTextViewText(R.id.widgetCount, presentation.countText)
        views.setTextViewText(R.id.widgetUnit, presentation.unitRes?.let(context::getString).orEmpty())
        views.setTextViewText(R.id.widgetDate, content.dateText(context))
        views.setTextViewText(R.id.widgetMilestone, presentation.milestone.orEmpty())
        views.setViewVisibility(R.id.widgetUnit, if (presentation.showUnit) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetMilestone, if (presentation.showMilestone) View.VISIBLE else View.GONE)
        views.setViewVisibility(R.id.widgetDate, if (presentation.showDate) View.VISIBLE else View.GONE)
        views.setContentDescription(R.id.widgetRoot, content.description(context))
    }
}
