package com.example.openplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/** Texto com borda preta e preenchimento em gradiente nas cores do tema. */
class OutlinedTextView @JvmOverloads constructor(
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
    private val strokePx = resources.displayMetrics.density * 4f

    override fun onDraw(canvas: Canvas) {
        val l = layout
        if (l == null) {
            super.onDraw(canvas)
            return
        }
        val p = l.paint
        val oldColor = p.color
        val oldStyle = p.style
        val oldWidth = p.strokeWidth
        val oldJoin = p.strokeJoin

        canvas.save()
        canvas.translate(
            (compoundPaddingLeft + scrollX).toFloat(),
            (extendedPaddingTop + scrollY).toFloat()
        )
        p.style = Paint.Style.STROKE
        p.strokeWidth = strokePx
        p.strokeJoin = Paint.Join.ROUND
        p.color = Color.BLACK
        l.draw(canvas)

        var textW = 0f
        for (i in 0 until l.lineCount) textW = maxOf(textW, l.getLineWidth(i))
        val oldShader = p.shader
        p.style = Paint.Style.FILL
        p.color = Color.WHITE
        p.shader = LinearGradient(
            0f, 0f, textW.coerceAtLeast(1f), 0f,
            gradStart, gradEnd, Shader.TileMode.CLAMP
        )
        l.draw(canvas)
        p.shader = oldShader
        canvas.restore()

        p.color = oldColor
        p.style = oldStyle
        p.strokeWidth = oldWidth
        p.strokeJoin = oldJoin
    }
}
