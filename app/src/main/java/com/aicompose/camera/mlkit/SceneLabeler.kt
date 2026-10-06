package com.aicompose.camera.mlkit

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.withContext

/**
 * ML Kit 场景/主体标注（bundled 模型，本地离线推理，无云端依赖）。
 * 与原版逆向产物一致：原版 assets 内置 mlkit_label_default_model，走 ML Kit ImageLabeling。
 */
class SceneLabeler(private val context: Context) {

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.6f)
                .build()
        )
    }

    data class Label(val text: String, val confidence: Float)

    /** 识别图片内容，返回 Top 标签列表 */
    suspend fun label(bitmap: Bitmap): List<Label> = withContext(Dispatchers.Default) {
        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = labeler.process(image).await()
            result.map { Label(it.text, it.confidence) }
        }.getOrElse { emptyList() }
    }

    /** 把标签翻译成构图建议 */
    fun adviceFrom(labels: List<Label>): String? {
        if (labels.isEmpty()) return null
        val top = labels.maxByOrNull { it.confidence }?.text?.lowercase() ?: return null
        return when {
            top.contains("person") || top.contains("human") || top.contains("face") ->
                "检测到人物主体：建议使用三分法将人物置于画面一侧，人眼位置放在三分线交叉点上"
            top.contains("landscape") || top.contains("nature") || top.contains("mountain") ||
                    top.contains("sky") || top.contains("water") ->
                "检测到风景场景：寻找地平线保持水平，利用道路/河流作引导线"
            top.contains("animal") || top.contains("bird") || top.contains("cat") || top.contains("dog") ->
                "检测到动物主体：在其视线方向留出空间（视线留白），降低快门捕捉神态"
            top.contains("food") || top.contains("dish") ->
                "检测到美食：45° 俯拍突出层次，主体居中偏上，注意光源方向"
            top.contains("building") || top.contains("architecture") || top.contains("city") ->
                "检测到建筑场景：注意垂直线校正，可用对称构图或广角透视引导"
            top.contains("flower") || top.contains("plant") ->
                "检测到花卉植物：低角度拍摄，虚化背景突出主体，构图紧凑"
            top.contains("car") || top.contains("vehicle") || top.contains("transportation") ->
                "检测到交通工具：45° 前侧角度更具动感，留出运动方向空间"
            else -> "识别场景：「$top」，保持简洁，突出主体，避免杂物入镜"
        }
    }
}

/** 轻量 await 辅助（避免引入额外协程库 API 差异） */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
