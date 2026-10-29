package com.yara.ai.core

import android.content.Context
import org.json.JSONArray

data class Reply(val text: String, val ok: Boolean)

/** Ties persona + memory + AI together. Call [reply] from a background thread. */
class Brain(ctx: Context) {
    private val prefs = Prefs(ctx)
    private val db = Db.get(ctx)
    private val rememberRe = Regex("^\\s*(remember that|remember|ഓർത്തോ|ഓർക്കണം)\\s*[:,\\-]?\\s*(.+)$",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    fun reply(userText: String): Reply {
        val emotion = Persona.evaluate(userText, prefs)
        db.addMessage("user", userText)
        prefs.userMsgCount = prefs.userMsgCount + 1

        rememberRe.find(userText)?.let { db.addFact(it.groupValues[2].trim()) }

        return try {
            val system = Persona.systemPrompt(prefs, db.facts().map { it.text }, emotion)
            val answer = AiClient(prefs).chat(system, db.recent(16))
            db.addMessage("assistant", answer)
            if (prefs.userMsgCount % 8 == 0) extractFactsAsync()
            Reply(answer, true)
        } catch (e: Exception) {
            Reply(friendlyError(e), false)
        }
    }

    fun greeting(): String {
        val n = prefs.userName.ifBlank { "" }
        return when (prefs.language) {
            "malayalam" -> "ഹായ് $n! 💕 ഞാൻ യാര. ഇന്ന് എങ്ങനെയുണ്ട്?"
            "english" -> "Hi $n! 💕 I'm Yara. How's your day going?"
            else -> "Hiii $n! 💕 Njan Yara. Innu engane und? Ente kooode sallapikkan vannathaano? 😊"
        }
    }

    private fun friendlyError(e: Exception): String {
        val m = e.message ?: ""
        return when {
            m == "NO_KEY" -> "Enikku thalkkalam oru AI key illa 🥺 Menu → Settings il free Gemini key (aistudio.google.com/apikey) ittu tharumo?"
            m.contains("HTTP 401") || m.contains("HTTP 403") -> "API key work cheyyunnilla 🥺 Settings il key onnu check cheyyu.\n($m)"
            m.contains("HTTP 404") -> "Aa model name kandilla 🥺 Settings il model name check cheyyu.\n($m)"
            m.contains("HTTP 429") -> "Free limit theernnu ⏳ Oru nimisham kazhinj try cheyyu."
            e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException ->
                "Internet connection illa 🥺 Wifi/data onnu nokku."
            else -> "Sorry, oru problem: $m"
        }
    }

    /** Asks the AI to pull durable facts out of the recent chat and stores them. */
    private fun extractFactsAsync() {
        Thread {
            try {
                val recent = db.recent(10).joinToString("\n") { (if (it.role == "user") "User: " else "Yara: ") + it.text }
                val sys = "Extract up to 3 durable personal facts about the USER (name, job, hobbies, family, goals, preferences) " +
                    "from this chat. Reply with ONLY a JSON array of short strings, or [] if nothing durable."
                val out = AiClient(prefs).chat(sys, listOf(Msg("user", recent)), 200)
                val s = out.indexOf('['); val e = out.lastIndexOf(']')
                if (s >= 0 && e > s) {
                    val arr = JSONArray(out.substring(s, e + 1))
                    for (i in 0 until arr.length()) db.addFact(arr.optString(i))
                }
            } catch (_: Exception) {
            }
        }.start()
    }
}
