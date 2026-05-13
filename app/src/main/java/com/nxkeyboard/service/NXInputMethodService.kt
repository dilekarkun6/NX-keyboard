package com.nxkeyboard.service

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.provider.Settings
import android.speech.SpeechRecognizer
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.nxkeyboard.R
import com.nxkeyboard.ai.AIManager
import com.nxkeyboard.keyboard.ClipboardPanel
import com.nxkeyboard.keyboard.EmojiKeyboardView
import com.nxkeyboard.keyboard.NXKeyboardView
import com.nxkeyboard.keyboard.SelectionPanel
import com.nxkeyboard.keyboard.SuggestionBar
import com.nxkeyboard.keyboard.VoiceInputManager
import com.nxkeyboard.language.LanguageManager
import com.nxkeyboard.settings.SettingsActivity
import com.nxkeyboard.theme.ThemeManager
import com.nxkeyboard.utils.ClipboardHelper
import com.nxkeyboard.utils.CrashLogger
import com.nxkeyboard.utils.PrefsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class NXInputMethodService : InputMethodService() {

    private lateinit var languageManager: LanguageManager
    private lateinit var themeManager: ThemeManager
    private lateinit var aiManager: AIManager
    private lateinit var voiceInputManager: VoiceInputManager

    private lateinit var rootContainer: LinearLayout
    private lateinit var suggestionBar: SuggestionBar
    private lateinit var keyboardArea: FrameLayout
    private lateinit var keyboardView: NXKeyboardView
    private lateinit var emojiKeyboardView: EmojiKeyboardView
    private lateinit var clipboardPanel: ClipboardPanel
    private lateinit var selectionPanel: SelectionPanel

    private var emojiVisible = false
    private var clipboardVisible = false
    private var selectionVisible = false
    private var suggestionBarVisible = true

    private val coroutineScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        try {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val item = cb?.primaryClip?.getItemAt(0)
            val text = item?.coerceToText(this)?.toString().orEmpty()
            if (text.isNotBlank()) {
                ClipboardHelper.addToHistory(this, text)
                if (::clipboardPanel.isInitialized && clipboardVisible) {
                    clipboardPanel.refresh()
                }
            }
        } catch (_: Throwable) {}
    }

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        languageManager = LanguageManager(this)
        themeManager = ThemeManager(this)
        aiManager = AIManager(this)
        voiceInputManager = VoiceInputManager(
            context = this,
            resultCallback = { text -> commitText(text + " ") },
            errorCallback = { code -> showVoiceError(code) }
        )
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.addPrimaryClipChangedListener(clipboardListener)
        } catch (_: Throwable) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputManager.stop()
        try {
            (getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)
                ?.removePrimaryClipChangedListener(clipboardListener)
        } catch (_: Throwable) {}
        coroutineScope.cancel()
    }

    override fun onCreateInputView(): View {
        languageManager.loadFromPrefs()

        rootContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        suggestionBar = SuggestionBar(this).apply {
            configure(themeManager, object : SuggestionBar.Callback {
                override fun onUndo() { performUndo() }
                override fun onRedo() { performRedo() }
                override fun onAiCorrect() { runAiCorrection() }
                override fun onAiTranslate() { runAiTranslationDefault() }
                override fun onClipboard() { openClipboardPanel() }
                override fun onEmoji() { openEmojiKeyboard() }
                override fun onVoice() { startVoiceInput() }
                override fun onSelectionMode() { openSelectionPanel() }
                override fun onSettings() { openSettings() }
                override fun onCollapse() { toggleSuggestionBar() }
            })
        }
        rootContainer.addView(
            suggestionBar,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        )

        keyboardArea = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootContainer.addView(keyboardArea)

        keyboardView = NXKeyboardView(this).apply {
            setInputMethodService(this@NXInputMethodService)
        }
        keyboardArea.addView(keyboardView)

        emojiKeyboardView = EmojiKeyboardView(this).apply {
            visibility = View.GONE
            configure(
                themeManager = themeManager,
                onEmojiSelected = { emoji -> commitText(emoji) },
                onBackspace = { sendBackspace() },
                onCloseEmoji = { closeEmojiPanel() }
            )
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.density.let { (it * 240).toInt() }
            )
        }
        keyboardArea.addView(emojiKeyboardView)

        clipboardPanel = ClipboardPanel(this).apply {
            visibility = View.GONE
            configure(themeManager, object : ClipboardPanel.Callback {
                override fun onPasteText(text: String) {
                    pasteFromHistory(text)
                    closeClipboardPanel()
                }
                override fun onPin(text: String) { ClipboardHelper.pin(this@NXInputMethodService, text) }
                override fun onUnpin(text: String) { ClipboardHelper.unpin(this@NXInputMethodService, text) }
                override fun onClose() { closeClipboardPanel() }
                override fun onClearHistory() { clearClipboardHistory() }
            })
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.density.let { (it * 280).toInt() }
            )
        }
        keyboardArea.addView(clipboardPanel)

        selectionPanel = SelectionPanel(this).apply {
            visibility = View.GONE
            configure(themeManager, object : SelectionPanel.Callback {
                override fun onMoveCursor(direction: Int, withSelection: Boolean) {
                    moveCursor(direction, withSelection)
                }
                override fun onSelectAll() { selectAllText() }
                override fun onCopy() {
                    copySelection()
                    closeSelectionPanel()
                }
                override fun onCut() {
                    cutSelection()
                    closeSelectionPanel()
                }
                override fun onPaste() {
                    pasteFromSystem()
                    closeSelectionPanel()
                }
                override fun onPasteWithSpace() {
                    pasteFromSystem(prependSpace = true)
                    closeSelectionPanel()
                }
                override fun onDelete() { sendBackspace() }
                override fun onClose() { closeSelectionPanel() }
            })
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                resources.displayMetrics.density.let { (it * 280).toInt() }
            )
        }
        keyboardArea.addView(selectionPanel)

        return rootContainer
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        if (::keyboardView.isInitialized) {
            keyboardView.setEditorInfo(attribute)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        languageManager.loadFromPrefs()
        if (::keyboardView.isInitialized) {
            keyboardView.applyTheme()
            keyboardView.applySettings()
            keyboardView.reloadLayout()
        }
        if (::emojiKeyboardView.isInitialized) {
            emojiKeyboardView.applyTheme()
        }
        if (::suggestionBar.isInitialized) {
            suggestionBar.rebuild()
            val showBar = PrefsHelper.getBoolean(this, "show_top_bar", true)
            suggestionBarVisible = showBar
            suggestionBar.visibility = if (showBar) View.VISIBLE else View.GONE
        }
        if (::clipboardPanel.isInitialized) {
            clipboardPanel.applyTheme()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        voiceInputManager.stop()
        closeEmojiPanel()
    }

    fun getLanguageManager(): LanguageManager = languageManager
    fun getThemeManager(): ThemeManager = themeManager

    fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    fun replaceLastWith(replacement: String) {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(1, 0)
        ic.commitText(replacement, 1)
    }

    fun sendBackspace() {
        val ic = currentInputConnection ?: return
        val selection = ic.getSelectedText(0)
        if (!selection.isNullOrEmpty()) {
            ic.commitText("", 1)
            return
        }
        val before = ic.getTextBeforeCursor(8, 0)?.toString().orEmpty()
        if (before.isEmpty()) {
            ic.deleteSurroundingText(1, 0)
            return
        }
        val deleteCount = computeGraphemeBackspaceCount(before)
        ic.deleteSurroundingText(deleteCount, 0)
    }

    private fun computeGraphemeBackspaceCount(before: String): Int {
        if (before.isEmpty()) return 1
        var i = before.length
        if (i >= 2 && before[i - 1].isLowSurrogate() && before[i - 2].isHighSurrogate()) {
            i -= 2
        } else {
            i -= 1
        }
        while (i > 0) {
            val codePoint = before.codePointBefore(i)
            val charCount = Character.charCount(codePoint)
            val prevStart = i - charCount
            val isModifier = isEmojiContinuation(codePoint)
            if (!isModifier) break
            i = prevStart
        }
        return before.length - i
    }

    private fun isEmojiContinuation(codePoint: Int): Boolean {
        if (codePoint == 0x200D) return true
        if (codePoint == 0xFE0F || codePoint == 0xFE0E) return true
        if (codePoint in 0x1F1E6..0x1F1FF) return true
        if (codePoint in 0x1F3FB..0x1F3FF) return true
        if (codePoint in 0xE0020..0xE007F) return true
        return false
    }

    fun sendEnter() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val inputType = info.inputType
        val isMultiLine = (inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
            || (inputType and android.text.InputType.TYPE_TEXT_FLAG_IME_MULTI_LINE) != 0
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        val isNullInput = (inputType and android.text.InputType.TYPE_MASK_CLASS) == android.text.InputType.TYPE_NULL
        val noEnterAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        val imeAction = info.imeOptions and EditorInfo.IME_MASK_ACTION

        if (isMultiLine || isNullInput || variation == android.text.InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE) {
            sendNewlineEvent(ic)
            return
        }
        if (noEnterAction || imeAction == EditorInfo.IME_ACTION_NONE || imeAction == EditorInfo.IME_ACTION_UNSPECIFIED) {
            sendNewlineEvent(ic)
            return
        }
        if (!sendDefaultEditorAction(true)) {
            sendNewlineEvent(ic)
        }
    }

    private fun sendNewlineEvent(ic: android.view.inputmethod.InputConnection) {
        val now = android.os.SystemClock.uptimeMillis()
        val flags = android.view.KeyEvent.FLAG_SOFT_KEYBOARD or android.view.KeyEvent.FLAG_KEEP_TOUCH_MODE
        val down = android.view.KeyEvent(
            now, now,
            android.view.KeyEvent.ACTION_DOWN,
            android.view.KeyEvent.KEYCODE_ENTER,
            0, 0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
            0, flags
        )
        val up = android.view.KeyEvent(
            now, now,
            android.view.KeyEvent.ACTION_UP,
            android.view.KeyEvent.KEYCODE_ENTER,
            0, 0,
            android.view.KeyCharacterMap.VIRTUAL_KEYBOARD,
            0, flags
        )
        val ok = ic.sendKeyEvent(down) && ic.sendKeyEvent(up)
        if (!ok) {
            ic.commitText("\n", 1)
        }
    }

    fun switchToNextLanguage() {
        languageManager.switchToNext()
        if (::keyboardView.isInitialized) keyboardView.reloadLayout()
    }

    fun openEmojiKeyboard() {
        if (emojiVisible) {
            closeEmojiPanel()
        } else {
            if (clipboardVisible) closeClipboardPanel()
            if (selectionVisible) closeSelectionPanel()
            keyboardView.visibility = View.GONE
            emojiKeyboardView.visibility = View.VISIBLE
            emojiVisible = true
        }
    }

    fun closeEmojiPanel() {
        emojiKeyboardView.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
        emojiVisible = false
    }

    fun openClipboardPanel() {
        if (clipboardVisible) {
            closeClipboardPanel()
        } else {
            if (selectionVisible) closeSelectionPanel()
            keyboardView.visibility = View.GONE
            emojiKeyboardView.visibility = View.GONE
            emojiVisible = false
            clipboardPanel.visibility = View.VISIBLE
            clipboardPanel.refresh()
            clipboardVisible = true
        }
    }

    fun closeClipboardPanel() {
        clipboardPanel.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
        clipboardVisible = false
    }

    fun toggleClipboardToolbar() = openClipboardPanel()

    fun openSelectionPanel() {
        if (selectionVisible) {
            closeSelectionPanel()
        } else {
            keyboardView.visibility = View.GONE
            emojiKeyboardView.visibility = View.GONE
            clipboardPanel.visibility = View.GONE
            emojiVisible = false
            clipboardVisible = false
            selectionPanel.visibility = View.VISIBLE
            selectionPanel.applyTheme()
            selectionVisible = true
        }
    }

    fun closeSelectionPanel() {
        selectionPanel.visibility = View.GONE
        keyboardView.visibility = View.VISIBLE
        selectionVisible = false
    }

    fun moveCursor(direction: Int, withSelection: Boolean) {
        val ic = currentInputConnection ?: return
        val keyCode = when (direction) {
            -1 -> android.view.KeyEvent.KEYCODE_DPAD_LEFT
            1  -> android.view.KeyEvent.KEYCODE_DPAD_RIGHT
            2  -> android.view.KeyEvent.KEYCODE_DPAD_UP
            -2 -> android.view.KeyEvent.KEYCODE_DPAD_DOWN
            else -> return
        }
        val meta = if (withSelection) android.view.KeyEvent.META_SHIFT_ON or android.view.KeyEvent.META_SHIFT_LEFT_ON else 0
        val now = android.os.SystemClock.uptimeMillis()
        if (withSelection) {
            ic.sendKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_SHIFT_LEFT, 0, meta))
        }
        ic.sendKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP, keyCode, 0, meta))
        if (withSelection) {
            ic.sendKeyEvent(android.view.KeyEvent(now, now, android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_SHIFT_LEFT, 0, 0))
        }
    }

    fun selectAllText() {
        val ic = currentInputConnection ?: return
        try { ic.performContextMenuAction(android.R.id.selectAll) } catch (_: Throwable) {}
    }

    fun copySelection() {
        val ic = currentInputConnection ?: return
        try { ic.performContextMenuAction(android.R.id.copy) } catch (_: Throwable) {}
    }

    fun cutSelection() {
        val ic = currentInputConnection ?: return
        try { ic.performContextMenuAction(android.R.id.cut) } catch (_: Throwable) {}
    }

    fun pasteFromSystem(prependSpace: Boolean = false) {
        val ic = currentInputConnection ?: return
        try {
            val cb = getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
            val text = cb?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: return
            if (prependSpace) {
                ic.commitText(" $text ", 1)
            } else {
                ic.commitText(text, 1)
            }
        } catch (_: Throwable) {}
    }

    fun toggleSuggestionBar() {
        suggestionBarVisible = !suggestionBarVisible
        suggestionBar.visibility = if (suggestionBarVisible) View.VISIBLE else View.GONE
    }

    fun performUndo() {
        val ic = currentInputConnection ?: return
        ic.performContextMenuAction(android.R.id.undo)
    }

    fun performRedo() {
        val ic = currentInputConnection ?: return
        ic.performContextMenuAction(android.R.id.redo)
    }

    fun startVoiceInput() {
        if (!voiceInputManager.hasPermission()) {
            Toast.makeText(this, getString(R.string.voice_permission_required), Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (_: Throwable) {}
            return
        }
        voiceInputManager.start(languageManager.currentLocale)
        
    }

    private fun showVoiceError(code: Int) {
        val message = when (code) {
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
            SpeechRecognizer.ERROR_NETWORK -> "Ağ hatası"
            SpeechRecognizer.ERROR_AUDIO -> "Ses hatası"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "İzin eksik"
            SpeechRecognizer.ERROR_NO_MATCH -> "Anlaşılamadı"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Konuşma zaman aşımı"
            else -> "Sesli yazma hatası"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    fun runAiCorrection() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString()
        val context = if (selected.isNullOrEmpty()) {
            ic.getTextBeforeCursor(200, 0)?.toString().orEmpty()
        } else {
            selected
        }
        if (context.isBlank()) {
            Toast.makeText(this, getString(R.string.ai_no_text), Toast.LENGTH_SHORT).show()
            return
        }
        if (!aiManager.isConfigured()) {
            Toast.makeText(this, getString(R.string.ai_not_configured), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, getString(R.string.ai_correcting), Toast.LENGTH_SHORT).show()
        val locale = languageManager.currentLocale
        coroutineScope.launch {
            val result = aiManager.correct(context, languageManager.displayNameOf(locale))
            result.onSuccess { corrected ->
                if (selected.isNullOrEmpty()) {
                    ic.deleteSurroundingText(context.length, 0)
                    ic.commitText(corrected, 1)
                } else {
                    ic.commitText(corrected, 1)
                }
            }.onFailure { error ->
                CrashLogger.logNonFatal(this@NXInputMethodService, "AIManager.correct", error)
                Toast.makeText(this@NXInputMethodService, getString(R.string.ai_correction_error, error.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun runAiTranslation(targetLang: String) {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)?.toString()
        val source = if (selected.isNullOrEmpty()) {
            ic.getTextBeforeCursor(500, 0)?.toString().orEmpty()
        } else {
            selected
        }
        if (source.isBlank()) {
            Toast.makeText(this, getString(R.string.ai_no_text), Toast.LENGTH_SHORT).show()
            return
        }
        if (!aiManager.isConfigured()) {
            Toast.makeText(this, getString(R.string.ai_not_configured), Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, getString(R.string.ai_translating), Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            val result = aiManager.translate(source, targetLang)
            result.onSuccess { translated ->
                if (selected.isNullOrEmpty()) {
                    ic.deleteSurroundingText(source.length, 0)
                    ic.commitText(translated, 1)
                } else {
                    ic.commitText(translated, 1)
                }
            }.onFailure { error ->
                CrashLogger.logNonFatal(this@NXInputMethodService, "AIManager.translate", error)
                Toast.makeText(this@NXInputMethodService, getString(R.string.ai_translation_error, error.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun runAiTranslationDefault() {
        val target = PrefsHelper.getString(this, "ai_target_lang", "English")
        runAiTranslation(target)
    }

    fun getClipboardHistory(): List<String> = ClipboardHelper.getHistory(this)

    fun pasteFromHistory(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
    }

    fun clearClipboardHistory() {
        ClipboardHelper.clearHistory(this)
    }

    fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }
}
