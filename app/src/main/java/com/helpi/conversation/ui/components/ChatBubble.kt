package com.helpi.conversation.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.helpi.conversation.R
import com.helpi.conversation.chat.Turn
import com.helpi.conversation.chat.TurnState
import com.helpi.conversation.chat.VoiceState
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiShapes
import com.helpi.conversation.ui.theme.HelpiType

/** Ancho máximo de una burbuja, para que el lado opuesto siempre se note. */
private const val ANCHO_BURBUJA = 0.86f

/**
 * Un turno de la conversación.
 *
 * Tres señales dicen lo mismo a la vez: el nombre escrito arriba, el lado en
 * que se apoya y la esquina «mordida» que apunta a quien habló. La atribución
 * nunca depende solo del color, porque en una herramienta de comunicación
 * atribuir mal un mensaje es peor que no mostrarlo.
 *
 * `mostrarAutor` es falso cuando el turno anterior es de la misma persona: el
 * nombre se escribe una vez por tanda, no una vez por mensaje.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TurnoMensaje(
    turn: Turn,
    autor: String,
    mostrarAutor: Boolean,
    propio: Boolean,
    onRepetir: (() -> Unit)?,
    onMarcarIncorrecto: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val hayAcciones = onRepetir != null || onMarcarIncorrecto != null

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (propio) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(ANCHO_BURBUJA),
            horizontalAlignment = if (propio) Alignment.End else Alignment.Start,
        ) {
            if (mostrarAutor) {
                Text(
                    text = autor,
                    style = HelpiType.LabelAutor,
                    color = HelpiColors.LedMuted,
                    modifier = Modifier
                        .padding(start = 6.dp, end = 6.dp, bottom = 5.dp)
                        .clearAndSetSemantics { },
                )
            }
            Box {
                Column(
                    modifier = Modifier
                        .background(
                            color = if (propio) HelpiColors.BrandCore else HelpiColors.SurfaceRaised,
                            shape = if (propio) HelpiShapes.BurbujaPropia else HelpiShapes.BurbujaAjena,
                        )
                        .then(
                            if (propio) {
                                Modifier
                            } else {
                                Modifier.border(
                                    1.dp,
                                    HelpiColors.Divider,
                                    HelpiShapes.BurbujaAjena,
                                )
                            },
                        )
                        .combinedClickable(
                            enabled = hayAcciones,
                            onClick = {},
                            onLongClick = { menu = true },
                            onLongClickLabel = "Opciones del mensaje",
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .semantics {
                            contentDescription = "$autor: ${turn.text()}"
                            liveRegion = LiveRegionMode.Polite
                        },
                ) {
                    Text(
                        text = turn.text(),
                        style = HelpiType.BodyChat,
                        color = HelpiColors.LedSoft,
                    )
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    onRepetir?.let { repetir ->
                        DropdownMenuItem(
                            text = { Text("Repetir en voz alta") },
                            onClick = { menu = false; repetir() },
                        )
                    }
                    onMarcarIncorrecto?.let { marcar ->
                        DropdownMenuItem(
                            text = { Text("La traducción no es correcta") },
                            onClick = { menu = false; marcar() },
                        )
                    }
                }
            }
            estadoDelTurno(turn)?.let {
                Text(
                    text = it,
                    style = HelpiType.BodyS,
                    color = HelpiColors.LedMuted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Estado de transcripción y de voz. Se dice en palabras porque un icono no
 * alcanza para distinguir «pendiente» de «no pronunciado», y esa diferencia
 * cambia lo que la otra persona escuchó.
 */
private fun estadoDelTurno(turn: Turn): String? = when {
    turn.markedIncorrect() -> "Marcado como incorrecto"
    turn.state() == TurnState.PARTIAL -> "Transcribiendo…"
    turn.state() == TurnState.INCOMPLETE -> "Incompleto"
    turn.voiceState() == VoiceState.NOT_SPOKEN -> "No se pudo leer en voz alta"
    else -> null
}

/**
 * Aviso del sistema: acá es donde aparece «no reconocí la seña», que es el
 * resultado esperado cuando la confianza no alcanza el umbral (CLAUDE.md §5).
 *
 * Va contraído a una línea. Estos avisos explican con detalle qué pasó y por
 * qué, y ese detalle importa cuando alguien lo busca, pero desplegado empuja
 * la conversación fuera de la pantalla cada vez que una seña no se reconoce.
 * El chevron solo aparece si el texto realmente no entra: un aviso corto no
 * necesita un control que no hace nada.
 *
 * Contraído o no, el texto completo viaja en `contentDescription`, así que un
 * lector de pantalla nunca recibe la versión recortada.
 */
@Composable
fun NotaSistema(texto: String, modifier: Modifier = Modifier) {
    var expandido by rememberSaveable(texto) { mutableStateOf(false) }
    var desbordado by remember(texto) { mutableStateOf(false) }
    val desplegable = desbordado || expandido
    val giro by animateFloatAsState(if (expandido) 180f else 0f, label = "chevron")

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(min = 48.dp)
                .background(HelpiColors.Surface, HelpiShapes.Chip)
                .border(1.dp, HelpiColors.Warning.copy(alpha = 0.42f), HelpiShapes.Chip)
                .clickable(enabled = desplegable) { expandido = !expandido }
                .semantics {
                    contentDescription = texto
                    if (desplegable) {
                        role = Role.Button
                        stateDescription = if (expandido) "Desplegado" else "Contraído"
                    }
                    liveRegion = LiveRegionMode.Polite
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(HelpiColors.Warning, HelpiShapes.Pastilla),
            )
            Text(
                text = texto,
                style = HelpiType.BodyS,
                color = HelpiColors.LedSoft,
                maxLines = if (expandido) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { if (!expandido) desbordado = it.hasVisualOverflow },
                modifier = Modifier
                    .weight(1f)
                    .clearAndSetSemantics { },
            )
            if (desplegable) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron),
                    contentDescription = null,
                    tint = HelpiColors.LedMuted,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(giro),
                )
            }
        }
    }
}
