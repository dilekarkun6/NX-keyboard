package com.nxkeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.nxkeyboard.theme.ThemeManager
import com.nxkeyboard.utils.HapticHelper
import com.nxkeyboard.utils.PrefsHelper

class SuggestionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    interface Callback {
        fun onUndo()
        fun onRedo()
        fun onAiCorrect()
        fun onAiTranslate()
        fun onClipboard()
        fun onEmoji()
        fun onVoice()
        fun onSelectionMode()
        fun onSettings()
        fun onCollapse()
    }

    private var callback: Callback? = null
    private var themeManager: ThemeManager? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(2), dp(4), dp(2))
    }

    fun configure(themeManager: ThemeManager, callback: Callback) {
        this.themeManager = themeManager
        this.callback = callback
        rebuild()
    }

    fun rebuild() {
        removeAllViews()
        val ctx = context
        val showAi = PrefsHelper.getBoolean(ctx, "show_ai_button", true)
        val showTranslate = PrefsHelper.getBoolean(ctx, "show_translate_button", false)
        val showUndo = PrefsHelper.getBoolean(ctx, "show_undo_redo", true)
        val showVoice = PrefsHelper.getBoolean(ctx, "show_voice_button", true)
        val showClipboard = PrefsHelper.getBoolean(ctx, "show_clipboard_button", true)

        val showSelection = PrefsHelper.getBoolean(ctx, "show_selection_button", true)

        if (showUndo) {
            addAction("↶") { callback?.onUndo() }
            addAction("↷") { callback?.onRedo() }
        }
        if (showAi) addAction("✨") { callback?.onAiCorrect() }
        if (showTranslate) addAction("🌍") { callback?.onAiTranslate() }
        if (showVoice) addAction("🎤") { callback?.onVoice() }
        if (showSelection) addAction("✂") { callback?.onSelectionMode() }
        if (showClipboard) addAction("📋") { callback?.onClipboard() }
        addAction("⚙") { callback?.onSettings() }
        addAction("▼") { callback?.onCollapse() }
        applyTheme()
    }

    private fun addAction(label: String, onClick: () -> Unit) {
        val view = TextView(context).apply {
            text = label
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setOnClickListener {
                HapticHelper.keyPress(this)
                onClick()
            }
        }
        addView(view, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    fun applyTheme() {
        val dark = themeManager?.isDarkActive() ?: false
        val bg = if (dark) Color.parseColor("#0F0F0F") else Color.parseColor("#EEEEEE")
        val text = if (dark) Color.WHITE else Color.parseColor("#212121")
        setBackgroundColor(bg)
        for (i in 0 until childCount) {
            (getChildAt(i) as? TextView)?.setTextColor(text)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
