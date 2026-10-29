package com.yara.ai.ui

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import com.yara.ai.core.Brain
import com.yara.ai.core.Db
import com.yara.ai.core.Msg
import com.yara.ai.core.Perm
import com.yara.ai.core.Persona
import com.yara.ai.core.Prefs
import com.yara.ai.core.Speaker
import com.yara.ai.core.YaraService

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var db: Db
    private lateinit var brain: Brain
    private lateinit var speaker: Speaker
    private lateinit var adapter: ChatAdapter
    private lateinit var list: ListView
    private lateinit var input: android.widget.EditText
    private lateinit var status: TextView
    private lateinit var ear: TextView
    private val msgs = ArrayList<Msg>()
    private var sending = false
    private var wantWakeAfterPermission = false

    private val newMessage = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { reload() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this); db = Db.get(this); brain = Brain(this); speaker = Speaker(this, prefs)
        buildUi()
        reload()
        if (msgs.isEmpty()) {
            val g = brain.greeting()
            db.addMessage("assistant", g)
            reload()
        }
        if (Perm.missing(this).isNotEmpty()) requestPermissions(Perm.missing(this), 10)
        else if (prefs.wakeEnabled && !YaraServiceRunning.isRunning(this)) Perm.startWake(this)
    }

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)

        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setBackgroundColor(Ui.SURFACE)
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10))
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(Ui.label(this, "Yara \u2764", 21f, Ui.RED, true))
        status = Ui.label(this, "", 12f, Ui.MUTED)
        col.addView(status)
        header.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        ear = Ui.label(this, "\uD83D\uDC42", 24f)
        ear.setPadding(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6))
        ear.setOnClickListener { toggleWake() }
        header.addView(ear)
        val menu = Ui.label(this, "\u22EE", 26f, Ui.TEXT, true)
        menu.setPadding(Ui.dp(this, 14), Ui.dp(this, 2), Ui.dp(this, 14), Ui.dp(this, 2))
        menu.setOnClickListener { showMenu(it) }
        header.addView(menu)
        root.addView(header)

        list = ListView(this)
        list.divider = null
        list.isVerticalScrollBarEnabled = false
        adapter = ChatAdapter(this, msgs)
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.gravity = Gravity.CENTER_VERTICAL
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 6), Ui.dp(this, 8), Ui.dp(this, 8))
        input = Ui.input(this, "Message Yara...", multiline = true)
        input.maxLines = 4
        input.imeOptions = EditorInfo.IME_ACTION_SEND
        bar.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val mic = Ui.ghostButton(this, "\uD83C\uDFA4") { startVoiceInput() }
        val send = Ui.button(this, "\u27A4") { send(input.text.toString()) }
        val blp = LinearLayout.LayoutParams(Ui.dp(this, 52), Ui.dp(this, 46))
        blp.leftMargin = Ui.dp(this, 6)
        bar.addView(mic, blp)
        bar.addView(send, blp)
        root.addView(bar)
        setContentView(root)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }

    private fun showMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "\uD83D\uDCBC Career Suite")
        pm.menu.add(0, 2, 1, "\uD83D\uDCF8 Photo Studio")
        pm.menu.add(0, 3, 2, "\uD83E\uDDE0 Memory")
        pm.menu.add(0, 4, 3, "\u2699 Settings")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> startActivity(Intent(this, CareerActivity::class.java))
                2 -> startActivity(Intent(this, StudioActivity::class.java))
                3 -> startActivity(Intent(this, MemoryActivity::class.java))
                4 -> startActivity(Intent(this, SettingsActivity::class.java))
            }
            true
        }
        pm.show()
    }

    private fun refreshStatus() {
        val e = Persona.current(prefs)
        status.text = if (sending) "typing..." else "${e.emoji} ${e.label}  \u00B7  ${Persona.affectionLabel(prefs.affection)}"
        ear.alpha = if (YaraServiceRunning.isRunning(this)) 1f else 0.35f
    }

    private fun reload() {
        msgs.clear()
        msgs.addAll(db.recent(200))
        adapter.notifyDataSetChanged()
        list.setSelection(adapter.count - 1)
        refreshStatus()
    }

    private fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || sending) return
        input.setText("")
        msgs.add(Msg("user", t))
        adapter.notifyDataSetChanged()
        list.setSelection(adapter.count - 1)
        sending = true
        refreshStatus()
        Thread {
            val r = brain.reply(t)
            runOnUiThread {
                sending = false
                if (r.ok) reload() else {
                    msgs.add(Msg("assistant", r.text))
                    adapter.notifyDataSetChanged()
                    list.setSelection(adapter.count - 1)
                    refreshStatus()
                }
                if (r.ok && prefs.voiceReplies) speaker.speak(r.text)
            }
        }.start()
    }

    private fun startVoiceInput() {
        speaker.stop()
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, prefs.speechTag)
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Yara...")
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, 21)
        } catch (e: Exception) {
            Toast.makeText(this, "Voice input is not available. Install the Google app.", Toast.LENGTH_LONG).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 21 && resultCode == RESULT_OK) {
            val text = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) send(text)
        }
    }

    private fun toggleWake() {
        if (YaraServiceRunning.isRunning(this)) {
            Perm.stopWake(this)
            Toast.makeText(this, "Background listening off", Toast.LENGTH_SHORT).show()
            ear.postDelayed({ refreshStatus() }, 400)
        } else if (Perm.missing(this).isNotEmpty()) {
            wantWakeAfterPermission = true
            requestPermissions(Perm.missing(this), 10)
        } else {
            Perm.startWake(this)
            Toast.makeText(this, "Say \"Yara\" anytime \uD83D\uDC95", Toast.LENGTH_SHORT).show()
            ear.postDelayed({ refreshStatus() }, 600)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 10) {
            if (wantWakeAfterPermission && Perm.hasMic(this)) { wantWakeAfterPermission = false; Perm.startWake(this) }
            else if (!Perm.hasMic(this)) Toast.makeText(this, "Microphone permission is needed for voice.", Toast.LENGTH_LONG).show()
            ear.postDelayed({ refreshStatus() }, 600)
        }
    }

    override fun onStart() {
        super.onStart()
        val f = IntentFilter(YaraService.ACTION_NEW)
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(newMessage, f, Context.RECEIVER_NOT_EXPORTED)
        else registerReceiver(newMessage, f)
    }

    override fun onStop() {
        try { unregisterReceiver(newMessage) } catch (_: Exception) {}
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (!sending) reload()
    }

    override fun onDestroy() {
        speaker.shutdown()
        super.onDestroy()
    }
}

