package com.example.openplayer

import android.app.Activity
import android.app.Dialog
import android.graphics.Color
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import com.google.android.material.color.MaterialColors

/** Janela de opções com o mesmo visual do menu dos três pontos (cantos arredondados, cinza escuro translúcido). */
object PopupDialog {

    class Group(
        val title: String?,
        val items: List<String>,
        var selected: Int,
        val onPick: (Int) -> Unit
    )

    fun show(a: Activity, title: String?, groups: List<Group>, closeOnPick: Boolean): Dialog {
        val dialog = Dialog(a, R.style.PopupDialog)
        dialog.context.theme.applyStyle(ThemeManager.accentStyle(a), true)
        val inflater = LayoutInflater.from(dialog.context)
        val root = inflater.inflate(R.layout.dialog_popup, null)
        val titleView = root.findViewById<TextView>(R.id.popupTitle)
        val body = root.findViewById<LinearLayout>(R.id.popupBody)
        val density = a.resources.displayMetrics.density

        if (title != null) {
            titleView.text = title
            titleView.visibility = View.VISIBLE
        }

        for (g in groups) {
            if (g.title != null) {
                val label = GradientTextView(dialog.context).apply {
                    text = g.title
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setPadding((12 * density).toInt(), (10 * density).toInt(), (12 * density).toInt(), (2 * density).toInt())
                }
                body.addView(label)
            }
            val radios = ArrayList<RadioButton>()
            g.items.forEachIndexed { i, label ->
                val row = inflater.inflate(R.layout.row_popup_option, body, false)
                val rb = row.findViewById<RadioButton>(R.id.optRadio)
                row.findViewById<TextView>(R.id.optLabel).text = label
                rb.isChecked = i == g.selected
                radios.add(rb)
                row.setOnClickListener {
                    g.selected = i
                    radios.forEachIndexed { j, r -> r.isChecked = j == i }
                    g.onPick(i)
                    if (closeOnPick) dialog.dismiss()
                }
                body.addView(row)
            }
        }

        dialog.setContentView(root)
        dialog.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.setLayout((300 * density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            w.setDimAmount(0.45f)
            if (Build.VERSION.SDK_INT >= 31) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = w.attributes
                lp.blurBehindRadius = 24
                w.attributes = lp
            }
        }
        dialog.show()
        return dialog
    }
}
