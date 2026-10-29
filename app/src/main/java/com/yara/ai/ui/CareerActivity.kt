package com.yara.ai.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.yara.ai.core.AiClient
import com.yara.ai.core.Career
import com.yara.ai.core.Msg
import com.yara.ai.core.Prefs

/** Data analytics career suite: every mode is powered by the real AI model with its own conversation. */
class CareerActivity : Activity() {
    private lateinit var prefs: Prefs
    private val histories = HashMap<Int, ArrayList<Msg>>()
    private var mode = 0
    private var busy = false
    private lateinit var hint: TextView
    private lateinit var output: TextView
    private lateinit var scroll: ScrollView
    private lateinit var input: android.widget.EditText
    private lateinit var jobBtn: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(this, "Career Suite", "Data Analytics coach") { finish() })

        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val modes = LinearLayout(this)
        modes.orientation = LinearLayout.HORIZONTAL
        modes.setPadding(Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 8), Ui.dp(this, 4))
        Career.modes.forEachIndexed { i, m ->
            val b = Ui.ghostButton(this, m.title) { selectMode(i) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = Ui.dp(this, 6)
            modes.addView(b, lp)
        }
        hs.addView(modes)
        root.addView(hs)

        hint = Ui.label(this, "", 12f, Ui.MUTED)
        hint.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4))
        root.addView(hint)

        scroll = ScrollView(this)
        output = Ui.label(this, "", 15f, Ui.TEXT)
        output.setPadding(Ui.dp(this, 16), Ui.dp(this, 8), Ui.dp(this, 16), Ui.dp(this, 8))
        output.setTextIsSelectable(true)
        scroll.addView(output)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        jobBtn = Ui.ghostButton(this, "\uD83D\uDD0E Open job boards for this search") { openBoards() }
        root.addView(jobBtn, Ui.vlp(0, this).also { it.leftMargin = Ui.dp(this, 12); it.rightMargin = Ui.dp(this, 12) })

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 8))
        input = Ui.input(this, "", multiline = true)
        input.maxLines = 5
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val lp = LinearLayout.LayoutParams(Ui.dp(this, 56), Ui.dp(this, 46))
        lp.leftMargin = Ui.dp(this, 6)
        bar.addView(Ui.button(this, "\u27A4") { send() }, lp)
        root.addView(bar)
        setContentView(root)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        selectMode(0)
    }

    private fun selectMode(i: Int) {
        mode = i
        hint.text = Career.modes[i].title + " \u2014 " + Career.modes[i].hint
        input.hint = Career.modes[i].hint
        jobBtn.visibility = if (i == 2) View.VISIBLE else View.GONE
        render()
    }

    private fun render() {
        val h = histories[mode] ?: ArrayList()
        output.text = if (h.isEmpty()) "Ask away, I'm here to help you get hired \uD83D\uDCAA"
        else h.joinToString("\n\n") { (if (it.role == "user") "You: " else "Yara: ") + it.text }
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun send() {
        val t = input.text.toString().trim()
        if (t.isEmpty() || busy) return
        val m = mode
        val h = histories.getOrPut(m) { ArrayList() }
        h.add(Msg("user", t))
        input.setText("")
        busy = true
        render()
        output.append("\n\nYara is thinking...")
        Thread {
            val answer = try {
                AiClient(prefs).chat(Career.modes[m].system, h.toList(), 1500)
            } catch (e: Exception) {
                if (e.message == "NO_KEY") "Please add your API key in Settings first." else "Error: ${e.message}"
            }
            runOnUiThread {
                h.add(Msg("assistant", answer))
                busy = false
                if (m == mode) render()
            }
        }.start()
    }

    private fun openBoards() {
        val q = input.text.toString().trim().ifEmpty {
            histories[2]?.lastOrNull { it.role == "user" }?.text?.take(60) ?: "data analyst"
        }
        val links = Career.jobBoardLinks(q)
        AlertDialog.Builder(this)
            .setTitle("Search: ${q.take(40)}")
            .setItems(links.map { it.first }.toTypedArray()) { _, which ->
                try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(links[which].second))) }
                catch (e: Exception) { Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show() }
            }.show()
    }
}