/** Is the background service alive? (Activity.getSystemService + running services check.) */
object YaraServiceRunning {
    @Suppress("DEPRECATION")
    fun isRunning(c: Context): Boolean {
        val am = c.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return am.getRunningServices(Int.MAX_VALUE).any { it.service.className == YaraService::class.java.name }
    }
}

class ChatAdapter(private val c: Context, private val items: List<Msg>) : BaseAdapter() {
    override fun getCount() = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val m = items[position]
        val mine = m.role == "user"
        val row = LinearLayout(c)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = if (mine) Gravity.END else Gravity.START
        row.setPadding(Ui.dp(c, 10), Ui.dp(c, 3), Ui.dp(c, 10), Ui.dp(c, 3))
        val tv = TextView(c)
        tv.text = m.text
        tv.textSize = 16f
        tv.setTextColor(android.graphics.Color.WHITE)
        tv.setPadding(Ui.dp(c, 14), Ui.dp(c, 9), Ui.dp(c, 14), Ui.dp(c, 9))
        tv.background = Ui.rounded(c, if (mine) Ui.RED else Ui.BUBBLE_YARA, 18)
        tv.maxWidth = (c.resources.displayMetrics.widthPixels * 0.78f).toInt()
        row.addView(tv)
        return row
    }
}
