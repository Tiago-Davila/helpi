package com.helpi.conversation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.Turn
import com.helpi.conversation.chat.TurnState
import com.helpi.conversation.chat.VoiceState
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiType

/**
 * Un turno del chat.
 *
 * Gramática visual del diseño (`Chat / Burbuja`): un lado habla en bloque
 * azul lleno alineado a la izquierda, el otro en bloque con contorno
 * alineado a la derecha, y **el autor va escrito arriba, nunca implícito**.
 * Forma, posición y texto dicen lo mismo tres veces: la atribución no
 * depende del color.
 *
 * El archivo de Figma rotula el bloque izquierdo como «Eva». Acá el
 * izquierdo es la persona oyente: lo que entra por micrófono es su voz, no
 * del sistema. Eva reconoce, no conversa (CLAUDE.md §1), y atribuirle un
 * turno hablado sería una atribución falsa dentro de una herramienta de
 * comunicación.
 */
@Composable
fun TurnBubble(
    turn: Turn,
    onMarkIncorrect: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // El turno vacío todavía no dice nada; el rechazo lo cuenta el sistema.
    if (turn.state() == TurnState.PENDING && turn.text().isEmpty()) return
    if (turn.state() == TurnState.REJECTED) return

    when (turn.speaker()) {
        Speaker.SYSTEM -> SystemNote(turn, modifier)
        Speaker.HEARING -> SpeakerBubble(
            turn = turn,
            autor = "Oyente · micrófono",
            alineadoADerecha = false,
            onMarkIncorrect = null,
            modifier = modifier,
        )
        Speaker.DEAF -> SpeakerBubble(
            turn = turn,
            autor = "Vos · señas",
            alineadoADerecha = true,
            onMarkIncorrect = onMarkIncorrect,
            modifier = modifier,
        )
    }
}

@Composable
private fun SpeakerBubble(
    turn: Turn,
    autor: String,
    alineadoADerecha: Boolean,
    onMarkIncorrect: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    // La esquina "mordida" apunta al hablante: arriba-derecha si el bloque
    // está a la derecha, arriba-izquierda si está a la izquierda.
    val forma = if (alineadoADerecha) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 8.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, topEnd = 24.dp, bottomEnd = 24.dp, bottomStart = 24.dp)
    }

    Row(modifier = modifier.fillMaxWidth()) {
        if (alineadoADerecha) Spacer(Modifier.weight(0.12f))
        Column(
            modifier = Modifier.weight(0.88f),
            horizontalAlignment = if (alineadoADerecha) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = autor,
                style = HelpiType.LabelAutor,
                color = HelpiColors.LedMuted,
                modifier = Modifier.clearAndSetSemantics { },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (alineadoADerecha) {
                            Modifier.border(2.dp, HelpiColors.LedSoft, forma)
                        } else {
                            Modifier.background(HelpiColors.BrandCore, forma)
                        },
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .semantics { contentDescription = "$autor dijo: ${turn.text()}" },
            ) {
                Text(
                    text = turn.text(),
                    style = HelpiType.BodyChat,
                    color = HelpiColors.LedSoft,
                )
            }
            EstadoDelTurno(turn)
            if (turn.markedIncorrect()) {
                Meta("Marcado como incorrecto")
            } else if (onMarkIncorrect != null && turn.state() == TurnState.FINAL) {
                TextButton(onClick = { onMarkIncorrect(turn.id()) }) {
                    Text(
                        text = "No quise decir eso",
                        style = HelpiType.LabelBoton,
                        color = HelpiColors.LedSoft,
                    )
                }
            }
        }
        if (!alineadoADerecha) Spacer(Modifier.weight(0.12f))
    }
}

/**
 * Estado de transcripción y de voz. Se dice en palabras porque un icono no
 * alcanza para distinguir «pendiente» de «no pronunciado», y esa diferencia
 * cambia lo que la otra persona escuchó.
 */
@Composable
private fun EstadoDelTurno(turn: Turn) {
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
}

@Composable
private fun Meta(text: String) {
    Text(text = text, style = HelpiType.BodyS, color = HelpiColors.LedMuted)
}

/**
 * Mensajes del sistema: centrados y con acento ámbar, nunca con la forma de
 * una burbuja de persona. Acá es donde aparece «no reconocí la seña», que es
 * el resultado esperado cuando la confianza no alcanza el umbral.
 */
@Composable
private fun SystemNote(turn: Turn, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .background(Color.Transparent, RoundedCornerShape(16.dp))
                .border(1.dp, HelpiColors.Warning.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "SISTEMA",
                style = HelpiType.LabelHotbar,
                color = HelpiColors.Warning,
            )
            Text(
                text = turn.text(),
                style = HelpiType.BodyM,
                color = HelpiColors.LedSoft,
                textAlign = TextAlign.Center,
            )
        }
    }
}
