package com.helpi.conversation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import java.util.Locale

private enum class Vista { TRADUCTOR, CONVERSACION }

private data class Medidas(val anchoExpandido: Boolean, val altoComprimido: Boolean) {
    val margen: Dp get() = if (anchoExpandido) 32.dp else 18.dp
    val alturaEntrada: Dp get() = if (altoComprimido) 56.dp else 64.dp
}

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
    var modo by remember { mutableStateOf(ModoEntrada.ESCRIBIR) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(HelpiColors.BgBase)) {
        val medidas = Medidas(maxWidth >= 700.dp, maxHeight < 620.dp)
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            AppHeader(
                state = state,
                modifier = Modifier.padding(horizontal = medidas.margen),
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    seccion == SeccionHotbar.INFORMACION -> InformacionScreen(state, medidas)
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
                        onIniciar = viewModel::start,
                        onPreparar = viewModel::prepare,
                        onFinalizar = viewModel::closeSession,
                        onEnviarTexto = viewModel::submitTyped,
                        onRepetir = viewModel::repeatTurn,
                        onCambiarUmbral = viewModel::setConfidenceThreshold,
                        onVerConversacion = { vista = Vista.CONVERSACION },
                    )
                }
            }

            if (seccion == SeccionHotbar.TRADUCTOR && vista == Vista.TRADUCTOR) {
                EntradaBar(
                    seleccionado = modo,
                    disponibles = modosDisponibles(state),
                    onSeleccionar = { modo = it },
                    altura = medidas.alturaEntrada,
                    modifier = Modifier.padding(horizontal = medidas.margen, vertical = 10.dp),
                )
            }
            Hotbar(seleccionada = seccion, onSeleccionar = { seccion = it })
        }
    }
}

