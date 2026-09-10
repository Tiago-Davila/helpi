package com.helpi.conversation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helpi.conversation.R
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.Turn
import com.helpi.conversation.chat.TurnState
import com.helpi.conversation.session.AudioChannelState
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.session.SessionState
import com.helpi.conversation.session.VisualChannelState
import com.helpi.conversation.ui.components.EntradaBar
import com.helpi.conversation.ui.components.EvaEstado
import com.helpi.conversation.ui.components.EvaOrb
import com.helpi.conversation.ui.components.Hotbar
import com.helpi.conversation.ui.components.ModoEntrada
import com.helpi.conversation.ui.components.SeccionHotbar
import com.helpi.conversation.ui.components.TurnBubble
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiType
import com.helpi.conversation.vision.FramingEvaluator

/** Vista dentro de la sección Traductor. */
private enum class Vista { TRADUCTOR, CONVERSACION }

/**
 * Clase de tamaño. No se usa `WindowSizeClass` para no sumar una dependencia
 * por dos umbrales: el ancho decide una o dos columnas, el alto decide si el
 * orbe y los botones se achican.
 */
private data class Medidas(val anchoExpandido: Boolean, val altoComprimido: Boolean) {
    val margen: Dp get() = if (anchoExpandido) 32.dp else 20.dp
    val alturaEntrada: Dp get() = if (altoComprimido) 88.dp else 112.dp
}

/**
 * Pantalla de conversación.
 *
 * La pantalla mira a la persona sorda: todo el estado del sistema es visual,
 * legible a distancia de conversación, y la atribución de hablantes no
 * depende solo del color.
 */
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    cameraPreview: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()

    if (state.session == SessionState.PAUSADA) {
        PantallaPausada(onResume = viewModel::resume)
        return
    }

    var vista by remember { mutableStateOf(Vista.TRADUCTOR) }
    var seccion by remember { mutableStateOf(SeccionHotbar.TRADUCTOR) }
    var modo by remember { mutableStateOf<ModoEntrada?>(null) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(HelpiColors.BgBase)) {
        val medidas = Medidas(
            anchoExpandido = maxWidth >= 600.dp,
            altoComprimido = maxHeight < 520.dp,
        )

        Column(modifier = Modifier.fillMaxSize()) {
            AvisoDeAlcance(state, modifier = Modifier.padding(horizontal = medidas.margen))

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    seccion == SeccionHotbar.CUENTA -> SeccionPendiente("Cuenta")
                    vista == Vista.CONVERSACION -> VistaConversacion(
                        state = state,
                        medidas = medidas,
                        onVolver = { vista = Vista.TRADUCTOR },
                        onMarkIncorrect = viewModel::markIncorrect,
                    )
                    else -> VistaTraductor(
                        state = state,
                        medidas = medidas,
                        modo = modo,
                        cameraPreview = cameraPreview,
                        onVerConversacion = { vista = Vista.CONVERSACION },
                        onIniciar = viewModel::start,
                        onFinalizar = viewModel::closeSession,
                    )
                }
            }

            if (seccion == SeccionHotbar.TRADUCTOR) {
                EntradaBar(
                    seleccionado = modo,
                    disponibles = modosDisponibles(state),
                    onSeleccionar = { elegido -> modo = if (modo == elegido) null else elegido },
                    altura = medidas.alturaEntrada,
                    modifier = Modifier.padding(
                        horizontal = medidas.margen,
                        vertical = if (medidas.altoComprimido) 8.dp else 16.dp,
                    ),
                )
            }

            Hotbar(seleccionada = seccion, onSeleccionar = { seccion = it })
        }
    }
}

// ----------------------------------------------------------------------
// Traductor
// ----------------------------------------------------------------------

