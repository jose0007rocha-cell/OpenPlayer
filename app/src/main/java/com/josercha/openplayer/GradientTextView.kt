package com.josercha.openplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** Texto preenchido com o gradiente do tema (usado onde antes aparecia a cor do tema). */
class GradientTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val gradStart: Int
    private val gradEnd: Int

    init {
        val (a, b) = ThemeManager.gradientColors(context)
        gradStart = a
        gradEnd = b
    }

    private var shader: Shader? = null
    private var shaderW = -1f

    override fun onDraw(canvas: Canvas) {
        val l = layout
        var w = 0f
        if (l != null) for (i in 0 until l.lineCount) w = maxOf(w, l.getLineWidth(i))
        w = w.coerceAtLeast(1f)
        if (shader == null || shaderW != w) {
            shader = LinearGradient(0f, 0f, w, 0f, gradStart, gradEnd, Shader.TileMode.CLAMP)
            shaderW = w
        }
        val old = paint.shader
        paint.shader = shader
        super.onDraw(canvas)
        paint.shader = old
    }
}