@Composable
private fun AppHeader(state: SessionCoordinator.UiState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().height(68.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text("Helpi", style = HelpiType.LabelBoton, color = HelpiColors.LedSoft)
            Text("Conversación accesible", style = HelpiType.BodyS, color = HelpiColors.LedMuted)
        }
        Surface(
            color = HelpiColors.Surface,
            shape = RoundedCornerShape(50),
            border = androidx.compose.foundation.BorderStroke(1.dp, HelpiColors.Divider),
        ) {
            Text(
                text = if (state.session == SessionState.ACTIVA ||
                    state.session == SessionState.ACTIVA_LIMITADA
                ) "● En curso" else "100 % local",
                style = HelpiType.LabelHotbar,
                color = if (state.session == SessionState.ACTIVA ||
                    state.session == SessionState.ACTIVA_LIMITADA
                ) HelpiColors.Success else HelpiColors.LedMuted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun VistaTraductor(
    state: SessionCoordinator.UiState,
    medidas: Medidas,
    modo: ModoEntrada,
    cameraPreview: @Composable () -> Unit,
    onIniciar: () -> Unit,
    onPreparar: () -> Unit,
    onFinalizar: () -> Unit,
    onEnviarTexto: (String) -> Unit,
    onRepetir: (Long) -> Unit,
    onCambiarUmbral: (Float) -> Unit,
    onVerConversacion: () -> Unit,
) {
    val activo = state.session == SessionState.ACTIVA ||
        state.session == SessionState.ACTIVA_LIMITADA
    val ultimo = state.turns.lastOrNull {
        it.speaker() != Speaker.SYSTEM && it.text().isNotBlank() && it.state() != TurnState.REJECTED
    }

    if (medidas.anchoExpandido) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = medidas.margen),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                PanelDeEntrada(modo, state, activo, cameraPreview, onEnviarTexto, onCambiarUmbral)
                ControlesDeSesion(state, onIniciar, onPreparar, onFinalizar)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TituloSeccion("Conversación")
                ListaDeTurnos(state.turns, null, Modifier.weight(1f))
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PanelDeEntrada(modo, state, activo, cameraPreview, onEnviarTexto, onCambiarUmbral)

        state.notice?.let { Aviso(it) }

        if (ultimo != null) {
            UltimoMensaje(
                turn = ultimo,
                puedeRepetir = activo && state.capabilities.tts && ultimo.speaker() == Speaker.DEAF,
                onRepetir = { onRepetir(ultimo.id()) },
            )
        }

        ControlesDeSesion(state, onIniciar, onPreparar, onFinalizar)

        if (state.turns.isNotEmpty()) {
            TextButton(onClick = onVerConversacion) {
                Text(
                    "Ver conversación (${state.turns.count { it.state() != TurnState.REJECTED }})",
                    style = HelpiType.LabelBoton,
                    color = HelpiColors.BrandCore,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun PanelDeEntrada(
    modo: ModoEntrada,
    state: SessionCoordinator.UiState,
    activo: Boolean,
    cameraPreview: @Composable () -> Unit,
    onEnviarTexto: (String) -> Unit,
    onCambiarUmbral: (Float) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        val (titulo, bajada) = when (modo) {
            ModoEntrada.ESCRIBIR -> "Escribir" to "El teléfono lo mostrará y lo dirá en voz alta."
            ModoEntrada.HABLAR -> "Escuchar" to "La otra persona puede responder con su voz."
            ModoEntrada.MOSTRAR -> "Cámara LSA" to "Mostrá una seña aislada y luego dejá las manos quietas."
        }
        TituloSeccion(titulo)
        Text(bajada, style = HelpiType.BodyS, color = HelpiColors.LedMuted)

        when (modo) {
            ModoEntrada.ESCRIBIR -> TecladoCard(activo, onEnviarTexto)
            ModoEntrada.HABLAR -> MicrofonoCard(state, activo)
            ModoEntrada.MOSTRAR -> CamaraCard(state, cameraPreview, onCambiarUmbral)
        }
    }
}

@Composable
private fun TecladoCard(activo: Boolean, onEnviarTexto: (String) -> Unit) {
    var texto by rememberSaveable { mutableStateOf("") }
    fun enviar() {
        if (activo && texto.isNotBlank()) {
            onEnviarTexto(texto)
            texto = ""
        }
    }

    Surface(
        color = HelpiColors.Surface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HelpiColors.Divider),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = texto,
                onValueChange = { if (it.length <= 300) texto = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(18.dp),
                label = { Text("Tu mensaje") },
                placeholder = { Text("Escribí lo que querés decir…") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { enviar() }),
                supportingText = {
                    Text(if (activo) "${texto.length}/300" else "Iniciá la conversación para enviar")
                },
            )
            Button(
                onClick = { enviar() },
                enabled = activo && texto.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(17.dp),
                colors = ButtonDefaults.buttonColors(containerColor = HelpiColors.BrandCore),
            ) {
                Text("Decir mensaje", style = HelpiType.LabelBoton)
            }
        }
    }
}

@Composable
private fun MicrofonoCard(state: SessionCoordinator.UiState, activo: Boolean) {
    val escuchando = activo && (state.audio == AudioChannelState.STT_ESCUCHANDO ||
        state.audio == AudioChannelState.STT_TRANSCRIBIENDO)
    val disponible = state.capabilities.stt
    Surface(
        color = HelpiColors.Surface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (escuchando) HelpiColors.Success.copy(alpha = 0.7f) else HelpiColors.Divider,
        ),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(
                        if (escuchando) HelpiColors.BrandCore else HelpiColors.SurfaceRaised,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_entrada_microfono),
                    contentDescription = null,
                    tint = HelpiColors.LedSoft,
                    modifier = Modifier.size(38.dp),
                )
            }
            Text(
                text = when {
                    !disponible -> "Micrófono no disponible"
                    !activo -> "Listo para escuchar"
                    state.audio == AudioChannelState.STT_TRANSCRIBIENDO -> "Transcribiendo…"
                    state.audio == AudioChannelState.TTS_HABLANDO -> "Helpi está hablando"
                    else -> "Escuchando"
                },
                style = HelpiType.BodyChat,
                color = HelpiColors.LedSoft,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
            Text(
                text = if (disponible) {
                    "Reconocimiento de español con Vosk, sin enviar audio a internet."
                } else {
                    state.capabilities.sttDetail
                },
                style = HelpiType.BodyS,
                color = if (disponible) HelpiColors.LedMuted else HelpiColors.Warning,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CamaraCard(
    state: SessionCoordinator.UiState,
    cameraPreview: @Composable () -> Unit,
    onCambiarUmbral: (Float) -> Unit,
) {
    Surface(
        color = HelpiColors.Surface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HelpiColors.Divider),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(HelpiColors.SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                if (state.capabilities.vision) cameraPreview() else {
                    Text(
                        state.capabilities.visionDetail.ifBlank { "Cámara no disponible" },
                        style = HelpiType.BodyM,
                        color = HelpiColors.Warning,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
                    color = HelpiColors.BgBase.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(50),
                ) {
                    Text(
                        visualLabel(state.visual),
                        style = HelpiType.LabelHotbar,
                        color = HelpiColors.LedSoft,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                MensajeDeEncuadre(state.framing, state.visual)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Umbral de confianza", style = HelpiType.BodyM, color = HelpiColors.LedSoft)
                    Text(
                        porcentaje(state.confidenceThreshold),
                        style = HelpiType.BodyM.copy(fontWeight = FontWeight.Bold),
                        color = HelpiColors.BrandCore,
                    )
                }
                Slider(
                    value = state.confidenceThreshold,
                    onValueChange = onCambiarUmbral,
                    valueRange = SessionCoordinator.MIN_THRESHOLD..SessionCoordinator.MAX_THRESHOLD,
                    colors = SliderDefaults.colors(
                        thumbColor = HelpiColors.LedSoft,
                        activeTrackColor = HelpiColors.BrandCore,
                        inactiveTrackColor = HelpiColors.SurfaceRaised,
                    ),
                    modifier = Modifier.semantics {
                        contentDescription = "Umbral de confianza ${porcentaje(state.confidenceThreshold)}"
                    },
                )
                Text(
                    "Sólo se comunica una seña si supera este valor. Fuera del vocabulario, no adivina.",
                    style = HelpiType.BodyS,
                    color = HelpiColors.LedMuted,
                )
            }

            state.lastRecognition?.let { feedback ->
                HorizontalDivider(color = HelpiColors.Divider)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (feedback.accepted) feedback.gloss ?: "Seña reconocida" else "Seña no reconocida",
                            style = HelpiType.BodyM,
                            color = HelpiColors.LedSoft,
                        )
                        Text(
                            "Confianza ${porcentaje(feedback.confidence)} · umbral ${porcentaje(feedback.threshold)}",
                            style = HelpiType.BodyS,
                            color = HelpiColors.LedMuted,
                        )
                    }
                    EstadoPill(feedback.accepted)
                }
            }
            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun EstadoPill(aceptada: Boolean) {
    val color = if (aceptada) HelpiColors.Success else HelpiColors.Warning
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
        Text(
            if (aceptada) "Aceptada" else "Bajo umbral",
            style = HelpiType.LabelHotbar,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun UltimoMensaje(turn: Turn, puedeRepetir: Boolean, onRepetir: () -> Unit) {
    Surface(
        color = HelpiColors.Surface,
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HelpiColors.Divider),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (turn.speaker() == Speaker.DEAF) "TU MENSAJE" else "RESPUESTA",
                style = HelpiType.LabelHotbar,
                color = HelpiColors.LedMuted,
            )
            Text(turn.text(), style = HelpiType.BodyChat, color = HelpiColors.LedSoft)
            if (puedeRepetir) {
                TextButton(onClick = onRepetir, contentPadding = PaddingValues(0.dp)) {
                    Text("Repetir en voz alta", style = HelpiType.LabelBoton, color = HelpiColors.BrandCore)
                }
            }
        }
    }
}

@Composable
private fun ControlesDeSesion(
    state: SessionCoordinator.UiState,
    onIniciar: () -> Unit,
    onPreparar: () -> Unit,
    onFinalizar: () -> Unit,
) {
    when (state.session) {
        SessionState.INICIO, SessionState.PREPARANDO -> Row(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = HelpiColors.BrandCore)
            Text("  Preparando modelos locales…", style = HelpiType.BodyS, color = HelpiColors.LedMuted)
        }
        SessionState.LISTA -> AccionPrincipal("Iniciar conversación", onIniciar)
        SessionState.ACTIVA, SessionState.ACTIVA_LIMITADA -> OutlinedButton(
            onClick = onFinalizar,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Finalizar y borrar", style = HelpiType.LabelBoton, color = HelpiColors.LedSoft)
        }
        SessionState.CERRADA -> AccionPrincipal("Nueva conversación", onPreparar)
        SessionState.BLOQUEADA -> Text(
            "No hay canales disponibles. Revisá los permisos del sistema.",
            style = HelpiType.BodyM,
            color = HelpiColors.Warning,
            textAlign = TextAlign.Center,
        )
        else -> Unit
    }
}

@Composable
private fun AccionPrincipal(texto: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(containerColor = HelpiColors.BrandCore),
    ) {
        Text(texto, style = HelpiType.LabelBoton, color = Color.White)
    }
}

@Composable
private fun VistaConversacion(
    state: SessionCoordinator.UiState,
    medidas: Medidas,
    onVolver: () -> Unit,
    onMarkIncorrect: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = medidas.margen, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Conversación", style = HelpiType.BodyChat, color = HelpiColors.LedSoft)
            TextButton(onClick = onVolver) {
                Text("Listo", style = HelpiType.LabelBoton, color = HelpiColors.BrandCore)
            }
        }
        ListaDeTurnos(
            turns = state.turns,
            onMarkIncorrect = onMarkIncorrect,
            modifier = Modifier.weight(1f).padding(horizontal = medidas.margen),
        )
    }
}

