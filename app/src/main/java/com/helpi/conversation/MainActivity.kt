package com.helpi.conversation

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helpi.conversation.ui.ConversationScreen
import com.helpi.conversation.ui.ConversationViewModel
import com.helpi.conversation.ui.components.LandmarkOverlay
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiTheme
import com.helpi.conversation.vision.FrontCameraSource
import com.helpi.conversation.vision.HolisticExtractor

class MainActivity : ComponentActivity() {
    private val viewModel: ConversationViewModel by viewModels()
    private var cameraSource: FrontCameraSource? = null
    private var extractor: HolisticExtractor? = null

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            viewModel.prepare()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        setContent {
            HelpiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = HelpiColors.BgBase) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        ConversationScreen(
                            viewModel = viewModel,
                            onStart = ::requestPermissionsAndPrepare,
                            cameraPreview = { CameraPreview() },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CameraPreview() {
        val landmarks by viewModel.landmarks.collectAsStateWithLifecycle()
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    PreviewView(context).also { preview ->
                        // TextureView respeta el recorte y las superposiciones de Compose.
                        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        preview.scaleType = PreviewView.ScaleType.FIT_CENTER
                        startVision(preview)
                    }
                },
                onRelease = {
                    cameraSource?.stop()
                    viewModel.clearLandmarks()
                },
            )
            LandmarkOverlay(
                frame = landmarks,
                visualState = state.visual,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    private fun startVision(previewView: PreviewView) {
        cameraSource?.let { existing ->
            existing.start(this, previewView.surfaceProvider)
            return
        }
        val ext = try {
            extractor ?: HolisticExtractor(
                context = this,
                onFrame = viewModel::onLandmarks,
                onError = {
                    viewModel.coordinator.reportVisionUnavailable("No se pudo analizar la cámara. Finalizá y volvé a iniciar.")
                },
                onMetrics = viewModel::onLandmarkMetrics,
            ).also { extractor = it }
        } catch (_: Throwable) {
            viewModel.coordinator.reportVisionUnavailable("No se pudo iniciar la cámara. Podés continuar escribiendo.")
            return
        }
        cameraSource = FrontCameraSource(
            context = this,
            onFrame = { bitmap, ts -> ext.analyze(bitmap, ts) },
            onUnavailable = viewModel.coordinator::reportVisionUnavailable,
            onMetrics = viewModel::onCameraMetrics,
        ).also { it.start(this, previewView.surfaceProvider) }
    }

    private fun requestPermissionsAndPrepare() {
        val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) viewModel.prepare() else permissionLauncher.launch(needed.toTypedArray())
    }

    override fun onPause() {
        super.onPause()
        viewModel.pause()
    }

    override fun onDestroy() {
        cameraSource?.shutdown()
        extractor?.close()
        super.onDestroy()
    }
}
