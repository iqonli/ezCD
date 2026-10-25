package com.iqonli.ezcd.model

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToInt

class OpaqueColorTest {
    @Test fun rgbAndHexPreserveAllThreeChannelsWithoutAlpha() {
        assertEquals(0xFF2196F3.toInt(),OpaqueColor.parseHex("2196f3"))
        assertEquals("2196F3",OpaqueColor.hex(OpaqueColor.BLUE))
        assertEquals(-1,OpaqueColor.parseHex("FFFFFF"))
        assertArrayEquals(intArrayOf(1,128,255),OpaqueColor.channels(OpaqueColor.rgb(1,128,255)))
        for (bad in listOf("#2196F3","2196F3FF","123","GGGGGG","", "000000000")) assertNull(bad,OpaqueColor.parseHex(bad))
    }
    @Test fun inputFiltersSwallowSymbolsWithoutATextLengthLimit() {
        assertEquals("1234",OpaqueColor.digits("-1.2 +3,4"))
        assertEquals("ABCDEF012345",OpaqueColor.hexDigits("#ab:cd-ef 01.23,45"))
        val longText = "9".repeat(10_000)
        assertEquals(longText,OpaqueColor.digits(longText))
        assertEquals(longText,OpaqueColor.hexDigits(longText))
        assertNull(longText.toIntOrNull())
    }
    @Test fun hslPrimariesGrayscaleAndHueWrapAreCorrect() {
        assertEquals(0xFFFF0000.toInt(),OpaqueColor.fromHsl(0,100,50))
        assertEquals(0xFF00FF00.toInt(),OpaqueColor.fromHsl(120,100,50))
        assertEquals(0xFF0000FF.toInt(),OpaqueColor.fromHsl(240,100,50))
        assertEquals(OpaqueColor.fromHsl(0,100,50),OpaqueColor.fromHsl(360,100,50))
        assertEquals(0xFF808080.toInt(),OpaqueColor.fromHsl(200,0,50))
        assertEquals(0xFF000000.toInt(),OpaqueColor.fromHsl(100,100,0))
        assertEquals(-1,OpaqueColor.fromHsl(100,100,100))
    }
    @Test fun integerHslRoundTripKeepsRgbWithinQuantizationError() {
        for (r in 0..255 step 17) for (g in 0..255 step 17) for (b in 0..255 step 17) {
            val original = OpaqueColor.rgb(r,g,b); val hsl = OpaqueColor.hsl(original)
            val restored = OpaqueColor.channels(OpaqueColor.fromHsl(hsl[0].roundToInt(),hsl[1].roundToInt(),hsl[2].roundToInt()))
            assertTrue("$r,$g,$b",abs(restored[0]-r) <= 5 && abs(restored[1]-g) <= 5 && abs(restored[2]-b) <= 5)
        }
    }
    @Test fun foregroundContrastsWithBothLightAndDarkCards() {
        assertEquals(0xFF000000.toInt(),OpaqueColor.foreground(0xFFFFFF00.toInt()))
        assertEquals(-1,OpaqueColor.foreground(0xFF003399.toInt()))
        assertEquals(-1,OpaqueColor.foreground(0xFF000000.toInt()))
        assertEquals(0xFF000000.toInt(),OpaqueColor.foreground(-1))
    }
    @Test fun invalidChannelRangesAreRejectedInsteadOfSilentlyClamped() {
        assertThrows(IllegalArgumentException::class.java) { OpaqueColor.rgb(256,0,0) }
        assertThrows(IllegalArgumentException::class.java) { OpaqueColor.fromHsl(361,0,0) }
        assertThrows(IllegalArgumentException::class.java) { OpaqueColor.fromHsl(0,101,0) }
    }
}
