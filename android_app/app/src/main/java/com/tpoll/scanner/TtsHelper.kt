package com.tpoll.scanner

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

class TtsHelper(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private val prefs = context.getSharedPreferences("tts_prefs", Context.MODE_PRIVATE)

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("pt", "BR")
                isInitialized = true
            }
        }
    }

    fun isEnabled(): Boolean = prefs.getBoolean("tts_enabled", false)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("tts_enabled", enabled).apply()
    }

    fun speak(text: String) {
        if (!isEnabled() || !isInitialized) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null)
        }
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
