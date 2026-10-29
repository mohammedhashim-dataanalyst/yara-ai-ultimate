package com.yara.ai.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.yara.ai.core.AiClient
import com.yara.ai.core.Msg
import com.yara.ai.core.Perm
import com.yara.ai.core.Prefs
import com.yara.ai.core.Presets

class SettingsActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var baseUrl: android.widget.EditText
    private lateinit var model: android.widget.EditText
    private lateinit var key: android.widget.EditText
    private lateinit var name: android.widget.EditText
    private lateinit var testResult: TextView
    private lateinit var presetSpinner: Spinner
    private lateinit var langSpinner: Spinner
    private val langKeys = listOf("manglish", "malayalam", "english")
    private val langNames = listOf("Manglish (Malayalam + English)", "Malayalam (script)", "English")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(this, "Settings", "AI brain, voice & background") { finish() })

        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(this, 16)
        col.setPadding(pad, pad, pad, pad * 2)
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // --- AI brain ---
        col.addView(Ui.label(this, "AI BRAIN", 12f, Ui.RED, true))
        col.addView(Ui.label(this, "Pick a provider and paste its API key. Gemini and Groq have free keys.", 12f, Ui.MUTED))
        presetSpinner = Spinner(this)
        presetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, Presets.all.map { it.name })
        presetSpinner.setSelection(prefs.presetIndex.coerceIn(0, Presets.all.size - 1))
        col.addView(presetSpinner, Ui.vlp(6, this))

        baseUrl = Ui.input(this, "Base URL (OpenAI-compatible providers)")
        baseUrl.setText(prefs.baseUrl)
        model = Ui.input(this, "Model name")
        model.setText(prefs.model)
        key = Ui.input(this, "API key", password = true)
        key.setText(prefs.apiKey)
        col.addView(baseUrl, Ui.vlp(8, this))
        col.addView(model, Ui.vlp(8, this))
        col.addView(key, Ui.vlp(8, this))

        var firstCall = true
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (firstCall) { firstCall = false; return }
                val pr = Presets.all[pos]
                baseUrl.setText(pr.baseUrl)
                model.setText(pr.model)
                baseUrl.visibility = if (pr.provider == "openai") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        baseUrl.visibility = if (Presets.all[prefs.presetIndex.coerceIn(0, Presets.all.size - 1)].provider == "openai") View.VISIBLE else View.GONE

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.addView(Ui.button(this, "Save & test") { saveAll(); runTest() })
        val getKey = Ui.ghostButton(this, "Get free Gemini key") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey")))
        }
        val glp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        glp.leftMargin = Ui.dp(this, 8)
        row.addView(getKey, glp)
        col.addView(row, Ui.vlp(10, this))
        testResult = Ui.label(this, "", 13f, Ui.MUTED)
        col.addView(testResult, Ui.vlp(6, this))

        // --- About you ---
        col.addView(Ui.label(this, "ABOUT YOU", 12f, Ui.RED, true).also { it.setPadding(0, Ui.dp(this, 22), 0, 0) })
        name = Ui.input(this, "Your name (Yara will call you this)")
        name.setText(prefs.userName)
        col.addView(name, Ui.vlp(6, this))
        langSpinner = Spinner(this)
        langSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, langNames)
        langSpinner.setSelection(langKeys.indexOf(prefs.language).coerceAtLeast(0))
        col.addView(langSpinner, Ui.vlp(8, this))
        col.addView(Ui.button(this, "Save") { saveAll(); Toast.makeText(this, "Saved \uD83D\uDC95", Toast.LENGTH_SHORT).show() }, Ui.vlp(8, this))

        // --- Voice ---
        col.addView(Ui.label(this, "VOICE & BACKGROUND", 12f, Ui.RED, true).also { it.setPadding(0, Ui.dp(this, 22), 0, 0) })
        val voice = Switch(this)
        voice.text = "Yara speaks her replies aloud"
        voice.setTextColor(Ui.TEXT)
        voice.isChecked = prefs.voiceReplies
        voice.setOnCheckedChangeListener { _, on -> prefs.voiceReplies = on }
        col.addView(voice, Ui.vlp(8, this))

        val wake = Switch(this)
        wake.text = "Always listen for \"Yara\" (background)"
        wake.setTextColor(Ui.TEXT)
        wake.isChecked = prefs.wakeEnabled && YaraServiceRunning.isRunning(this)
        wake.setOnCheckedChangeListener { btn, on ->
            if (on) {
                if (Perm.missing(this).isNotEmpty()) {
                    requestPermissions(Perm.missing(this), 12)
                    btn.isChecked = false
                } else Perm.startWake(this)
            } else Perm.stopWake(this)
        }
        col.addView(wake, Ui.vlp(10, this))
        col.addView(Ui.label(this,
            "Works while you use other apps. It uses Android's speech recognition, so it needs internet, uses some battery and may beep.", 12f, Ui.MUTED))

        col.addView(Ui.ghostButton(this, "Allow floating reply bubble over other apps") {
            if (Settings.canDrawOverlays(this)) Toast.makeText(this, "Already allowed \u2713", Toast.LENGTH_SHORT).show()
            else startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }, Ui.vlp(12, this))
        col.addView(Ui.ghostButton(this, "Stop Android from killing Yara (battery)") {
            val pm = getSystemService(PowerManager::class.java)
            if (pm.isIgnoringBatteryOptimizations(packageName)) Toast.makeText(this, "Already unrestricted \u2713", Toast.LENGTH_SHORT).show()
            else try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }, Ui.vlp(8, this))
        setContentView(root)
    }

    private fun saveAll() {
        prefs.presetIndex = presetSpinner.selectedItemPosition
        prefs.baseUrl = baseUrl.text.toString().trim()
        prefs.model = model.text.toString().trim()
        prefs.apiKey = key.text.toString()
        prefs.userName = name.text.toString()
        prefs.language = langKeys[langSpinner.selectedItemPosition]
    }

    private fun runTest() {
        testResult.setTextColor(Ui.MUTED)
        testResult.text = "Testing..."
        Thread {
            val res = try {
                val out = AiClient(prefs).chat("You are Yara, a sweet girlfriend. Reply in one short sentence.", listOf(Msg("user", "Say hi!")), 80)
                Pair(true, "\u2713 Connected! Yara says: $out")
            } catch (e: Exception) {
                Pair(false, if (e.message == "NO_KEY") "Paste an API key first." else "\u2717 ${e.message}")
            }
            runOnUiThread {
                testResult.setTextColor(if (res.first) android.graphics.Color.parseColor("#66BB6A") else Ui.RED)
                testResult.text = res.second
            }
        }.start()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 12 && Perm.hasMic(this)) {
            Perm.startWake(this)
            Toast.makeText(this, "Say \"Yara\" anytime \uD83D\uDC95", Toast.LENGTH_SHORT).show()
        }
    }
}
