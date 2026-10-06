package com.aicompose.camera.camera

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.aicompose.camera.compose.CompositionResult

/** 实时构图引导线叠加层 */
class GuideOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var result: CompositionResult? = null

    private val gridPaint = Paint().apply {
        color = Color.argb(120, 255, 255, 255)
        strokeWidth = 1.5f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint().apply {
        color = Color.argb(200, 255, 193, 7)
        strokeWidth = 3f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val subjectPaint = Paint().apply {
        color = Color.argb(220, 76, 175, 80)
        strokeWidth = 2.5f * resources.displayMetrics.density
        style = Paint.Style.STROKE
    }
    private val crossPaint = Paint().apply {
        color = Color.argb(220, 255, 235, 59)
        strokeWidth = 2f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // 九宫格
        for (i in 1..2) {
            val x = w * i / 3f
            canvas.drawLine(x, 0f, x, h, gridPaint)
            val y = h * i / 3f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        val r = result ?: return
        fun nx(f: Float) = f * w
        fun ny(f: Float) = f * h

        // 主体框
        r.mainSubject?.let {
            val rect = RectF(nx(it.left), ny(it.top), nx(it.right), ny(it.bottom))
            canvas.drawRect(rect, subjectPaint)
        }
        // 主体中心十字
        r.subjectCenter?.let {
            val x = nx(it.x); val y = ny(it.y)
            val s = 14f * resources.displayMetrics.density
            canvas.drawLine(x - s, y, x + s, y, crossPaint)
            canvas.drawLine(x, y - s, x, y + s, crossPaint)
        }
        // 引导线
        for (l in r.guideLines) {
            canvas.drawLine(nx(l.p1.x), ny(l.p1.y), nx(l.p2.x), ny(l.p2.y), linePaint)
        }
        // 三分点
        if (r.subjectCenter == null) {
            for (i in 1..2) for (j in 1..2) {
                val x = w * i / 3f; val y = h * j / 3f
                val s = 8f * resources.displayMetrics.density
                canvas.drawLine(x - s, y, x + s, y, crossPaint)
                canvas.drawLine(x, y - s, x, y + s, crossPaint)
            }
        }
    }
}
