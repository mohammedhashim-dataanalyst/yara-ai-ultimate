package com.yara.ai.core

import android.content.Context

/** All user settings, stored in SharedPreferences. */
class Prefs(ctx: Context) {
    private val sp = ctx.applicationContext.getSharedPreferences("yara_prefs", Context.MODE_PRIVATE)

    var presetIndex: Int
        get() = sp.getInt("preset", 0)
        set(v) = sp.edit().putInt("preset", v).apply()
    var baseUrl: String
        get() = sp.getString("base_url", Presets.all[0].baseUrl) ?: ""
        set(v) = sp.edit().putString("base_url", v).apply()
    var model: String
        get() = sp.getString("model", Presets.all[0].model) ?: ""
        set(v) = sp.edit().putString("model", v).apply()
    var apiKey: String
        get() = sp.getString("api_key", "") ?: ""
        set(v) = sp.edit().putString("api_key", v.trim()).apply()
    var userName: String
        get() = sp.getString("user_name", "") ?: ""
        set(v) = sp.edit().putString("user_name", v.trim()).apply()
    /** manglish | malayalam | english */
    var language: String
        get() = sp.getString("language", "manglish") ?: "manglish"
        set(v) = sp.edit().putString("language", v).apply()
    var voiceReplies: Boolean
        get() = sp.getBoolean("voice_replies", true)
        set(v) = sp.edit().putBoolean("voice_replies", v).apply()
    var wakeEnabled: Boolean
        get() = sp.getBoolean("wake_enabled", false)
        set(v) = sp.edit().putBoolean("wake_enabled", v).apply()
    var affection: Int
        get() = sp.getInt("affection", 60)
        set(v) = sp.edit().putInt("affection", v.coerceIn(0, 200)).apply()
    var emotion: String
        get() = sp.getString("emotion", "HAPPY") ?: "HAPPY"
        set(v) = sp.edit().putString("emotion", v).apply()
    var userMsgCount: Int
        get() = sp.getInt("user_msg_count", 0)
        set(v) = sp.edit().putInt("user_msg_count", v).apply()

    val provider: String get() = Presets.all.getOrElse(presetIndex) { Presets.all[0] }.provider

    /** Language tag for speech recognition and text-to-speech. */
    val speechTag: String get() = if (language == "english") "en-IN" else "ml-IN"
}
