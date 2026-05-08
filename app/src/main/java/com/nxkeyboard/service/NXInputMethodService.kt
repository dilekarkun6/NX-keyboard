package com.nxkeyboard.service

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
import com.nxkeyboard.ai.AIManager
import com.nxkeyboard.keyboard.ClipboardToolbar
import com.nxkeyboard.keyboard.EmojiKeyboardView
import com.nxkeyboard.keyboard.NXKeyboardView
import com.nxkeyboard.keyboard.VoiceInputManager
import com.nxkeyboard.language.LanguageManager
import com.nxkeyboard.settings.SettingsActivity
import com.nxkeyboard.theme.ThemeManager
import com.nxkeyboard.utils.ClipboardHelper
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
    private lateinit var toolbarContainer: FrameLayout
    private lateinit var keyboardArea: FrameLayout
    private lateinit var keyboardView: NXKeyboardView
    private lateinit var emojiKeyboardView: EmojiKeyboardView
    private lateinit var clipboardToolbar: ClipboardToolbar

    private var emojiVisible = false
    private var clipboardVisible = false

    private val coroutineScope: CoroutineScope by lazy {
        CoroutineScope(Dispatchers.Main + SupervisorJob())
    }

    override fun onCreate() {
        super.onCreate()
        languageManager = LanguageManager(this)
        themeManager = ThemeManager(this)
        aiManager = AIManager(this)
        voiceInputManager = VoiceInputManager(
            context = this,
            onResult = { text -> commitText(text + " ") },
            onError = { code -> showVoiceError(code) }
        )
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

        toolbarContainer = FrameLayout(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootContainer.addView(toolbarContainer)

        clipboardToolbar = ClipboardToolbar(this).apply {
            configure(themeManager, object : ClipboardToolbar.Callback {
                override fun onCopy() { ClipboardHelper.copy(this@NXInputMethodService, currentInputConnection) }
                override fun onCut() { ClipboardHelper.cut(this@NXInputMethodService, currentInputConnection) }
                override fun onPaste() { ClipboardHelper.paste(this@NXInputMethodService, currentInputConnection) }
                override fun onSelectAll() { ClipboardHelper.selectAll(currentInputConnection) }
                override fun onCursorLeft() { ClipboardHelper.moveCursor(currentInputConnection, -1) }
                override fun onCursorRight() { ClipboardHelper.moveCursor(currentInputConnection, 1) }
                override fun onClose() { toggleClipboardToolbar() }
                override fun onAiCorrect() { runAiCorrection() }
                override fun onAiTranslate() { runAiTranslationDefault() }
                override fun onPasteHistoryItem(text: String) {
                    pasteFromHistory(text)
                }
                override fun onClearHistory() {
                    clearClipboardHistory()
                }
                override fun getHistorySnapshot(): List<String> {
                    return getClipboardHistory()
                }
            })
        }
        toolbarContainer.addView(clipboardToolbar)

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
        if (::keyboardView.isInitialized) {
            keyboardView.applyTheme()
            keyboardView.reloadLayout()
        }
        if (::emojiKeyboardView.isInitialized) {
            emojiKeyboardView.applyTheme()
        }
        if (::clipboardToolbar.isInitialized) {
            clipboardToolbar.applyTheme()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        voiceInputManager.stop()
        closeEmojiPanel()
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceInputManager.stop()
        coroutineScope.cancel()
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
        if (selection.isNullOrEmpty()) {
            ic.deleteSurroundingText(1, 0)
        } else {
            ic.commitText("", 1)
        }
    }

    fun sendEnter() {
        val info = currentInputEditorInfo
        val imeAction = info.imeOptions and EditorInfo.IME_MASK_ACTION
        when (imeAction) {
            EditorInfo.IME_ACTION_SEARCH,
            EditorInfo.IME_ACTION_GO,
            EditorInfo.IME_ACTION_SEND,
            EditorInfo.IME_ACTION_NEXT,
            EditorInfo.IME_ACTION_DONE -> sendDefaultEditorAction(true)
            else -> currentInputConnection?.commitText("\n", 1)
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

    fun toggleClipboardToolbar() {
        clipboardVisible = !clipboardVisible
        toolbarContainer.visibility = if (clipboardVisible) View.VISIBLE else View.GONE
        if (clipboardVisible && ::clipboardToolbar.isInitialized) {
            clipboardToolbar.refreshHistory()
        }
    }

    fun startVoiceInput() {
        if (!voiceInputManager.hasPermission()) {
            Toast.makeText(this, "Mikrofon izni gerekli — Ayarlardan verin", Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, "🎤 Konuşun…", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Düzeltilecek metin yok", Toast.LENGTH_SHORT).show()
            return
        }
        if (!aiManager.isConfigured()) {
            Toast.makeText(this, "AI yapılandırılmamış", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "✨ AI çalışıyor…", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@NXInputMethodService, "AI hatası: ${error.message}", Toast.LENGTH_SHORT).show()
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
            Toast.makeText(this, "Çevirilecek metin yok", Toast.LENGTH_SHORT).show()
            return
        }
        if (!aiManager.isConfigured()) {
            Toast.makeText(this, "AI yapılandırılmamış", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "✨ Çeviriliyor…", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this@NXInputMethodService, "Çeviri hatası: ${error.message}", Toast.LENGTH_SHORT).show()
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
