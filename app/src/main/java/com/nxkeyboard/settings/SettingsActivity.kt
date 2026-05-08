package com.nxkeyboard.settings

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.nxkeyboard.R
import com.nxkeyboard.ai.ApiKeyVault
import com.nxkeyboard.language.LanguageManager
import com.nxkeyboard.utils.PrefsHelper

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)

            findPreference<Preference>("enable_keyboard")?.setOnPreferenceClickListener {
                startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                true
            }

            findPreference<Preference>("choose_keyboard")?.setOnPreferenceClickListener {
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                    as? android.view.inputmethod.InputMethodManager
                imm?.showInputMethodPicker()
                true
            }

            val languagesPref = findPreference<MultiSelectListPreference>("enabled_languages")
            languagesPref?.let { pref ->
                val entries = LanguageManager.SUPPORTED_LOCALES
                    .filter { it in setOf("en","tr","de","fr","es","ru","ar","ja") }
                pref.entries = entries.map { localeDisplayName(it) }.toTypedArray()
                pref.entryValues = entries.toTypedArray()
                if (pref.values.isEmpty()) pref.values = setOf("en", "tr")
            }

            findPreference<Preference>("ai_status")?.let { pref ->
                pref.summary = if (ApiKeyVault.isAvailable()) {
                    getString(R.string.ai_built_in_key_active)
                } else {
                    getString(R.string.ai_built_in_key_missing)
                }
            }

            findPreference<Preference>("clear_recent_emojis")?.setOnPreferenceClickListener {
                com.nxkeyboard.utils.RecentEmojiManager.clear(requireContext())
                true
            }

            findPreference<Preference>("clear_clipboard_history")?.setOnPreferenceClickListener {
                com.nxkeyboard.utils.ClipboardHelper.clearHistory(requireContext())
                true
            }

            findPreference<Preference>("about_version")?.summary =
                getString(R.string.about_version_summary, "1.0.0")
        }

        private fun localeDisplayName(locale: String): String = when (locale) {
            "tr" -> "Türkçe"
            "en" -> "English"
            "de" -> "Deutsch"
            "fr" -> "Français"
            "es" -> "Español"
            "ru" -> "Русский"
            "ar" -> "العربية"
            "ja" -> "日本語"
            else -> locale
        }
    }
}
