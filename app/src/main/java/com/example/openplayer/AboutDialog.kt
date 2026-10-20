package com.example.openplayer

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Janela "Sobre o app": autor, link do GitHub e verificação de atualização. */
object AboutDialog {

    private const val REPO_URL = "https://github.com/jose0007rocha-cell/OpenPlayer"
    private const val LINK_TEXT = "https://github.com/jose0007rocha-cell/OpenPlayer.git"
    private const val API_LATEST =
        "https://api.github.com/repos/jose0007rocha-cell/OpenPlayer/releases/latest"
    private const val RAW_GRADLE =
        "https://raw.githubusercontent.com/jose0007rocha-cell/OpenPlayer/main/app/build.gradle.kts"

    private class Latest(val version: String, val url: String)

    @Suppress("DEPRECATION")
    fun installedVersion(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (_: Exception) {
        "?"
    }

    fun show(a: Activity) {
        val dialog = Dialog(a, R.style.PopupDialog)
        dialog.context.theme.applyStyle(ThemeManager.accentStyle(a), true)
        val root = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_about, null)
        val density = a.resources.displayMetrics.density

        val link = root.findViewById<TextView>(R.id.aboutLink)
        link.text = LINK_TEXT
        link.paintFlags = link.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        link.setOnClickListener { open(a, REPO_URL) }

        val btn = root.findViewById<Button>(R.id.btnCheckUpdate)
        val installed = installedVersion(a)
        var updateUrl: String? = null
        var busy = false

        fun grayButton(text: String) {
            btn.text = text
            btn.backgroundTintList = null
            btn.background = RippleDrawable(
                ColorStateList.valueOf(0x33FFFFFF),
                GradientDrawable().apply {
                    setColor(Color.parseColor("#3A3A42"))
                    cornerRadius = 100f * density
                },
                null
            )
            btn.setTextColor(Color.WHITE)
        }

        fun greenButton(text: String) {
            btn.text = text
            btn.backgroundTintList = null
            btn.background = RippleDrawable(
                ColorStateList.valueOf(0x33000000),
                GradientDrawable(
                    GradientDrawable.Orientation.LEFT_RIGHT,
                    intArrayOf(Color.parseColor("#00C853"), Color.parseColor("#76FF03"))
                ).apply { cornerRadius = 100f * density },
                null
            )
            btn.setTextColor(Color.BLACK)
        }

        fun check(manual: Boolean) {
            if (busy) return
            busy = true
            btn.isEnabled = false
            btn.text = a.getString(R.string.about_checking)
            Thread {
                val latest = fetchLatest()
                a.runOnUiThread {
                    busy = false
                    btn.isEnabled = true
                    if (!dialog.isShowing) return@runOnUiThread
                    when {
                        latest == null -> {
                            grayButton(a.getString(R.string.about_check))
                            if (manual) Toast.makeText(a, R.string.about_error, Toast.LENGTH_LONG).show()
                        }
                        isNewer(latest.version, installed) -> {
                            updateUrl = latest.url
                            greenButton(a.getString(R.string.about_update, latest.version))
                        }
                        else -> {
                            updateUrl = null
                            grayButton(a.getString(R.string.about_check))
                            if (manual) Toast.makeText(a, R.string.about_uptodate, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }.start()
        }

        grayButton(a.getString(R.string.about_check))
        btn.setOnClickListener {
            val url = updateUrl
            if (url != null) open(a, url) else check(true)
        }

        dialog.setContentView(root)
        dialog.window?.let { w ->
            w.setBackgroundDrawableResource(android.R.color.transparent)
            w.setLayout((320 * density).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
            w.setDimAmount(0.45f)
            if (Build.VERSION.SDK_INT >= 31) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                val lp = w.attributes
                lp.blurBehindRadius = 24
                w.attributes = lp
            }
        }
        dialog.show()
        check(false) // já confere ao abrir; se houver novidade, o botão fica verde
    }

    private fun open(a: Activity, url: String) {
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
        }
    }

    /** Tenta a última "release"; se o repositório não tiver, lê a versão do build.gradle.kts no GitHub. */
    private fun fetchLatest(): Latest? {
        httpGet(API_LATEST, true)?.let { body ->
            try {
                val o = JSONObject(body)
                val tag = o.optString("tag_name")
                if (tag.isNotBlank()) {
                    return Latest(tag.trimStart('v', 'V'), o.optString("html_url", "$REPO_URL/releases"))
                }
            } catch (_: Exception) {
            }
        }
        httpGet(RAW_GRADLE, false)?.let { text ->
            Regex("versionName\\s*=\\s*\"([^\"]+)\"").find(text)?.let {
                return Latest(it.groupValues[1], "$REPO_URL/actions")
            }
        }
        return null
    }

    private fun httpGet(url: String, json: Boolean): String? {
        var con: HttpURLConnection? = null
        return try {
            con = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "OpenPlayer-App")
                if (json) setRequestProperty("Accept", "application/vnd.github+json")
            }
            if (con.responseCode == 200) con.inputStream.bufferedReader().use { it.readText() } else null
        } catch (_: Exception) {
            null
        } finally {
            con?.disconnect()
        }
    }

    private fun parts(v: String): List<Int> =
        v.split(Regex("[^0-9]+")).filter { it.isNotEmpty() }.map { it.toIntOrNull() ?: 0 }

    private fun isNewer(remote: String, local: String): Boolean {
        val r = parts(remote)
        val l = parts(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val x = r.getOrElse(i) { 0 }
            val y = l.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
