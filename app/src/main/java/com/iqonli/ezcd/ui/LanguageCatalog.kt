// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.content.Context
import com.iqonli.ezcd.R
import org.xmlpull.v1.XmlPullParser

internal data class SupportedLanguage(
    val tag: String,
    val qualifier: String,
    val endonym: String,
    val iconRes: Int,
    val fdroidLocale: String,
    val playLocale: String,
)

internal data class SupportedLanguageCatalog(
    val defaultTag: String,
    val languages: List<SupportedLanguage>,
) {
    val tags: List<String> get() = languages.map(SupportedLanguage::tag)
    fun language(tag: String): SupportedLanguage? = languages.firstOrNull { it.tag == tag }
}

internal object LanguageCatalog {
    fun load(context: Context): SupportedLanguageCatalog {
        val parser = context.resources.getXml(R.xml.supported_languages)
        var defaultTag = ""
        val languages = mutableListOf<SupportedLanguage>()
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            if (parser.eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "languages" -> defaultTag = parser.getAttributeValue(null, "defaultTag").orEmpty()
                    "language" -> languages += SupportedLanguage(
                        tag = parser.required("tag"),
                        qualifier = parser.required("qualifier"),
                        endonym = parser.required("endonym"),
                        iconRes = parser.getAttributeResourceValue(null, "icon", R.drawable.ic_language),
                        fdroidLocale = parser.required("fdroidLocale"),
                        playLocale = parser.required("playLocale"),
                    )
                }
            }
            parser.next()
        }
        check(languages.isNotEmpty()) { "supported_languages.xml has no enabled languages" }
        check(languages.map { it.tag }.distinct().size == languages.size) { "Duplicate language tag" }
        check(defaultTag in languages.map { it.tag }) { "Default language is not enabled" }
        return SupportedLanguageCatalog(defaultTag, languages)
    }

    private fun XmlPullParser.required(name: String): String =
        getAttributeValue(null, name)?.takeIf(String::isNotBlank)
            ?: error("Missing language catalog attribute: $name")
}
