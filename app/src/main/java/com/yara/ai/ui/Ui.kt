package com.yara.ai.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Tiny UI helpers so every screen shares the dark + red WhatsApp-style look. */
object Ui {
    val BG = Color.parseColor("#121212")
    val SURFACE = Color.parseColor("#1E1E1E")
    val RED = Color.parseColor("#FF4B5C")
    val TEXT = Color.parseColor("#EEEEEE")
    val MUTED = Color.parseColor("#9E9E9E")
    val BUBBLE_YARA = Color.parseColor("#2A2A2A")

    fun dp(c: Context, v: Int): Int = (v * c.resources.displayMetrics.density).toInt()

    fun rounded(c: Context, color: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(color)
        g.cornerRadius = dp(c, radiusDp).toFloat()
        return g
    }

    fun button(c: Context, text: String, onClick: () -> Unit): Button {
        val b = Button(c)
        b.text = text
        b.isAllCaps = false
        b.setTextColor(Color.WHITE)
        b.background = rounded(c, RED, 22)
        b.setPadding(dp(c, 16), dp(c, 8), dp(c, 16), dp(c, 8))
        b.setOnClickListener { onClick() }
        return b
    }

    fun ghostButton(c: Context, text: String, onClick: () -> Unit): Button {
        val b = button(c, text, onClick)
        b.background = rounded(c, SURFACE, 22)
        return b
    }

    fun label(c: Context, text: String, sizeSp: Float = 14f, color: Int = TEXT, bold: Boolean = false): TextView {
        val t = TextView(c)
        t.text = text
        t.textSize = sizeSp
        t.setTextColor(color)
        if (bold) t.setTypeface(t.typeface, android.graphics.Typeface.BOLD)
        return t
    }

    fun input(c: Context, hint: String, password: Boolean = false, multiline: Boolean = false): EditText {
        val e = EditText(c)
        e.hint = hint
        e.setHintTextColor(MUTED)
        e.setTextColor(TEXT)
        e.textSize = 15f
        e.background = rounded(c, SURFACE, 18)
        e.setPadding(dp(c, 14), dp(c, 10), dp(c, 14), dp(c, 10))
        e.inputType = when {
            password -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            multiline -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            else -> InputType.TYPE_CLASS_TEXT
        }
        return e
    }

    fun vlp(topDp: Int = 0, c: Context): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(c, topDp)
        return lp
    }

    fun header(c: Context, title: String, subtitle: String = "", onBack: () -> Unit): LinearLayout {
        val h = LinearLayout(c)
        h.orientation = LinearLayout.HORIZONTAL
        h.gravity = Gravity.CENTER_VERTICAL
        h.setBackgroundColor(SURFACE)
        h.setPadding(dp(c, 8), dp(c, 10), dp(c, 12), dp(c, 10))
        val back = TextView(c)
        back.text = "\u2190"
        back.textSize = 24f
        back.setTextColor(RED)
        back.setPadding(dp(c, 12), 0, dp(c, 14), 0)
        back.setOnClickListener { onBack() }
        h.addView(back)
        val col = LinearLayout(c)
        col.orientation = LinearLayout.VERTICAL
        col.addView(label(c, title, 19f, RED, true))
        if (subtitle.isNotEmpty()) col.addView(label(c, subtitle, 12f, MUTED))
        h.addView(col)
        return h
    }

    fun gone(v: View) { v.visibility = View.GONE }
}
