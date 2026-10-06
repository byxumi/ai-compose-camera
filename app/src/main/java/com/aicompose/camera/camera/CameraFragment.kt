package com.aicompose.camera.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.aicompose.camera.R
import com.aicompose.camera.compose.CompositionAnalyzer
import com.aicompose.camera.compose.CompositionResult
import com.aicompose.camera.edit.EditActivity
import com.aicompose.camera.mlkit.SceneLabeler
import com.aicompose.camera.util.BitmapUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraFragment : Fragment() {

    private lateinit var overlay: GuideOverlayView
    private lateinit var scoreText: MaterialTextView
    private lateinit var tipsText: MaterialTextView
    private lateinit var captureButton: MaterialButton
    private lateinit var galleryFrame: FrameLayout
    private var imageCapture: ImageCapture? = null
    private val analyzer = CompositionAnalyzer()
    private lateinit var cameraExecutor: ExecutorService
    private var currentScore = 0
    private val uiScope = CoroutineScope(Dispatchers.Main)
    private var sceneLabeler: SceneLabeler? = null
    private var frameCount = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_camera, container, false)
        overlay = view.findViewById(R.id.overlay)
        scoreText = view.findViewById(R.id.scoreText)
        tipsText = view.findViewById(R.id.tipsText)
        captureButton = view.findViewById(R.id.captureButton)
        galleryFrame = view.findViewById(R.id.galleryFrame)
        cameraExecutor = Executors.newSingleThreadExecutor()

        sceneLabeler = SceneLabeler(requireContext())
        captureButton.setOnClickListener { takePhoto() }
        galleryFrame.setOnClickListener {
            val last = lastPhoto() ?: return@setOnClickListener
            startActivity(EditActivity.intent(requireContext(), last.absolutePath))
        }
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        startCamera()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(requireContext())
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(view?.findViewById<androidx.camera.view.PreviewView>(R.id.previewView)?.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                analyzeFrame(imageProxy)
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun analyzeFrame(proxy: ImageProxy) {
        val bitmap = BitmapUtils.proxyToBitmap(proxy) ?: return
        proxy.close()
        val scaled = BitmapUtils.scaleDown(bitmap, 320)
        val result = analyzer.analyze(scaled)
        bitmap.recycle(); scaled.recycle()
        frameCount++
        if (frameCount % 45 == 0) { // 低频 ML Kit 场景标注
            val labeler = sceneLabeler
            if (labeler != null) {
                uiScope.launch {
                    val labels = labeler.label(scaled)
                    val advice = labeler.adviceFrom(labels)
                    if (advice != null) {
                        tipsText.text = advice
                    }
                }
            }
        }
        requireActivity().runOnUiThread {
            overlay.result = result
            overlay.invalidate()
            scoreText.text = "构图评分 ${result.score}"
            currentScore = result.score
            if (tipsText.text.isNullOrEmpty()) tipsText.text = result.tips.firstOrNull() ?: ""
        }
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        val file = File(requireContext().cacheDir, "shot_${System.currentTimeMillis()}.jpg")
        capture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    requireActivity().runOnUiThread {
                        Toast.makeText(requireContext(), "拍摄成功（构图评分 $currentScore）", Toast.LENGTH_SHORT).show()
                        startActivity(EditActivity.intent(requireContext(), file.absolutePath))
                    }
                }
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(requireContext(), "拍摄失败", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun lastPhoto(): File? {
        val dir = requireContext().cacheDir
        return dir.listFiles { f -> f.name.startsWith("shot_") }?.maxByOrNull { it.lastModified() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
    }
}
