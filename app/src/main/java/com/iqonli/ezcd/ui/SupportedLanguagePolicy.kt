// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.ui

import java.util.Locale

internal object SupportedLanguagePolicy {
    fun canonicalSupportedTag(
        locale: Locale,
        supportedTags: List<String>,
        likelyScript: (Locale) -> String? = { null },
    ): String? {
        val language = locale.language.lowercase(Locale.ROOT)
        if (language.isBlank()) return null
        val candidates = supportedTags.map(Locale::forLanguageTag).filter { it.language == language }
        if (candidates.isEmpty()) return null

        val resolvedScript = locale.script.takeIf(String::isNotBlank)
            ?: likelyScript(locale)?.takeIf(String::isNotBlank)
        if (resolvedScript != null) {
            candidates.firstOrNull { it.script.equals(resolvedScript, ignoreCase = true) }
                ?.let { return it.toLanguageTag() }
        }
        candidates.firstOrNull { it.script.isBlank() }?.let { return it.toLanguageTag() }
        return null
    }

    fun firstSupportedTag(
        locales: List<Locale>,
        supportedTags: List<String>,
        likelyScript: (Locale) -> String? = { null },
    ): String? = locales.firstNotNullOfOrNull {
        canonicalSupportedTag(it, supportedTags, likelyScript)
    }
}
