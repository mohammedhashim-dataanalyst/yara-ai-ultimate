package com.yara.ai.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import com.yara.ai.core.Db
import com.yara.ai.core.Fact
import com.yara.ai.core.Prefs

class MemoryActivity : Activity() {
    private lateinit var db: Db
    private lateinit var prefs: Prefs
    private val facts = ArrayList<Fact>()
    private lateinit var adapter: ArrayAdapter<String>
    private val labels = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = Db.get(this); prefs = Prefs(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(this, "Yara's Memory", "Things she remembers about you") { finish() })

        val pad = Ui.dp(this, 14)
        val add = Ui.input(this, "Add something for Yara to remember...")
        val addRow = LinearLayout(this)
        addRow.orientation = LinearLayout.HORIZONTAL
        addRow.setPadding(pad, pad, pad, 4)
        addRow.addView(add, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val addBtn = Ui.button(this, "Add") {
            val t = add.text.toString()
            if (db.addFact(t)) { add.setText(""); load() }
        }
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 46))
        lp.leftMargin = Ui.dp(this, 8)
        addRow.addView(addBtn, lp)
        root.addView(addRow)

        root.addView(Ui.label(this, "Tip: tell her \"remember: my interview is on Friday\". Tap a memory to delete it.", 12f, Ui.MUTED)
            .also { it.setPadding(pad, 4, pad, 8) })

        val lv = ListView(this)
        adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, labels) {
            override fun getView(position: Int, convertView: android.view.View?, parent: ViewGroup): android.view.View {
                val v = super.getView(position, convertView, parent) as TextView
                v.setTextColor(Ui.TEXT)
                return v
            }
        }
        lv.adapter = adapter
        lv.setOnItemClickListener { _, _, pos, _ ->
            AlertDialog.Builder(this)
                .setMessage("Delete this memory?\n\n${facts[pos].text}")
                .setPositiveButton("Delete") { _, _ -> db.deleteFact(facts[pos].id); load() }
                .setNegativeButton("Cancel", null).show()
        }
        root.addView(lv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val bottom = LinearLayout(this)
        bottom.orientation = LinearLayout.HORIZONTAL
        bottom.setPadding(pad, pad, pad, pad)
        bottom.addView(Ui.ghostButton(this, "Clear chat") {
            AlertDialog.Builder(this).setMessage("Delete the whole chat history? Memories stay.")
                .setPositiveButton("Delete") { _, _ -> db.clearMessages(); Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show() }
                .setNegativeButton("Cancel", null).show()
        })
        bottom.addView(Ui.ghostButton(this, "Reset closeness") {
            prefs.affection = 60; prefs.emotion = "HAPPY"
            Toast.makeText(this, "Reset", Toast.LENGTH_SHORT).show()
        })
        root.addView(bottom)
        setContentView(root)
        load()
    }

    private fun load() {
        facts.clear(); facts.addAll(db.facts())
        labels.clear(); labels.addAll(facts.map { "\u2022 " + it.text })
        adapter.notifyDataSetChanged()
    }
}
