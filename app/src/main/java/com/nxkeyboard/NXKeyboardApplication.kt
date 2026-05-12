package com.nxkeyboard

import android.app.Application
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat

class NXKeyboardApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            val config = BundledEmojiCompatConfig(this)
                .setReplaceAll(true)
                .setMetadataLoadStrategy(EmojiCompat.LOAD_STRATEGY_DEFAULT)
                .registerInitCallback(object : EmojiCompat.InitCallback() {
                    override fun onInitialized() {
                        EmojiCompatState.ready = true
                        EmojiCompatState.notifyReady()
                    }
                    override fun onFailed(throwable: Throwable?) {
                        EmojiCompatState.ready = false
                    }
                })
            EmojiCompat.init(config)
            try { EmojiCompat.get().load() } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }
}

object EmojiCompatState {
    @Volatile var ready: Boolean = false
    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun addListener(listener: () -> Unit) {
        listeners += listener
        if (ready) listener()
    }

    @Synchronized
    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    @Synchronized
    fun notifyReady() {
        for (l in listeners.toList()) {
            try { l() } catch (_: Throwable) {}
        }
    }
}
