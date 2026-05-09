package com.nxkeyboard.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

object ClipboardHelper {

    fun copy(context: Context, ic: InputConnection?): Boolean {
        if (ic == null) return false
        val selected = ic.getSelectedText(0) ?: return false
        if (selected.isEmpty()) return false
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cb.setPrimaryClip(ClipData.newPlainText("NXKeyboard", selected))
        addToHistory(context, selected.toString())
        return true
    }

    fun cut(context: Context, ic: InputConnection?): Boolean {
        if (ic == null) return false
        val didCopy = copy(context, ic)
        if (didCopy) ic.commitText("", 1)
        return didCopy
    }

    fun paste(context: Context, ic: InputConnection?): Boolean {
        if (ic == null) return false
        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cb.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() ?: return false
        ic.commitText(text, 1)
        return true
    }

    fun selectAll(ic: InputConnection?) {
        ic?.performContextMenuAction(android.R.id.selectAll)
    }

    fun moveCursor(ic: InputConnection?, direction: Int) {
        if (ic == null) return
        val keyCode = if (direction < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private const val HISTORY_KEY = "clipboard_history"
    private const val PINNED_KEY = "clipboard_pinned"
    private const val MAX_HISTORY = 20
    private const val MAX_PINNED = 50

    fun addToHistory(context: Context, text: String) {
        if (text.isBlank()) return
        val list = getHistory(context).toMutableList()
        list.remove(text)
        list.add(0, text)
        val trimmed = list.take(MAX_HISTORY)
        PrefsHelper.get(context).edit()
            .putString(HISTORY_KEY, trimmed.joinToString("\u0001"))
            .apply()
    }

    fun getHistory(context: Context): List<String> {
        val raw = PrefsHelper.get(context).getString(HISTORY_KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\u0001")
    }

    fun clearHistory(context: Context) {
        PrefsHelper.get(context).edit().remove(HISTORY_KEY).apply()
    }

    fun getPinned(context: Context): List<String> {
        val raw = PrefsHelper.get(context).getString(PINNED_KEY, "") ?: ""
        return if (raw.isEmpty()) emptyList() else raw.split("\u0001")
    }

    fun pin(context: Context, text: String) {
        if (text.isBlank()) return
        val list = getPinned(context).toMutableList()
        list.remove(text)
        list.add(0, text)
        val trimmed = list.take(MAX_PINNED)
        PrefsHelper.get(context).edit()
            .putString(PINNED_KEY, trimmed.joinToString("\u0001"))
            .apply()
    }

    fun unpin(context: Context, text: String) {
        val list = getPinned(context).toMutableList()
        list.remove(text)
        PrefsHelper.get(context).edit()
            .putString(PINNED_KEY, list.joinToString("\u0001"))
            .apply()
    }

    fun isPinned(context: Context, text: String): Boolean {
        return text in getPinned(context)
    }
}
