package com.yara.ai.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Text-to-speech with a girly pitch, Malayalam when the phone has the voice, else Indian English. */
class Speaker(ctx: Context, private val prefs: Prefs) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = TextToSpeech(ctx.applicationContext, this)
    @Volatile private var ready = false
    private var pending: Pair<String, (() -> Unit)?>? = null
    @Volatile private var callback: (() -> Unit)? = null

    override fun onInit(status: Int) {
        val t = tts ?: return
        if (status != TextToSpeech.SUCCESS) { pending?.second?.invoke(); pending = null; return }
        applyLanguage()
        t.setPitch(1.15f)
        t.setSpeechRate(1.0f)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { fire() }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { fire() }
        })
        ready = true
        pending?.let { speak(it.first, it.second) }
        pending = null
    }

    private fun fire() {
        val cb = callback
        callback = null
        cb?.invoke()
    }

    fun applyLanguage() {
        val t = tts ?: return
        val wanted = if (prefs.language == "english") Locale("en", "IN") else Locale("ml", "IN")
        val r = t.setLanguage(wanted)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale("en", "IN"))
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        val t = tts
        if (t == null) { onDone?.invoke(); return }
        if (!ready) { pending = Pair(text, onDone); return }
        val clean = clean(text)
        if (clean.isBlank()) { onDone?.invoke(); return }
        applyLanguage()
        callback = onDone
        val r = t.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "yara-" + System.nanoTime())
        if (r == TextToSpeech.ERROR) fire()
    }

    fun stop() { callback = null; tts?.stop() }

    fun shutdown() {
        callback = null
        tts?.stop(); tts?.shutdown(); tts = null; ready = false
    }

    private fun clean(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            if (Character.isSurrogate(ch)) continue
            val type = Character.getType(ch)
            if (type == Character.OTHER_SYMBOL.toInt() || type == Character.MATH_SYMBOL.toInt()) continue
            if (ch == '*' || ch == '#' || ch == '_' || ch == '`' || ch == '~') continue
            sb.append(ch)
        }
        return sb.toString().trim()
    }
}
