@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.helpi.evaluacion.ui.protocolo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helpi.conversation.vision.SigningDistanceGuide
import com.helpi.evaluacion.R
import com.helpi.evaluacion.datos.entidades.CausaSinResultado
import com.helpi.evaluacion.datos.entidades.ResultadoIntento

data class ProtocoloCamaraUiState(
    val instruccionEncuadre: String,
    val estadoReconocimiento: String,
    val estadoDistancia: String = "",
    val distancia: ProtocoloDistanciaUiState = ProtocoloDistanciaUiState.DESCONOCIDA,
    val preparacionTerminada: Boolean = false,
    val listoParaReconocer: Boolean = false,
    val umbral: Float = 0.9f
)

enum class ProtocoloDistanciaUiState {
    DESCONOCIDA,
    ACERCARSE,
    ADECUADA,
    ALEJARSE
}

data class ProtocoloAcciones(
    val onDescartar: () -> Unit,
    val onRepetir: () -> Unit,
    val onLoHiceMal: () -> Unit,
    val onContinuar: () -> Unit,
    val onSeguir: () -> Unit,
    val onRetomarOtroDia: () -> Unit,
    val onPausar: () -> Unit,
    val onTerminarSesion: () -> Unit,
    val onSalir: () -> Unit
)

@Composable
@Suppress("LongMethod")
fun ProtocoloScreen(
    state: ProtocoloUiState,
    camara: ProtocoloCamaraUiState,
    cameraPreview: @Composable () -> Unit,
    acciones: ProtocoloAcciones,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.protocolo_progreso, state.posicion, state.bloque),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.testTag("protocoloProgress")
        )
        Text(
            text = stringResource(R.string.protocolo_sena_pedida),
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = state.glosaEsperada,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("protocoloExpectedGloss")
        )

        if (state.etapa != EtapaProtocolo.PAUSA_BLOQUE &&
            state.etapa != EtapaProtocolo.RETOMAR_OTRO_DIA &&
            state.etapa != EtapaProtocolo.COMPLETA
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .testTag("protocoloCameraPreview"),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    cameraPreview()
                    GuiaDistanciaProtocolo(
                        state = camara.distancia,
                        modifier = Modifier.fillMaxSize().testTag("protocoloDistanceGuide")
                    )
                }
            }
            Text(
                text = camara.instruccionEncuadre,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("protocoloFramingGuide")
            )
            Text(
                text = camara.estadoDistancia,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("protocoloDistanceStatus")
            )
            Text(
                text = camara.estadoReconocimiento,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("protocoloRecognitionState")
            )
            ReferenciaVideoPlayer()
        }

        mensajeDeEtapa(state)?.let { mensaje ->
            Text(
                text = mensaje,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("protocoloResultMessage")
            )
        }

        when (state.etapa) {
            EtapaProtocolo.PAUSA_BLOQUE -> {
                Button(
                    onClick = acciones.onSeguir,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("protocoloContinueBlockButton")
                ) {
                    Text(stringResource(R.string.protocolo_seguir))
                }
                OutlinedButton(
                    onClick = acciones.onRetomarOtroDia,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("protocoloResumeTomorrowButton")
                ) {
                    Text(stringResource(R.string.protocolo_retomar_otro_dia))
                }
            }

            EtapaProtocolo.RETOMAR_OTRO_DIA -> Text(
                text = stringResource(R.string.protocolo_sesion_pausada),
                modifier = Modifier.testTag("protocoloPausedMessage")
            )

            EtapaProtocolo.COMPLETA -> {
                Text(
                    text = stringResource(R.string.protocolo_completado),
                    modifier = Modifier.testTag("protocoloCompleteMessage")
                )
                OutlinedButton(
                    onClick = acciones.onSalir,
                    modifier = Modifier.fillMaxWidth().testTag("protocoloExitButton")
                ) {
                    Text(stringResource(R.string.protocolo_salir))
                }
            }

            else -> ControlesDeIntento(state, acciones)
        }

        if (state.etapa != EtapaProtocolo.COMPLETA &&
            state.etapa != EtapaProtocolo.RETOMAR_OTRO_DIA
        ) {
            OutlinedButton(
                onClick = acciones.onPausar,
                enabled = state.etapa != EtapaProtocolo.RESULTADO_PENDIENTE,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("protocoloPauseButton")
            ) {
                Text(stringResource(R.string.protocolo_pausar))
            }
            OutlinedButton(
                onClick = acciones.onTerminarSesion,
                enabled = state.etapa != EtapaProtocolo.RESULTADO_PENDIENTE,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("protocoloFinishButton")
            ) {
                Text(stringResource(R.string.protocolo_terminar))
            }
        }
    }
}

