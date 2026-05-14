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
        com.nxkeyboard.utils.CrashLogger.install(this)
        applyAppLanguage()
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
            when (key) {
                "theme" -> {
                    applyThemeFromPrefs()
                    recreate()
                }
                "app_language" -> {
                    applyAppLanguage()
                    syncKeyboardWithAppLanguage()
                    recreate()
                }
            }
        }
        PrefsHelper.get(this).registerOnSharedPreferenceChangeListener(themeListener)
    }

    private fun applyAppLanguage() {
        val lang = PrefsHelper.getString(this, "app_language", "system")
        val locales = if (lang == "system" || lang.isBlank()) {
            androidx.core.os.LocaleListCompat.getEmptyLocaleList()
        } else {
            androidx.core.os.LocaleListCompat.forLanguageTags(lang)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }

    private fun syncKeyboardWithAppLanguage() {
        val lang = PrefsHelper.getString(this, "app_language", "system")
        if (lang == "system" || lang.isBlank()) return
        val keyboardLocale = when (lang) {
            "en" -> "en"
            "tr" -> "tr"
            "az" -> "az"
            "de" -> "de"
            "fr" -> "fr"
            "es" -> "es"
            "ru" -> "ru"
            "ar" -> "ar"
            "ja" -> "ja"
            "hi" -> "hi"
            else -> return
        }
        val prefs = PrefsHelper.get(this)
        val current = prefs.getStringSet("enabled_languages", emptySet()) ?: emptySet()
        val updated = LinkedHashSet<String>().apply {
            add(keyboardLocale)
            addAll(current)
        }
        prefs.edit()
            .putStringSet("enabled_languages", updated)
            .putString("current_locale", keyboardLocale)
            .apply()
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
        private lateinit var audioPicker: ActivityResultLauncher<Array<String>>

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            imagePicker = registerForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    val ctx = requireContext()
                    val mime = ctx.contentResolver.getType(uri).orEmpty()
                    if (!mime.startsWith("image/")) {
                        android.widget.Toast.makeText(ctx,
                            "Sadece resim dosyası seç / Image files only",
                            android.widget.Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }
                    try {
                        ctx.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Throwable) {}
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                        .putString("keyboard_background_uri", uri.toString())
                        .apply()
                    findPreference<Preference>("keyboard_background")?.summary = getString(R.string.keyboard_background_set)
                }
            }
            audioPicker = registerForActivityResult(
                ActivityResultContracts.OpenDocument()
            ) { uri: Uri? ->
                if (uri != null) {
                    val ctx = requireContext()
                    val mime = ctx.contentResolver.getType(uri).orEmpty()
                    if (!mime.startsWith("audio/")) {
                        android.widget.Toast.makeText(ctx,
                            "Sadece ses dosyası seç / Audio files only",
                            android.widget.Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }
                    try {
                        ctx.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    } catch (_: Throwable) {}
                    PreferenceManager.getDefaultSharedPreferences(ctx).edit()
                        .putString("custom_sound_uri", uri.toString())
                        .putString("key_sound", "custom")
                        .apply()
                    findPreference<Preference>("custom_sound")?.summary = getString(R.string.custom_sound_set)
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
                val entries = listOf("en", "tr", "tr_f", "az", "de", "fr", "es", "ru", "ar", "ja", "hi")
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
                pref.summary = if (uri.isNullOrBlank()) getString(R.string.keyboard_background_summary) else getString(R.string.keyboard_background_set)
                pref.setOnPreferenceClickListener {
                    imagePicker.launch(arrayOf("image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp", "image/heic", "image/heif"))
                    true
                }
            }

            findPreference<Preference>("clear_keyboard_background")?.setOnPreferenceClickListener {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .remove("keyboard_background_uri")
                    .apply()
                findPreference<Preference>("keyboard_background")?.summary = getString(R.string.keyboard_background_summary)
                true
            }

            findPreference<Preference>("custom_sound")?.let { pref ->
                val uri = PreferenceManager.getDefaultSharedPreferences(requireContext())
                    .getString("custom_sound_uri", "")
                pref.summary = if (uri.isNullOrBlank())
                    getString(R.string.custom_sound_summary)
                else
                    getString(R.string.custom_sound_set)
                pref.setOnPreferenceClickListener {
                    audioPicker.launch(arrayOf("audio/mpeg", "audio/mp4", "audio/ogg", "audio/wav", "audio/x-wav", "audio/aac", "audio/x-m4a"))
                    true
                }
            }

            findPreference<Preference>("clear_custom_sound")?.setOnPreferenceClickListener {
                PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()
                    .remove("custom_sound_uri")
                    .apply()
                findPreference<Preference>("custom_sound")?.summary = getString(R.string.custom_sound_summary)
                true
            }

            val versionPref = findPreference<Preference>("about_version")
            versionPref?.summary = getString(R.string.about_version_summary, "1.0.0")
            var versionTaps = 0
            versionPref?.setOnPreferenceClickListener {
                versionTaps += 1
                val remaining = 10 - versionTaps
                if (versionTaps >= 10) {
                    PrefsHelper.get(requireContext()).edit()
                        .putBoolean("dev_mode_enabled", true)
                        .apply()
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.dev_mode_enabled),
                        android.widget.Toast.LENGTH_LONG).show()
                    updateDevModeVisibility()
                    versionTaps = 0
                } else if (versionTaps >= 6) {
                    android.widget.Toast.makeText(requireContext(),
                        getString(R.string.dev_mode_taps_remaining, remaining),
                        android.widget.Toast.LENGTH_SHORT).show()
                }
                true
            }

            findPreference<Preference>("about_license")?.setOnPreferenceClickListener {
                showLicenseDialog()
                true
            }

            updateDevModeVisibility()

            findPreference<Preference>("dev_force_crash")?.setOnPreferenceClickListener {
                android.widget.Toast.makeText(requireContext(),
                    "Force crash in 1 second…", android.widget.Toast.LENGTH_SHORT).show()
                view?.postDelayed({
                    throw RuntimeException("Developer mode forced crash for testing")
                }, 1000)
                true
            }

            findPreference<Preference>("dev_disable")?.setOnPreferenceClickListener {
                PrefsHelper.get(requireContext()).edit()
                    .putBoolean("dev_mode_enabled", false)
                    .apply()
                updateDevModeVisibility()
                android.widget.Toast.makeText(requireContext(),
                    getString(R.string.dev_mode_disabled),
                    android.widget.Toast.LENGTH_SHORT).show()
                true
            }

            updateErrorLogSummary()

            findPreference<Preference>("share_error_log")?.setOnPreferenceClickListener {
                shareErrorLog()
                true
            }

            findPreference<Preference>("clear_error_log")?.setOnPreferenceClickListener {
                com.nxkeyboard.utils.CrashLogger.clear(requireContext())
                updateErrorLogSummary()
                true
            }
        }

        private fun updateDevModeVisibility() {
            val enabled = PrefsHelper.get(requireContext())
                .getBoolean("dev_mode_enabled", false)
            findPreference<Preference>("dev_force_crash")?.isVisible = enabled
            findPreference<Preference>("dev_disable")?.isVisible = enabled
            findPreference<androidx.preference.PreferenceCategory>("cat_dev")?.isVisible = enabled
        }

        private fun showLicenseDialog() {
            val ctx = requireContext()
            val licenseText = try {
                ctx.resources.openRawResource(R.raw.license_agplv3).bufferedReader().use { it.readText() }
            } catch (_: Throwable) {
                "GNU Affero General Public License v3.0\n\nSee https://www.gnu.org/licenses/agpl-3.0.html"
            }
            val scrollView = android.widget.ScrollView(ctx)
            val tv = android.widget.TextView(ctx).apply {
                text = licenseText
                textSize = 12f
                setPadding(48, 32, 48, 32)
                typeface = android.graphics.Typeface.MONOSPACE
            }
            scrollView.addView(tv)
            androidx.appcompat.app.AlertDialog.Builder(ctx)
                .setTitle("GNU AGPLv3")
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        private fun updateErrorLogSummary() {
            val ctx = requireContext()
            val pref = findPreference<Preference>("share_error_log") ?: return
            if (com.nxkeyboard.utils.CrashLogger.hasLog(ctx)) {
                val size = com.nxkeyboard.utils.CrashLogger.getLogFile(ctx).length()
                pref.summary = "Hata kaydı mevcut (${size} bayt). Geliştirici ile paylaşmak için dokun"
            } else {
                pref.summary = "Henüz kayıtlı hata yok"
            }
        }

        private fun shareErrorLog() {
            val ctx = requireContext()
            if (!com.nxkeyboard.utils.CrashLogger.hasLog(ctx)) {
                android.widget.Toast.makeText(ctx, "Henüz kayıtlı hata yok", android.widget.Toast.LENGTH_SHORT).show()
                return
            }
            try {
                val file = com.nxkeyboard.utils.CrashLogger.getLogFile(ctx)
                val authority = "${ctx.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(ctx, authority, file)
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "NX Keyboard error log")
                    putExtra(Intent.EXTRA_TEXT, "THIS IS THE ERROR — please give this to the developer to fix it")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(sendIntent, "Hatayı paylaş"))
            } catch (t: Throwable) {
                android.widget.Toast.makeText(ctx, "Paylaşım hatası: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }

        private fun localeDisplayName(locale: String): String = when (locale) {
            "tr"   -> "Türkçe (Q)"
            "tr_f" -> "Türkçe (F)"
            "az"   -> "Azərbaycanca"
            "en"   -> "English"
            "de"   -> "Deutsch"
            "fr"   -> "Français"
            "es"   -> "Español"
            "ru"   -> "Русский"
            "ar"   -> "العربية"
            "ja"   -> "日本語"
            "hi"   -> "हिन्दी"
            else   -> locale
        }
    }
}
