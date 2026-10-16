package com.example.openplayer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import com.google.android.material.color.MaterialColors

/** Texto com borda preta e preenchimento na cor do tema. */
class OutlinedTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val fillColor = MaterialColors.getColor(
        context, com.google.android.material.R.attr.colorPrimary, Color.WHITE
    )
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

        p.style = Paint.Style.FILL
        p.color = fillColor
        l.draw(canvas)
        canvas.restore()

        p.color = oldColor
        p.style = oldStyle
        p.strokeWidth = oldWidth
        p.strokeJoin = oldJoin
    }
}
