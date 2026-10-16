package com.helpi.conversation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.observation.RecognitionObserver
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.session.SessionState
import com.helpi.conversation.session.VisualChannelState
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.SigningDistanceGuide
import com.helpi.evaluacion.consentimiento.AjustesEvaluacionRoute
import com.helpi.evaluacion.consentimiento.BorradoDatosRoute
import com.helpi.evaluacion.consentimiento.ConsentimientoRoute
import com.helpi.evaluacion.navigation.EvaluacionGraph
import com.helpi.evaluacion.registro.RegistroObserver
import com.helpi.evaluacion.ui.AltaSesionRoute
import com.helpi.evaluacion.ui.SesionesRoute
import com.helpi.evaluacion.ui.protocolo.ProtocoloCamaraUiState
import com.helpi.evaluacion.ui.protocolo.ProtocoloDistanciaUiState
import com.helpi.evaluacion.ui.protocolo.ProtocoloRoute

/** Evaluation-only UI and recognition wiring. */
@Suppress("ComposableNaming")
object VariantBindings {
    internal val registroObserver = RegistroObserver()
    val recognitionObserver: RecognitionObserver = registroObserver
    val deliveryMode = SessionCoordinator.DeliveryMode.OBSERVER_ONLY

    @Composable
    fun applicationContent(modifier: Modifier, content: @Composable () -> Unit) {
        Box(modifier) {
            when (EvaluacionGraph.currentRoute) {
                EvaluacionGraph.VALIDATION_ROUTE -> ConsentimientoRoute(
                    onVolver = EvaluacionGraph::volver,
                    onAjustes = { EvaluacionGraph.navigate(EvaluacionGraph.SETTINGS_ROUTE) },
                    onContinuar = { EvaluacionGraph.navigate(EvaluacionGraph.ALTA_SESION_ROUTE) }
                )

                EvaluacionGraph.ALTA_SESION_ROUTE -> AltaSesionRoute(
                    onVolver = { EvaluacionGraph.navigate(EvaluacionGraph.VALIDATION_ROUTE) },
                    onSesionCreada = EvaluacionGraph::establecerSesionActual
                )

                EvaluacionGraph.SESSIONS_ROUTE -> SesionesRoute(
                    onVolver = EvaluacionGraph::volver,
                    onRetomar = EvaluacionGraph::establecerSesionActual
                )

                EvaluacionGraph.SETTINGS_ROUTE -> AjustesEvaluacionRoute(
                    onVolver = { EvaluacionGraph.navigate(EvaluacionGraph.VALIDATION_ROUTE) },
                    onBorrarDatos = { EvaluacionGraph.navigate(EvaluacionGraph.DELETE_ROUTE) }
                )

                EvaluacionGraph.DELETE_ROUTE -> BorradoDatosRoute(
                    onVolver = { EvaluacionGraph.navigate(EvaluacionGraph.SETTINGS_ROUTE) }
                )

                else -> content()
            }
            evaluationBadge(Modifier.align(Alignment.TopCenter))
        }
    }

    @Composable
    fun applicationContent(
        modifier: Modifier,
        sessionCoordinator: SessionCoordinator,
        cameraPreview: @Composable () -> Unit,
        onStartRecognition: () -> Unit,
        content: @Composable () -> Unit
    ) {
        val state = sessionCoordinator.uiState.collectAsStateWithLifecycle().value
        Box(modifier) {
            when (EvaluacionGraph.currentRoute) {
                EvaluacionGraph.VALIDATION_ROUTE -> ConsentimientoRoute(
                    onVolver = EvaluacionGraph::volver,
                    onAjustes = { EvaluacionGraph.navigate(EvaluacionGraph.SETTINGS_ROUTE) },
                    onContinuar = { EvaluacionGraph.navigate(EvaluacionGraph.ALTA_SESION_ROUTE) }
                )

                EvaluacionGraph.ALTA_SESION_ROUTE -> AltaSesionRoute(
                    onVolver = { EvaluacionGraph.navigate(EvaluacionGraph.VALIDATION_ROUTE) },
                    onSesionCreada = EvaluacionGraph::establecerSesionActual
                )

                EvaluacionGraph.SESSIONS_ROUTE -> SesionesRoute(
                    onVolver = EvaluacionGraph::volver,
                    onRetomar = EvaluacionGraph::establecerSesionActual
                )

                EvaluacionGraph.PROTOCOL_ROUTE -> ProtocoloRoute(
                    sessionId = EvaluacionGraph.sesionActualId,
                    camara = protocoloCamaraState(state),
                    observer = registroObserver,
                    cameraPreview = cameraPreview,
                    onStartRecognition = onStartRecognition,
                    onExit = {
                        sessionCoordinator.pause()
                        EvaluacionGraph.volver()
                    }
                )

                EvaluacionGraph.SETTINGS_ROUTE -> AjustesEvaluacionRoute(
                    onVolver = { EvaluacionGraph.navigate(EvaluacionGraph.VALIDATION_ROUTE) },
                    onBorrarDatos = { EvaluacionGraph.navigate(EvaluacionGraph.DELETE_ROUTE) }
                )

                EvaluacionGraph.DELETE_ROUTE -> BorradoDatosRoute(
                    onVolver = { EvaluacionGraph.navigate(EvaluacionGraph.SETTINGS_ROUTE) }
                )

                else -> content()
            }
            evaluationBadge(Modifier.align(Alignment.TopCenter))
        }
    }