@Composable
private fun ListaDeTurnos(
    turns: List<Turn>,
    onMarkIncorrect: ((Long) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val visibles = turns.filter { it.state() != TurnState.REJECTED }
    val listState = rememberLazyListState()
    LaunchedEffect(visibles.size) {
        if (visibles.isNotEmpty()) listState.animateScrollToItem(visibles.lastIndex)
    }
    if (visibles.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Todavía no hay mensajes", style = HelpiType.BodyM, color = HelpiColors.LedMuted)
        }
        return
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        items(visibles, key = { it.id() }) { turn ->
            TurnBubble(turn, onMarkIncorrect)
        }
    }
}

@Composable
private fun InformacionScreen(state: SessionCoordinator.UiState, medidas: Medidas) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = medidas.margen, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Acerca de Helpi", style = HelpiType.BodyChat, color = HelpiColors.LedSoft)
        InfoCard("Privacidad", "La cámara, el micrófono y los modelos funcionan en el dispositivo. La conversación vive sólo en memoria y se borra al finalizar.")
        InfoCard("Modelo LSA", "Eva reconoce 64 señas aisladas del conjunto LSA64. Umbral actual: ${porcentaje(state.confidenceThreshold)}. No interpreta frases ni fuerza una clase cuando no tiene confianza.")
        InfoCard("Limitaciones", "Es una asistencia experimental: puede equivocarse, no reemplaza a una persona intérprete y no debe usarse para emergencias. El modelo fue entrenado principalmente con personas diestras en condiciones de laboratorio.")
        InfoCard(
            "Atribución",
            "LSA64 — LIDI, Universidad Nacional de La Plata. Modelo derivado bajo " +
                "CC BY-NC-SA 4.0, sólo para uso no comercial. Reconocimiento de voz: " +
                "Vosk y modelo español small 0.42, Apache 2.0.",
        )
        InfoCard(
            "Estado local",
            "Cámara/modelo: ${capacidad(state.capabilities.vision, state.capabilities.visionDetail)}\n" +
                "Micrófono/Vosk: ${capacidad(state.capabilities.stt, state.capabilities.sttDetail)}\n" +
                "Voz: ${capacidad(state.capabilities.tts, state.capabilities.ttsDetail)}",
        )
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun InfoCard(titulo: String, texto: String) {
    Surface(
        color = HelpiColors.Surface,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HelpiColors.Divider),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titulo, style = HelpiType.BodyM.copy(fontWeight = FontWeight.Bold), color = HelpiColors.LedSoft)
            Text(texto, style = HelpiType.BodyS, color = HelpiColors.LedMuted)
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    Surface(
        color = HelpiColors.Warning.copy(alpha = 0.10f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, HelpiColors.Warning.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            texto,
            style = HelpiType.BodyS,
            color = HelpiColors.Warning,
            modifier = Modifier.padding(12.dp).semantics { liveRegion = LiveRegionMode.Assertive },
        )
    }
}

