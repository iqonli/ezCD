package com.iqonli.ezcd.widget
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
class BitmapLayoutCodecTest {
    private fun defaults()=WidgetLayoutCodec.decode(listOf(File("src/main/assets/widget-layout-default.json"),File("app/src/main/assets/widget-layout-default.json")).first { it.isFile }.readText())
    private fun extended(): WidgetLayoutDocument {
        val d=defaults();return d.copy(renderer="bitmap",layouts=d.layouts.mapValues { (_,l) -> l.copy(elements=l.elements.map { it.copy(fontId="a".repeat(64)+":0",italic=true,weight=600) }) })
    }
    @Test fun oldNativeLayoutsStillExportVersionTwoAndNewFeaturesRoundTripInVersionThree() {
        val native=defaults();assertEquals("native",native.renderer);assertEquals(2,JSONObject(WidgetLayoutCodec.encode(native)).getInt("version"))
        val d=extended();assertEquals(3,JSONObject(WidgetLayoutCodec.encode(d)).getInt("version"));assertEquals(d,WidgetLayoutCodec.decode(WidgetLayoutCodec.encode(d)))
        val off=d.copy(renderer="native");assertEquals(off,WidgetLayoutCodec.decode(WidgetLayoutCodec.encode(off)))
    }
    @Test fun invalidRendererFontReferenceAndItalicTypesAreRejected() {
        val raw=WidgetLayoutCodec.encode(extended())
        val r=JSONObject(raw).put("renderer","canvas");assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(r.toString()) }
        for(pair in listOf("fontId" to "file:///not-a-font-id","italic" to "true")) {
            val root=JSONObject(raw);root.getJSONObject("layouts").getJSONObject("small").getJSONArray("elements").getJSONObject(0).put(pair.first,pair.second)
            assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(root.toString()) }
        }
    }
}
