package com.aicompose.camera.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.aicompose.camera.compose.CompositionAnalyzer
import com.aicompose.camera.compose.CompositionResult
import com.aicompose.camera.mlkit.SceneLabeler
import com.aicompose.camera.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/**
 * 相机主界面（Compose）—— Mola 风格深色沉浸式设计
 * 实时预览 + 构图叠加 + 评分圆环 + 场景识别 + 滤镜条 + 大快门
 */
@Composable
fun CameraScreen(
    onCapture: (String) -> Unit,
    onOpenEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analyzer = remember { CompositionAnalyzer() }
    val sceneLabeler = remember { SceneLabeler(context) }
    val uiScope = rememberCoroutineScope()

    var result by remember { mutableStateOf(CompositionResult.empty()) }
    var sceneAdvice by remember { mutableStateOf<String?>(null) }
    var filterIndex by remember { mutableStateOf(0) }
    var blurEnabled by remember { mutableStateOf(false) }
    var frameCount by remember { mutableIntStateOf(0) }

    val imageCapture = remember { mutableStateOf<ImageCapture?>(null) }

    // CameraX 绑定
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val providerFuture = ProcessCameraProvider.getInstance(ctx)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()
                imageCapture.value = capture
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(Executors.singleThreadExecutor()) { proxy ->
                    analyzeFrame(proxy, analyzer, sceneLabeler, uiScope) { r, advice ->
                        result = r
                        if (advice != null) sceneAdvice = advice
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, capture, analysis
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )

    // 构图叠加层
    CompositionOverlay(result = result, modifier = Modifier.fillMaxSize())

    // ===== 顶部信息区 =====
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // 评分圆环 + 文本
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(score = result.score)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "构图评分",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Text(
                    text = "${result.score} 分",
                    color = Color(0xFFFFD54F),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        sceneAdvice?.let {
            Spacer(Modifier.height(6.dp))
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                maxLines = 2
            )
        }
    }

    // ===== 底部控制区 =====
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
            .padding(horizontal = 20.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 滤镜条
        FilterStrip(
            selected = filterIndex,
            blurEnabled = blurEnabled,
            onSelect = { filterIndex = it },
            onToggleBlur = { blurEnabled = !blurEnabled }
        )
        Spacer(Modifier.height(14.dp))

        // 控制行：设置 | 快门 | 图库
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(
                onClick = { /* 设置页预留 */ },
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "设置", tint = Color.White)
            }

            // 大快门
            ShutterButton(
                onClick = {
                    val cap = imageCapture.value ?: return@ShutterButton
                    val file = File(context.cacheDir, "shot_${System.currentTimeMillis()}.jpg")
                    cap.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                onCapture(file.absolutePath)
                            }
                            override fun onError(exc: ImageCaptureException) {
                                Log.e("Camera", "capture error", exc)
                            }
                        }
                    )
                }
            )

            IconButton(
                onClick = {
                    val last = lastShot(context)
                    if (last != null) onOpenEditor(last.absolutePath)
                },
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = "图库", tint = Color.White)
            }
        }
    }
}

// ============ 子组件 ============

/** 评分圆环（带动画） */
@Composable
private fun ScoreRing(score: Int) {
    val animated by animateFloatAsState(
        targetValue = score / 100f,
        animationSpec = tween(600),
        label = "score"
    )
    Canvas(modifier = Modifier.size(52.dp)) {
        val stroke = 4.dp.toPx()
        val sweep = animated * 360f
        drawArc(
            color = Color.White.copy(alpha = 0.15f),
            startAngle = -90f, sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = stroke)
        )
        val color = when {
            score >= 80 -> Color(0xFF4CAF50)
            score >= 60 -> Color(0xFFFFC107)
            else -> Color(0xFFFF7043)
        }
        drawArc(
            color = color,
            startAngle = -90f, sweepAngle = sweep,
            useCenter = false,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

/** 大快门按钮（涟漪反馈） */
@Composable
private fun ShutterButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(74.dp)
            .shadow(12.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .border(3.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color(0xFFE0E0E0))
        )
    }
}

/** 滤镜条（4 种滤镜 + 虚化开关） */
private val filterNames = listOf("原图", "暖调", "镜像", "黑白")

@Composable
private fun FilterStrip(
    selected: Int,
    blurEnabled: Boolean,
    onSelect: (Int) -> Unit,
    onToggleBlur: () -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(filterNames) { index, name ->
            val active = selected == index
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color(0xFFFFC107) else Color.White.copy(alpha = 0.12f))
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = name,
                    color = if (active) Color(0xFF231A00) else Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
        // 虚化开关
        item {
            val active = blurEnabled
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color(0xFF26A69A) else Color.White.copy(alpha = 0.12f))
                    .clickable { onToggleBlur() }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    Icons.Filled.BlurOn,
                    contentDescription = "人像虚化",
                    tint = if (active) Color.White else Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "虚化",
                    color = if (active) Color.White else Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

// ============ 帧分析逻辑（复用现有引擎） ============

private fun analyzeFrame(
    proxy: ImageProxy,
    analyzer: CompositionAnalyzer,
    sceneLabeler: SceneLabeler,
    uiScope: CoroutineScope,
    onResult: (CompositionResult, String?) -> Unit
) {
    val bitmap = BitmapUtils.proxyToBitmap(proxy) ?: return
    proxy.close()
    val scaled = BitmapUtils.scaleDown(bitmap, 320)
    val r = analyzer.analyze(scaled)
    bitmap.recycle()
    var advice: String? = null
    // 低频 ML Kit 标注（每 45 帧）
    frameAnalysisCounter++
    if (frameAnalysisCounter % 45 == 0) {
        val bmpCopy = scaled.copy(Bitmap.Config.ARGB_8888, false)
        uiScope.launch {
            val labels = sceneLabeler.label(bmpCopy)
            advice = sceneLabeler.adviceFrom(labels)
            onResult(r, advice)
            bmpCopy.recycle()
        }
    } else {
        onResult(r, null)
    }
    scaled.recycle()
}

private var frameAnalysisCounter = 0

private fun lastShot(ctx: android.content.Context): File? {
    val dir = ctx.cacheDir
    return dir.listFiles { f -> f.name.startsWith("shot_") }?.maxByOrNull { it.lastModified() }
}
