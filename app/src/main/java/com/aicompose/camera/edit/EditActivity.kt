package com.aicompose.camera.edit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import com.aicompose.camera.R
import com.aicompose.camera.compose.CompositionAnalyzer
import com.aicompose.camera.mlkit.SceneLabeler
import com.aicompose.camera.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.slider.Slider
import com.google.android.material.textview.MaterialTextView
import com.google.mlkit.vision.common.InputImage
import java.io.File
import java.io.FileOutputStream

class EditActivity : AppCompatActivity() {

    private lateinit var imageView: ImageView
    private var bitmap: Bitmap? = null
    private var rotation = 0
    private var blurAmount = 0
    private var filterMode = 0
    private lateinit var srcPath: String

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            srcPath = uri.toString()
            loadFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)
        imageView = findViewById(R.id.editImage)
        val scoreText = findViewById<MaterialTextView>(R.id.editScore)
        srcPath = intent.getStringExtra("path") ?: return

        loadSource(srcPath)

        findViewById<MaterialButton>(R.id.btnRotate).setOnClickListener {
            rotation = (rotation + 90) % 360
            applyTransform()
        }
        findViewById<MaterialButton>(R.id.btnMirror).setOnClickListener {
            applyTransform()
        }
        findViewById<MaterialButton>(R.id.btnFilter).setOnClickListener {
            filterMode = (filterMode + 1) % 4
            applyTransform()
        }
        findViewById<MaterialButton>(R.id.btnPick).setOnClickListener { pickImage.launch("image/*") }
        findViewById<Slider>(R.id.blurSlider).addOnChangeListener { _, value, _ ->
            blurAmount = value.toInt()
            applyTransform()
        }
        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener { save() }
        findViewById<MaterialButton>(R.id.btnAnalyze).setOnClickListener {
            val bmp = bitmap ?: return@setOnClickListener
            val scaled = BitmapUtils.scaleDown(bmp, 480)
            val r = CompositionAnalyzer().analyze(scaled)
            scaled.recycle()
            scoreText.text = "构图评分：${r.score}/100  「${r.tips.firstOrNull() ?: "优秀"}」"
            // ML Kit 场景识别（本地 bundled 模型）
            CoroutineScope(Dispatchers.Main).launch {
                val labels = SceneLabeler(this@EditActivity).label(bmp)
                val advice = SceneLabeler(this@EditActivity).adviceFrom(labels)
                val labelStr = labels.take(3).joinToString("、") { it.text }
                if (advice != null) scoreText.text = "${scoreText.text}
[场景] $labelStr
$advice"
            }
        }
    }

    private fun loadSource(path: String) {
        if (path.startsWith("content://") || path.startsWith("file://")) {
            loadFromUri(Uri.parse(path))
        } else {
            loadFromUri(Uri.fromFile(File(path)))
        }
    }

    private fun loadFromUri(uri: Uri) {
        runCatching {
            val bmp = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))
            val exif = contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
            val orient = exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) ?: 0
            var out = bmp
            when (orient) {
                ExifInterface.ORIENTATION_ROTATE_90 -> out = rotate(bmp, 90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> out = rotate(bmp, 180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> out = rotate(bmp, 270f)
                else -> Unit
            }
            bitmap = out
            applyTransform()
        }.onFailure { Toast.makeText(this, "图片加载失败", Toast.LENGTH_SHORT).show() }
    }

    private fun rotate(bmp: Bitmap, deg: Float): Bitmap {
        val m = Matrix().apply { postRotate(deg) }
        val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        if (r != bmp) bmp.recycle()
        return r
    }

    private fun applyTransform() {
        val src = bitmap ?: return
        val m = Matrix()
        m.postRotate(rotation.toFloat())
        if (filterMode == 2) m.postScale(-1f, 1f)
        var bmp = src
        if (rotation != 0 || filterMode == 2) {
            bmp = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, true)
        }
        // 滤镜（简单色调调整）
        when (filterMode) {
            1 -> bmp = warm(bmp)
            2 -> Unit
            3 -> bmp = mono(bmp)
        }
        // 虚化（简单高斯）
        if (blurAmount > 0) bmp = blur(bmp, blurAmount)
        imageView.setImageBitmap(bmp)
        if (bmp != src) bmp.recycle()
    }

    private fun warm(bmp: Bitmap): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(out.width * out.height)
        out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
        for (i in px.indices) {
            val r = (px[i] shr 16) and 0xff
            val g = (px[i] shr 8) and 0xff
            val b = px[i] and 0xff
            val nr = minOf(255, (r * 1.12f).toInt())
            val ng = minOf(255, (g * 1.04f).toInt())
            val nb = (b * 0.92f).toInt()
            px[i] = (0xff shl 24) or (nr shl 16) or (ng shl 8) or nb
        }
        out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        return out
    }

    private fun mono(bmp: Bitmap): Bitmap {
        val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
        val px = IntArray(out.width * out.height)
        out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
        for (i in px.indices) {
            val r = (px[i] shr 16) and 0xff
            val g = (px[i] shr 8) and 0xff
            val b = px[i] and 0xff
            val y = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
            px[i] = (0xff shl 24) or (y shl 16) or (y shl 8) or y
        }
        out.setPixels(px, 0, out.width, 0, 0, out.width, out.height)
        return out
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

    private fun save() {
        val bmp = bitmap ?: return
        val out = File(filesDir, "edited_${System.currentTimeMillis()}.jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        Toast.makeText(this, "已保存：${out.absolutePath}", Toast.LENGTH_LONG).show()
    }

    companion object {
        fun intent(ctx: Context, path: String): Intent =
            Intent(ctx, EditActivity::class.java).putExtra("path", path)
    }
}
