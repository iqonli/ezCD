package com.iqonli.ezcd.widget

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WidgetLayoutCodecTest {
    private fun raw(): String = listOf(File("src/main/assets/widget-layout-default.json"),File("app/src/main/assets/widget-layout-default.json")).first { it.isFile }.readText()
    private fun root()=JSONObject(raw())
    private fun element(root: JSONObject)=root.getJSONObject("layouts").getJSONObject("small").getJSONArray("elements").getJSONObject(0)
    @Test fun actualDefaultAssetRoundTripsAllThreeSizes() {
        val document=WidgetLayoutCodec.decode(raw())
        assertEquals(setOf("small","medium","large"),document.layouts.keys)
        assertEquals(document,WidgetLayoutCodec.decode(WidgetLayoutCodec.encode(document)))
        assertEquals(listOf("title","count","status","date"),document.layouts.getValue("small").elements.map { it.source })
    }
    @Test fun legacyTopEdgeMigratesToCenterAndExportsVersionTwo() {
        val root=root().put("version",1)
        val layouts=root.getJSONObject("layouts")
        layouts.keys().forEach { size ->
            val a=layouts.getJSONObject(size).getJSONArray("elements")
            for (i in 0 until a.length()) a.getJSONObject(i).remove("maxSize")
        }
        element(root).put("top",10).put("height",30)
        val decoded=WidgetLayoutCodec.decode(root.toString())
        assertEquals(25.0,decoded.layouts.getValue("small").elements.first().top,0.0)
        assertEquals(2,JSONObject(WidgetLayoutCodec.encode(decoded)).getInt("version"))
    }
    @Test fun fontCapsCannotBeMissingOrInvalidAndDefaultsLeaveMargins() {
        for (bad in listOf(0,513,"24")) {
            val root=root(); element(root).put("maxSize",bad)
            assertThrows(Exception::class.java) { WidgetLayoutCodec.decode(root.toString()) }
        }
        val doc=WidgetLayoutCodec.decode(raw())
        doc.layouts.values.forEach { l -> l.elements.filter { it.visible }.forEach {
            assertTrue(it.top-it.height/2 > 0)
            assertTrue(it.top+it.height/2 < 100)
            assertTrue(it.maxSize >= 12 && it.maxSize <= 48)
        } }
    }
    @Test fun overlapIsValidAndPreservesPaintOrder() {
        val root=root(); val array=root.getJSONObject("layouts").getJSONObject("small").getJSONArray("elements")
        for (i in 0 until array.length()) array.getJSONObject(i).put("top",25).put("height",60)
        val document=WidgetLayoutCodec.decode(root.toString())
        assertEquals(listOf("title","count","status","date"),document.layouts.getValue("small").elements.map { it.id })
        assertTrue(document.layouts.getValue("small").elements.all { it.top == 25.0 })
    }
    @Test fun customTextsAreNotLimitedToFourAndUnicodeContentSurvives() {
        val root=root(); val array=root.getJSONObject("layouts").getJSONObject("large").getJSONArray("elements")
        repeat(600) { n -> array.put(JSONObject(array.getJSONObject(0).toString()).put("id","custom_$n").put("source","custom").put("content","自定义\n${n} 🐈")) }
        val doc=WidgetLayoutCodec.decode(root.toString())
        assertEquals(604,doc.layouts.getValue("large").elements.size)
        assertEquals(doc,WidgetLayoutCodec.decode(WidgetLayoutCodec.encode(doc)))
    }
    @Test fun builtinCannotBeRemovedOrConvertedToCustom() {
        val root=root(); root.getJSONObject("layouts").getJSONObject("small").getJSONArray("elements").remove(0)
        assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(root.toString()) }
        val converted=root(); element(converted).put("source","custom")
        assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(converted.toString()) }
    }
    @Test fun duplicateIdsAndBuiltinOverridesAreRejected() {
        val root=root(); val array=root.getJSONObject("layouts").getJSONObject("small").getJSONArray("elements")
        array.put(JSONObject(array.getJSONObject(0).toString()))
        assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(root.toString()) }
        val content=root(); element(content).put("content","override")
        assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(content.toString()) }
    }
    @Test fun allNineAlignmentsAndPreciseFontWeightsAreAccepted() {
        WidgetLayoutCodec.alignments.forEach { alignment ->
            val root=root(); element(root).put("alignment",alignment).put("weight",523)
            assertEquals(523,WidgetLayoutCodec.decode(root.toString()).layouts.getValue("small").elements.first().weight)
        }
    }
    @Test fun invalidPropertyTypesAndRangesAreRejectedAtomically() {
        for ((key,value) in listOf("visible" to "true","top" to -1,"top" to 201,"width" to 0,"height" to 101,
            "weight" to 0,"weight" to 1001,"weight" to 500.5,"color" to "#12345","alignment" to "diagonal","source" to "unknown")) {
            val root=root(); element(root).put(key,value)
            assertThrows(Exception::class.java) { WidgetLayoutCodec.decode(root.toString()) }
        }
    }
    @Test fun unknownOrMissingFieldsAndVersionsAreRejected() {
        val root=root().put("version",3)
        assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(root.toString()) }
        val unknown=root(); element(unknown).put("x",1)
        assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(unknown.toString()) }
        val missing=root(); element(missing).remove("height")
        assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(missing.toString()) }
    }
    @Test fun platformJsonExtensionsAreNotAccepted() {
        for (bad in listOf("{'a':1}","{a:1}","{\"a\":1,}","[1,]","{} trailing","{\"a\":1,\"a\":2}","{\"a\":1,\"\\u0061\":2}","{\"a\":01}","{\"a\":NaN}","{\"a\":+1}","[.5]","[1.]","[1e]","[/*x*/1]","{\"a\":\"\n\"}"))
            assertThrows(IllegalArgumentException::class.java) { StrictLayoutJson.validate(bad) }
    }
    @Test fun validStrictJsonEscapesAndNumbersAreAccepted() {
        StrictLayoutJson.validate(" {\"a\":[true,false,null,-1.2e+3,0,\"\\u0061\\n\\\"\\\\\"]} \n")
    }
    @Test fun oversizedAndDeepPayloadsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { WidgetLayoutCodec.decode(" ".repeat(WidgetLayoutCodec.MAX_BYTES+1)) }
        assertThrows(IllegalArgumentException::class.java) { StrictLayoutJson.validate("[".repeat(40)+"0"+"]".repeat(40)) }
    }
    @Test fun rgbaAndRgbColorsRoundTripAcrossAllAlphaBytes() {
        assertEquals(0xFF112233.toInt(),WidgetColor.parse("#112233"))
        assertEquals(0x80112233.toInt(),WidgetColor.parse("#11223380"))
        assertEquals(0x00112233,WidgetColor.parse("11223300"))
        repeat(256) { alpha ->
            val argb=(alpha shl 24) or 0x1234AB
            assertEquals(argb,WidgetColor.parse(WidgetColor.hex(argb)))
        }
        assertNull(WidgetColor.parse("#123456789")); assertNull(WidgetColor.parse("#ZZ0000"))
        val root=root(); element(root).put("color","#1234567F")
        root.getJSONObject("layouts").getJSONObject("small").put("background","#FFFFFF00")
        assertEquals("#FFFFFF00",WidgetLayoutCodec.decode(root.toString()).layouts.getValue("small").background)
    }
    @Test fun adaptiveSizesUseExactlyThreeIndependentClasses() {
        assertEquals(WidgetLayoutSize.SMALL,WidgetLayoutSize.fromDimensions(56,50))
        assertEquals(WidgetLayoutSize.SMALL,WidgetLayoutSize.fromDimensions(220,50))
        assertEquals(WidgetLayoutSize.MEDIUM,WidgetLayoutSize.fromDimensions(160,100))
        assertEquals(WidgetLayoutSize.LARGE,WidgetLayoutSize.fromDimensions(240,160))
    }
}
