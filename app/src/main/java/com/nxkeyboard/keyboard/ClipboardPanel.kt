package com.nxkeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nxkeyboard.R
import com.nxkeyboard.theme.ThemeManager
import com.nxkeyboard.utils.ClipboardHelper
import com.nxkeyboard.utils.HapticHelper

class ClipboardPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    interface Callback {
        fun onPasteText(text: String)
        fun onPin(text: String)
        fun onUnpin(text: String)
        fun onClose()
        fun onClearHistory()
    }

    private var callback: Callback? = null
    private var themeManager: ThemeManager? = null
    private lateinit var headerRow: LinearLayout
    private lateinit var contentColumn: LinearLayout

    init {
        orientation = VERTICAL
        buildSections()
    }

    fun configure(themeManager: ThemeManager, callback: Callback) {
        this.themeManager = themeManager
        this.callback = callback
        applyTheme()
        refresh()
    }

    private fun buildSections() {
        headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.clipboard_panel_title)
            textSize = 15f
            setPadding(0, 0, dp(8), 0)
        }
        headerRow.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val clearBtn = TextView(context).apply {
            text = context.getString(R.string.clipboard_clear)
            textSize = 12f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                HapticHelper.keyPress(this)
                callback?.onClearHistory()
                refresh()
            }
        }
        headerRow.addView(clearBtn)
        val closeBtn = TextView(context).apply {
            text = " ✕ "
            textSize = 16f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                HapticHelper.keyPress(this)
                callback?.onClose()
            }
        }
        headerRow.addView(closeBtn)
        addView(headerRow)

        val scroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        contentColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
        }
        scroll.addView(contentColumn)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        contentColumn.removeAllViews()
        val pinned = ClipboardHelper.getPinned(context)
        val history = ClipboardHelper.getHistory(context).filter { it !in pinned }

        if (pinned.isNotEmpty()) {
            contentColumn.addView(buildSectionLabel(context.getString(R.string.clipboard_pinned)))
            for (text in pinned) {
                contentColumn.addView(buildCard(text, true))
            }
        }
        if (history.isNotEmpty()) {
            contentColumn.addView(buildSectionLabel(context.getString(R.string.clipboard_history)))
            for (text in history) {
                contentColumn.addView(buildCard(text, false))
            }
        }
        if (pinned.isEmpty() && history.isEmpty()) {
            val empty = TextView(context).apply {
                text = context.getString(R.string.clipboard_empty)
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(32), dp(16), dp(32))
                setTextColor(currentMutedTextColor())
            }
            contentColumn.addView(empty)
        }
    }

    private fun buildSectionLabel(text: String): View {
        val label = TextView(context).apply {
            this.text = text
            textSize = 12f
            setPadding(dp(4), dp(8), dp(4), dp(4))
            setTextColor(currentMutedTextColor())
        }
        return label
    }

    private fun buildCard(text: String, isPinned: Boolean): View {
        val container = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBackground()
        }
        val params = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        params.setMargins(0, dp(2), 0, dp(2))
        container.layoutParams = params

        val displayText = if (text.length > 80) text.take(80) + "…" else text
        val textView = TextView(context).apply {
            this.text = displayText
            textSize = 14f
            setPadding(dp(12), dp(10), dp(8), dp(10))
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener {
                HapticHelper.keyPress(this)
                callback?.onPasteText(text)
            }
            setTextColor(currentTextColor())
        }
        container.addView(textView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))

        val pinBtn = TextView(context).apply {
            this.text = if (isPinned) "📌" else "📍"
            textSize = 16f
            setPadding(dp(10), dp(10), dp(12), dp(10))
            setOnClickListener {
                HapticHelper.keyPress(this)
                if (isPinned) {
                    callback?.onUnpin(text)
                } else {
                    callback?.onPin(text)
                }
                refresh()
            }
        }
        container.addView(pinBtn)

        return container
    }

    private fun cardBackground(): GradientDrawable {
        val dark = themeManager?.isDarkActive() ?: false
        val drawable = GradientDrawable()
        drawable.cornerRadius = dp(10).toFloat()
        drawable.setColor(if (dark) Color.parseColor("#1F1F1F") else Color.WHITE)
        drawable.setStroke(dp(1), if (dark) Color.parseColor("#333333") else Color.parseColor("#DDDDDD"))
        return drawable
    }

    private fun currentTextColor(): Int {
        val dark = themeManager?.isDarkActive() ?: false
        return if (dark) Color.WHITE else Color.parseColor("#212121")
    }

    private fun currentMutedTextColor(): Int {
        val dark = themeManager?.isDarkActive() ?: false
        return if (dark) Color.parseColor("#9E9E9E") else Color.parseColor("#616161")
    }

    fun applyTheme() {
        val dark = themeManager?.isDarkActive() ?: false
        val bg = if (dark) Color.parseColor("#0A0A0A") else Color.parseColor("#F0F0F0")
        setBackgroundColor(bg)
        for (i in 0 until headerRow.childCount) {
            (headerRow.getChildAt(i) as? TextView)?.setTextColor(currentTextColor())
        }
        refresh()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
