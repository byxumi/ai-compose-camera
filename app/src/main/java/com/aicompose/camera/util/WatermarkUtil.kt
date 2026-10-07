package com.aicompose.camera.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.util.Random

/**
 * 艺术字水印 + 胶片噪点（自写，复用 Mola 提取的艺术字体）
 */
object WatermarkUtil {

    private const val DIM = 16

    /** 四种水印样式：0=无, 1=Allura 右下角, 2=Damion 左上角, 3=GreatVibes 居中 */
    fun addWatermark(ctx: Context, bitmap: Bitmap, mode: Int): Bitmap {
        if (mode == 0) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width
        val h = out.height
        val density = out.density

        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = when (mode) {
                1 -> w * 0.030f
                2 -> w * 0.028f
                else -> w * 0.050f
            }
            setShadowLayer(textSize / 3f, 0f, textSize / 6f, Color.Black)
            typeface = when (mode) {
                1 -> Typeface.createFromAsset(ctx.assets, "fonts/Allura-Regular.ttf")
                2 -> Typeface.createFromAsset(ctx.assets, "fonts/Damion-Regular.ttf")
                else -> Typeface.createFromAsset(ctx.assets, "fonts/GreatVibes-Regular.ttf")
            }
        }

        val text = "Mola Style"
        when (mode) {
            1 -> canvas.drawText(text, w * 0.66f, h * 0.93f, paint)
            2 -> canvas.drawText(text, w * 0.03f, h * 0.10f, paint)
            else -> {
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText(text, w / 2f, h * 0.52f, paint)
            }
        }
        return out
    }

    /** 胶片噪点：叠加高斯噪点 + 轻微颗粒 */
    fun addGrain(bitmap: Bitmap, strength: Int): Bitmap {
        if (strength <= 0) return bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        val rnd = Random(42L)
        val s = strength
        for (i in px.indices) {
            val p = px[i]
            val r = ((p shr 16) and 0xff)
            val g = ((p shr 8) and 0xff)
            val b = (p and 0xff)
            val n = rnd.nextInt(s * 2 + 1) - s
            val nr = (r + n).coerceIn(0, 255)
            val ng = (g + n).coerceIn(0, 255)
            val nb = (b + n).coerceIn(0, 255)
            px[i] = (0xff shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        return out
    }

}
