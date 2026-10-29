package com.yara.ai.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import com.yara.ai.core.ImageStudio
import com.yara.ai.core.Prefs

/** Photo Studio: AI-generated Yara pictures, local photo filters, couple collage, save to gallery. */
class StudioActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var preview: ImageView
    private lateinit var status: TextView
    private lateinit var scene: android.widget.EditText
    private lateinit var styleGroup: RadioGroup
    private var yara: Bitmap? = null
    private var userOriginal: Bitmap? = null
    private var userEdited: Bitmap? = null
    private var shown: Bitmap? = null
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setBackgroundColor(Ui.BG)
        root.addView(Ui.header(this, "Photo Studio", "Yara pictures, filters & couple frames") { finish() })

        val sv = ScrollView(this)
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val pad = Ui.dp(this, 14)
        col.setPadding(pad, pad, pad, pad * 2)
        sv.addView(col)
        root.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        preview = ImageView(this)
        preview.scaleType = ImageView.ScaleType.FIT_CENTER
        preview.background = Ui.rounded(this, Ui.SURFACE, 16)
        col.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 380)))
        status = Ui.label(this, "Generate Yara, pick your photo, or make a couple frame.", 13f, Ui.MUTED)
        col.addView(status, Ui.vlp(8, this))

        col.addView(Ui.label(this, "1. YARA PHOTO (AI generated, needs internet)", 12f, Ui.RED, true).also { it.setPadding(0, Ui.dp(this, 14), 0, 0) })
        scene = Ui.input(this, "Scene, e.g. sitting in a cafe, rainy window, saree...")
        col.addView(scene, Ui.vlp(6, this))
        styleGroup = RadioGroup(this)
        styleGroup.orientation = RadioGroup.HORIZONTAL
        val anime = RadioButton(this); anime.text = "Anime"; anime.setTextColor(Ui.TEXT); anime.id = 101
        val real = RadioButton(this); real.text = "Realistic"; real.setTextColor(Ui.TEXT); real.id = 102
        styleGroup.addView(anime); styleGroup.addView(real)
        styleGroup.check(101)
        col.addView(styleGroup, Ui.vlp(4, this))
        col.addView(Ui.button(this, "\u2728 Generate Yara") { generate() }, Ui.vlp(4, this))

        col.addView(Ui.label(this, "2. YOUR PHOTO (edited on your phone, your face stays untouched)", 12f, Ui.RED, true).also { it.setPadding(0, Ui.dp(this, 18), 0, 0) })
        col.addView(Ui.ghostButton(this, "\uD83D\uDDBC Pick my photo") { pickPhoto() }, Ui.vlp(6, this))
        val hs = HorizontalScrollView(this)
        hs.isHorizontalScrollBarEnabled = false
        val frow = LinearLayout(this)
        frow.orientation = LinearLayout.HORIZONTAL
        for (f in ImageStudio.filterNames) {
            val b = Ui.ghostButton(this, f) { applyFilter(f) }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.rightMargin = Ui.dp(this, 6)
            frow.addView(b, lp)
        }
        hs.addView(frow)
        col.addView(hs, Ui.vlp(6, this))

        col.addView(Ui.label(this, "3. COUPLE FRAME & SAVE", 12f, Ui.RED, true).also { it.setPadding(0, Ui.dp(this, 18), 0, 0) })
        col.addView(Ui.button(this, "\uD83D\uDC91 Make couple frame (me + Yara)") { couple() }, Ui.vlp(6, this))
        col.addView(Ui.ghostButton(this, "\uD83D\uDCBE Save shown picture to gallery") { save() }, Ui.vlp(8, this))
        setContentView(root)
    }

    private fun show(b: Bitmap, msg: String) {
        shown = b
        preview.setImageBitmap(b)
        status.text = msg
    }

    private fun work(startMsg: String, job: () -> Unit) {
        if (busy) return
        busy = true
        status.text = startMsg
        Thread {
            try { job() } catch (e: Exception) {
                runOnUiThread { status.text = "\u2717 ${e.message ?: e.javaClass.simpleName}" }
            } finally {
                busy = false
            }
        }.start()
    }

    private fun generate() {
        val s = scene.text.toString()
        val anime = styleGroup.checkedRadioButtonId == 101
        work("Yara is getting ready... (can take up to a minute)") {
            val bmp = ImageStudio.generateYara(s, anime)
            runOnUiThread { yara = bmp; show(bmp, "Here's Yara \uD83D\uDC95") }
        }
    }

    private fun pickPhoto() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(i, "Choose your photo"), 31)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 31 && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            work("Loading your photo...") {
                val bmp = ImageStudio.loadUserPhoto(this, uri) ?: throw java.io.IOException("Could not read that image")
                runOnUiThread { userOriginal = bmp; userEdited = bmp; show(bmp, "Photo loaded. Try a filter!") }
            }
        }
    }

    private fun applyFilter(name: String) {
        val o = userOriginal
        if (o == null) { status.text = "Pick your photo first."; return }
        work("Applying $name...") {
            val out = ImageStudio.applyFilter(o, name)
            runOnUiThread { userEdited = out; show(out, "$name applied") }
        }
    }

    private fun couple() {
        val u = userEdited
        val y = yara
        if (u == null || y == null) { status.text = "You need both: generate Yara (step 1) and pick your photo (step 2)."; return }
        val n = prefs.userName
        work("Making your couple frame...") {
            val out = ImageStudio.coupleCollage(u, y, n)
            runOnUiThread { show(out, "Couple frame ready \uD83D\uDC91 Save it!") }
        }
    }

    private fun save() {
        val b = shown
        if (b == null) { status.text = "Nothing to save yet."; return }
        work("Saving...") {
            val where = ImageStudio.saveToGallery(this, b)
            runOnUiThread { status.text = "Saved to $where \u2713" }
        }
    }
}
