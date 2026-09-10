package com.helpi.conversation

import android.Manifest
import android.content.pm.PackageManager
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.helpi.conversation.ui.ConversationScreen
import com.helpi.conversation.ui.ConversationViewModel
import com.helpi.conversation.ui.components.EvaEstado
import com.helpi.conversation.ui.components.EvaOrb
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiTheme
import com.helpi.conversation.ui.theme.HelpiType
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
        // Producción protege conversación y miniaturas. Debug permite las
        // capturas necesarias para QA visual automatizado en el emulador.
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
        setContent {
            HelpiTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = HelpiColors.BgBase) {
                    // El fondo llega al borde, el contenido no: con targetSdk
                    // 35 Android dibuja de borde a borde y sin esto la barra
                    // de estado tapa el aviso de alcance y la barra de
                    // navegación tapa la hotbar.
                    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
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
    }

    @Composable
    private fun CameraPreview() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            Text(
                text = "No puedo ver señas: la cámara no está permitida.",
                style = HelpiType.BodyM,
                color = HelpiColors.LedSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
            return
        }
        // El visor que lo contiene ya fija proporción y esquinas: acá solo
        // hay que llenarlo.
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                PreviewView(context).also { previewView ->
                    startVision(previewView)
                }
            },
            onRelease = { cameraSource?.stop() },
        )
    }

    private fun startVision(previewView: PreviewView) {
        cameraSource?.let { existing ->
            existing.start(this, previewView.surfaceProvider)
            return
        }
        val ext = try {
            HolisticExtractor(
                context = this,
                onFrame = { frame -> viewModel.coordinator.onLandmarks(frame) },
                onError = { error ->
                    viewModel.coordinator.reportVisionUnavailable(
                        "MediaPipe no pudo analizar la cámara: ${error.message ?: "error desconocido"}",
                    )
                },
            )
        } catch (e: Throwable) {
            // .task ausente/incompatible, o .so del ABI equivocado
            // (UnsatisfiedLinkError es Error, no Exception): el canal visual
            // se inhabilita con causa, la actividad no se cae.
            viewModel.coordinator.reportVisionUnavailable(
                "No se pudo iniciar el detector corporal: ${e.message ?: e.javaClass.simpleName}",
            )
            null
        }
        extractor = ext
        if (ext == null) return
        cameraSource = FrontCameraSource(
            context = this,
            onFrame = { bitmap, ts -> ext.analyze(bitmap, ts) },
            onUnavailable = viewModel.coordinator::reportVisionUnavailable,
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
        // CameraX se pausa y reanuda con el LifecycleOwner sin reconstruir
        // el pipeline; no se desliga acá porque eso impedía reanudar cámara.
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        EvaOrb(estado = EvaEstado.REPOSO, modifier = Modifier.size(132.dp))
        Text(
            text = "Entendernos, más fácil",
            style = HelpiType.BodyChat.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = HelpiColors.LedSoft,
            textAlign = TextAlign.Center,
        )
        Text(
            style = HelpiType.BodyM,
            color = HelpiColors.LedMuted,
            text = "Helpi combina señas, texto y voz sin enviar tu conversación a internet.",
            textAlign = TextAlign.Center,
        )
        OnboardingCard(
            title = "Cómo usarlo",
            text = "Sostené el teléfono con la pantalla hacia la otra persona. Podés mostrar una seña aislada, escribir o escuchar su respuesta.",
        )
        OnboardingCard(
            title = "Importante",
            text = "Es una asistencia experimental de 64 señas. Puede equivocarse, no reemplaza a una persona intérprete y no sirve para emergencias.",
        )
        Text(
            style = HelpiType.CaptionDisclaimer,
            color = HelpiColors.LedMuted,
            text = "Todo queda en el dispositivo y se borra al finalizar. Modelo LSA64 (LIDI, UNLP), CC BY-NC-SA 4.0.",
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp).height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = HelpiColors.BrandCore),
        ) {
            Text(
                text = "Entendido, continuar",
                style = HelpiType.LabelBoton,
                color = HelpiColors.LedSoft,
            )
        }
    }
}

@Composable
private fun OnboardingCard(title: String, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
        color = HelpiColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HelpiColors.Divider),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(title, style = HelpiType.LabelBoton, color = HelpiColors.LedSoft)
            Text(text, style = HelpiType.BodyS, color = HelpiColors.LedMuted)
        }
    }
}
