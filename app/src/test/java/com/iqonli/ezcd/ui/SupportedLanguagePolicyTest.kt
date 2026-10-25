package com.iqonli.ezcd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class SupportedLanguagePolicyTest {
    private val current = listOf("en", "es", "ca")
    private val futureWithHans = current + "zh-Hans"

    @Test
    fun unsupportedFirstLocaleFallsThroughToSupportedLaterLocale() {
        assertEquals("es", SupportedLanguagePolicy.firstSupportedTag(
            listOf(Locale.GERMAN, Locale.forLanguageTag("es-AR")), current))
    }

    @Test
    fun regionalSupportedLocaleKeepsTheExistingLanguageChoice() {
        assertEquals("es", SupportedLanguagePolicy.canonicalSupportedTag(Locale.forLanguageTag("es-AR"), current))
    }

    @Test
    fun unsupportedLocaleListHasNoImplicitSupportedMatch() {
        assertNull(SupportedLanguagePolicy.firstSupportedTag(listOf(Locale.GERMAN, Locale.JAPANESE), current))
    }

    @Test
    fun explicitScriptWinsOverRegion() {
        assertEquals("zh-Hans", SupportedLanguagePolicy.canonicalSupportedTag(
            Locale.forLanguageTag("zh-Hans-TW"), futureWithHans) { "Hant" })
    }

    @Test
    fun likelyScriptAllowsHansRegionsAndBareChinese() {
        for (tag in listOf("zh-CN", "zh-SG", "zh")) {
            assertEquals("zh-Hans", SupportedLanguagePolicy.canonicalSupportedTag(
                Locale.forLanguageTag(tag), futureWithHans) { "Hans" })
        }
    }

    @Test
    fun traditionalChineseNeverFallsIntoHans() {
        for (tag in listOf("zh-Hant", "zh-TW", "zh-HK", "zh-MO")) {
            assertNull(SupportedLanguagePolicy.canonicalSupportedTag(
                Locale.forLanguageTag(tag), futureWithHans) { "Hant" })
        }
    }
}
