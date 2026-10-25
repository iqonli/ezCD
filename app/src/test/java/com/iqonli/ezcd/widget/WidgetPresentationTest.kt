package com.iqonli.ezcd.widget

import com.iqonli.ezcd.R
import com.iqonli.ezcd.countdown.CountdownStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetPresentationTest {
    @Test
    fun wrappedDetailsKeepTheirSpaceBeforeTitleExpansion() {
        val event = content(CountdownStatus.FUTURE, "365")
        assertEquals(1, WidgetPresentationResolver.resolve(event, WidgetSize.STANDARD, 1.7f, 144).titleMaxLines)
        assertEquals(2, WidgetPresentationResolver.resolve(event, WidgetSize.STANDARD, 1.7f, 172).titleMaxLines)
        assertEquals(1, WidgetPresentationResolver.resolve(event, WidgetSize.LARGE, 1.4f, 180).titleMaxLines)
        assertEquals(2, WidgetPresentationResolver.resolve(event, WidgetSize.LARGE, 1.4f, 237).titleMaxLines)
    }

    @Test
    fun shortWidgetUsesOnlyTitleLinesThatFitAtLargeFontScale() {
        val event = content(CountdownStatus.FUTURE, "91")
        assertEquals(1, WidgetPresentationResolver.resolve(event, WidgetSize.SHORT, 2f, 50).titleMaxLines)
        assertEquals(2, WidgetPresentationResolver.resolve(event, WidgetSize.SHORT, 2f, 65).titleMaxLines)
        assertEquals(1, WidgetPresentationResolver.placeholder(WidgetSize.SHORT, 2f, 50).titleMaxLines)
        assertEquals(2, WidgetPresentationResolver.placeholder(WidgetSize.SHORT, 2f, 65).titleMaxLines)
    }

    @Test
    fun narrowTallWidgetUsesItsHeightForTitleWithNoIcon() {
        val result = WidgetPresentationResolver.resolve(
            content(CountdownStatus.FUTURE, "91"), WidgetSize.COMPACT, 1f, heightDp = 102,
        )
        assertEquals(3, result.titleMaxLines)
        assertTrue(result.showTitle)
        assertFalse(result.showIcon)
    }

    @Test
    fun minimumWidgetStillReservesItsCountSpace() {
        val normal = WidgetPresentationResolver.resolve(
            content(CountdownStatus.FUTURE, "91"), WidgetSize.COMPACT, 1f, heightDp = 50,
        )
        assertEquals(1, normal.titleMaxLines)
        assertFalse(normal.showIcon)
        val enlarged = WidgetPresentationResolver.resolve(
            content(CountdownStatus.FUTURE, "91"), WidgetSize.COMPACT, 2f, heightDp = 50,
        )
        assertFalse(enlarged.showTitle)
        assertFalse(enlarged.showIcon)
    }

    @Test
    fun extraHeightRestoresDetailsEvenAtLargeFontScale() {
        val result = WidgetPresentationResolver.resolve(
            content(CountdownStatus.FUTURE, "91"), WidgetSize.COMPACT, 2f, heightDp = 180,
        )
        assertEquals(3, result.titleMaxLines)
        assertTrue(result.showTitle)
        assertFalse(result.showIcon)
    }

    @Test
    fun compactElapsedKeepsMarkerAndDropsOptionalDetail() {
        val result = WidgetPresentationResolver.resolve(
            content = content(CountdownStatus.DONE, "6"),
            size = WidgetSize.COMPACT,
            fontScale = 1f,
        )

        assertEquals("6", result.countText)
        assertFalse(result.showUnit)
        assertFalse(result.showDate)
        assertFalse(result.showMilestone)
    }

    @Test
    fun arrivalArtworkDoesNotConsumeATextRowOrReduceTitleLines() {
        val event = content(CountdownStatus.TOMORROW, "1")
        val tight = WidgetPresentationResolver.resolve(event, WidgetSize.STANDARD, 1f, 100)
        assertEquals("1", tight.countText)
        assertTrue(tight.showUnit)
        assertFalse(tight.showMilestone)
        assertEquals(null, tight.milestone)
        assertFalse(tight.showDate)
        assertFalse(tight.showIcon)
        val roomy = WidgetPresentationResolver.resolve(event, WidgetSize.STANDARD, 1f, 144)
        assertFalse(roomy.showIcon)
        assertEquals(2, roomy.titleMaxLines)
        assertFalse(roomy.showMilestone)
    }

    @Test
    fun largeWidgetKeepsDateAtNormalFontScale() {
        val result = WidgetPresentationResolver.resolve(
            content = content(CountdownStatus.FUTURE, "120"),
            size = WidgetSize.LARGE,
            fontScale = 1f,
        )

        assertTrue(result.showUnit)
        assertTrue(result.showDate)
        assertFalse(result.showMilestone)
    }

    @Test
    fun twoHundredPercentFontPrioritizesEssentialCount() {
        listOf(WidgetSize.STANDARD, WidgetSize.LARGE).forEach { size ->
            val result = WidgetPresentationResolver.resolve(
                content = content(CountdownStatus.TODAY, "0"),
                size = size,
                fontScale = 2f,
            )

            assertEquals("0", result.countText)
            assertFalse(result.showUnit)
            assertFalse(result.showDate)
            assertFalse(result.showMilestone)
        }
    }

    @Test
    fun shortWidgetKeepsOnlyEssentialHorizontalContent() {
        val result = WidgetPresentationResolver.resolve(
            content = content(CountdownStatus.TWO_DAYS, "2"),
            size = WidgetSize.SHORT,
            fontScale = 1f,
        )

        assertFalse(result.showUnit)
        assertFalse(result.showDate)
        assertFalse(result.showMilestone)
    }

    private fun content(status: CountdownStatus, count: String) = WidgetEventContent(
        iconRes = R.drawable.ic_event_calendar,
        title = "A deliberately long countdown title for preview parity",
        date = LocalDate.of(2026, 12, 31),
        countText = count,
        unitRes = R.string.widget_days_left,
        status = status,
    )
}
