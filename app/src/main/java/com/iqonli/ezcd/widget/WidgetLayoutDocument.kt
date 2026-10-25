// Copyright (c) 2026 IQ Online Studio. MIT.
package com.iqonli.ezcd.widget

import com.iqonli.ezcd.model.OpaqueColor
import org.json.JSONArray
import org.json.JSONObject

/** Percentages refer to the actual widget bounds; all boxes remain horizontally centered. */
data class WidgetTextElement(
    val id: String, val source: String, val visible: Boolean = true, val content: String = "",
    val weight: Int = 400, val color: String = "auto", val top: Double = 0.0,
    val width: Double = 90.0, val height: Double = 20.0, val alignment: String = "center", val maxSize: Double = 24.0,
    val fontId: String = "", val italic: Boolean = false,
)
data class WidgetSizeLayout(val background: String = "auto", val elements: List<WidgetTextElement>)
data class WidgetLayoutDocument(val layouts: Map<String, WidgetSizeLayout>, val renderer: String = "native")

enum class WidgetLayoutSize(val key: String, val widthDp: Int, val heightDp: Int) {
    SMALL("small",84,84), MEDIUM("medium",160,100), LARGE("large",240,160);
    companion object {
        fun fromDimensions(width: Int, height: Int): WidgetLayoutSize = when {
            width < 110 || height < 70 -> SMALL
            width >= 180 && height >= 120 -> LARGE
            else -> MEDIUM
        }
    }
}

object WidgetLayoutCodec {
    const val MAX_BYTES = 1024 * 1024
    val builtins = listOf("title","count","status","date")
    val alignments = listOf("top_left","top_center","top_right","center_left","center","center_right","bottom_left","bottom_center","bottom_right")
    private val elementKeys = setOf("id","source","visible","content","weight","color","top","width","height","alignment")
    fun decode(text: String): WidgetLayoutDocument {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Layout exceeds 1 MiB" }
        StrictLayoutJson.validate(text)
        val root = JSONObject(text)
        val version = root.getInt("version")
        keys(root,setOf("format","version","layouts") + if(version==3) setOf("renderer") else emptySet(),"root")
        require(string(root,"format") == "ezcd-widget-layout") { "Unknown format" }
        require(root.get("version") is Int && root.getInt("version") in 1..3) { "Unsupported version" }
        val renderer=if(version==3) string(root,"renderer").also { require(it in setOf("native","bitmap")) } else "native"
        val sizes = root.getJSONObject("layouts")
        keys(sizes,WidgetLayoutSize.entries.map { it.key }.toSet(),"layouts")
        return WidgetLayoutDocument(WidgetLayoutSize.entries.associate { size ->
            val node = sizes.getJSONObject(size.key)
            keys(node,setOf("background","elements"),size.key)
            val background = string(node,"background"); color(background)
            val array = node.getJSONArray("elements")
            val elements = (0 until array.length()).map { index ->
                val path = "${size.key}.elements[$index]"
                val e = array.getJSONObject(index); keys(e,if (version == 1) elementKeys else elementKeys + "maxSize" + if(version==3) setOf("fontId","italic") else emptySet(),path)
                val id = string(e,"id"); val source = string(e,"source")
                require(id.isNotBlank()) { "$path: empty id" }
                require(source in builtins || source == "custom") { "$path: unknown source" }
                require(source == "custom" && id !in builtins || id == source) { "$path: builtin id/source mismatch" }
                val visible = e.get("visible"); require(visible is Boolean) { "$path: visible must be boolean" }
                val content = string(e,"content")
                require(source == "custom" || content.isEmpty()) { "$path: builtin content must be empty" }
                val weight = e.get("weight"); require(weight is Int && weight in 1..1000) { "$path: weight must be an integer from 1 to 1000" }
                val color = string(e,"color"); color(color)
                val top = number(e,"top",0.0,if (version == 1) 100.0 else 200.0)
                val width = number(e,"width",0.1,100.0)
                val height = number(e,"height",0.1,100.0)
                val alignment = string(e,"alignment"); require(alignment in alignments) { "$path: unknown alignment" }
                WidgetTextElement(id,source,visible,content,weight,color,if (version == 1) top + height/2 else top,width,height,alignment,
                    if (version >= 2) number(e,"maxSize",1.0,512.0) else when(source) { "count" -> 48.0; "title" -> 18.0; "custom" -> 24.0; else -> 14.0 },
                    if(version==3) string(e,"fontId").also { require(it.isEmpty() || it.matches(Regex("[a-f0-9]{64}:[0-9]{1,3}"))) } else "",
                    if(version==3) e.get("italic").also { require(it is Boolean) } as Boolean else false)
            }
            require(elements.map { it.id }.distinct().size == elements.size) { "${size.key}: duplicate text id" }
            require(builtins.all { name -> elements.count { it.source == name } == 1 }) { "${size.key}: title/count/status/date are required exactly once" }
            size.key to WidgetSizeLayout(background,elements)
        },renderer)
    }
    fun encode(document: WidgetLayoutDocument): String {
        val extended=document.renderer!="native" || document.layouts.values.any { it.elements.any { e -> e.fontId.isNotEmpty() || e.italic } }
        val layouts = JSONObject()
        WidgetLayoutSize.entries.forEach { size ->
            val layout = document.layouts.getValue(size.key)
            layouts.put(size.key,JSONObject().put("background",layout.background).put("elements",JSONArray().apply {
                layout.elements.forEach { e -> put(JSONObject().put("id",e.id).put("source",e.source).put("visible",e.visible)
                    .put("content",e.content).put("weight",e.weight).put("color",e.color).put("top",e.top)
                    .put("width",e.width).put("height",e.height).put("alignment",e.alignment).put("maxSize",e.maxSize).apply { if(extended) { put("fontId",e.fontId);put("italic",e.italic) } }) }
            }))
        }
        return JSONObject().put("format","ezcd-widget-layout").put("version",if(extended) 3 else 2).put("layouts",layouts).apply { if(extended) put("renderer",document.renderer) }.toString(2)
    }
    private fun keys(obj: JSONObject, allowed: Set<String>, path: String) {
        val actual = obj.keys().asSequence().toSet()
        require(actual == allowed) { "$path: missing or unknown fields: ${(actual - allowed) + (allowed - actual)}" }
    }
    private fun string(obj: JSONObject, key: String): String {
        val value = obj.get(key); require(value is String) { "$key must be a string" }; return value
    }
    private fun number(obj: JSONObject,key: String,min: Double,max: Double): Double {
        val value = obj.get(key); require(value is Number) { "$key must be numeric" }
        val n = value.toDouble(); require(n.isFinite() && n in min..max) { "$key must be $min..$max" }; return n
    }
    private fun color(value: String) { require(value in setOf("auto","default") || value.startsWith("#") && WidgetColor.parse(value) != null) { "Color must be auto, default, #RRGGBB, or #RRGGBBAA" } }
}

