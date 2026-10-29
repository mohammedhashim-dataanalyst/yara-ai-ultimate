package com.yara.ai.core

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

data class Preset(val name: String, val provider: String, val baseUrl: String, val model: String)

object Presets {
    val all = listOf(
        Preset("Google Gemini (free key)", "gemini", "", "gemini-2.5-flash"),
        Preset("Groq (free key)", "openai", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        Preset("OpenRouter (free models)", "openai", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free"),
        Preset("OpenAI GPT", "openai", "https://api.openai.com/v1", "gpt-4o-mini"),
        Preset("xAI Grok", "openai", "https://api.x.ai/v1", "grok-3-mini"),
        Preset("Anthropic Claude", "anthropic", "", "claude-haiku-5-5"),
        Preset("Custom (OpenAI-compatible)", "openai", "", "")
    )
}

/** One client for Gemini, any OpenAI-compatible API (OpenAI, Groq, OpenRouter, Grok...) and Claude. */
class AiClient(private val p: Prefs) {

    fun chat(system: String, history: List<Msg>, maxTokens: Int = 700): String {
        if (p.apiKey.isBlank()) throw IOException("NO_KEY")
        val msgs = normalize(history)
        if (msgs.isEmpty()) throw IOException("Nothing to send")
        return when (p.provider) {
            "gemini" -> gemini(system, msgs, maxTokens)
            "anthropic" -> anthropic(system, msgs, maxTokens)
            else -> openai(system, msgs, maxTokens)
        }
    }

    /** Roles must start with "user" and alternate; merge neighbours with the same role. */
    private fun normalize(h: List<Msg>): List<Msg> {
        val out = ArrayList<Msg>()
        for (m in h) {
            val role = if (m.role == "user") "user" else "assistant"
            if (m.text.isBlank()) continue
            if (out.isEmpty() && role != "user") continue
            if (out.isNotEmpty() && out.last().role == role) {
                val last = out.removeAt(out.size - 1)
                out.add(Msg(role, last.text + "\n" + m.text))
            } else out.add(Msg(role, m.text))
        }
        return out
    }

    private fun gemini(system: String, msgs: List<Msg>, maxTokens: Int): String {
        val contents = JSONArray()
        for (m in msgs) {
            contents.put(JSONObject()
                .put("role", if (m.role == "user") "user" else "model")
                .put("parts", JSONArray().put(JSONObject().put("text", m.text))))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", maxTokens).put("temperature", 0.9))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${p.model.trim()}:generateContent"
        val res = Http.postJson(url, mapOf("x-goog-api-key" to p.apiKey), body.toString())
        val cands = JSONObject(res).optJSONArray("candidates")
        if (cands == null || cands.length() == 0) throw IOException("Gemini returned no answer (blocked or empty).")
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini returned an empty answer.")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.getJSONObject(i).optString("text"))
        return sb.toString().trim().ifEmpty { throw IOException("Gemini returned an empty answer.") }
    }

    private fun openai(system: String, msgs: List<Msg>, maxTokens: Int): String {
        val arr = JSONArray()
        arr.put(JSONObject().put("role", "system").put("content", system))
        for (m in msgs) arr.put(JSONObject().put("role", m.role).put("content", m.text))
        val body = JSONObject().put("model", p.model.trim()).put("messages", arr)
            .put("max_tokens", maxTokens).put("temperature", 0.9)
        val base = p.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw IOException("Set the Base URL in Settings.")
        val res = Http.postJson("$base/chat/completions", mapOf("Authorization" to "Bearer ${p.apiKey}"), body.toString())
        val choices = JSONObject(res).optJSONArray("choices")
        if (choices == null || choices.length() == 0) throw IOException("The model returned no answer.")
        return choices.getJSONObject(0).getJSONObject("message").optString("content").trim()
            .ifEmpty { throw IOException("The model returned an empty answer.") }
    }

    private fun anthropic(system: String, msgs: List<Msg>, maxTokens: Int): String {
        val arr = JSONArray()
        for (m in msgs) arr.put(JSONObject().put("role", m.role).put("content", m.text))
        val body = JSONObject().put("model", p.model.trim()).put("max_tokens", maxTokens)
            .put("system", system).put("messages", arr)
        val res = Http.postJson(
            "https://api.anthropic.com/v1/messages",
            mapOf("x-api-key" to p.apiKey, "anthropic-version" to "2023-06-01"),
            body.toString()
        )
        val content = JSONObject(res).optJSONArray("content") ?: throw IOException("Claude returned no answer.")
        val sb = StringBuilder()
        for (i in 0 until content.length()) {
            val b = content.getJSONObject(i)
            if (b.optString("type") == "text") sb.append(b.optString("text"))
        }
        return sb.toString().trim().ifEmpty { throw IOException("Claude returned an empty answer.") }
    }
}
