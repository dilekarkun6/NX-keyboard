package com.nxkeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nxkeyboard.theme.ThemeManager
import com.nxkeyboard.utils.HapticHelper

class ClipboardToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    interface Callback {
        fun onCopy()
        fun onCut()
        fun onPaste()
        fun onSelectAll()
        fun onCursorLeft()
        fun onCursorRight()
        fun onClose()
        fun onAiCorrect()
        fun onAiTranslate()
        fun onPasteHistoryItem(text: String)
        fun onClearHistory()
        fun getHistorySnapshot(): List<String>
    }

    private var callback: Callback? = null
    private var themeManager: ThemeManager? = null
    private lateinit var actionRow: LinearLayout
    private lateinit var historyRow: LinearLayout
    private lateinit var historyScroll: HorizontalScrollView

    init {
        orientation = VERTICAL
        setPadding(dp(6), dp(4), dp(6), dp(4))
        buildSections()
    }

    fun configure(themeManager: ThemeManager, callback: Callback) {
        this.themeManager = themeManager
        this.callback = callback
        applyTheme()
        refreshHistory()
    }

    fun refreshHistory() {
        val cb = callback ?: return
        val items = cb.getHistorySnapshot()
        historyRow.removeAllViews()
        if (items.isEmpty()) {
            val empty = TextView(context).apply {
                text = "Pano geçmişi boş"
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(6), dp(10), dp(6))
                setTextColor(currentTextColor())
            }
            historyRow.addView(empty)
            historyScroll.visibility = View.VISIBLE
            return
        }
        for (item in items) {
            historyRow.addView(buildHistoryChip(item))
        }
        historyScroll.visibility = View.VISIBLE
    }

    private fun buildSections() {
        historyScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }
        historyRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }
        historyScroll.addView(historyRow)
        addView(historyScroll)

        actionRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(4), dp(2), dp(2))
        }
        addView(actionRow)
        buildActionButtons()
    }

    private fun buildActionButtons() {
        addAction("✂") { callback?.onCut() }
        addAction("📋") { callback?.onCopy() }
        addAction("📥") { callback?.onPaste() }
        addAction("🅰") { callback?.onSelectAll() }
        addAction("◀") { callback?.onCursorLeft() }
        addAction("▶") { callback?.onCursorRight() }
        addAction("✨") { callback?.onAiCorrect() }
        addAction("🌍") { callback?.onAiTranslate() }
        addAction("🗑") {
            callback?.onClearHistory()
            refreshHistory()
        }
        addAction("✕") { callback?.onClose() }
    }

    private fun addAction(label: String, onClick: () -> Unit) {
        val view = TextView(context).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(dp(6), dp(8), dp(6), dp(8))
            setOnClickListener {
                HapticHelper.keyPress(this)
                onClick()
            }
        }
        actionRow.addView(view, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun buildHistoryChip(text: String): View {
        val displayText = if (text.length > 28) text.take(28) + "…" else text
        val chip = TextView(context).apply {
            this.text = displayText
            textSize = 13f
            gravity = Gravity.CENTER
            isSingleLine = true
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                HapticHelper.keyPress(this)
                callback?.onPasteHistoryItem(text)
            }
            background = chipBackground()
            setTextColor(currentTextColor())
        }
        val params = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        params.setMargins(dp(3), dp(2), dp(3), dp(2))
        chip.layoutParams = params
        return chip
    }

    private fun chipBackground(): GradientDrawable {
        val dark = themeManager?.isDarkActive() ?: false
        val drawable = GradientDrawable()
        drawable.cornerRadius = dp(14).toFloat()
        drawable.setColor(if (dark) Color.parseColor("#2C2C2C") else Color.parseColor("#FFFFFF"))
        drawable.setStroke(dp(1), if (dark) Color.parseColor("#3D3D3D") else Color.parseColor("#BDBDBD"))
        return drawable
    }

    private fun currentTextColor(): Int {
        val dark = themeManager?.isDarkActive() ?: false
        return if (dark) Color.WHITE else Color.parseColor("#212121")
    }

    fun applyTheme() {
        val dark = themeManager?.isDarkActive() ?: false
        val bg = if (dark) Color.parseColor("#1A1A1A") else Color.parseColor("#E0E0E0")
        val text = if (dark) Color.WHITE else Color.parseColor("#212121")
        setBackgroundColor(bg)
        for (i in 0 until actionRow.childCount) {
            (actionRow.getChildAt(i) as? TextView)?.setTextColor(text)
        }
        for (i in 0 until historyRow.childCount) {
            (historyRow.getChildAt(i) as? TextView)?.setTextColor(text)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
