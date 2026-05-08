package com.nxkeyboard.language

import android.content.Context
import com.nxkeyboard.R
import com.nxkeyboard.utils.PrefsHelper

class LanguageManager(private val context: Context) {

    private val enabledLanguages: MutableList<String> = mutableListOf()
    private var currentIndex = 0

    val currentLocale: String
        get() = enabledLanguages.getOrElse(currentIndex) { "en" }

    val enabledList: List<String>
        get() = enabledLanguages.toList()

    init {
        loadFromPrefs()
    }

    fun loadFromPrefs() {
        val prefs = PrefsHelper.get(context)
        val saved = prefs.getStringSet("enabled_languages", setOf("en", "tr")) ?: setOf("en", "tr")
        enabledLanguages.clear()
        enabledLanguages.addAll(saved.sorted())
        if (enabledLanguages.isEmpty()) {
            enabledLanguages.add("en")
            enabledLanguages.add("tr")
        }
        currentIndex = currentIndex.coerceIn(0, enabledLanguages.size - 1)
    }

    fun switchToNext() {
        if (enabledLanguages.size <= 1) return
        currentIndex = (currentIndex + 1) % enabledLanguages.size
    }

    fun setCurrentLocale(locale: String) {
        val idx = enabledLanguages.indexOf(locale)
        if (idx >= 0) currentIndex = idx
    }

    fun isRTL(locale: String): Boolean = locale in setOf("ar", "fa", "ur", "he")

    fun displayNameOf(locale: String): String = when (locale) {
        "tr"    -> "Türkçe (Q)"
        "tr_f"  -> "Türkçe (F)"
        "en"    -> "English"
        "de"    -> "Deutsch"
        "fr"    -> "Français"
        "es"    -> "Español"
        "it"    -> "Italiano"
        "pt"    -> "Português"
        "ru"    -> "Русский"
        "uk"    -> "Українська"
        "ar"    -> "العربية"
        "fa"    -> "فارسی"
        "ur"    -> "اردو"
        "he"    -> "עברית"
        "ja"    -> "日本語"
        "ko"    -> "한국어"
        "zh-CN" -> "中文 (简体)"
        "zh-TW" -> "中文 (繁體)"
        "hi"    -> "हिन्दी"
        "bn"    -> "বাংলা"
        "el"    -> "Ελληνικά"
        "th"    -> "ไทย"
        "vi"    -> "Tiếng Việt"
        "pl"    -> "Polski"
        "nl"    -> "Nederlands"
        "sv"    -> "Svenska"
        "da"    -> "Dansk"
        "fi"    -> "Suomi"
        "id"    -> "Bahasa Indonesia"
        else    -> locale
    }

    fun getLayoutResourceForLocale(locale: String): Int = when (locale) {
        "tr"    -> R.xml.keyboard_tr
        "tr_f"  -> R.xml.keyboard_tr_f
        "en"    -> R.xml.keyboard_en
        "de"    -> R.xml.keyboard_de
        "fr"    -> R.xml.keyboard_fr
        "es"    -> R.xml.keyboard_es
        "ru"    -> R.xml.keyboard_ru
        "ar"    -> R.xml.keyboard_ar
        "ja"    -> R.xml.keyboard_ja
        else    -> R.xml.keyboard_en
    }

    companion object {
        val SUPPORTED_LOCALES = listOf(
            "en", "tr", "de", "fr", "es", "it", "pt", "ru", "uk", "ar", "fa", "ur", "he",
            "ja", "ko", "zh-CN", "zh-TW", "hi", "bn", "el", "th", "vi", "pl", "nl", "sv",
            "da", "fi", "id"
        )
    }
}
