package com.iqonli.ezcd.widget
import org.junit.Assert.*
import org.junit.Test

class WidgetPinSizeTest {
    @Test fun gridParsingRejectsInvalidOrUnreportedLimits() {
        assertEquals(DesktopGrid(4,6),DesktopGrid.parse("4x6"));assertEquals(DesktopGrid(5,7),DesktopGrid.parse(" 5 × 7 "))
        for(value in listOf(null,"","0x6","4x0","4x999","-1x5","1080x2400","4x6suffix")) assertNull(DesktopGrid.parse(value))
    }
    @Test fun sizeIsPreservedByCallbackAndOldCallbacksDefaultToOneCell() {
        val style=WidgetStyleSelection(WidgetAppearance.LIGHT,WidgetBackground.CLASSIC)
        val before=WidgetPinRequestSnapshot.create("event",style,"token")
        assertEquals(WidgetPinSize(),WidgetPinRequestSnapshot.fromCallbackData(before.callbackData())!!.size)
        val sized=WidgetPinRequestSnapshot.create("event",style,"token",WidgetPinSize(4,6))
        assertEquals(sized,WidgetPinRequestSnapshot.fromCallbackData(sized.callbackData()))
        assertNull(WidgetPinRequestSnapshot.fromCallbackData(before.callbackData()+"&columns=0&rows=4"))
        assertNull(WidgetPinRequestSnapshot.fromCallbackData(before.callbackData()+"&columns=no&rows=4"))
        assertThrows(IllegalArgumentException::class.java) { WidgetPinSize(17,1) }
    }
}