@Composable
private fun VistaTraductor(
    state: SessionCoordinator.UiState,
    medidas: Medidas,
    modo: ModoEntrada?,
    cameraPreview: @Composable () -> Unit,
    onVerConversacion: () -> Unit,
    onIniciar: () -> Unit,
    onFinalizar: () -> Unit,
) {
    val estadoEva = evaEstado(state)
    val ultimo = state.turns.lastOrNull { it.speaker() != Speaker.SYSTEM && it.text().isNotEmpty() }

    if (medidas.anchoExpandido) {
        // Dos columnas: el escenario a la izquierda, la conversación viva a
        // la derecha. Es la disposición de tablet que ya asumía la app.
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = medidas.margen)) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                Escenario(state, medidas, modo, estadoEva, cameraPreview)
            }
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(start = medidas.margen),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ListaDeTurnos(
                    turns = state.turns,
                    onMarkIncorrect = null,
                    modifier = Modifier.weight(1f),
                )
                ControlesDeSesion(state, onIniciar, onFinalizar)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = medidas.margen),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Escenario(state, medidas, modo, estadoEva, cameraPreview)

        if (ultimo != null) {
            FraseTraducida(ultimo)
            // `Repetir` está en el diseño pero el coordinador todavía no
            // expone repetición de voz: se muestra apagado en vez de aceptar
            // el toque y no hacer nada.
            BotonContorno(texto = "Repetir", habilitado = false, onClick = {})
            TextoAuxiliar("Repetir todavía no está conectado a la salida de voz.")
        }

        ControlesDeSesion(state, onIniciar, onFinalizar)

        if (state.turns.isNotEmpty()) {
            BotonContorno(
                texto = "Ver conversación (${state.turns.size})",
                habilitado = true,
                onClick = onVerConversacion,
            )
        }

        Box(modifier = Modifier.height(8.dp))
    }
}

/**
 * El escenario: o la esfera de Eva, o el visor de cámara cuando la persona
 * eligió mostrar una seña.
 */
@Composable
private fun Escenario(
    state: SessionCoordinator.UiState,
    medidas: Medidas,
    modo: ModoEntrada?,
    estadoEva: EvaEstado,
    cameraPreview: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
    ) {
        if (modo == ModoEntrada.MOSTRAR) {
            Visor(cameraPreview)
            Text(
                text = "Mostrá la seña frente a la cámara",
                style = HelpiType.BodyS,
                color = HelpiColors.LedMuted,
                textAlign = TextAlign.Center,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                EvaOrb(estado = estadoEva, modifier = Modifier.size(56.dp))
                Text(
                    text = textoDeEstado(state, estadoEva),
                    style = HelpiType.BodyM,
                    color = HelpiColors.LedSoft,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            MensajeDeEncuadre(state.framing, state.visual)
        } else {
            val ladoOrbe = if (medidas.altoComprimido) 160.dp else 240.dp
            EvaOrb(estado = estadoEva, modifier = Modifier.size(ladoOrbe))
            Text(
                text = textoDeEstado(state, estadoEva),
                style = HelpiType.BodyM,
                color = HelpiColors.LedSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        EstadoDeCanales(state)
    }
}

@Composable
private fun Visor(cameraPreview: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(350f / 428f)
            .clip(RoundedCornerShape(28.dp))
            .background(HelpiColors.BrandCore),
        contentAlignment = Alignment.Center,
    ) {
        cameraPreview()
    }
}

@Composable
private fun FraseTraducida(turn: Turn) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .background(HelpiColors.BrandCore, RoundedCornerShape(28.dp))
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (turn.speaker() == Speaker.DEAF) "Vos · señas" else "Oyente · micrófono",
            style = HelpiType.LabelAutor,
            color = HelpiColors.LedMuted,
        )
        Text(text = turn.text(), style = HelpiType.BodyChat, color = HelpiColors.LedSoft)
        if (turn.state() == TurnState.PARTIAL) {
            Text(text = "transcribiendo…", style = HelpiType.BodyS, color = HelpiColors.LedMuted)
        }
    }
}

// ----------------------------------------------------------------------
// Conversación
// ----------------------------------------------------------------------

@Composable
private fun VistaConversacion(
    state: SessionCoordinator.UiState,
    medidas: Medidas,
    onVolver: () -> Unit,
    onMarkIncorrect: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BarraSuperior(onVolver = onVolver, modifier = Modifier.padding(horizontal = 16.dp))
        ListaDeTurnos(
            turns = state.turns,
            onMarkIncorrect = onMarkIncorrect,
            modifier = Modifier.weight(1f).padding(horizontal = medidas.margen),
        )
    }
}

@Composable
private fun BarraSuperior(onVolver: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().height(88.dp), contentAlignment = Alignment.CenterStart) {
        Row(
            modifier = Modifier
                .height(56.dp)
                .widthIn(min = 140.dp)
                .background(HelpiColors.BrandCore, RoundedCornerShape(18.dp))
                .clickable(onClick = onVolver)
                .semantics { contentDescription = "Volver al traductor" }
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_flecha_volver),
                contentDescription = null,
                tint = HelpiColors.LedSoft,
                modifier = Modifier.size(width = 12.dp, height = 10.dp),
            )
            Text(text = "Volver", style = HelpiType.LabelBoton, color = HelpiColors.LedSoft)
        }
    }
}

