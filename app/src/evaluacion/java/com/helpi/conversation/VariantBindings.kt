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
import com.helpi.conversation.observation.RecognitionObserver
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.evaluacion.consentimiento.AjustesEvaluacionRoute
import com.helpi.evaluacion.consentimiento.BorradoDatosRoute
import com.helpi.evaluacion.consentimiento.ConsentimientoRoute
import com.helpi.evaluacion.ui.AltaSesionRoute
import com.helpi.evaluacion.navigation.EvaluacionGraph
import com.helpi.evaluacion.registro.RegistroObserver

/** Evaluation-only UI and recognition wiring. */
@Suppress("ComposableNaming")
object VariantBindings {
    val recognitionObserver: RecognitionObserver = RegistroObserver()
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
        Spacer(Modifier.height(24.dp))
    }

    fun createSessionCoordinator(context: android.content.Context) =
        SessionCoordinator(context, recognitionObserver, deliveryMode)
}