/** Android's JSONTokener accepts non-JSON syntax; reject it before platform decoding. */
internal object StrictLayoutJson {
    fun validate(text: String) = Parser(text).parse()
    private class Parser(private val text: String) {
        private var i = 0
        private fun fail(): Nothing = throw IllegalArgumentException("Invalid JSON at character ${i + 1}")
        private fun space() { while (i < text.length && text[i] in " \t\r\n") i++ }
        fun parse() { value(0); space(); if (i != text.length) fail() }
        private fun value(depth: Int) {
            if (depth > 32) fail()
            space(); if (i >= text.length) fail()
            when (text[i]) {
                '{' -> {
                    i++; space(); val seen = HashSet<String>()
                    if (take('}')) return
                    while (true) {
                        space(); if (i >= text.length || text[i] != '"') fail()
                        val key = string(); if (!seen.add(key)) fail()
                        space(); if (!take(':')) fail(); value(depth+1); space()
                        if (take('}')) return
                        if (!take(',')) fail()
                    }
                }
                '[' -> {
                    i++; space(); if (take(']')) return
                    while (true) { value(depth+1); space(); if (take(']')) return; if (!take(',')) fail() }
                }
                '"' -> string()
                't' -> literal("true")
                'f' -> literal("false")
                'n' -> literal("null")
                else -> number()
            }
        }
        private fun take(c: Char): Boolean = if (i < text.length && text[i] == c) { i++; true } else false
        private fun literal(s: String) { if (!text.startsWith(s,i)) fail(); i += s.length }
        private fun string(): String {
            val start = i; if (!take('"')) fail()
            while (i < text.length) {
                val c = text[i++]
                if (c == '"') return org.json.JSONTokener(text.substring(start,i)).nextValue() as String
                if (c < ' ') fail()
                if (c == '\\') {
                    if (i >= text.length) fail()
                    when (text[i++]) {
                        '"','\\','/','b','f','n','r','t' -> Unit
                        'u' -> { repeat(4) { if (i >= text.length || text[i++] !in "0123456789abcdefABCDEF") fail() } }
                        else -> fail()
                    }
                }
            }
            fail()
        }
        private fun number() {
            val start = i; take('-')
            if (!take('0')) { if (i >= text.length || text[i] !in '1'..'9') fail(); while (i < text.length && text[i] in '0'..'9') i++ }
            if (take('.')) { val j=i; while (i < text.length && text[i] in '0'..'9') i++; if (i==j) fail() }
            if (i < text.length && text[i] in "eE") { i++; if (i < text.length && text[i] in "+-") i++; val j=i; while (i < text.length && text[i] in '0'..'9') i++; if (j==i) fail() }
            if (i == start) fail()
        }
    }
}
