// Modified for ezCD (2026, IQ Online Studio). Modifications: MIT; original CountAway portions: Apache-2.0.
package com.iqonli.ezcd.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.icu.util.ULocale
import android.os.Build
import android.os.LocaleList
import java.util.Locale

object LanguageManager {
    const val ENGLISH = "en"
    const val SPANISH = "es"
    const val CATALAN = "ca"

    private const val PREFS_NAME = "ezcd_ui"
    private const val KEY_LANGUAGE = "language"

    internal fun supportedLanguages(context: Context): SupportedLanguageCatalog = LanguageCatalog.load(context)

    fun wrap(context: Context): Context {
        val languageTag = explicitLanguageTag(context) ?: return context
        return localizedContext(context, languageTag)
    }

    fun localizedContext(context: Context): Context =
        localizedContext(context, currentLanguageTag(context))

    fun currentLanguageTag(context: Context): String =
        explicitLanguageTag(context) ?: systemLanguageTag(context)

    fun systemLanguageTag(context: Context): String {
        val catalog = supportedLanguages(context)
        return firstSupportedTag(systemLocales(context), catalog) ?: catalog.defaultTag
    }

    fun isFollowingSystem(context: Context): Boolean = explicitLanguageTag(context) == null

    fun setLanguage(activity: Activity, languageTag: String) {
        val catalog = supportedLanguages(activity)
        val canonical = canonicalSupportedTag(Locale.forLanguageTag(languageTag), catalog) ?: catalog.defaultTag
        if (explicitLanguageTag(activity) == canonical) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.forLanguageTags(canonical)
            clearLegacyLanguage(activity)
        } else {
            activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_LANGUAGE, canonical).apply()
            activity.recreate()
        }
    }

    fun useSystemLanguage(activity: Activity) {
        if (isFollowingSystem(activity)) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales =
                LocaleList.getEmptyLocaleList()
            clearLegacyLanguage(activity)
        } else {
            clearLegacyLanguage(activity)
            activity.recreate()
        }
    }

    fun syncPlatformLocale(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val catalog = supportedLanguages(context)
        val localeManager = context.getSystemService(LocaleManager::class.java)
        if (!localeManager.applicationLocales.isEmpty) {
            val canonical = firstSupportedTag(localeManager.applicationLocales, catalog)
            if (canonical != null) {
                if (localeManager.applicationLocales.toLanguageTags() != canonical) {
                    localeManager.applicationLocales = LocaleList.forLanguageTags(canonical)
                }
                clearLegacyLanguage(context)
            }
            return
        }
        val stored = storedLanguageTag(context, catalog) ?: return
        localeManager.applicationLocales = LocaleList.forLanguageTags(stored)
        clearLegacyLanguage(context)
    }

    private fun clearLegacyLanguage(context: Context) {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (preferences.contains(KEY_LANGUAGE)) preferences.edit().remove(KEY_LANGUAGE).apply()
    }

    private fun explicitLanguageTag(context: Context): String? {
        val catalog = supportedLanguages(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val platformLocales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (!platformLocales.isEmpty) return firstSupportedTag(platformLocales, catalog)
        }
        return storedLanguageTag(context, catalog)
    }

    private fun storedLanguageTag(context: Context, catalog: SupportedLanguageCatalog): String? {
        val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val stored = preferences.getString(KEY_LANGUAGE, null)
        val normalized = stored?.let(Locale::forLanguageTag)?.let { canonicalSupportedTag(it, catalog) }
        if (stored != null && normalized != null && stored != normalized) {
            preferences.edit().putString(KEY_LANGUAGE, normalized).apply()
        }
        return normalized
    }

    private fun localizedContext(context: Context, languageTag: String): Context {
        val locale = Locale.forLanguageTag(languageTag)
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        configuration.setLayoutDirection(locale)
        return context.createConfigurationContext(configuration)
    }

    private fun systemLocales(context: Context): LocaleList =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).systemLocales
        } else {
            Resources.getSystem().configuration.locales
        }

    private fun firstSupportedTag(locales: LocaleList, catalog: SupportedLanguageCatalog): String? =
        buildList {
            for (index in 0 until locales.size()) add(locales[index])
        }.let { SupportedLanguagePolicy.firstSupportedTag(it, catalog.tags, ::likelyScript) }

    private fun canonicalSupportedTag(locale: Locale, catalog: SupportedLanguageCatalog): String? =
        SupportedLanguagePolicy.canonicalSupportedTag(locale, catalog.tags, ::likelyScript)

    private fun likelyScript(locale: Locale): String? {
        if (locale.script.isNotBlank()) return locale.script
        return ULocale.addLikelySubtags(ULocale.forLocale(locale)).script.takeIf(String::isNotBlank)
    }
}
