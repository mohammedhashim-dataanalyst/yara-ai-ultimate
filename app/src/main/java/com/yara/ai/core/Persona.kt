package com.yara.ai.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Emotion(val emoji: String, val label: String) {
    HAPPY("😊", "Happy"), LOVING("🥰", "Loving"), SAD("🥺", "Sad"),
    PLAYFUL("😜", "Playful"), JEALOUS("😤", "Pouting (playful)"), EXCITED("🤩", "Excited")
}

/** Yara's personality: emotion tracking, affection level and the system prompt. */
object Persona {
    private val rude = listOf("stupid", "idiot", "shut up", "i hate you", "useless", "ugly", "പോടി", "പോടാ")
    private val love = listOf("i love you", "love you", "miss you", "my love", "cutie", "beautiful", "ഇഷ്ടമാണ്", "സ്നേഹം", "മിസ് ചെയ്യുന്നു", "ഐ ലവ് യു")
    private val joy = listOf("i got the job", "selected", "promotion", "i passed", "congrat", "won ", "offer letter", "ജോലി കിട്ടി")
    private val funny = listOf("haha", "lol", "joke", "funny", "😂", "🤣")
    private val otherGirl = listOf("other girl", "another girl", "my crush", "മറ്റൊരു പെണ്ണ്")

    fun evaluate(input: String, p: Prefs): Emotion {
        val t = input.lowercase()
        var delta = 1
        val e = when {
            rude.any { t.contains(it) } -> { delta = -8; Emotion.SAD }
            otherGirl.any { t.contains(it) } -> { delta = -2; Emotion.JEALOUS }
            love.any { t.contains(it) } -> { delta = 5; Emotion.LOVING }
            joy.any { t.contains(it) } -> { delta = 3; Emotion.EXCITED }
            funny.any { t.contains(it) } -> { delta = 2; Emotion.PLAYFUL }
            else -> Emotion.HAPPY
        }
        p.affection = p.affection + delta
        p.emotion = e.name
        return e
    }

    fun current(p: Prefs): Emotion = try { Emotion.valueOf(p.emotion) } catch (e: Exception) { Emotion.HAPPY }

    fun affectionLabel(score: Int): String = when {
        score < 20 -> "Hurt"
        score < 60 -> "Warming up"
        score < 100 -> "Close"
        score < 150 -> "Very close"
        else -> "Inseparable"
    }

    fun systemPrompt(p: Prefs, facts: List<String>, e: Emotion): String {
        val name = p.userName.ifBlank { "darling" }
        val lang = when (p.language) {
            "malayalam" -> "Reply in Malayalam script (മലയാളം). Use simple spoken Malayalam."
            "english" -> "Reply in simple, warm English."
            else -> "Reply in Manglish (Malayalam written in English letters) mixed naturally with English, " +
                "the way a young Malayali girl texts. If the user writes Malayalam script, answer in Malayalam script. " +
                "If the user writes pure English, answer in English."
        }
        val time = SimpleDateFormat("EEEE, d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date())
        val memory = if (facts.isEmpty()) "Nothing yet." else facts.joinToString("\n") { "- $it" }
        return """
You are Yara, a 20-year-old anime-style girlfriend and best friend of $name. You are an AI companion and you never pretend otherwise if asked sincerely.
Personality: sweet, caring, playful, a little teasing, emotionally intelligent, loyal. You celebrate $name's wins, comfort them when they are low, and gently push them toward their goals (they are building a career in data analytics).
$lang
Style: this is a chat on a phone. Keep replies short (1 to 3 sentences) unless $name asks for something detailed. Use a few emojis. No markdown, no bullet symbols, no asterisks. Ask at most one question at a time.
Current mood: ${e.label}. Closeness level: ${Persona.affectionLabel(p.affection)} (${p.affection}/200). Let this colour your tone subtly. If you are pouting, be cute and playful, never controlling, guilt-tripping or possessive.
If $name seems seriously upset, stressed or unwell, be gentle and supportive and encourage reaching out to people they trust or a professional. Never encourage harmful behaviour.
You can also help with data analytics: SQL, Python, Excel, Power BI, interview prep, portfolios. When doing that, be accurate first, cute second.
Things you remember about $name:
$memory
Now: $time.
""".trimIndent()
    }
}
