package com.helpi.conversation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.Turn
import com.helpi.conversation.chat.TurnState
import com.helpi.conversation.chat.VoiceState
import com.helpi.conversation.session.AudioChannelState
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.session.SessionState
import com.helpi.conversation.session.VisualChannelState
import com.helpi.conversation.vision.FramingEvaluator

/**
 * Pantalla de conversación. La pantalla mira a la persona sorda: todo el
 * estado del sistema es visual, legible a distancia de conversación, y la
 * atribución de hablantes no depende solo del color.
 */
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    cameraPreview: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    if (state.session == SessionState.PAUSADA) {
        // el chat no se muestra hasta reanudar explícitamente (privacidad)
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "La conversación está pausada", fontSize = 28.sp)
            Text(
                text = "Por privacidad, el contenido queda oculto. " +
                    "Si la pausa supera los 2 minutos, la conversación se descarta.",
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(modifier = Modifier.padding(top = 24.dp), onClick = viewModel::resume) {
                Text(text = "Reanudar", fontSize = 22.sp)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        StatusBar(state)
        Row(modifier = Modifier.weight(1f)) {
            ChatColumn(
                turns = state.turns,
                onMarkIncorrect = viewModel::markIncorrect,
                modifier = Modifier.weight(3f),
            )
            Column(modifier = Modifier.weight(2f).padding(8.dp)) {
                cameraPreview()
                FramingMessage(state.framing, state.visual)
            }
        }
        BottomBar(state, onStart = viewModel::start, onClose = viewModel::closeSession)
    }
}

@Composable
private fun StatusBar(state: SessionCoordinator.UiState) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "Asistencia experimental · 64 señas · No reemplaza a una persona intérprete",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = audioLabel(state.audio), fontSize = 16.sp)
                Text(text = visualLabel(state.visual), fontSize = 16.sp)
                if (!state.capabilities.tts) {
                    Text(
                        text = "Sin voz: la persona oyente no recibirá audio",
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            state.notice?.let {
                Text(text = it, fontSize = 16.sp, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun audioLabel(s: AudioChannelState): String = when (s) {
    AudioChannelState.NO_DISPONIBLE -> "Micrófono: no disponible"
    AudioChannelState.STT_ESCUCHANDO -> "Micrófono: escuchando"
    AudioChannelState.STT_TRANSCRIBIENDO -> "Escuchando al oyente…"
    AudioChannelState.TTS_PENDIENTE -> "Voz pendiente"
    AudioChannelState.CERRANDO_ENTRADA_STT,
    AudioChannelState.TTS_HABLANDO,
    -> "La app está hablando. No transcribe voces."
    AudioChannelState.GUARDA_ACUSTICA -> "Reactivando escucha…"
}

private fun visualLabel(s: VisualChannelState): String = when (s) {
    VisualChannelState.NO_DISPONIBLE -> "Cámara: no disponible"
    VisualChannelState.BUSCANDO_ENCUADRE -> "Buscando encuadre…"
    VisualChannelState.ESPERANDO_REPOSO -> "Dejá las manos quietas un momento"
    VisualChannelState.ARMADO -> "Cámara lista"
    VisualChannelState.CAPTURANDO_SENA -> "Viendo tu seña…"
    VisualChannelState.CONFIRMANDO_FIN -> "Esperando que termines…"
    VisualChannelState.REARMANDO -> "Procesando…"
    VisualChannelState.CALIDAD_INSUFICIENTE -> "No veo bien tus manos"
    VisualChannelState.SUSPENDIDO_RENDIMIENTO -> "Traducción visual pausada por rendimiento"
}

@Composable
private fun FramingMessage(issue: FramingEvaluator.Issue, visual: VisualChannelState) {
    if (visual == VisualChannelState.NO_DISPONIBLE) return
    val text = when (issue) {
        FramingEvaluator.Issue.OK -> null
        FramingEvaluator.Issue.SIN_PERSONA -> "No veo a nadie frente a la cámara"
        FramingEvaluator.Issue.SIN_HOMBROS -> "No veo tus hombros: alejá un poco la tablet"
        FramingEvaluator.Issue.MANOS_AL_BORDE -> "Tus manos están cerca del borde"
        FramingEvaluator.Issue.MANO_PERDIDA -> "Perdí de vista una mano"
    }
    text?.let {
        Text(
            text = it,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ChatColumn(
    turns: List<Turn>,
    onMarkIncorrect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(turns, key = { it.id() }) { turn ->
            TurnCard(turn, onMarkIncorrect)
        }
    }
}

@Composable
private fun TurnCard(turn: Turn, onMarkIncorrect: (Long) -> Unit) {
    when (turn.speaker()) {
        Speaker.SYSTEM -> SystemCard(turn)
        Speaker.HEARING -> SpeakerCard(
            turn = turn,
            label = "OYENTE · micrófono",
            alignEnd = false,
            container = Color(0xFFE3F2FD),
            onMarkIncorrect = null,
        )
        Speaker.DEAF -> SpeakerCard(
            turn = turn,
            label = "VOS · señas",
            alignEnd = true,
            container = Color(0xFFE8F5E9),
            onMarkIncorrect = onMarkIncorrect,
        )
    }
}

@Composable
private fun SpeakerCard(
    turn: Turn,
    label: String,
    alignEnd: Boolean,
    container: Color,
    onMarkIncorrect: ((Long) -> Unit)?,
) {
    if (turn.state() == TurnState.PENDING && turn.text().isEmpty()) return
    if (turn.state() == TurnState.REJECTED) return // el rechazo lo cuenta el sistema

    Row(modifier = Modifier.fillMaxWidth()) {
        if (alignEnd) Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .background(container, RoundedCornerShape(12.dp))
                .border(
                    width = if (alignEnd) 3.dp else 1.dp, // distinción no solo por color
                    color = Color.DarkGray,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(12.dp),
        ) {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                text = turn.text(),
                fontSize = 26.sp, // legible a distancia de conversación
            )
            when (turn.state()) {
                TurnState.PARTIAL -> Meta("transcribiendo…")
                TurnState.INCOMPLETE -> Meta("Incompleto")
                else -> Unit
            }
            when (turn.voiceState()) {
                VoiceState.PENDING -> Meta("Voz pendiente")
                VoiceState.SPOKEN -> Meta("Pronunciado")
                VoiceState.NOT_SPOKEN -> Meta("No pronunciado")
                VoiceState.CANCELLED -> Meta("Cancelado antes de la voz")
                VoiceState.NONE -> Unit
            }
            if (turn.markedIncorrect()) {
                Meta("Marcado como incorrecto")
            } else if (onMarkIncorrect != null && turn.state() == TurnState.FINAL) {
                TextButton(onClick = { onMarkIncorrect(turn.id()) }) {
                    Text(text = "No quise decir eso", fontSize = 18.sp)
                }
            }
        }
        if (!alignEnd) Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun Meta(text: String) {
    Text(text = text, fontSize = 14.sp, color = Color.DarkGray)
}

@Composable
private fun SystemCard(turn: Turn) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(Color(0xFFFFF8E1), RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = "SISTEMA", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(text = turn.text(), fontSize = 18.sp)
        }
    }
}

@Composable
private fun BottomBar(
    state: SessionCoordinator.UiState,
    onStart: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.session == SessionState.LISTA) {
            Button(onClick = onStart) {
                Text(text = "Iniciar conversación", fontSize = 20.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (state.session == SessionState.ACTIVA || state.session == SessionState.ACTIVA_LIMITADA) {
            Button(onClick = onClose) {
                Text(text = "Finalizar y borrar la conversación", fontSize = 20.sp)
            }
        }
    }
}
