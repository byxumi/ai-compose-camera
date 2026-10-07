package com.aicompose.camera.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * ML Kit 人脸检测 —— 人脸/眼睛关键点辅助构图
 * 与原版逆向产物一致：原版打包 face-detection 组件
 */
class FaceDetectorHelper(context: Context) {

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .build()
        )
    }

    data class Face(
        val bounds: RectF,        // 归一化
        val leftEye: PointF?,     // 归一化
        val rightEye: PointF?
    )

    /** 检测人脸（归一化坐标 0..1） */
    suspend fun detect(bitmap: Bitmap): List<Face> = withContext(Dispatchers.Default) {
        runCatching {
            val image = InputImage.fromBitmap(bitmap, 0)
            val faces = detector.process(image).await()
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            faces.map { f ->
                val b = f.boundingBox
                val leftEye = f.getLandmark(com.google.mlkit.vision.face.FaceLandmark.LEFT_EYE)?.position
                val rightEye = f.getLandmark(com.google.mlkit.vision.face.FaceLandmark.RIGHT_EYE)?.position
                Face(
                    RectF(b.left / w, b.top / h, b.right / w, b.bottom / h),
                    leftEye?.let { PointF(it.x / w, it.y / h) },
                    rightEye?.let { PointF(it.x / w, it.y / h) }
                )
            }
        }.getOrElse { emptyList() }
    }

    /** 人脸构图建议 */
    fun adviceFrom(faces: List<Face>): String? {
        if (faces.isEmpty()) return null
        val first = faces.first()
        val eyeY = first.leftEye?.y ?: (first.bounds.top + first.bounds.height() * 0.35f)
        return when {
            eyeY in 0.30f..0.38f -> "人脸位置很好：眼睛正处于上三分线附近 ✓"
            eyeY < 0.30f -> "人物略靠上：将镜头稍向下移，让眼睛落在上三分线（画面 1/3 处）"
            else -> "人物略靠下：将镜头稍向上移，让眼睛落在上三分线（画面 1/3 处）"
        }
    }
}

/** 轻量 await 辅助 */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    kotlin.coroutines.suspendCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
    }
