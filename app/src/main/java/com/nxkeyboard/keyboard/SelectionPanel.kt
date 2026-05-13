package com.nxkeyboard.keyboard

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.nxkeyboard.theme.ThemeManager
import com.nxkeyboard.utils.HapticHelper

class SelectionPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : LinearLayout(context, attrs, defStyle) {

    interface Callback {
        fun onMoveCursor(direction: Int, withSelection: Boolean)
        fun onSelectAll()
        fun onCopy()
        fun onCut()
        fun onPaste()
        fun onPasteWithSpace()
        fun onDelete()
        fun onClose()
    }

    private var callback: Callback? = null
    private var themeManager: ThemeManager? = null
    private var selecting = false
    private lateinit var selectButton: TextView

    init {
        orientation = VERTICAL
        setPadding(dp(8), dp(8), dp(8), dp(8))
        buildLayout()
    }

    fun configure(themeManager: ThemeManager, callback: Callback) {
        this.themeManager = themeManager
        this.callback = callback
        applyTheme()
    }

    private fun buildLayout() {
        // Header
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(8))
        }
        val title = TextView(context).apply {
            text = "✂ Seçim modu"
            textSize = 14f
        }
        header.addView(title, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val closeBtn = TextView(context).apply {
            text = " ✕ "
            textSize = 16f
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnClickListener {
                HapticHelper.keyPress(this)
                callback?.onClose()
            }
        }
        header.addView(closeBtn)
        addView(header)

        // Arrow pad: 3 rows, center has Select button surrounded by ↑↓←→
        val arrowPad = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER
        }
        // Top row: empty | ↑ | empty
        val topRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        topRow.addView(spacer())
        topRow.addView(arrowKey("↑") { callback?.onMoveCursor(2, selecting) })
        topRow.addView(spacer())
        arrowPad.addView(topRow)

        // Mid row: ← | SELECT | →
        val midRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        midRow.addView(arrowKey("←") { callback?.onMoveCursor(-1, selecting) })
        selectButton = TextView(context).apply {
            text = "SEÇ"
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = padBackground(active = false)
            setOnClickListener {
                HapticHelper.keyPress(this)
                selecting = !selecting
                background = padBackground(active = selecting)
                text = if (selecting) "SEÇİYOR" else "SEÇ"
            }
        }
        val selectParams = LayoutParams(dp(70), dp(56))
        selectParams.setMargins(dp(2), dp(2), dp(2), dp(2))
        selectButton.layoutParams = selectParams
        midRow.addView(selectButton)
        midRow.addView(arrowKey("→") { callback?.onMoveCursor(1, selecting) })
        arrowPad.addView(midRow)

        // Bottom row: empty | ↓ | empty
        val botRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER
        }
        botRow.addView(spacer())
        botRow.addView(arrowKey("↓") { callback?.onMoveCursor(-2, selecting) })
        botRow.addView(spacer())
        arrowPad.addView(botRow)

        addView(arrowPad)

        // Action buttons row
        val actions = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), dp(12), dp(2), dp(2))
        }
        actions.addView(actionKey("🅰 Tümü") { callback?.onSelectAll() })
        actions.addView(actionKey("📋 Kopya") { callback?.onCopy() })
        actions.addView(actionKey("✂ Kes") { callback?.onCut() })
        actions.addView(actionKey("📥 Yapıştır") { callback?.onPaste() })
        actions.addView(actionKey("⎵📥") { callback?.onPasteWithSpace() })
        actions.addView(actionKey("⌫") { callback?.onDelete() })
        addView(actions)
    }

    private fun arrowKey(label: String, onClick: () -> Unit): TextView {
        val view = TextView(context).apply {
            text = label
            textSize = 18f
            gravity = Gravity.CENTER
            background = padBackground(active = false)
            setOnClickListener {
                HapticHelper.keyPress(this)
                onClick()
            }
        }
        val params = LayoutParams(dp(56), dp(56))
        params.setMargins(dp(2), dp(2), dp(2), dp(2))
        view.layoutParams = params
        return view
    }

    private fun spacer(): View {
        val v = View(context)
        v.layoutParams = LayoutParams(dp(56), dp(56))
        return v
    }

    private fun actionKey(label: String, onClick: () -> Unit): TextView {
        val view = TextView(context).apply {
            text = label
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            isSingleLine = true
            setPadding(dp(4), dp(8), dp(4), dp(8))
            background = padBackground(active = false)
            setOnClickListener {
                HapticHelper.keyPress(this)
                onClick()
            }
        }
        val params = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        params.setMargins(dp(2), 0, dp(2), 0)
        view.layoutParams = params
        return view
    }

    private fun padBackground(active: Boolean): GradientDrawable {
        val dark = themeManager?.isDarkActive() ?: false
        val drawable = GradientDrawable()
        drawable.cornerRadius = dp(8).toFloat()
        val color = when {
            active -> Color.parseColor("#1976D2")
            dark -> Color.parseColor("#2C2C2C")
            else -> Color.WHITE
        }
        drawable.setColor(color)
        drawable.setStroke(dp(1), if (dark) Color.parseColor("#3D3D3D") else Color.parseColor("#BDBDBD"))
        return drawable
    }

    fun applyTheme() {
        val dark = themeManager?.isDarkActive() ?: false
        val bg = if (dark) Color.parseColor("#0A0A0A") else Color.parseColor("#F0F0F0")
        val text = if (dark) Color.WHITE else Color.parseColor("#212121")
        setBackgroundColor(bg)
        applyTextColorRecursively(this, text)
    }

    private fun applyTextColorRecursively(group: View, color: Int) {
        if (group is TextView) {
            if (group != selectButton || !selecting) group.setTextColor(color)
            if (group == selectButton && selecting) group.setTextColor(Color.WHITE)
        }
        if (group is LinearLayout) {
            for (i in 0 until group.childCount) applyTextColorRecursively(group.getChildAt(i), color)
        }
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
