package com.helpi.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.helpi.conversation.ui.ConversationScreen
import com.helpi.conversation.ui.ConversationViewModel
import com.helpi.conversation.vision.FrontCameraSource
import com.helpi.conversation.vision.HolisticExtractor

/**
 * Punto de entrada. FLAG_SECURE porque la conversación contiene datos
 * sensibles (Ley 25.326): no debe aparecer en capturas ni miniaturas.
 *
 * Orientación física (decisión tomada): cámara FRONTAL, pantalla mirando a
 * la persona sorda. El primer uso lo explica antes de encender sensores.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: ConversationViewModel by viewModels()

    private var cameraSource: FrontCameraSource? = null
    private var extractor: HolisticExtractor? = null
    private var onboardingDone by mutableStateOf(false)
    private var permissionsChecked by mutableStateOf(false)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionsChecked = true
            viewModel.prepare()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!onboardingDone) {
                        Onboarding {
                            onboardingDone = true
                            requestPermissionsAndPrepare()
                        }
                    } else {
                        ConversationScreen(
                            viewModel = viewModel,
                            cameraPreview = { CameraPreview() },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CameraPreview() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            Text(
                text = "No puedo ver señas: la cámara no está permitida.",
                fontSize = 20.sp,
            )
            return
        }
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f),
            factory = { context ->
                PreviewView(context).also { previewView ->
                    startVision(previewView)
                }
            },
        )
    }

    private fun startVision(previewView: PreviewView) {
        if (cameraSource != null) return
        val ext = try {
            HolisticExtractor(
                context = this,
                onFrame = { frame -> viewModel.coordinator.onLandmarks(frame) },
                onError = { /* el coordinador ya refleja calidad insuficiente */ },
            )
        } catch (e: Throwable) {
            // .task ausente/incompatible, o .so del ABI equivocado
            // (UnsatisfiedLinkError es Error, no Exception): el canal visual
            // se inhabilita con causa, la actividad no se cae.
            null
        }
        extractor = ext
        if (ext == null) return
        cameraSource = FrontCameraSource(
            context = this,
            onFrame = { bitmap, ts -> ext.analyze(bitmap, ts) },
            onUnavailable = { /* reflejado por capacidades */ },
        ).also { it.start(this, previewView.surfaceProvider) }
    }

    private fun requestPermissionsAndPrepare() {
        val needed = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            .filterNot(::hasPermission)
        if (needed.isEmpty()) {
            permissionsChecked = true
            viewModel.prepare()
        } else {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    override fun onPause() {
        super.onPause()
        // pasar a segundo plano pausa sensores y descarta lo pendiente
        cameraSource?.stop()
        viewModel.pause()
    }

    override fun onDestroy() {
        cameraSource?.shutdown()
        extractor?.close()
        super.onDestroy()
    }
}

@Composable
private fun Onboarding(onContinue: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text(
            text = "Herramienta de ASISTENCIA experimental",
            fontSize = 28.sp,
        )
        Text(
            modifier = Modifier.padding(top = 12.dp),
            fontSize = 20.sp,
            text = "Reconoce solo 64 señas aisladas de LSA64 y puede equivocarse. " +
                "No reemplaza a una persona intérprete ni sirve para emergencias. " +
                "El vocabulario no fue diseñado para comunicación asistida.",
        )
        Text(
            modifier = Modifier.padding(top = 12.dp),
            fontSize = 20.sp,
            text = "Sostené la tablet con la PANTALLA hacia la otra persona. " +
                "Vos vas a escuchar sus respuestas por el parlante.",
        )
        Text(
            modifier = Modifier.padding(top = 12.dp),
            fontSize = 16.sp,
            text = "La conversación vive solo en memoria y se borra al cerrarla. " +
                "Nada sale del dispositivo. " +
                "Modelo entrenado sobre LSA64 (LIDI, UNLP) — CC BY-NC-SA 4.0.",
        )
        Button(
            modifier = Modifier.padding(top = 24.dp),
            onClick = onContinue,
        ) {
            Text(text = "Entendido, continuar", fontSize = 22.sp)
        }
    }
}