    @Composable
    fun evaluationBadge(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF8B1E1E))
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("evaluationBadge"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "EVALUACIÓN — registra lo que se seña",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    @Composable
    fun validationMenuEntry() {
        TextButton(
            onClick = { EvaluacionGraph.navigate(EvaluacionGraph.VALIDATION_ROUTE) },
            modifier = Modifier.testTag("validationEntry")
        ) {
            Text("Validación")
        }
        TextButton(
            onClick = { EvaluacionGraph.navigate(EvaluacionGraph.SESSIONS_ROUTE) },
            modifier = Modifier.testTag("evaluationSessionsEntry")
        ) {
            Text("Sesiones de evaluación")
        }
        Spacer(Modifier.height(24.dp))
    }

    fun createSessionCoordinator(context: android.content.Context) =
        SessionCoordinator(context, recognitionObserver, deliveryMode)
}

private fun protocoloCamaraState(state: SessionCoordinator.UiState): ProtocoloCamaraUiState {
    val listo = state.session in setOf(SessionState.ACTIVA, SessionState.ACTIVA_LIMITADA) &&
        state.capabilities.vision &&
        state.cameraEnabled &&
        state.recognitionMode == RecognitionMode.SINGLE_SIGN
    val preparacionTerminada = state.session in setOf(
        SessionState.ACTIVA,
        SessionState.ACTIVA_LIMITADA,
        SessionState.BLOQUEADA
    ) &&
        state.recognitionMode == RecognitionMode.SINGLE_SIGN
    val instruccionEncuadre = when (state.framing) {
        FramingEvaluator.Issue.SIN_PERSONA -> "Mostrá los hombros y las manos."
        FramingEvaluator.Issue.SIN_HOMBROS -> "Alejá un poco el teléfono para mostrar los hombros."
        FramingEvaluator.Issue.MANOS_AL_BORDE -> "Mantené las manos dentro del cuadro."
        FramingEvaluator.Issue.MANO_PERDIDA -> "Volvé a mostrar las dos manos."
        FramingEvaluator.Issue.OK -> when (state.visual) {
            VisualChannelState.ARMADO -> "Mostrá una seña."
            VisualChannelState.ESPERANDO_REPOSO -> "Dejá las manos quietas un momento."
            VisualChannelState.CAPTURANDO_SENA -> "Reconociendo tu seña…"
            VisualChannelState.REARMANDO -> "Procesando la seña…"
            else -> null
        }
    }
    val instruccion = instruccionEncuadre ?: when (state.signingDistance) {
        SigningDistanceGuide.State.UNKNOWN -> "Alineá los hombros con la guía."
        SigningDistanceGuide.State.MOVE_CLOSER -> "Acercate un poco."
        SigningDistanceGuide.State.OPTIMAL -> "Distancia adecuada."
        SigningDistanceGuide.State.MOVE_FARTHER -> "Alejate un poco."
    }
    val (estadoDistancia, distancia) = when (state.signingDistance) {
        SigningDistanceGuide.State.UNKNOWN ->
            "Alineá los hombros con la guía" to ProtocoloDistanciaUiState.DESCONOCIDA

        SigningDistanceGuide.State.MOVE_CLOSER ->
            "Acercate un poco" to ProtocoloDistanciaUiState.ACERCARSE

        SigningDistanceGuide.State.OPTIMAL ->
            "Distancia adecuada" to ProtocoloDistanciaUiState.ADECUADA

        SigningDistanceGuide.State.MOVE_FARTHER ->
            "Alejate un poco" to ProtocoloDistanciaUiState.ALEJARSE
    }
    val reconocimiento = when (state.visual) {
        VisualChannelState.NO_DISPONIBLE -> "Cámara no disponible."
        VisualChannelState.BUSCANDO_ENCUADRE -> "Buscando el encuadre."
        VisualChannelState.ESPERANDO_REPOSO -> "Esperando que dejes las manos quietas."
        VisualChannelState.ARMADO -> "Lista para reconocer."
        VisualChannelState.CAPTURANDO_SENA -> "Reconociendo la seña…"
        VisualChannelState.CONFIRMANDO_FIN -> "Confirmando el final de la seña…"
        VisualChannelState.REARMANDO -> "Procesando la seña…"
        VisualChannelState.CALIDAD_INSUFICIENTE -> "La calidad de imagen es insuficiente."
        VisualChannelState.SUSPENDIDO_RENDIMIENTO -> "Análisis pausado para cuidar el rendimiento."
    }
    return ProtocoloCamaraUiState(
        instruccionEncuadre = instruccion,
        estadoReconocimiento = reconocimiento,
        estadoDistancia = estadoDistancia,
        distancia = distancia,
        preparacionTerminada = preparacionTerminada,
        listoParaReconocer = listo,
        umbral = state.confidenceThreshold
    )
}
