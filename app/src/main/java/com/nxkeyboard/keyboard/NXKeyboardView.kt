package com.nxkeyboard.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import com.nxkeyboard.R
import com.nxkeyboard.language.LanguageManager
import com.nxkeyboard.service.NXInputMethodService
import com.nxkeyboard.theme.ThemeManager
import com.nxkeyboard.utils.HapticHelper

class NXKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var imeService: NXInputMethodService? = null
    private var languageManager: LanguageManager? = null
    private var themeManager: ThemeManager? = null

    private var layout: KeyboardLayout = KeyboardLayout(emptyList())
    private val keyRects: MutableList<KeyRect> = mutableListOf()

    private var rowHeight: Float = dp(56f)
    private val keyPadding: Float = dp(2f)
    private val cornerRadius: Float = dp(6f)

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyModifierPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val enterPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val modifierLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val enterLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var isShifted = false
    private var isCapsLocked = false
    private var isSymbolMode = false
    private var pressedKey: Key? = null
    private var pressedRect: KeyRect? = null
    private var lastShiftClickTime = 0L
    private var lastSpaceClickTime = 0L

    private val handler = Handler(Looper.getMainLooper())
    private var deleteRunnable: Runnable? = null
    private var lastEditorInfo: EditorInfo? = null

    private data class KeyRect(val key: Key, val rect: RectF, val rowIndex: Int)

    init {
        setupPaints()
    }

    fun setInputMethodService(service: NXInputMethodService) {
        this.imeService = service
        this.languageManager = service.getLanguageManager()
        this.themeManager = service.getThemeManager()
        applyTheme()
        reloadLayout()
    }

    fun setEditorInfo(info: EditorInfo?) {
        this.lastEditorInfo = info
        invalidate()
    }

    fun reloadLayout() {
        val lang = languageManager ?: return
        val locale = lang.currentLocale
        val resourceId = if (isSymbolMode) R.xml.keyboard_symbols
            else lang.getLayoutResourceForLocale(locale)
        val rtl = !isSymbolMode && lang.isRTL(locale)
        layout = KeyboardLayoutManager.load(context, resourceId, locale, rtl)
        layoutDirection = if (rtl) LAYOUT_DIRECTION_RTL else LAYOUT_DIRECTION_LTR
        rebuildKeyRects()
        invalidate()
    }

    fun applyTheme() {
        val dark = themeManager?.isDarkActive() ?: false
        if (dark) {
            backgroundPaint.color = Color.parseColor("#121212")
            keyPaint.color = Color.parseColor("#2C2C2C")
            keyPressedPaint.color = Color.parseColor("#0D47A1")
            keyModifierPaint.color = Color.parseColor("#1A1A1A")
            enterPaint.color = Color.parseColor("#0D47A1")
            labelPaint.color = Color.WHITE
            modifierLabelPaint.color = Color.parseColor("#E0E0E0")
            enterLabelPaint.color = Color.WHITE
        } else {
            backgroundPaint.color = Color.parseColor("#F5F5F5")
            keyPaint.color = Color.WHITE
            keyPressedPaint.color = Color.parseColor("#BBDEFB")
            keyModifierPaint.color = Color.parseColor("#E0E0E0")
            enterPaint.color = Color.parseColor("#1976D2")
            labelPaint.color = Color.parseColor("#212121")
            modifierLabelPaint.color = Color.parseColor("#424242")
            enterLabelPaint.color = Color.WHITE
        }
        invalidate()
    }

    private fun setupPaints() {
        labelPaint.textAlign = Paint.Align.CENTER
        labelPaint.textSize = sp(16f)
        labelPaint.typeface = Typeface.DEFAULT
        modifierLabelPaint.textAlign = Paint.Align.CENTER
        modifierLabelPaint.textSize = sp(13f)
        enterLabelPaint.textAlign = Paint.Align.CENTER
        enterLabelPaint.textSize = sp(15f)
        enterLabelPaint.typeface = Typeface.DEFAULT_BOLD
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val rowCount = if (layout.rows.isEmpty()) 4 else layout.rows.size
        val height = (rowHeight * rowCount).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildKeyRects()
    }

    private fun rebuildKeyRects() {
        keyRects.clear()
        if (width == 0 || layout.rows.isEmpty()) return
        val totalWidth = width.toFloat()
        var y = paddingTop.toFloat()
        for ((rowIndex, row) in layout.rows.withIndex()) {
            val totalPercent = row.keys.sumOf { it.widthPercent.toDouble() }.toFloat()
            val percentScale = if (totalPercent > 0) 100f / totalPercent else 1f
            var x = 0f
            for (key in row.keys) {
                val keyWidth = totalWidth * (key.widthPercent * percentScale / 100f)
                val rect = RectF(
                    x + keyPadding,
                    y + keyPadding,
                    x + keyWidth - keyPadding,
                    y + rowHeight - keyPadding
                )
                keyRects.add(KeyRect(key, rect, rowIndex))
                x += keyWidth
            }
            y += rowHeight
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawPaint(backgroundPaint)
        for (kr in keyRects) {
            drawKey(canvas, kr)
        }
    }

    private fun drawKey(canvas: Canvas, kr: KeyRect) {
        val isPressed = pressedRect?.rect == kr.rect
        val key = kr.key
        val rect = kr.rect

        val bgPaint = when {
            isPressed -> keyPressedPaint
            key.code == KeyboardLayoutManager.CODE_ENTER -> enterPaint
            key.isFunctional || key.code == KeyboardLayoutManager.CODE_SPACE -> keyModifierPaint
            else -> keyPaint
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

        val text = displayLabelFor(key)
        val paint = when {
            key.code == KeyboardLayoutManager.CODE_ENTER -> enterLabelPaint
            key.isFunctional -> modifierLabelPaint
            else -> labelPaint
        }
        if (text.isNotEmpty()) {
            val cx = rect.centerX()
            val cy = rect.centerY() - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(text, cx, cy, paint)
        }
    }

    private fun displayLabelFor(key: Key): String {
        return when (key.code) {
            KeyboardLayoutManager.CODE_SHIFT -> if (isCapsLocked) "⇧⇧" else "⇧"
            KeyboardLayoutManager.CODE_BACKSPACE -> "⌫"
            KeyboardLayoutManager.CODE_ENTER -> enterActionLabel()
            KeyboardLayoutManager.CODE_EMOJI -> "😀"
            KeyboardLayoutManager.CODE_SYMBOLS -> "?123"
            KeyboardLayoutManager.CODE_LETTERS -> "ABC"
            KeyboardLayoutManager.CODE_LANGUAGE -> "🌐"
            KeyboardLayoutManager.CODE_VOICE -> "🎤"
            KeyboardLayoutManager.CODE_CLIPBOARD -> "📋"
            KeyboardLayoutManager.CODE_AI -> "✨"
            KeyboardLayoutManager.CODE_TRANSLATE -> "🌍"
            KeyboardLayoutManager.CODE_SETTINGS -> "⚙"
            KeyboardLayoutManager.CODE_SPACE -> {
                languageManager?.displayNameOf(languageManager?.currentLocale ?: "en") ?: "space"
            }
            else -> if (isShifted || isCapsLocked) key.label.uppercase() else key.label
        }
    }

    private fun enterActionLabel(): String {
        val action = (lastEditorInfo?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        return when (action) {
            EditorInfo.IME_ACTION_SEARCH -> "🔍"
            EditorInfo.IME_ACTION_GO -> "→"
            EditorInfo.IME_ACTION_SEND -> "➤"
            EditorInfo.IME_ACTION_NEXT -> "⤓"
            EditorInfo.IME_ACTION_DONE -> "✓"
            else -> "⏎"
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val target = keyRects.firstOrNull { it.rect.contains(x, y) }
                pressedRect = target
                pressedKey = target?.key
                if (target != null) {
                    HapticHelper.keyPress(this)
                    if (target.key.code == KeyboardLayoutManager.CODE_BACKSPACE) {
                        scheduleRepeatingDelete()
                    }
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                cancelRepeatingDelete()
                pressedRect?.let { handleKeyClick(it.key) }
                pressedRect = null
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelRepeatingDelete()
                pressedRect = null
                pressedKey = null
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val target = keyRects.firstOrNull { it.rect.contains(x, y) }
                if (target?.rect != pressedRect?.rect) {
                    pressedRect = target
                    pressedKey = target?.key
                    invalidate()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleKeyClick(key: Key) {
        val service = imeService ?: return
        when (key.code) {
            KeyboardLayoutManager.CODE_SHIFT -> handleShift()
            KeyboardLayoutManager.CODE_BACKSPACE -> service.sendBackspace()
            KeyboardLayoutManager.CODE_ENTER -> service.sendEnter()
            KeyboardLayoutManager.CODE_EMOJI -> service.openEmojiKeyboard()
            KeyboardLayoutManager.CODE_SYMBOLS -> {
                isSymbolMode = true
                reloadLayout()
            }
            KeyboardLayoutManager.CODE_LETTERS -> {
                isSymbolMode = false
                reloadLayout()
            }
            KeyboardLayoutManager.CODE_LANGUAGE -> {
                service.switchToNextLanguage()
                isShifted = false
                isSymbolMode = false
                reloadLayout()
            }
            KeyboardLayoutManager.CODE_VOICE -> service.startVoiceInput()
            KeyboardLayoutManager.CODE_CLIPBOARD -> service.toggleClipboardToolbar()
            KeyboardLayoutManager.CODE_AI -> service.runAiCorrection()
            KeyboardLayoutManager.CODE_TRANSLATE -> service.runAiTranslationDefault()
            KeyboardLayoutManager.CODE_SETTINGS -> service.openSettings()
            KeyboardLayoutManager.CODE_SPACE -> handleSpace(service)
            else -> handleCharacterKey(key, service)
        }
    }

    private fun handleShift() {
        val now = System.currentTimeMillis()
        if (now - lastShiftClickTime < 300) {
            isCapsLocked = !isCapsLocked
            isShifted = isCapsLocked
        } else {
            if (isCapsLocked) {
                isCapsLocked = false
                isShifted = false
            } else {
                isShifted = !isShifted
            }
        }
        lastShiftClickTime = now
        invalidate()
    }

    private fun handleSpace(service: NXInputMethodService) {
        val now = System.currentTimeMillis()
        if (now - lastSpaceClickTime < 300) {
            service.replaceLastWith(". ")
            lastSpaceClickTime = 0L
        } else {
            service.commitText(" ")
            lastSpaceClickTime = now
        }
    }

    private fun handleCharacterKey(key: Key, service: NXInputMethodService) {
        if (key.label.isEmpty()) return
        val text = if (isShifted || isCapsLocked) key.label.uppercase() else key.label
        service.commitText(text)
        if (isShifted && !isCapsLocked) {
            isShifted = false
            invalidate()
        }
    }

    private fun scheduleRepeatingDelete() {
        cancelRepeatingDelete()
        deleteRunnable = object : Runnable {
            override fun run() {
                imeService?.sendBackspace()
                handler.postDelayed(this, 50)
            }
        }
        handler.postDelayed(deleteRunnable!!, 400)
    }

    private fun cancelRepeatingDelete() {
        deleteRunnable?.let { handler.removeCallbacks(it) }
        deleteRunnable = null
    }

    private fun dp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}
