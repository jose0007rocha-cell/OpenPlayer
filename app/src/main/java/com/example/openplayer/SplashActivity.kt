package com.example.openplayer

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import androidx.core.view.WindowCompat

/** Tela de abertura: ícone grande piscando suavemente, como se estivesse carregando. */
class SplashActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var pulse: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_splash)

        val icon = findViewById<ImageView>(R.id.splashIcon)
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            icon,
            PropertyValuesHolder.ofFloat(View.ALPHA, 0.3f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 0.93f, 1.04f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.93f, 1.04f)
        ).apply {
            duration = 900
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    override fun onStart() {
        super.onStart()
        handler.postDelayed({ goNext() }, SPLASH_MS)
    }

    override fun onStop() {
        handler.removeCallbacksAndMessages(null)
        super.onStop()
    }

    override fun onDestroy() {
        pulse?.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun goNext() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    private companion object {
        const val SPLASH_MS = 2000L
    }
}
