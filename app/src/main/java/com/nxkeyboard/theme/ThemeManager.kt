package com.nxkeyboard.theme

import android.content.Context
import android.content.res.Configuration
import com.nxkeyboard.utils.PrefsHelper

class ThemeManager(private val context: Context) {

    enum class Theme { LIGHT, DARK, SYSTEM }

    fun getCurrentTheme(): Theme {
        return when (PrefsHelper.get(context).getString("theme", "system")) {
            "light" -> Theme.LIGHT
            "dark"  -> Theme.DARK
            else    -> Theme.SYSTEM
        }
    }

    fun isDarkActive(): Boolean = when (getCurrentTheme()) {
        Theme.DARK   -> true
        Theme.LIGHT  -> false
        Theme.SYSTEM -> {
            val nightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            nightMode == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
