package com.vbs.pulse

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.view.View

/** Rolling line chart of the last 64 samples, 0..1. */
class SparkView(context: Context) : View(context) {

    private val n = 64
    private val data = FloatArray(n)
    private var count = 0

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFE8B04B.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val fillPath = Path()
    private val d = resources.displayMetrics.density

    fun push(v: Float) {
        if (count < n) {
            data[count++] = v.coerceIn(0f, 1f)
        } else {
            System.arraycopy(data, 1, data, 0, n - 1)
            data[n - 1] = v.coerceIn(0f, 1f)
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 4f * d
        if (count < 2) return

        path.reset()
        fillPath.reset()
        val step = (w - pad * 2) / (n - 1).toFloat()
        val offset = (n - count) * step
        for (i in 0 until count) {
            val x = pad + offset + i * step
            val y = pad + (h - pad * 2) * (1f - data[i])
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, h)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        val lastX = pad + offset + (count - 1) * step
        fillPath.lineTo(lastX, h)
        fillPath.close()

        fill.shader = LinearGradient(
            0f, 0f, 0f, h,
            intArrayOf(0x55E8B04B, 0x00E8B04B),
            null, Shader.TileMode.CLAMP
        )
        fill.style = Paint.Style.FILL
        canvas.drawPath(fillPath, fill)
        canvas.drawPath(path, line)
    }
}
