package com.vbs.pulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import kotlin.math.min

/** Big circular RAM gauge: 270° arc, warm gradient, animated sweep. */
class RingView(context: Context) : View(context) {

    var fraction = 0f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    var big = ""
    var small = ""

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val d = resources.displayMetrics.density

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val stroke = 22f * d
        val r = min(width, height) / 2f - stroke / 2f - 10f * d

        rect.set(cx - r, cy - r, cx + r, cy + r)

        p.style = Paint.Style.STROKE
        p.strokeWidth = stroke
        p.strokeCap = Paint.Cap.ROUND
        p.shader = null
        p.color = 0xFF2A2620.toInt()
        canvas.drawArc(rect, 135f, 270f, false, p)

        if (fraction > 0.001f) {
            p.shader = LinearGradient(
                cx - r, cy + r, cx + r, cy - r,
                intArrayOf(0xFFE8B04B.toInt(), 0xFFD9663A.toInt()),
                null, Shader.TileMode.CLAMP
            )
            canvas.drawArc(rect, 135f, 270f * fraction, false, p)
            p.shader = null
        }

        p.style = Paint.Style.FILL
        p.textAlign = Paint.Align.CENTER
        p.color = 0xFFF2EDE4.toInt()
        p.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        p.textSize = 54f * d
        canvas.drawText(big, cx, cy + 8f * d, p)

        p.typeface = Typeface.DEFAULT
        p.textSize = 13f * d
        p.color = 0xFFA79E90.toInt()
        canvas.drawText(small, cx, cy + 34f * d, p)
    }
}