@Composable
private fun ListaDeTurnos(
    turns: List<Turn>,
    onMarkIncorrect: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // La conversación se sigue sola: al llegar un turno nuevo baja al final.
    LaunchedEffect(turns.size) {
        if (turns.isNotEmpty()) listState.animateScrollToItem(turns.lastIndex)
    }

    if (turns.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TextoAuxiliar("Todavía no hay turnos en esta conversación.")
        }
        return
    }

    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
    ) {
        items(turns, key = { it.id() }) { turn ->
            TurnBubble(turn = turn, onMarkIncorrect = onMarkIncorrect)
        }
    }
}

// ----------------------------------------------------------------------
// Chrome: alcance, canales, sesión
// ----------------------------------------------------------------------

/**
 * Aviso permanente de alcance. En Figma dice solo «Eva puede cometer
 * errores»; acá lleva además qué es y qué no es la herramienta, porque es
 * un requisito de la interfaz, no del texto de marketing (CLAUDE.md §4).
 */
@Composable
private fun AvisoDeAlcance(state: SessionCoordinator.UiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .border(1.dp, HelpiColors.Divider, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                text = "Asistencia experimental · 64 señas · No reemplaza a una intérprete",
                style = HelpiType.CaptionDisclaimer,
                color = HelpiColors.LedMuted,
                textAlign = TextAlign.Center,
            )
        }
        state.notice?.let { aviso ->
            Text(
                text = aviso,
                style = HelpiType.BodyS,
                color = HelpiColors.Warning,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
            )
        }
    }
}

