package com.aicompose.camera.util

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

object BitmapUtils {

    /** 将 ImageProxy 转为 Bitmap（YUV → RGB） */
    fun proxyToBitmap(proxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer: ByteBuffer = proxy.planes[0].buffer
            val uBuffer: ByteBuffer = proxy.planes[1].buffer
            val vBuffer: ByteBuffer = proxy.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, proxy.width, proxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(android.graphics.Rect(0, 0, proxy.width, proxy.height), 80, out)
            val imageBytes = out.toByteArray()
            val bmp = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            // 旋转到正立
            val rotation = proxy.imageInfo.rotationDegrees
            if (rotation != 0 && bmp != null) {
                val m = Matrix().apply { postRotate(rotation.toFloat()) }
                val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                if (rotated != bmp) bmp.recycle()
                rotated
            } else bmp
        } catch (e: Exception) {
            null
        }
    }

    fun scaleDown(bmp: Bitmap, maxSize: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        val scale = maxSize.toFloat() / kotlin.math.max(w, h)
        if (scale >= 1f) return bmp.copy(Bitmap.Config.ARGB_8888, false)
        return Bitmap.createScaledBitmap(bmp, (w * scale).toInt().coerceAtLeast(8), (h * scale).toInt().coerceAtLeast(8), true)
    }

    fun loadScaled(path: String, maxSize: Int): Bitmap? {
        val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / (sample * 2) > maxSize || opts.outHeight / (sample * 2) > maxSize) sample *= 2
        val o = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeFile(path, o)
    }
}
