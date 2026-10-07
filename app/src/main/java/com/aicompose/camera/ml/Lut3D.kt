package com.aicompose.camera.ml

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 3D LUT 滤镜引擎（16³ RGB 查找表 + 三线性插值，纯 CPU 本地实现）
 * 复用 Mola 相机资产格式：1 字节维度头(0x10) + 16*16*16*3 字节 RGB 数据
 */
class Lut3D(private val context: Context) {

    companion object {
        const val DIM = 16
        val FILTER_NAMES = listOf(
            "原图", "王家卫港风硬调", "德味徕卡硬调", "电影情绪深邃", "港风电影柔润",
            "滨田英明柔润", "通透明亮柔和", "经典婚礼柔润", "城市黑金硬调", "暗调街拍硬调",
            "柯达街拍柔润", "咖啡文艺浓郁", "奶油褪色", "莫兰迪森系柔润", "法式巧克力柔润",
            "黄金时刻", "风光大片硬调", "青橙夜景浓郁", "通透美食暖调", "霓虹粉紫"
        )
    }

    private class LutData(val data: ByteArray)

    private val lutCache = HashMap<Int, LutData>()

    /** 加载第 index 个 LUT（0 = 无滤镜） */
    private fun load(index: Int): LutData? {
        if (index == 0) return null
        lutCache[index]?.let { return it }
        return runCatching {
            val bytes = context.assets.open("luts/lut_%03d.lut".format(index - 1)).readBytes()
            // 1 字节维度头 + 数据
            val data = bytes.copyOfRange(1, bytes.size)
            if (data.size != DIM * DIM * DIM * 3) return null
            LutData(data).also { lutCache[index] = it }
        }.getOrNull()
    }

    /** 应用 LUT 到整图（可先降采样再放大以提速） */
    suspend fun apply(bitmap: Bitmap, index: Int): Bitmap = withContext(Dispatchers.Default) {
        val lut = load(index) ?: return@withContext bitmap.copy(Bitmap.Config.ARGB_8888, false)
        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val w = out.width
        val h = out.height
        val px = IntArray(w * h)
        out.getPixels(px, 0, w, 0, 0, w, h)
        val d = lut.data
        for (i in px.indices) {
            val p = px[i]
            val r = (p shr 16) and 0xff
            val g = (p shr 8) and 0xff
            val b = p and 0xff
            val (nr, ng, nb) = lookup(d, r, g, b)
            px[i] = (0xff shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        out.setPixels(px, 0, w, 0, 0, w, h)
        out
    }

    /** 三线性插值查表 */
    private fun lookup(d: ByteArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        val step = 255f / (DIM - 1)
        val fr = (r / step).coerceIn(0f, (DIM - 1).toFloat())
        val fg = (g / step).coerceIn(0f, (DIM - 1).toFloat())
        val fb = (b / step).coerceIn(0f, (DIM - 1).toFloat())
        val r0 = fr.toInt(); val r1 = (r0 + 1).coerceAtMost(DIM - 1)
        val g0 = fg.toInt(); val g1 = (g0 + 1).coerceAtMost(DIM - 1)
        val b0 = fb.toInt(); val b1 = (b0 + 1).coerceAtMost(DIM - 1)
        val dr = fr - r0; val dg = fg - g0; val db = fb - b0

        fun idx(ri: Int, gi: Int, bi: Int) = ((ri * DIM + gi) * DIM + bi) * 3
        fun sample(ri: Int, gi: Int, bi: Int): Triple<Int, Int, Int> {
            val o = idx(ri, gi, bi)
            val v = d[o].toInt() and 0xff
            return Triple(v, d[o + 1].toInt() and 0xff, d[o + 2].toInt() and 0xff)
        }

        // 8 邻域三线性
        val c000 = sample(r0, g0, b0); val c100 = sample(r1, g0, b0)
        val c010 = sample(r0, g1, b0); val c110 = sample(r1, g1, b0)
        val c001 = sample(r0, g0, b1); val c101 = sample(r1, g0, b1)
        val c011 = sample(r0, g1, b1); val c111 = sample(r1, g1, b1)

        fun mix(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()
        val x00 = Triple(mix(c000.first, c100.first, dr), mix(c000.second, c100.second, dr), mix(c000.third, c100.third, dr))
        val x10 = Triple(mix(c010.first, c110.first, dr), mix(c010.second, c110.second, dr), mix(c010.third, c110.third, dr))
        val x01 = Triple(mix(c001.first, c101.first, dr), mix(c001.second, c101.second, dr), mix(c001.third, c101.third, dr))
        val x11 = Triple(mix(c011.first, c111.first, dr), mix(c011.second, c111.second, dr), mix(c011.third, c111.third, dr))
        val y0 = Triple(mix(x00.first, x10.first, dg), mix(x00.second, x10.second, dg), mix(x00.third, x10.third, dg))
        val y1 = Triple(mix(x01.first, x11.first, dg), mix(x01.second, x11.second, dg), mix(x01.third, x11.third, dg))
        return Triple(mix(y0.first, y1.first, db), mix(y0.second, y1.second, db), mix(y0.third, y1.third, db))
    }
}
