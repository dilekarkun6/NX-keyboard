package com.nxkeyboard.utils

import android.content.Context

object RecentEmojiManager {

    private const val PREF_KEY = "recent_emojis"
    private const val MAX_COUNT = 30

    fun add(context: Context, emoji: String) {
        if (emoji.isEmpty()) return
        val list = getAll(context).toMutableList()
        list.remove(emoji)
        list.add(0, emoji)
        val trimmed = list.take(MAX_COUNT)
        PrefsHelper.get(context).edit()
            .putString(PREF_KEY, trimmed.joinToString("\u0001"))
            .apply()
    }

    fun getAll(context: Context): List<String> {
        val raw = PrefsHelper.get(context).getString(PREF_KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\u0001")
    }

    fun clear(context: Context) {
        PrefsHelper.get(context).edit().remove(PREF_KEY).apply()
    }
}
