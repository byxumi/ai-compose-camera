package com.aicompose.camera.ml

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ONNX Runtime 主力模型推理 —— SqueezeNet 图像细分类（assets 内置，离线推理）。
 * 与原版逆向产物一致：原版打包 libonnxruntime.so（17MB）做主力模型推理。
 */
class OnnxClassifier(context: Context) {

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession by lazy {
        val bytes = context.assets.open("squeezenet1.0-9.onnx").use { it.readBytes() }
        env.createSession(bytes)
    }
    private val labels: List<String> by lazy {
        runCatching {
            context.assets.open("imagenet_labels.txt").bufferedReader().readLines()
        }.getOrElse { emptyList() }
    }

    data class Label(val index: Int, val name: String, val confidence: Float)

    /** 返回 top-k 细分类 */
    suspend fun classify(bitmap: Bitmap, topK: Int = 5): List<Label> = withContext(Dispatchers.Default) {
        runCatching {
            val input = preprocess(bitmap)          // [1,3,224,224] float32
            val tensor = OnnxTensor.createTensor(env, input)
            val output = session.run(mapOf(session.inputNames.first() to tensor))
            val out = output.get(0).value as Array<*>
            // squeeze [1,1000,1,1] -> FloatArray(1000)
            val scores = flattenScores(out)
            tensor.close(); output.close()
            topKIndices(scores).map { (idx, conf) ->
                Label(idx, labelName(idx), conf)
            }
        }.getOrElse { emptyList() }
    }

    private fun flattenScores(out: Array<*>): FloatArray {
        // 兼容多种 shape：尝试 [1,1000,1,1] / [1,1,1,1000]
        return try {
            val a = out[0] as Array<*>
            val b = a[0] as Array<*>
            val c = b[0] as Array<*>
            FloatArray(c.size) { (c[it] as Number).toFloat() }
        } catch (e: Exception) {
            FloatArray(out.size) { (out[it] as Number).toFloat() }
        }
    }

    private fun topKIndices(scores: FloatArray, k: Int = 5): List<Pair<Int, Float>> {
        return scores.withIndex()
            .sortedByDescending { it.value }
            .take(k)
            .map { it.index to it.value }
    }

    private fun labelName(idx: Int): String {
        // ImageNetLabels.txt 第 0 行 background，1..1000 对应输出 0..999
        val i = idx + 1
        return if (labels.size > i) labels[i] else "class#$idx"
    }

    /** 224x224 RGB 归一化（SqueezeNet 输入 0..1） */
    private fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val bmp = if (bitmap.width == 224 && bitmap.height == 224) bitmap
        else Bitmap.createScaledBitmap(bitmap, 224, 224, true)
        val pixels = IntArray(224 * 224)
        bmp.getPixels(pixels, 0, 224, 0, 0, 224, 224)
        if (bmp != bitmap) bmp.recycle()
        val chw = Array(3) { Array(224) { FloatArray(224) } }
        for (y in 0 until 224) {
            for (x in 0 until 224) {
                val p = pixels[y * 224 + x]
                chw[0][y][x] = ((p shr 16) and 0xff) / 255f
                chw[1][y][x] = ((p shr 8) and 0xff) / 255f
                chw[2][y][x] = (p and 0xff) / 255f
            }
        }
        return arrayOf(chw)
    }

    /** 细分类 → 构图建议（与 ML Kit 互补） */
    fun adviceFrom(labels: List<Label>): String? {
        val top = labels.maxByOrNull { it.confidence } ?: return null
        val n = top.name.lowercase()
        return when {
            n.contains("person") || n.contains("man") || n.contains("woman") ||
                    n.contains("baby") || n.contains("child") || n.contains("face") ->
                "ONNX 识别为「${top.name}」：人物主体建议置于三分线，人眼位留出视线空间"
            n.contains("dog") || n.contains("cat") || n.contains("fox") || n.contains("bird") ||
                    n.contains("horse") || n.contains("animal") ->
                "ONNX 识别为「${top.name}」：动物主体，在其视线/运动方向留白，连拍捕捉神态"
            n.contains("flower") || n.contains("plant") || n.contains("tree") ||
                    n.contains("forest") || n.contains("garden") ->
                "ONNX 识别为「${top.name}」：低角度紧凑构图，虚化背景突出主体"
            n.contains("mountain") || n.contains("lake") || n.contains("sea") ||
                    n.contains("landscape") || n.contains("valley") ->
                "ONNX 识别为「${top.name}」：风景场景，保持地平线水平，用前景物增强层次"
            n.contains("building") || n.contains("church") || n.contains("tower") ||
                    n.contains("palace") || n.contains("house") ->
                "ONNX 识别为「${top.name}」：建筑主体，校正垂直线，可尝试对称构图"
            n.contains("food") || n.contains("pizza") || n.contains("cake") ||
                    n.contains("dish") || n.contains("fruit") ->
                "ONNX 识别为「${top.name}」：美食建议 45° 俯拍，主体居中偏上，注意光线"
            n.contains("car") || n.contains("motor") || n.contains("bicycle") ||
                    n.contains("vehicle") ->
                "ONNX 识别为「${top.name}」：交通工具，45° 前侧角度更富动感，留出前进方向空间"
            else -> "ONNX 细分类：「${top.name}」（置信度 ${(top.confidence * 100).toInt()}%）"
        }
    }
}
