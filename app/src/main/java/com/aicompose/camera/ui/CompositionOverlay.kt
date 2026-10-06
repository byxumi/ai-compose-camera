package com.aicompose.camera.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.aicompose.camera.compose.CompositionResult

/**
 * 实时构图叠加层（Compose Canvas 版）
 * 九宫格 + 主体框 + 主体中心十字 + 引导线 + 三分点
 */
@Composable
fun CompositionOverlay(
    result: CompositionResult?,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val gridColor = Color.White.copy(alpha = 0.35f)
        val lineColor = Color(0xFFFFC107).copy(alpha = 0.9f)
        val subjectColor = Color(0xFF4CAF50).copy(alpha = 0.95f)
        val crossColor = Color(0xFFFFEB3B).copy(alpha = 0.95f)
        val gridStroke = Stroke(width = 1.2.dp.toPx())
        val lineStroke = Stroke(width = 2.6.dp.toPx())

        // 九宫格
        for (i in 1..2) {
            val x = w * i / 3f
            drawLine(gridColor, Offset(x, 0f), Offset(x, h), gridStroke)
            val y = h * i / 3f
            drawLine(gridColor, Offset(0f, y), Offset(w, y), gridStroke)
        }

        val r = result ?: return@Canvas
        fun nx(f: Float) = f * w
        fun ny(f: Float) = f * h

        // 主体框（圆角矩形）
        r.mainSubject?.let {
            val path = Path().apply {
                val l = nx(it.left); val t = ny(it.top)
                val rt = nx(it.right); val b = ny(it.bottom)
                val rad = 8.dp.toPx()
                moveTo(l + rad, t)
                lineTo(rt - rad, t); quadraticBezierTo(rt, t, rt, t + rad)
                lineTo(rt, b - rad); quadraticBezierTo(rt, b, rt - rad, b)
                lineTo(l + rad, b); quadraticBezierTo(l, b, l, b - rad)
                lineTo(l, t + rad); quadraticBezierTo(l, t, l + rad, t)
                close()
            }
            drawPath(path, color = subjectColor, style = Stroke(width = 2.dp.toPx()))
        }

        // 主体中心十字
        r.subjectCenter?.let { c ->
            val x = nx(c.x); val y = ny(c.y)
            val s = 12.dp.toPx()
            drawLine(crossColor, Offset(x - s, y), Offset(x + s, y), Stroke(width = 1.8.dp.toPx()))
            drawLine(crossColor, Offset(x, y - s), Offset(x, y + s), Stroke(width = 1.8.dp.toPx()))
        }

        // 引导线
        r.guideLines.forEach { l ->
            drawLine(
                lineColor,
                Offset(nx(l.p1.x), ny(l.p1.y)),
                Offset(nx(l.p2.x), ny(l.p2.y)),
                lineStroke
            )
        }

        // 无主体时显示三分点
        if (r.subjectCenter == null) {
            val s = 7.dp.toPx()
            for (i in 1..2) for (j in 1..2) {
                val x = w * i / 3f; val y = h * j / 3f
                drawLine(crossColor, Offset(x - s, y), Offset(x + s, y), Stroke(width = 1.6.dp.toPx()))
                drawLine(crossColor, Offset(x, y - s), Offset(x, y + s), Stroke(width = 1.6.dp.toPx()))
            }
        }
    }
}
