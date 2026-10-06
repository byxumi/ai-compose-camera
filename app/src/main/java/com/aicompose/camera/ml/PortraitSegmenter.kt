package com.aicompose.camera.ml

import android.content.Context
import android.graphics.Bitmap
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imagesegmenter.ImageSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.FloatBuffer

/**
 * MediaPipe Tasks 人像分割 —— selfie_segmentation 模型（assets 内置，离线流式推理）。
 * 与原版逆向产物一致：原版打包 libmediapipe_tasks_jni.so 做人像分割/背景虚化。
 */
class PortraitSegmenter(context: Context) {

    private val segmenter by lazy {
        val base = BaseOptions.builder()
            .setModelAssetPath("selfie_segmenter.tflite")
            .build()
        val options = ImageSegmenter.ImageSegmenterOptions.builder()
            .setBaseOptions(base)
            .setRunningMode(RunningMode.IMAGE)
            .setOutputType(ImageSegmenter.OutputType.CONFIDENCE_MASK)
            .build()
        ImageSegmenter.createFromOptions(context, options)
    }

    /**
     * 人像背景虚化：mask 置信度 >= threshold 的区域保持清晰，其余高斯虚化。
     * @param radius 背景虚化强度（像素半径）
     */
    suspend fun blurBackground(src: Bitmap, radius: Int = 4, threshold: Float = 0.55f): Bitmap =
        withContext(Dispatchers.Default) {
            runCatching {
                val mpImage: MPImage = BitmapImageBuilder(src).build()
                val result = segmenter.segment(mpImage)
                val mask = result.confidenceMasks().first().buffer as FloatBuffer
                val maskArr = FloatArray(mask.remaining())
                mask.get(maskArr)

                val out = src.copy(Bitmap.Config.ARGB_8888, true)
                val w = out.width
                val h = out.height
                val blurred = blur(src, radius)
                val pxOut = IntArray(w * h)
                val pxBlur = IntArray(w * h)
                out.getPixels(pxOut, 0, w, 0, 0, w, h)
                blurred.getPixels(pxBlur, 0, w, 0, 0, w, h)
                for (y in 0 until h) {
                    for (x in 0 until w) {
                        val mx = (x * mask.columns() / w).coerceIn(0, mask.columns() - 1)
                        val my = (y * mask.rows() / h).coerceIn(0, mask.rows() - 1)
                        val conf = maskArr[my * mask.columns() + mx]
                        pxOut[y * w + x] = if (conf >= threshold) pxOut[y * w + x] else pxBlur[y * w + x]
                    }
                }
                out.setPixels(pxOut, 0, w, 0, 0, w, h)
                blurred.recycle()
                out
            }.getOrElse { src.copy(Bitmap.Config.ARGB_8888, false) }
        }

    private fun blur(bmp: Bitmap, radius: Int): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        val r = if (radius > 0) radius else 1
        val tmp = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var rr = 0L; var gg = 0L; var bb = 0L; var n = 0L
                for (dy in -r..r) {
                    for (dx in -r..r) {
                        val xx = (x + dx).coerceIn(0, w - 1)
                        val yy = (y + dy).coerceIn(0, h - 1)
                        val p = px[yy * w + xx]
                        rr += (p shr 16) and 0xff
                        gg += (p shr 8) and 0xff
                        bb += p and 0xff
                        n++
                    }
                }
                tmp[y * w + x] = (0xff shl 24) or ((rr / n).toInt() shl 16) or ((gg / n).toInt() shl 8) or (bb / n).toInt()
            }
        }
        out.setPixels(tmp, 0, w, 0, 0, w, h)
        return out
    }
}
