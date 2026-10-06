package com.aicompose.camera.compose

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * 构图分析引擎（纯本地经典 CV，无云端、无外部模型依赖）。
 *
 * 流程：灰度 → 高斯模糊 → Sobel 边缘 → Hough 直线 → 显著性主体 → 规则评分。
 */
class CompositionAnalyzer {

    data class Working(val w: Int, val h: Int, val gray: IntArray, val mag: IntArray, val sal: FloatArray)

    /** 分析一张图片（可传降采样后的位图以提速） */
    fun analyze(src: Bitmap): CompositionResult {
        val w = src.width
        val h = src.height
        if (w < 8 || h < 8) return CompositionResult.empty()

        val gray = IntArray(w * h)
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in 0 until w * h) {
            val p = pixels[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            gray[i] = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
        }
        val blurred = gaussianBlur(gray, w, h)
        val mag = sobel(blurred, w, h)
        val sal = saliency(blurred, w, h)
        return analyzeWorking(Working(w, h, blurred, mag, sal), src)
    }

    private fun analyzeWorking(work: Working, src: Bitmap): CompositionResult {
        val w = work.w
        val h = work.h
        val subject = detectSubject(work)
        val center = subject?.let { PointF(it.centerX(), it.centerY()) }
        val lines = detectLines(work, subject)
        val rules = mutableMapOf<String, Int>()

        // ---- 三分法评分 ----
        var thirds = 0
        if (center != null) {
            val tx = listOf(1f / 3f, 2f / 3f)
            val ty = listOf(1f / 3f, 2f / 3f)
            val d = tx.map { x -> ty.map { y ->
                val dx = center.x - x
                val dy = center.y - y
                sqrt((dx * dx + dy * dy).toDouble())
            }.minOrNull()!! }.minOrNull()!!
            // 距离 0..~0.47，映射到 0..100
            thirds = (100.0 * (1.0 - min(1.0, d / 0.47))).toInt()
        }
        rules["三分法"] = thirds

        // ---- 对称评分 ----
        var symmetry = 0
        if (w > 20) {
            val lw = w / 2
            var diff = 0.0
            var cnt = 0
            for (y in 0 until h step 3) {
                for (x in 0 until lw step 3) {
                    diff += abs(work.gray[y * w + x] - work.gray[y * w + (w - 1 - x)])
                    cnt++
                }
            }
            val avg = if (cnt > 0) diff / cnt else 0.0
            symmetry = (100.0 * (1.0 - min(1.0, avg / 60.0))).toInt()
        }
        rules["对称"] = symmetry

        // ---- 引导线评分 ----
        var leading = 0
        if (lines.isNotEmpty() && center != null) {
            // 与主体最近的线：距离越近分越高
            val nearest = lines.map { line ->
                val mid = PointF((line.p1.x + line.p2.x) / 2f, (line.p1.y + line.p2.y) / 2f)
                sqrt(((mid.x - center.x).toDouble().pow(2) + (mid.y - center.y).toDouble().pow(2)))
            }.minOrNull()!!
            leading = (100.0 * (1.0 - min(1.0, nearest / 0.6))).toInt()
        }
        rules["引导线"] = leading

        // ---- 水平/垂直校准 ----
        var straight = 50
        if (lines.isNotEmpty()) {
            val best = lines.minByOrNull { l ->
                val a = l.angleDegrees()
                min(abs(a - 0.0), min(abs(a - 90.0), abs(a - 180.0)))
            }!!
            val a = best.angleDegrees()
            val off = min(abs(a - 0.0), min(abs(a - 90.0), abs(a - 180.0)))
            straight = (100.0 * (1.0 - min(1.0, off / 15.0))).toInt()
        }
        rules["水平"] = straight

        // ---- 留白评分 ----
        var whitespace = 50
        if (subject != null) {
            val subjArea = subject.width() * subject.height()
            val total = w.toFloat() * h.toFloat()
            val ratio = subjArea / total
            whitespace = if (ratio in 0.10f..0.60f) 90 else (100.0 * (1.0 - abs(ratio - 0.3))).toInt().coerceIn(0, 100)
        }
        rules["留白"] = whitespace

        // ---- 综合评分 ----
        val weights = mapOf("三分法" to 0.30f, "对称" to 0.15f, "引导线" to 0.20f, "水平" to 0.15f, "留白" to 0.20f)
        var score = 0
        for ((k, v) in rules) score += (v * (weights[k] ?: 0f)).toInt()
        score = score.coerceIn(0, 100)

        val isBalanced = symmetry >= 60

        val tips = mutableListOf<String>()
        if (center != null) {
            if (thirds < 55) tips.add("建议将主体放在三分线交叉点上")
            else tips.add("三分法运用得很好")
        } else {
            tips.add("画面主体不够突出，试试靠近拍摄")
        }
        if (leading < 55 && lines.isNotEmpty()) tips.add("利用引导线把视线引向主体")
        if (straight < 55) tips.add("注意保持水平")
        if (whitespace < 55) tips.add("留白太多，调整取景")
        if (symmetry < 55 && !isBalanced) tips.add("尝试对称构图")
        if (tips.isEmpty()) tips.add("构图很优秀，保持！")

        return CompositionResult(
            score = score,
            ruleScores = rules,
            mainSubject = subject,
            subjectCenter = center,
            guideLines = lines,
            isBalanced = isBalanced,
            tips = tips
        )
    }

