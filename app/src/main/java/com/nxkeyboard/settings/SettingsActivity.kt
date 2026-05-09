package com.nxkeyboard.settings

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.nxkeyboard.R
import com.nxkeyboard.ai.ApiKeyVault
import com.nxkeyboard.language.LanguageManager
import com.nxkeyboard.utils.PrefsHelper

class SettingsActivity : AppCompatActivity() {

    private lateinit var themeListener: SharedPreferences.OnSharedPreferenceChangeListener

    override fun onCreate(savedInstanceState: Bundle?) {
        applyThemeFromPrefs()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.settings_title)

        themeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme") {
                applyThemeFromPrefs()
                recreate()
            }
        }
        PrefsHelper.get(this).registerOnSharedPreferenceChangeListener(themeListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::themeListener.isInitialized) {
            PrefsHelper.get(this).unregisterOnSharedPreferenceChangeListener(themeListener)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applyThemeFromPrefs() {
        val mode = when (PrefsHelper.getString(this, "theme", "system")) {
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            "dark"  -> AppCompatDelegate.MODE_NIGHT_YES
            else    -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        private lateinit var imagePicker: ActivityResultLauncher<Array<String>>

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            imagePicker = registerForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    val ctx = requireContext()
                    try {
                        ctx.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Throwable) {}
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                        .putString("keyboard_background_uri", uri.toString())
                        .apply()
                    findPreference<Preference>("keyboard_background")?.summary = "✓ Arkaplan ayarlandı"
                }
            }
        }

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
                val entries = listOf("en", "tr", "tr_f", "de", "fr", "es", "ru", "ar", "ja")
                pref.entries = entries.map { localeDisplayName(it) }.toTypedArray()
                pref.entryValues = entries.toTypedArray()
                if (pref.values.isEmpty()) pref.values = setOf("tr")
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

            findPreference<Preference>("keyboard_background")?.let { pref ->
                val uri = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("keyboard_background_uri", "")
                pref.summary = if (uri.isNullOrBlank()) "Resim seçmek için dokunun" else "✓ Arkaplan ayarlandı"
                pref.setOnPreferenceClickListener {
                    imagePicker.launch(arrayOf("image/*"))
                    true
                }
            }

            findPreference<Preference>("clear_keyboard_background")?.setOnPreferenceClickListener {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .remove("keyboard_background_uri")
                    .apply()
                findPreference<Preference>("keyboard_background")?.summary = "Resim seçmek için dokunun"
                true
            }

            findPreference<Preference>("about_version")?.summary =
                getString(R.string.about_version_summary, "1.0.0")
        }

        private fun localeDisplayName(locale: String): String = when (locale) {
            "tr"   -> "Türkçe (Q)"
            "tr_f" -> "Türkçe (F)"
            "en"   -> "English"
            "de"   -> "Deutsch"
            "fr"   -> "Français"
            "es"   -> "Español"
            "ru"   -> "Русский"
            "ar"   -> "العربية"
            "ja"   -> "日本語"
            else   -> locale
        }
    }
}
