package com.aicompose.camera.compose

import android.graphics.PointF
import android.graphics.RectF

/** 一条检测到的直线（归一化坐标 0..1） */
data class GuideLine(
    val p1: PointF,
    val p2: PointF
) {
    fun angleDegrees(): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val d = kotlin.math.atan2(dy.toDouble(), dx.toDouble())
        return (((Math.toDegrees(d) % 180.0) + 180.0) % 180.0).toFloat()
    }
}

/** 构图分析结果 */
data class CompositionResult(
    val score: Int,                      // 0..100
    val ruleScores: Map<String, Int>,    // 规则明细
    val mainSubject: RectF?,             // 主体区域（归一化）
    val subjectCenter: PointF?,          // 主体中心（归一化）
    val guideLines: List<GuideLine>,     // 引导线
    val isBalanced: Boolean,             // 左右均衡
    val tips: List<String>               // 建议
) {
    companion object {
        fun empty() = CompositionResult(
            score = 0, ruleScores = emptyMap(), mainSubject = null,
            subjectCenter = null, guideLines = emptyList(), isBalanced = false, tips = emptyList()
        )
    }
}