@Composable
private fun EstadoDeCanales(state: SessionCoordinator.UiState) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TextoAuxiliar("${audioLabel(state.audio)} · ${visualLabel(state.visual)}")
        if (!state.capabilities.tts) {
            Text(
                text = "Sin voz: la persona oyente no recibirá audio",
                style = HelpiType.BodyS,
                color = HelpiColors.Warning,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MensajeDeEncuadre(issue: FramingEvaluator.Issue, visual: VisualChannelState) {
    if (visual == VisualChannelState.NO_DISPONIBLE) return
    val texto = when (issue) {
        FramingEvaluator.Issue.OK -> null
        FramingEvaluator.Issue.SIN_PERSONA -> "No veo a nadie frente a la cámara"
        FramingEvaluator.Issue.SIN_HOMBROS -> "No veo tus hombros: alejá un poco el dispositivo"
        FramingEvaluator.Issue.MANOS_AL_BORDE -> "Tus manos están cerca del borde"
        FramingEvaluator.Issue.MANO_PERDIDA -> "Perdí de vista una mano"
    } ?: return

    Text(
        text = texto,
        style = HelpiType.BodyM,
        color = HelpiColors.Warning,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun ControlesDeSesion(
    state: SessionCoordinator.UiState,
    onIniciar: () -> Unit,
    onFinalizar: () -> Unit,
) {
    when (state.session) {
        SessionState.LISTA -> BotonContorno("Iniciar conversación", true, onIniciar)
        SessionState.ACTIVA, SessionState.ACTIVA_LIMITADA ->
            BotonContorno("Finalizar y borrar la conversación", true, onFinalizar)
        SessionState.BLOQUEADA -> TextoAuxiliar(
            "No hay ningún canal disponible: revisá los permisos de cámara y micrófono.",
        )
        else -> Unit
    }
}

/** Botón secundario del diseño: contorno de luz, sin relleno. */
@Composable
private fun BotonContorno(texto: String, habilitado: Boolean, onClick: () -> Unit) {
    val color = if (habilitado) HelpiColors.LedSoft else HelpiColors.LedMuted.copy(alpha = 0.38f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 420.dp)
            .height(72.dp)
            .border(1.5.dp, color, RoundedCornerShape(24.dp))
            .clickable(enabled = habilitado, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = texto, style = HelpiType.LabelBoton, color = color, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TextoAuxiliar(texto: String) {
    Text(
        text = texto,
        style = HelpiType.BodyS,
        color = HelpiColors.LedMuted,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun SeccionPendiente(nombre: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        TextoAuxiliar("$nombre todavía no está implementado.")
    }
}

@Composable
private fun PantallaPausada(onResume: () -> Unit) {
    // El chat no se muestra hasta reanudar explícitamente (privacidad).
    Surface(color = HelpiColors.BgBase, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EvaOrb(estado = EvaEstado.REPOSO, modifier = Modifier.size(160.dp))
            Text(
                text = "La conversación está pausada",
                style = HelpiType.BodyChat,
                color = HelpiColors.LedSoft,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Por privacidad, el contenido queda oculto. " +
                    "Si la pausa supera los 2 minutos, la conversación se descarta.",
                style = HelpiType.BodyM,
                color = HelpiColors.LedMuted,
                textAlign = TextAlign.Center,
            )
            BotonContorno("Reanudar", true, onResume)
        }
    }
}

// ----------------------------------------------------------------------
// Derivaciones de estado
// ----------------------------------------------------------------------

/**
 * Estado de la esfera a partir de los canales. El orden importa: hablar tapa
 * a escuchar, porque mientras la app habla no transcribe.
 */
private fun evaEstado(state: SessionCoordinator.UiState): EvaEstado = when {
    state.audio == AudioChannelState.TTS_HABLANDO ||
        state.audio == AudioChannelState.CERRANDO_ENTRADA_STT -> EvaEstado.HABLANDO

    state.visual == VisualChannelState.REARMANDO ||
        state.visual == VisualChannelState.CONFIRMANDO_FIN -> EvaEstado.PENSANDO

    state.audio == AudioChannelState.STT_ESCUCHANDO ||
        state.audio == AudioChannelState.STT_TRANSCRIBIENDO -> EvaEstado.ESCUCHANDO

    else -> EvaEstado.REPOSO
}

private fun textoDeEstado(state: SessionCoordinator.UiState, estado: EvaEstado): String = when {
    state.session == SessionState.LISTA -> "Iniciá la conversación para empezar"
    state.session == SessionState.INICIO ||
        state.session == SessionState.PREPARANDO -> "Preparando…"
    estado == EvaEstado.HABLANDO -> "Eva está hablando"
    estado == EvaEstado.PENSANDO -> "Pensando…"
    estado == EvaEstado.ESCUCHANDO -> "Escuchando al oyente…"
    state.visual == VisualChannelState.CAPTURANDO_SENA -> "Viendo tu seña…"
    else -> "Tocá para hablar"
}

/**
 * Un modo solo se ofrece si su canal existe. `Escribir` no tiene soporte en
 * el coordinador todavía, así que nunca está disponible.
 */
private fun modosDisponibles(state: SessionCoordinator.UiState): Set<ModoEntrada> = buildSet {
    if (state.capabilities.stt) add(ModoEntrada.HABLAR)
    if (state.capabilities.vision) add(ModoEntrada.MOSTRAR)
}

private fun audioLabel(s: AudioChannelState): String = when (s) {
    AudioChannelState.NO_DISPONIBLE -> "Micrófono: no disponible"
    AudioChannelState.STT_ESCUCHANDO -> "Micrófono: escuchando"
    AudioChannelState.STT_TRANSCRIBIENDO -> "Escuchando al oyente"
    AudioChannelState.TTS_PENDIENTE -> "Voz pendiente"
    AudioChannelState.CERRANDO_ENTRADA_STT,
    AudioChannelState.TTS_HABLANDO,
    -> "La app está hablando. No transcribe voces."
    AudioChannelState.GUARDA_ACUSTICA -> "Reactivando escucha"
}

private fun visualLabel(s: VisualChannelState): String = when (s) {
    VisualChannelState.NO_DISPONIBLE -> "Cámara: no disponible"
    VisualChannelState.BUSCANDO_ENCUADRE -> "Buscando encuadre"
    VisualChannelState.ESPERANDO_REPOSO -> "Dejá las manos quietas un momento"
    VisualChannelState.ARMADO -> "Cámara lista"
    VisualChannelState.CAPTURANDO_SENA -> "Viendo tu seña"
    VisualChannelState.CONFIRMANDO_FIN -> "Esperando que termines"
    VisualChannelState.REARMANDO -> "Procesando"
    VisualChannelState.CALIDAD_INSUFICIENTE -> "No veo bien tus manos"
    VisualChannelState.SUSPENDIDO_RENDIMIENTO -> "Traducción visual pausada por rendimiento"
}