    // ================= 图像基础 =================

    private fun gaussianBlur(src: IntArray, w: Int, h: Int, radius: Int = 2): IntArray {
        val out = IntArray(w * h)
        val kernel = floatArrayOf(1f, 4f, 6f, 4f, 1f)
        val ksum = 16f
        for (y in 0 until h) {
            for (x in 0 until w) {
                var acc = 0f
                for (ky in -radius..radius) {
                    val yy = (y + ky).coerceIn(0, h - 1)
                    for (kx in -radius..radius) {
                        val xx = (x + kx).coerceIn(0, w - 1)
                        acc += src[yy * w + xx] * kernel[ky + radius] * kernel[kx + radius]
                    }
                }
                out[y * w + x] = (acc / (ksum * ksum)).toInt()
            }
        }
        return out
    }

    private fun sobel(src: IntArray, w: Int, h: Int): IntArray {
        val mag = IntArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = -src[(y - 1) * w + (x - 1)] - 2 * src[y * w + (x - 1)] - src[(y + 1) * w + (x - 1)]
                        + src[(y - 1) * w + (x + 1)] + 2 * src[y * w + (x + 1)] + src[(y + 1) * w + (x + 1)]
                val gy = -src[(y - 1) * w + (x - 1)] - 2 * src[(y - 1) * w + x] - src[(y - 1) * w + (x + 1)]
                        + src[(y + 1) * w + (x - 1)] + 2 * src[(y + 1) * w + x] + src[(y + 1) * w + (x + 1)]
                mag[y * w + x] = sqrt((gx * gx + gy * gy).toDouble()).toInt()
            }
        }
        return mag
    }

    /** 显著性：中心权重 + 局部对比度（简化 Saliency） */
    private fun saliency(gray: IntArray, w: Int, h: Int): FloatArray {
        val sal = FloatArray(w * h)
        val cx = w / 2f
        val cy = h / 2f
        for (y in 0 until h) {
            for (x in 0 until w) {
                var contrast = 0f
                val v = gray[y * w + x]
                for (dy in -3..3 step 2) {
                    for (dx in -3..3 step 2) {
                        if (dx == 0 && dy == 0) continue
                        val xx = (x + dx).coerceIn(0, w - 1)
                        val yy = (y + dy).coerceIn(0, h - 1)
                        contrast += abs(v - gray[yy * w + xx])
                    }
                }
                val centerDist = sqrt(((x - cx).toDouble().pow(2) + (y - cy).toDouble().pow(2))) /
                        sqrt((cx.toDouble().pow(2) + cy.toDouble().pow(2)))
                sal[y * w + x] = contrast * (1.4f - centerDist.toFloat() * 0.8f)
            }
        }
        return sal
    }

    // ================= 主体检测 =================

    private fun detectSubject(work: Working): RectF? {
        val w = work.w
        val h = work.h
        var maxV = 0f
        var maxI = 0
        for (i in 0 until w * h) {
            if (work.sal[i] > maxV) {
                maxV = work.sal[i]
                maxI = i
            }
        }
        if (maxV <= 0f) return null
        // 以最显著点为中心，向四周扩展直到显著度下降（简化的区域生长）
        val cx = maxI % w
        val cy = maxI / w
        val thr = maxV * 0.35f
        var minX = cx; var maxX = cx; var minY = cy; var maxY = cy
        for (step in 1..max(w, h)) {
            var grow = false
            if (minX > 0 && work.sal[cy * w + (minX - 1)] > thr) { minX--; grow = true }
            if (maxX < w - 1 && work.sal[cy * w + (maxX + 1)] > thr) { maxX++; grow = true }
            if (minY > 0 && work.sal[(minY - 1) * w + cx] > thr) { minY--; grow = true }
            if (maxY < h - 1 && work.sal[(maxY + 1) * w + cx] > thr) { maxY++; grow = true }
            if (!grow) break
        }
        val bw = maxX - minX + 1
        val bh = maxY - minY + 1
        if (bw < 4 || bh < 4) return RectF(cx / w.toFloat(), cy / h.toFloat(), (cx + 1) / w.toFloat(), (cy + 1) / h.toFloat())
        return RectF(minX / w.toFloat(), minY / h.toFloat(), (maxX + 1) / w.toFloat(), (maxY + 1) / h.toFloat())
    }

    // ================= Hough 直线 =================

    private fun detectLines(work: Working, subject: RectF?): List<GuideLine> {
        val w = work.w
        val h = work.h
        // 只用较亮的边缘点投票（降采样加速）
        val thr = 90
        val rhoMax = sqrt((w * w + h * h).toDouble()).toInt()
        val thetaCount = 180
        val rhoCount = rhoMax + 1
        val acc = IntArray(rhoCount * thetaCount)

        val step = 2
        var idx = 0
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val m = work.mag[y * w + x]
                if (m < thr) continue
                for (t in 0 until thetaCount step 4) {
                    val th = Math.toRadians(t.toDouble())
                    val rho = ((x * cos(th) + y * sin(th)) + rhoMax / 2).toInt()
                    if (rho in 0 until rhoCount) {
                        acc[rho * thetaCount + t]++
                    }
                }
                idx++
            }
        }

        // 找票数最多的线段
        val lines = mutableListOf<GuideLine>()
        val threshold = 6
        var tries = 0
        while (lines.size < 3 && tries < 2000) {
            tries++
            var bestR = -1; var bestT = -1; var bestV = threshold
            for (i in 0 until rhoCount * thetaCount) {
                if (acc[i] > bestV) {
                    bestV = acc[i]
                    bestR = i / thetaCount
                    bestT = i % thetaCount
                }
            }
            if (bestR < 0) break
            // 抑制邻域
            for (dr in -10..10) {
                for (dt in -8..8) {
                    val r = bestR + dr
                    val t = (bestT + dt + thetaCount) % thetaCount
                    if (r in 0 until rhoCount) acc[r * thetaCount + t] = 0
                }
            }
            val th = Math.toRadians(bestT.toDouble())
            val rho = bestR - rhoMax / 2
            // 换算成图像坐标的直线两端
            val (p1, p2) = lineFromPolar(rho.toDouble(), th, w, h)
            if (p1 != null && p2 != null) {
                val a = GuideLine(p1, p2)
                // 过滤太短的线
                val len = sqrt(((p2.x - p1.x).toDouble().pow(2) + (p2.y - p1.y).toDouble().pow(2)))
                if (len > 0.28) lines.add(a)
            }
        }
        return lines
    }

    private fun lineFromPolar(rho: Double, th: Double, w: Int, h: Int): Pair<PointF?, PointF?> {
        val cosT = cos(th)
        val sinT = sin(th)
        if (abs(sinT) > 0.6) {
            // 交点 y = (rho - x*cos)/sin
            val p1 = PointF(0f, ((rho - 0 * cosT) / sinT).toFloat())
            val p2 = PointF(w.toFloat(), ((rho - w * cosT) / sinT).toFloat())
            if (p1.y < 0f || p1.y > h || p2.y < 0f || p2.y > h) return null to null
            return p1 to p2
        } else {
            val p1 = PointF(((rho - 0 * sinT) / cosT).toFloat(), 0f)
            val p2 = PointF(((rho - h * sinT) / cosT).toFloat(), h.toFloat())
            if (p1.x < 0f || p1.x > w || p2.x < 0f || p2.x > w) return null to null
            return p1 to p2
        }
    }

    private fun cos(d: Double): Double = kotlin.math.cos(d)
    private fun sin(d: Double): Double = kotlin.math.sin(d)
}
