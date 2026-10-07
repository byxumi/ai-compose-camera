package com.aicompose.camera.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aicompose.camera.compose.CompositionAnalyzer
import com.aicompose.camera.ml.OnnxClassifier
import com.aicompose.camera.ml.Lut3D
import com.aicompose.camera.ml.PortraitSegmenter
import com.aicompose.camera.util.WatermarkUtil
import com.aicompose.camera.mlkit.SceneLabeler
import com.aicompose.camera.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * 构图编辑页（Compose）—— 大图展示 + 底部工具条 + AI 分析结果卡片
 */
@Composable
fun EditScreen(
    initialPath: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var rotation by remember { mutableIntStateOf(0) }
    var mirrored by remember { mutableStateOf(false) }
    var filterMode by remember { mutableIntStateOf(0) }
    var lutIndex by remember { mutableIntStateOf(0) }
    var noiseEnabled by remember { mutableStateOf(false) }
    var watermarkMode by remember { mutableIntStateOf(0) }
    var blurRadius by remember { mutableIntStateOf(0) }
    var analysisText by remember { mutableStateOf("") }
    var analyzing by remember { mutableStateOf(false) }
    var saveMsg by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            bitmap = runCatching {
                val bmp = BitmapUtils.loadScaled(uri.toString(), 2048) ?: return@runCatching null
                applyOrientation(bmp, uri, context)
            }.getOrNull()
        }
    }

    LaunchedEffect(initialPath) {
        bitmap = runCatching {
            BitmapUtils.loadScaled(initialPath, 2048)
        }.getOrNull()
    }

    fun applyFilters(src: Bitmap): Bitmap {
        var out = src
        val m = android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }
        if (rotation != 0) out = android.graphics.Bitmap.createBitmap(out, 0, 0, out.width, out.height, m, true)
        if (mirrored) out = android.graphics.Bitmap.createBitmap(out, 0, 0, out.width, out.height, android.graphics.Matrix().apply { postScale(-1f, 1f) }, true)
        when (filterMode) {
            1 -> out = warm(out)
            3 -> out = mono(out)
        }
        if (blurRadius > 0) out = gaussianBlur(out, blurRadius)
        return out
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
    ) {
        // 图片
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "编辑图",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } ?: Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("暂无图片，点击右上角选择", color = Color.Gray)
        }

        // 返回按钮
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = Color.White)
        }
        // 相册选择
        IconButton(
            onClick = { picker.launch("image/*") },
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopEnd)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
        ) {
            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "选择图片", tint = Color.White)
        }

        // 分析结果卡片
        if (analysisText.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = analysisText,
                    color = Color(0xFFFFD54F),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(12.dp),
                    maxLines = 6
                )
            }
        }

        // 保存提示
        saveMsg?.let {
            Text(
                text = it,
                color = Color.White,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 120.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF2E7D32))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // ===== 底部工具条 =====
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(vertical = 10.dp)
        ) {
            // 工具行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EditTool(Icons.Filled.RotateRight, "旋转") {
                    rotation = (rotation + 90) % 360
                    bitmap?.let { bitmap = applyFilters(it) }
                }
                EditTool(Icons.Filled.Flip, "镜像") {
                    mirrored = !mirrored
                    bitmap?.let { bitmap = applyFilters(it) }
                }
                EditTool(Icons.Filled.Palette, "滤镜") {
                    filterMode = (filterMode + 1) % 4
                    bitmap?.let { bitmap = applyFilters(it) }
                }
                EditTool(Icons.Filled.BlurOn, "虚化") {
                    blurRadius = if (blurRadius == 0) 4 else 0
                    if (blurRadius > 0) {
                        bitmap?.let { bmp ->
                            scope.launch(Dispatchers.Default) {
                                val out = PortraitSegmenter(context).blurBackground(bmp, blurRadius)
                                kotlinx.coroutines.withContext(Dispatchers.Main) { bitmap = out }
                            }
                        }
                    } else {
                        bitmap?.let { bitmap = applyFilters(it) }
                    }
                }
                EditTool(if (noiseEnabled) Icons.Filled.Grain else Icons.Filled.Grain, "胶片噪点") {
                    noiseEnabled = !noiseEnabled
                    bitmap?.let { bmp ->
                        scope.launch {
                            val out = if (noiseEnabled) WatermarkUtil.addGrain(bmp, 18) else bmp.copy(Bitmap.Config.ARGB_8888, true)
                            kotlinx.coroutines.withContext(Dispatchers.Main) { bitmap = out }
                        }
                    }
                }
                EditTool(Icons.Filled.ColorLens, "LUT滤镜") {
                    lutIndex = (lutIndex + 1) % 20
                    bitmap?.let { bmp ->
                        scope.launch {
                            val out = if (lutIndex == 0) bmp.copy(Bitmap.Config.ARGB_8888, true)
                            else Lut3D(context).apply(bmp, lutIndex)
                            kotlinx.coroutines.withContext(Dispatchers.Main) { bitmap = out }
                        }
                    }
                }
                EditTool(Icons.Filled.TextFields, "艺术字") {
                    watermarkMode = (watermarkMode + 1) % 4
                    bitmap?.let { bmp ->
                        scope.launch {
                            val out = WatermarkUtil.addWatermark(context, bmp, watermarkMode)
                            kotlinx.coroutines.withContext(Dispatchers.Main) { bitmap = out }
                        }
                    }
                }
                EditTool(Icons.Filled.Insights, "AI分析", loading = analyzing) {
                    val bmp = bitmap ?: return@EditTool
                    analyzing = true
                    analysisText = "AI 分析中…"
                    scope.launch(Dispatchers.Default) {
                        val cv = CompositionAnalyzer().analyze(BitmapUtils.scaleDown(bmp, 480))
                        val mlkit = SceneLabeler(context).label(bmp)
                        val onnx = OnnxClassifier(context).classify(bmp, 3)
                        val text = buildString {
                            append("构图评分 ${cv.score}/100  「${cv.tips.firstOrNull() ?: "优秀"}」")
                            if (mlkit.isNotEmpty()) append("\n[ML Kit] " + mlkit.take(3).joinToString("、") { it.text })
                            if (onnx.isNotEmpty()) append("\n[ONNX] " + onnx.joinToString("、") { it.name })
                            onnx.let { o ->
                                o.maxByOrNull { it.confidence }?.let { t ->
                                    OnnxClassifier(context).adviceFrom(o)?.let { append("\n$it") }
                                }
                            }
                        }
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            analysisText = text
                            analyzing = false
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            // 保存按钮
            Button(
                onClick = {
                    val bmp = bitmap ?: return@Button
                    val out = File(context.filesDir, "edited_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
                    saveMsg = "已保存：${out.absolutePath}"
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(46.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107), contentColor = Color(0xFF231A00))
            ) {
                Text("保存成片", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EditTool(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, loading: Boolean = false, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = Color(0xFFFFC107))
        } else {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text(label, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
    }
}

// ============ 图片工具函数 ============

private fun applyOrientation(bmp: Bitmap, uri: Uri, ctx: android.content.Context): Bitmap {
    val exif = ctx.contentResolver.openInputStream(uri)?.use {
        androidx.exifinterface.media.ExifInterface(it)
    }
    val orient = exif?.getAttributeInt(
        androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
        androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
    ) ?: 0
    return when (orient) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bmp, 90f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bmp, 180f)
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bmp, 270f)
        else -> bmp
    }
}

private fun rotate(bmp: Bitmap, deg: Float): Bitmap {
    val m = android.graphics.Matrix().apply { postRotate(deg) }
    val r = android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    if (r != bmp) bmp.recycle()
    return r
}

private fun warm(bmp: Bitmap): Bitmap {
    val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
    val px = IntArray(out.width * out.height)
    out.getPixels(px, 0, out.width, 0, 0, out.width, out.height)
    for (i in px.indices) {
        val r = (px[i] shr 16) and 0xff
        val g = (px[i] shr 8) and 0xff
        val b = px[i] and 0xff
        px[i] = (0xff shl 24) or (minOf(255, (r * 1.12f).toInt()) shl 16) or (minOf(255, (g * 1.04f).toInt()) shl 8) or ((b * 0.92f).toInt())
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

private fun gaussianBlur(bmp: Bitmap, radius: Int): Bitmap {
    val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
    val w = out.width; val h = out.height
    val px = IntArray(w * h)
    out.getPixels(px, 0, w, 0, 0, w, h)
    val r = if (radius > 0) radius else 1
    val tmp = IntArray(w * h)
    for (y in 0 until h) {
        for (x in 0 until w) {
            var rr = 0L; var gg = 0L; var bb = 0L; var n = 0L
            for (dy in -r..r) for (dx in -r..r) {
                val xx = (x + dx).coerceIn(0, w - 1)
                val yy = (y + dy).coerceIn(0, h - 1)
                val p = px[yy * w + xx]
                rr += (p shr 16) and 0xff; gg += (p shr 8) and 0xff; bb += p and 0xff; n++
            }
            tmp[y * w + x] = (0xff shl 24) or ((rr / n).toInt() shl 16) or ((gg / n).toInt() shl 8) or (bb / n).toInt()
        }
    }
    out.setPixels(tmp, 0, w, 0, 0, w, h)
    return out
}