@Composable
@Suppress("MagicNumber")
private fun GuiaDistanciaProtocolo(
    state: ProtocoloDistanciaUiState,
    modifier: Modifier = Modifier
) {
    val color = when (state) {
        ProtocoloDistanciaUiState.ADECUADA -> Color(0xFF4CAF50)
        ProtocoloDistanciaUiState.ACERCARSE,
        ProtocoloDistanciaUiState.ALEJARSE -> Color(0xFFFFC107)
        ProtocoloDistanciaUiState.DESCONOCIDA -> Color.White.copy(alpha = 0.55f)
    }
    Canvas(modifier) {
        val maxSpan = SigningDistanceGuide.maxShoulderSpan(size.width.toInt(), size.height.toInt())
        val targetSpan = maxSpan * (1f + SigningDistanceGuide.MIN_FRACTION_OF_MAX) / 2f
        val halfSpan = size.width * targetSpan / 2f
        val centerX = size.width / 2f
        val top = size.height * 0.30f
        val bottom = size.height * 0.58f
        val tick = size.width * 0.035f
        val stroke = 2.dp.toPx()
        val left = centerX - halfSpan
        val right = centerX + halfSpan

        drawLine(
            color.copy(alpha = 0.78f),
            Offset(left, top),
            Offset(left, bottom),
            stroke,
            StrokeCap.Round
        )
        drawLine(
            color.copy(alpha = 0.78f),
            Offset(right, top),
            Offset(right, bottom),
            stroke,
            StrokeCap.Round
        )
        drawLine(color, Offset(left, top), Offset(left + tick, top), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(left + tick, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(right - tick, top), Offset(right, top), stroke, StrokeCap.Round)
        drawLine(
            color,
            Offset(right - tick, bottom),
            Offset(right, bottom),
            stroke,
            StrokeCap.Round
        )
    }
}

@Composable
private fun ControlesDeIntento(state: ProtocoloUiState, acciones: ProtocoloAcciones) {
    if (state.puedeDescartar) {
        OutlinedButton(
            onClick = acciones.onDescartar,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("protocoloDiscardButton")
        ) {
            Text(stringResource(R.string.protocolo_descartar))
        }
    }
    if (state.puedeRepetir) {
        Button(
            onClick = acciones.onRepetir,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("protocoloRepeatButton")
        ) {
            Text(stringResource(R.string.protocolo_repetir))
        }
    }
    if (state.etapa in setOf(
            EtapaProtocolo.RESULTADO_PENDIENTE,
            EtapaProtocolo.RESULTADO,
            EtapaProtocolo.SIN_RESULTADO
        )
    ) {
        OutlinedButton(
            onClick = acciones.onLoHiceMal,
            enabled = !state.loHiceMal,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("protocoloLoHiceMalButton")
        ) {
            Text(
                stringResource(
                    if (state.loHiceMal) {
                        R.string.protocolo_lo_hice_mal_marcado
                    } else {
                        R.string.protocolo_lo_hice_mal
                    }
                )
            )
        }
    }
    if (state.etapa == EtapaProtocolo.RESULTADO) {
        Button(
            onClick = acciones.onContinuar,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("protocoloContinueButton")
        ) {
            Text(stringResource(R.string.protocolo_continuar))
        }
    }
}

@Composable
private fun mensajeDeEtapa(state: ProtocoloUiState): String? = when (state.etapa) {
    EtapaProtocolo.ESPERANDO -> null
    EtapaProtocolo.RESULTADO_PENDIENTE -> state.glosaPredicha?.let {
        stringResource(R.string.protocolo_eva_propone, it)
    }
    EtapaProtocolo.RESULTADO -> when (state.resultado) {
        ResultadoIntento.SOBRE_UMBRAL -> state.glosaPredicha?.let {
            stringResource(R.string.protocolo_eva_reconocio, it)
        }
        ResultadoIntento.BAJO_UMBRAL -> stringResource(R.string.protocolo_no_reconocida)
        ResultadoIntento.SIN_RESULTADO, null -> null
    }
    EtapaProtocolo.SIN_RESULTADO -> mensajeDeCausa(state.causaSinResultado)
    EtapaProtocolo.PAUSA_BLOQUE -> stringResource(R.string.protocolo_pausa_bloque)
    EtapaProtocolo.COMPLETA -> stringResource(R.string.protocolo_completado)
    EtapaProtocolo.RETOMAR_OTRO_DIA -> stringResource(R.string.protocolo_sesion_pausada)
}

@Composable
private fun mensajeDeCausa(causa: CausaSinResultado?): String = when (causa) {
    CausaSinResultado.SIN_HOMBROS -> stringResource(R.string.protocolo_causa_sin_hombros)
    CausaSinResultado.SEGMENTO_CORTO -> stringResource(R.string.protocolo_causa_segmento_corto)
    CausaSinResultado.SEGMENTO_LARGO -> stringResource(R.string.protocolo_causa_segmento_largo)
    CausaSinResultado.SEGUIMIENTO_PERDIDO -> stringResource(R.string.protocolo_causa_seguimiento)
    CausaSinResultado.ORIENTACION_CAMBIO -> stringResource(R.string.protocolo_causa_orientacion)
    CausaSinResultado.ERROR_MODELO -> stringResource(R.string.protocolo_causa_modelo)
    CausaSinResultado.TIEMPO_AGOTADO -> stringResource(R.string.protocolo_causa_tiempo)
    null -> stringResource(R.string.protocolo_no_reconocida)
}