@Composable
private fun MensajeDeEncuadre(issue: FramingEvaluator.Issue, visual: VisualChannelState) {
    if (visual == VisualChannelState.NO_DISPONIBLE) return
    val texto = when (issue) {
        FramingEvaluator.Issue.OK -> null
        FramingEvaluator.Issue.SIN_PERSONA -> "No veo a nadie frente a la cámara"
        FramingEvaluator.Issue.SIN_HOMBROS -> "Mostrá también los hombros; alejá un poco el teléfono"
        FramingEvaluator.Issue.MANOS_AL_BORDE -> "Mantené las manos dentro del cuadro"
        FramingEvaluator.Issue.MANO_PERDIDA -> "Perdí de vista una mano"
    } ?: return
    Text(
        texto,
        style = HelpiType.BodyS,
        color = HelpiColors.Warning,
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@Composable
private fun TituloSeccion(texto: String) {
    Text(
        texto,
        style = HelpiType.BodyChat.copy(fontWeight = FontWeight.Bold),
        color = HelpiColors.LedSoft,
    )
}

@Composable
private fun PantallaPausada(onResume: () -> Unit) {
    Surface(color = HelpiColors.BgBase, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            EvaOrb(EvaEstado.REPOSO, Modifier.size(150.dp))
            Text("Conversación pausada", style = HelpiType.BodyChat, color = HelpiColors.LedSoft)
            Text(
                "El contenido está oculto por privacidad. Después de 2 minutos se borra.",
                style = HelpiType.BodyM,
                color = HelpiColors.LedMuted,
                textAlign = TextAlign.Center,
            )
            AccionPrincipal("Reanudar", onResume)
        }
    }
}

private fun modosDisponibles(state: SessionCoordinator.UiState): Set<ModoEntrada> = buildSet {
    add(ModoEntrada.ESCRIBIR)
    if (state.capabilities.stt) add(ModoEntrada.HABLAR)
    if (state.capabilities.vision) add(ModoEntrada.MOSTRAR)
}

private fun visualLabel(state: VisualChannelState): String = when (state) {
    VisualChannelState.NO_DISPONIBLE -> "Cámara no disponible"
    VisualChannelState.BUSCANDO_ENCUADRE -> "Buscando encuadre"
    VisualChannelState.ESPERANDO_REPOSO -> "Dejá las manos quietas"
    VisualChannelState.ARMADO -> "Lista para una seña"
    VisualChannelState.CAPTURANDO_SENA -> "Capturando seña"
    VisualChannelState.CONFIRMANDO_FIN -> "Confirmando fin"
    VisualChannelState.REARMANDO -> "Procesando con Eva"
    VisualChannelState.CALIDAD_INSUFICIENTE -> "Mejorá el encuadre"
    VisualChannelState.SUSPENDIDO_RENDIMIENTO -> "Pausada por rendimiento"
}

private fun porcentaje(value: Float): String =
    String.format(Locale("es", "AR"), "%.0f %%", value * 100f)

private fun capacidad(disponible: Boolean, detalle: String): String = when {
    disponible && detalle.isBlank() -> "disponible"
    disponible -> "disponible ($detalle)"
    detalle.isNotBlank() -> "no disponible ($detalle)"
    else -> "no disponible"
}
