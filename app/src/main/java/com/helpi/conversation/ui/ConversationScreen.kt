package com.helpi.conversation.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.helpi.conversation.R
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.Turn
import com.helpi.conversation.chat.TurnState
import com.helpi.conversation.lsa.sequence.PhraseCaptureState
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.session.AudioChannelState
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.session.SessionState
import com.helpi.conversation.session.VisualChannelState
import com.helpi.conversation.ui.components.EvaEstado
import com.helpi.conversation.ui.components.EvaOrb
import com.helpi.conversation.ui.components.Hotbar
import com.helpi.conversation.ui.components.NotaSistema
import com.helpi.conversation.ui.components.SeccionHotbar
import com.helpi.conversation.ui.components.TurnoMensaje
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiShapes
import com.helpi.conversation.ui.theme.HelpiType
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.SigningDistanceGuide
import kotlin.math.roundToInt

internal data class ConversationActions(
    val start: () -> Unit = {},
    val close: () -> Unit = {},
    val resume: () -> Unit = {},
    val microphone: () -> Unit = {},
    val camera: () -> Unit = {},
    val threshold: (Float) -> Unit = {},
    val recognitionMode: (RecognitionMode) -> Unit = {},
    val startPhrase: () -> Unit = {},
    val finishPhrase: () -> Unit = {},
    val cancelPhrase: () -> Unit = {},
    val confirmPhrase: (String?) -> Unit = {},
    val rejectPhrase: () -> Unit = {},
    val send: (String) -> Unit = {},
    val repeat: (Long) -> Unit = {},
    val incorrect: (Long) -> Unit = {},
    val dismissNotice: () -> Unit = {},
    val nuevaConversacion: () -> Unit = {},
    val nombrarInterlocutor: (String) -> Unit = {},
    val renombrarCuenta: (String) -> Unit = {},
    val cancelarPedidoDeNombre: () -> Unit = {},
)

@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    onStart: () -> Unit,
    cameraPreview: @Composable () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val participantes by viewModel.participantes.collectAsStateWithLifecycle()
    ConversationContent(
        state,
        ConversationActions(
            start = onStart,
            close = viewModel::closeSession,
            resume = viewModel::resume,
            microphone = viewModel::toggleMicrophone,
            camera = viewModel::toggleCamera,
            threshold = viewModel::setConfidenceThreshold,
            recognitionMode = viewModel::setRecognitionMode,
            startPhrase = viewModel::startPhraseCapture,
            finishPhrase = viewModel::finishPhraseCapture,
            cancelPhrase = viewModel::cancelPhraseCapture,
            confirmPhrase = viewModel::confirmPhraseCandidate,
            rejectPhrase = viewModel::rejectPhraseCandidate,
            send = viewModel::submitTyped,
            repeat = viewModel::repeatTurn,
            incorrect = viewModel::markIncorrect,
            dismissNotice = viewModel::dismissNotice,
            nuevaConversacion = viewModel::nuevaConversacion,
            nombrarInterlocutor = viewModel::nombrarInterlocutor,
            renombrarCuenta = viewModel::renombrarCuenta,
            cancelarPedidoDeNombre = viewModel::cancelarPedidoDeNombre,
        ),
        participantes,
        cameraPreview,
    )
}

@Composable
internal fun ConversationContent(
    state: SessionCoordinator.UiState,
    actions: ConversationActions,
    participantes: Participantes = Participantes(),
    cameraPreview: @Composable () -> Unit = {},
) {
    when (state.session) {
        SessionState.ACTIVA, SessionState.ACTIVA_LIMITADA ->
            SalaDeConversacion(state, actions, participantes, cameraPreview)
        else -> Inicio(state, actions, participantes)
    }
}

// ---------------------------------------------------------------------------
// Inicio
// ---------------------------------------------------------------------------

/**
 * Fuera de una conversación la aplicación tiene dos destinos: el traductor y
 * la cuenta. La barra inferior solo existe acá; una vez que hay alguien del
 * otro lado la pantalla es entera para la conversación.
 */
@Composable
private fun Inicio(
    state: SessionCoordinator.UiState,
    actions: ConversationActions,
    participantes: Participantes,
) {
    var seccion by rememberSaveable { mutableStateOf(SeccionHotbar.TRADUCTOR) }
    var pidiendoNombre by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(participantes.solicitarNombre) {
        if (participantes.solicitarNombre) {
            seccion = SeccionHotbar.TRADUCTOR
            pidiendoNombre = true
        }
    }

    Column(Modifier.fillMaxSize().testTag("welcome")) {
        Box(Modifier.weight(1f)) {
            when (seccion) {
                SeccionHotbar.TRADUCTOR -> Bienvenida(
                    state = state,
                    onIniciar = { pidiendoNombre = true },
                    onReanudar = actions.resume,
                )
                SeccionHotbar.CUENTA -> PantallaCuenta(
                    state = state,
                    participantes = participantes,
                    onThreshold = actions.threshold,
                    onRenombrarCuenta = actions.renombrarCuenta,
                )
            }
        }
        Hotbar(seccion, onSeleccionar = { seccion = it })
    }

    if (pidiendoNombre) {
        DialogoDeNombre(
            titulo = "¿Con quién vas a conversar?",
            explicacion = "El nombre aparece arriba de cada mensaje que Helpi traduzca. " +
                "Se borra al terminar la conversación.",
            inicial = participantes.interlocutor,
            confirmar = "Empezar",
            onConfirmar = {
                actions.nombrarInterlocutor(it)
                pidiendoNombre = false
                actions.start()
            },
            omitir = "Sin nombre",
            onOmitir = {
                actions.nombrarInterlocutor("")
                pidiendoNombre = false
                actions.start()
            },
            onDismiss = {
                pidiendoNombre = false
                actions.cancelarPedidoDeNombre()
            },
        )
    }
}

@Composable
private fun Bienvenida(
    state: SessionCoordinator.UiState,
    onIniciar: () -> Unit,
    onReanudar: () -> Unit,
) {
    val pausada = state.session == SessionState.PAUSADA
    val preparando = state.session == SessionState.PREPARANDO ||
        state.session == SessionState.LISTA ||
        state.session == SessionState.CERRANDO

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        EvaOrb(
            if (preparando) EvaEstado.PENSANDO else EvaEstado.REPOSO,
            Modifier.size(200.dp).semantics { contentDescription = "Helpi" },
        )
        Spacer(Modifier.height(12.dp))
        Text("Helpi", style = HelpiType.Display, color = HelpiColors.LedSoft)
        Spacer(Modifier.height(10.dp))
        Text(
            if (pausada) {
                "Reanudá para continuar. Después de dos minutos la conversación se borra."
            } else {
                "Lengua de señas argentina traducida acá mismo, sin conexión y sin que el " +
                    "video salga del teléfono."
            },
            style = HelpiType.BodyM,
            color = HelpiColors.LedMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = if (pausada) onReanudar else onIniciar,
            enabled = !preparando,
            shape = HelpiShapes.Pastilla,
            modifier = Modifier.widthIn(max = 380.dp).fillMaxWidth().heightIn(min = 58.dp),
        ) {
            if (preparando) {
                CircularProgressIndicator(
                    Modifier.size(20.dp),
                    color = HelpiColors.BgBase,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(12.dp))
            }
            Text(
                when {
                    preparando -> "Preparando…"
                    pausada -> "Reanudar conversación"
                    else -> "Iniciar conversación"
                },
                style = HelpiType.LabelBoton,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Helpi ayuda a comunicarse. No reemplaza a una persona intérprete.",
            style = HelpiType.CaptionDisclaimer,
            color = HelpiColors.LedMuted,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------------------------------------------------------------
// Conversación
// ---------------------------------------------------------------------------

@Composable
private fun SalaDeConversacion(
    state: SessionCoordinator.UiState,
    actions: ConversationActions,
    participantes: Participantes,
    cameraPreview: @Composable () -> Unit,
) {
    var ajustes by remember { mutableStateOf(false) }
    var finalizando by remember { mutableStateOf(false) }
    var empezandoDeNuevo by remember { mutableStateOf(false) }
    var teclado by remember { mutableStateOf(false) }
    val controlTeclado = LocalSoftwareKeyboardController.current

    BackHandler {
        if (teclado) {
            teclado = false
            controlTeclado?.hide()
        } else {
            finalizando = true
        }
    }

    Column(Modifier.fillMaxSize().imePadding().testTag("conversation")) {
        Encabezado(
            interlocutor = participantes.interlocutor,
            onNueva = { empezandoDeNuevo = true },
            onAjustes = { ajustes = true },
            onCerrar = { finalizando = true },
        )
        BoxWithConstraints(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            val apaisado = maxWidth > maxHeight && maxWidth >= 600.dp
            // La cámara cede altura cuando se abre el teclado: escribir es una
            // actividad de lectura, y con el teclado arriba la conversación
            // necesita el espacio más que la imagen.
            val fraccion by animateFloatAsState(if (teclado) 0.36f else 0.58f, label = "camara")
            val alturaCamara = (maxHeight * fraccion).coerceIn(180.dp, 520.dp)
            val panel = @Composable { modificador: Modifier ->
                PanelDeCaptura(
                    state = state,
                    actions = actions,
                    teclado = teclado,
                    onTeclado = {
                        teclado = !teclado
                        if (!teclado) controlTeclado?.hide()
                    },
                    cameraPreview = cameraPreview,
                    modifier = modificador,
                )
            }
            if (apaisado) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    panel(Modifier.weight(0.46f).fillMaxHeight())
                    ListaDeMensajes(
                        state,
                        actions,
                        participantes,
                        Modifier.weight(0.54f).fillMaxHeight(),
                    )
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    panel(Modifier.fillMaxWidth().height(alturaCamara))
                    ListaDeMensajes(
                        state,
                        actions,
                        participantes,
                        Modifier.weight(1f).fillMaxWidth(),
                    )
                }
            }
        }
        state.notice?.let {
            Row(
                Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    it,
                    style = HelpiType.BodyS,
                    color = HelpiColors.Warning,
                    modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                )
                IconButton(onClick = actions.dismissNotice) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        "Cerrar aviso",
                        tint = HelpiColors.LedMuted,
                    )
                }
            }
        }
        if (teclado) Redactor(actions.send)
        Spacer(Modifier.height(8.dp))
    }

    if (ajustes) {
        HojaDeAjustes(state, actions.threshold, actions.recognitionMode) { ajustes = false }
    }
    state.phraseCandidate?.let { candidate ->
        var candidateText by remember(candidate) { mutableStateOf(candidate.text) }
        AlertDialog(
            onDismissRequest = actions.rejectPhrase,
            title = { Text("¿Usar esta frase?") },
            text = {
                OutlinedTextField(
                    value = candidateText,
                    onValueChange = { candidateText = it.take(300) },
                    label = { Text("Texto de la frase") },
                    textStyle = HelpiType.BodyM,
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { actions.confirmPhrase(candidateText.trim()) },
                    enabled = candidateText.isNotBlank(),
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = actions.rejectPhrase) { Text("Descartar") }
            },
            containerColor = HelpiColors.Surface,
        )
    }
    if (finalizando) {
        Confirmacion(
            titulo = "¿Finalizar conversación?",
            cuerpo = "Se borran los mensajes de esta conversación.",
            confirmar = "Finalizar",
            onConfirmar = {
                controlTeclado?.hide()
                actions.close()
                finalizando = false
            },
            onCancelar = { finalizando = false },
        )
    }
    if (empezandoDeNuevo) {
        Confirmacion(
            titulo = "¿Empezar una conversación nueva?",
            cuerpo = "Se borran los mensajes actuales y Helpi te pide el nombre de la " +
                "próxima persona.",
            confirmar = "Empezar de nuevo",
            onConfirmar = {
                controlTeclado?.hide()
                actions.nuevaConversacion()
                empezandoDeNuevo = false
            },
            onCancelar = { empezandoDeNuevo = false },
        )
    }
}

@Composable
private fun Confirmacion(
    titulo: String,
    cuerpo: String,
    confirmar: String,
    onConfirmar: () -> Unit,
    onCancelar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancelar,
        title = { Text(titulo) },
        text = { Text(cuerpo) },
        confirmButton = { TextButton(onClick = onConfirmar) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = onCancelar) { Text("Continuar") } },
        containerColor = HelpiColors.Surface,
    )
}

@Composable
private fun Encabezado(
    interlocutor: String,
    onNueva: () -> Unit,
    onAjustes: () -> Unit,
    onCerrar: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Helpi", style = HelpiType.TitleM, color = HelpiColors.LedSoft)
            Text(
                interlocutor,
                style = HelpiType.BodyS,
                color = HelpiColors.LedMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AccionDeEncabezado(R.drawable.ic_nueva_conversacion, "Nueva conversación", onNueva)
        AccionDeEncabezado(R.drawable.ic_settings, "Configuración", onAjustes, tamano = 21.dp)
        AccionDeEncabezado(R.drawable.ic_close, "Finalizar conversación", onCerrar)
    }
}

@Composable
private fun AccionDeEncabezado(
    @DrawableRes icono: Int,
    etiqueta: String,
    onClick: () -> Unit,
    tamano: androidx.compose.ui.unit.Dp = 22.dp,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(painterResource(icono), etiqueta, tint = HelpiColors.LedMuted, modifier = Modifier.size(tamano))
    }
}

// ---------------------------------------------------------------------------
// Cámara
// ---------------------------------------------------------------------------

/**
 * La cámara ocupa todo el ancho y algo más de la mitad del alto: es lo que la
 * persona que hace señas tiene que ver de sí misma para saber si entra en el
 * cuadro, y recortarla para hacerle lugar a una columna de botones era cambiar
 * lo importante por lo accesorio.
 *
 * Los controles flotan encima, sobre un velo oscuro y con borde de vidrio, de
 * modo que se lean igual contra una pared blanca o contra una sombra.
 */
@Composable
private fun PanelDeCaptura(
    state: SessionCoordinator.UiState,
    actions: ConversationActions,
    teclado: Boolean,
    onTeclado: () -> Unit,
    cameraPreview: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visionAvailable = if (state.recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL) {
        state.capabilities.phraseVision
    } else {
        state.capabilities.vision
    }
    val visionDetail = if (state.recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL) {
        state.capabilities.phraseVisionDetail
    } else {
        state.capabilities.visionDetail
    }
    val camaraViva = state.cameraEnabled && visionAvailable

    Box(
        modifier
            .clip(HelpiShapes.Panel)
            .background(Color.Black)
            .testTag("camera"),
        contentAlignment = Alignment.Center,
    ) {
        if (camaraViva) {
            cameraPreview()
            Box(
                Modifier.matchParentSize().background(
                    Brush.verticalGradient(
                        0.00f to Color.Black.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.68f to Color.Transparent,
                        1.00f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
            )
            GuiaDeDistancia(
                state = state.signingDistance,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    painterResource(R.drawable.ic_entrada_camara),
                    null,
                    tint = HelpiColors.LedMuted,
                    modifier = Modifier.size(32.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        visionAvailable -> "Activá la cámara para mostrar una seña."
                        visionDetail.contains("permiso") ->
                            "Permití el acceso a la cámara en los ajustes del teléfono."
                        else -> "No hay cámara disponible. Podés continuar con voz o texto."
                    },
                    style = HelpiType.BodyS,
                    color = HelpiColors.LedMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BotonDeVidrio(
                icono = R.drawable.ic_entrada_microfono,
                etiqueta = when {
                    !state.capabilities.stt -> "Micrófono no disponible"
                    state.microphoneEnabled -> "Silenciar micrófono"
                    else -> "Activar micrófono"
                },
                activo = state.microphoneEnabled && state.capabilities.stt,
                habilitado = state.capabilities.stt,
                tachado = !state.microphoneEnabled || !state.capabilities.stt,
                onClick = actions.microphone,
            )
            BotonDeVidrio(
                icono = R.drawable.ic_entrada_camara,
                etiqueta = when {
                    !visionAvailable -> "Cámara no disponible"
                    state.cameraEnabled -> "Apagar cámara"
                    else -> "Activar cámara"
                },
                activo = camaraViva,
                habilitado = visionAvailable,
                tachado = !camaraViva,
                onClick = actions.camera,
            )
            BotonDeVidrio(
                icono = R.drawable.ic_entrada_teclado,
                etiqueta = if (teclado) "Ocultar teclado" else "Escribir mensaje",
                activo = teclado,
                onClick = onTeclado,
            )
        }

        if (camaraViva) {
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .widthIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EstadoDeDistancia(state.signingDistance)
                if (state.recognitionMode == RecognitionMode.SINGLE_SIGN) {
                    state.lastRecognition?.takeIf { it.predictions.isNotEmpty() }?.let {
                        PrediccionesDeEva(it)
                    }
                }
            }
        }

        if (state.recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL &&
            state.capabilities.phraseVision
        ) {
            val capturing = state.phraseCapture == PhraseCaptureState.CAPTURING
            Row(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = if (capturing) actions.finishPhrase else actions.startPhrase,
                    enabled = state.phraseCapture == PhraseCaptureState.READY || capturing,
                ) {
                    Text(if (capturing) "Terminar frase" else "Comenzar frase")
                }
                if (capturing) {
                    TextButton(onClick = actions.cancelPhrase) { Text("Cancelar") }
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 10.dp, end = 68.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val eva = estadoDeEva(state)
            EvaOrb(
                eva,
                Modifier.size(54.dp).semantics {
                    contentDescription = when (eva) {
                        EvaEstado.ESCUCHANDO -> "Helpi está escuchando"
                        EvaEstado.HABLANDO -> "Helpi está hablando"
                        EvaEstado.PENSANDO -> "Helpi está reconociendo la seña"
                        EvaEstado.REPOSO -> "Helpi en pausa"
                    }
                },
            )
            if (camaraViva) {
                instruccionDeEncuadre(state)?.let {
                    Text(
                        it,
                        style = HelpiType.BodyS,
                        color = Color.White,
                        modifier = Modifier
                            .background(HelpiColors.VeloCamara, HelpiShapes.Chip)
                            .border(1.dp, HelpiColors.VidrioBorde, HelpiShapes.Chip)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        }
    }
}

@Composable
private fun GuiaDeDistancia(
    state: SigningDistanceGuide.State,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        SigningDistanceGuide.State.OPTIMAL -> HelpiColors.Success
        SigningDistanceGuide.State.MOVE_CLOSER,
        SigningDistanceGuide.State.MOVE_FARTHER -> HelpiColors.Warning
        SigningDistanceGuide.State.UNKNOWN -> Color.White.copy(alpha = 0.55f)
    }
    Canvas(modifier.clearAndSetSemantics { }.testTag("distanceGuideOverlay")) {
        val centerX = size.width / 2f
        val shoulderHalfSpan = size.width * 0.10f
        val top = size.height * 0.30f
        val bottom = size.height * 0.58f
        val tick = size.width * 0.035f
        val stroke = 2.dp.toPx()
        val left = centerX - shoulderHalfSpan
        val right = centerX + shoulderHalfSpan

        drawLine(
            color.copy(alpha = 0.78f),
            Offset(left, top),
            Offset(left, bottom),
            stroke,
            StrokeCap.Round,
        )
        drawLine(
            color.copy(alpha = 0.78f),
            Offset(right, top),
            Offset(right, bottom),
            stroke,
            StrokeCap.Round,
        )
        drawLine(color, Offset(left, top), Offset(left + tick, top), stroke, StrokeCap.Round)
        drawLine(color, Offset(left, bottom), Offset(left + tick, bottom), stroke, StrokeCap.Round)
        drawLine(color, Offset(right - tick, top), Offset(right, top), stroke, StrokeCap.Round)
        drawLine(color, Offset(right - tick, bottom), Offset(right, bottom), stroke, StrokeCap.Round)
    }
}

@Composable
private fun EstadoDeDistancia(state: SigningDistanceGuide.State) {
    val (text, color) = when (state) {
        SigningDistanceGuide.State.UNKNOWN -> "Alineá los hombros con la guía" to Color.White
        SigningDistanceGuide.State.MOVE_CLOSER -> "Acercate un poco" to HelpiColors.Warning
        SigningDistanceGuide.State.OPTIMAL -> "Distancia adecuada" to HelpiColors.Success
        SigningDistanceGuide.State.MOVE_FARTHER -> "Alejate un poco" to HelpiColors.Warning
    }
    Text(
        text,
        style = HelpiType.BodyS,
        color = color,
        modifier = Modifier
            .background(HelpiColors.VeloCamara, HelpiShapes.Chip)
            .border(1.dp, color.copy(alpha = 0.65f), HelpiShapes.Chip)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
            .testTag("distanceStatus"),
    )
}

@Composable
private fun PrediccionesDeEva(feedback: SessionCoordinator.RecognitionFeedback) {
    Column(
        Modifier
            .background(HelpiColors.VeloCamara, HelpiShapes.Panel)
            .border(1.dp, HelpiColors.VidrioBorde, HelpiShapes.Panel)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("predictionPanel"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            if (feedback.accepted) "Predicciones" else "Predicciones sin confirmar",
            style = HelpiType.LabelBoton,
            color = Color.White,
        )
        feedback.predictions.take(3).forEachIndexed { index, prediction ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${index + 1}. ${prediction.label}",
                    style = HelpiType.BodyS,
                    color = if (index == 0 && feedback.accepted) HelpiColors.Success else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${(prediction.confidence * 100).roundToInt()}%",
                    style = HelpiType.BodyS,
                    color = Color.White,
                )
            }
        }
    }
}

private fun estadoDeEva(state: SessionCoordinator.UiState) = when {
    state.audio == AudioChannelState.TTS_HABLANDO -> EvaEstado.HABLANDO
    state.visual == VisualChannelState.REARMANDO && state.cameraEnabled -> EvaEstado.PENSANDO
    state.microphoneEnabled && state.capabilities.stt &&
        (
            state.audio == AudioChannelState.STT_ESCUCHANDO ||
                state.audio == AudioChannelState.STT_TRANSCRIBIENDO
            ) -> EvaEstado.ESCUCHANDO
    else -> EvaEstado.REPOSO
}

@Composable
private fun BotonDeVidrio(
    @DrawableRes icono: Int,
    etiqueta: String,
    activo: Boolean = false,
    habilitado: Boolean = true,
    tachado: Boolean = false,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = habilitado,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (activo) HelpiColors.BrandCore.copy(alpha = 0.92f) else HelpiColors.VeloCamara)
            .border(1.dp, HelpiColors.VidrioBorde, CircleShape)
            .semantics { stateDescription = if (activo) "Activado" else "Desactivado" },
    ) {
        Box(Modifier.size(21.dp)) {
            val tinte = if (habilitado) HelpiColors.LedSoft else HelpiColors.LedMuted.copy(alpha = 0.5f)
            Icon(painterResource(icono), etiqueta, tint = tinte, modifier = Modifier.fillMaxSize())
            if (tachado) {
                Canvas(Modifier.fillMaxSize()) {
                    drawLine(
                        tinte,
                        Offset(1f, 1f),
                        Offset(size.width - 1f, size.height - 1f),
                        2.dp.toPx(),
                        StrokeCap.Round,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Mensajes
// ---------------------------------------------------------------------------

@Composable
private fun ListaDeMensajes(
    state: SessionCoordinator.UiState,
    actions: ConversationActions,
    participantes: Participantes,
    modifier: Modifier = Modifier,
) {
    val turnos = state.turns
        .filter { it.state() != TurnState.REJECTED && it.text().isNotBlank() }
        .reversed()
    val listState = rememberLazyListState()
    LaunchedEffect(turnos.firstOrNull()?.id(), turnos.firstOrNull()?.text()) {
        if (turnos.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    if (turnos.isEmpty()) {
        Box(modifier.testTag("messages"), contentAlignment = Alignment.Center) {
            Text(
                "Mostrá una seña frente a la cámara. También podés hablar o escribir.",
                style = HelpiType.BodyM,
                color = HelpiColors.LedMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(28.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.testTag("messages"),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(top = 18.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(turnos, key = { _, turno -> turno.id() }) { indice, turno ->
            // La lista está invertida: el mensaje de arriba es el siguiente del array.
            val anterior = turnos.getOrNull(indice + 1)
            if (turno.speaker() == Speaker.SYSTEM) {
                NotaSistema(turno.text())
            } else {
                val propio = turno.speaker() == Speaker.HEARING
                TurnoMensaje(
                    turn = turno,
                    autor = if (propio) participantes.cuenta else participantes.interlocutor,
                    mostrarAutor = anterior?.speaker() != turno.speaker(),
                    propio = propio,
                    onRepetir = repetirDe(turno, propio, state, actions),
                    onMarcarIncorrecto = marcarIncorrectoDe(turno, propio, actions),
                )
            }
        }
    }
}

/**
 * Solo se repiten los turnos de la persona sorda: son los que Helpi pronuncia
 * en voz alta. Repetir la voz de quien está hablando al lado no tendría
 * sentido.
 */
private fun repetirDe(
    turno: Turn,
    propio: Boolean,
    state: SessionCoordinator.UiState,
    actions: ConversationActions,
): (() -> Unit)? =
    if (!propio && state.capabilities.tts && !turno.markedIncorrect()) {
        { actions.repeat(turno.id()) }
    } else {
        null
    }

private fun marcarIncorrectoDe(
    turno: Turn,
    propio: Boolean,
    actions: ConversationActions,
): (() -> Unit)? =
    if (!propio && !turno.markedIncorrect() && turno.state() == TurnState.FINAL) {
        { actions.incorrect(turno.id()) }
    } else {
        null
    }

@Composable
private fun Redactor(onEnviar: (String) -> Unit) {
    // El borrador vive únicamente durante esta conversación, no se guarda en el Bundle.
    var texto by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    fun enviar() {
        if (texto.isNotBlank()) {
            onEnviar(texto.trim())
            texto = ""
        }
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = { texto = it.take(300) },
            modifier = Modifier.weight(1f).focusRequester(foco).testTag("messageInput"),
            placeholder = { Text("Escribí un mensaje…", style = HelpiType.BodyM) },
            textStyle = HelpiType.BodyM,
            maxLines = 3,
            shape = HelpiShapes.Pastilla,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { enviar() }),
        )
        FilledIconButton(
            onClick = { enviar() },
            enabled = texto.isNotBlank(),
            modifier = Modifier.padding(bottom = 4.dp).size(50.dp),
        ) {
            Icon(painterResource(R.drawable.ic_send), "Enviar y leer en voz alta")
        }
    }
}

// ---------------------------------------------------------------------------
// Ajustes de la conversación
// ---------------------------------------------------------------------------

/**
 * Lo que se toca sin salir de la conversación. Los límites del modelo y las
 * licencias no están duplicados acá: viven en la pestaña Cuenta, que es donde
 * se leen con tiempo y no con alguien esperando enfrente.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HojaDeAjustes(
    state: SessionCoordinator.UiState,
    onThreshold: (Float) -> Unit,
    onRecognitionMode: (RecognitionMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var ayuda by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HelpiColors.Surface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Configuración", style = HelpiType.LabelBoton)
                TextButton(onClick = onDismiss) { Text("Listo") }
            }
            SelectorDeModo(state, onRecognitionMode)
            ControlDeUmbral(state, onThreshold)
            DiagnosticoVisual(state)
            AvisosDeCapacidades(state)
            HorizontalDivider(color = HelpiColors.Divider)
            TextButton(onClick = { ayuda = !ayuda }) {
                Text(if (ayuda) "Ocultar instrucciones" else "Cómo usar Helpi")
            }
            if (ayuda) {
                Text(
                    "Mostrá los hombros y las manos. Hacé una seña aislada y dejá las manos " +
                        "quietas al terminar. Usá los botones sobre la cámara para silenciar, " +
                        "apagar la imagen o escribir. Mantené presionado un mensaje traducido " +
                        "para repetirlo en voz alta o marcarlo como incorrecto. Esperá a que " +
                        "Helpi termine de hablar antes de la seña siguiente.",
                    style = HelpiType.BodyM,
                )
            }
            Text(
                "Los límites del modelo y las licencias están en la pestaña Cuenta.",
                style = HelpiType.BodyS,
                color = HelpiColors.LedMuted,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Datos locales para saber si falla cámara, MediaPipe, segmentación o modelo. */
@Composable
private fun DiagnosticoVisual(state: SessionCoordinator.UiState) {
    val metrics = state.visionMetrics
    fun fps(value: Float): String = "${value.roundToInt()} fps"
    fun detected(count: Long): String = if (metrics.landmarkFrames == 0L) {
        "—"
    } else {
        "${(count * 100f / metrics.landmarkFrames).roundToInt()} %"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .testTag("visionDiagnostics"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Diagnóstico de cámara", style = HelpiType.BodyM, color = HelpiColors.LedSoft)
        Text(
            "Muestreo ${state.samplingProfile.displayName}: cámara ${fps(metrics.cameraFps)} de ${metrics.targetFps} objetivo · " +
                "MediaPipe ${fps(metrics.mediaPipeFps)} · ${metrics.lastMediaPipeLatencyMs} ms",
            style = HelpiType.BodyS,
            color = HelpiColors.LedMuted,
        )
        Text(
            "Pose ${detected(metrics.poseFrames)} · mano izquierda ${detected(metrics.leftHandFrames)} · " +
                "mano derecha ${detected(metrics.rightHandFrames)}",
            style = HelpiType.BodyS,
            color = HelpiColors.LedMuted,
        )
        Text(
            "Segmentos: ${metrics.segmentsStarted} iniciados, ${metrics.segmentsCompleted} terminados, " +
                "${metrics.segmentsAborted} abortados · modelo: ${metrics.classifierRuns} corridas, " +
                "${metrics.lastClassifierLatencyMs} ms",
            style = HelpiType.BodyS,
            color = HelpiColors.LedMuted,
        )
        if (metrics.rateLimitedFrames > 0L) {
            Text(
                "${metrics.rateLimitedFrames} cuadros se descartaron para conservar baja latencia.",
                style = HelpiType.CaptionDisclaimer,
                color = HelpiColors.LedMuted,
            )
        }
    }
}

@Composable
private fun SelectorDeModo(
    state: SessionCoordinator.UiState,
    onRecognitionMode: (RecognitionMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Modo de reconocimiento",
            style = HelpiType.BodyM,
            color = HelpiColors.LedSoft,
        )
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.recognitionMode == RecognitionMode.SINGLE_SIGN,
                onClick = { onRecognitionMode(RecognitionMode.SINGLE_SIGN) },
                label = { Text("Seña individual") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = state.recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL,
                onClick = { onRecognitionMode(RecognitionMode.PHRASE_EXPERIMENTAL) },
                enabled = state.capabilities.phraseVision,
                label = { Text("Frase experimental") },
                modifier = Modifier.weight(1f),
            )
        }
        Text(
            if (state.capabilities.phraseVision) {
                "La frase se captura manualmente y siempre se confirma antes de publicarse."
            } else {
                state.capabilities.phraseVisionDetail.ifBlank {
                    "El modelo de frases todavía no está instalado. Eva sigue disponible."
                }
            },
            style = HelpiType.BodyS,
            color = HelpiColors.LedMuted,
        )
    }
}

private fun instruccionDeEncuadre(state: SessionCoordinator.UiState): String? = when (state.framing) {
    FramingEvaluator.Issue.SIN_PERSONA -> "Mostrá los hombros y las manos."
    FramingEvaluator.Issue.SIN_HOMBROS -> "Alejá un poco el teléfono para mostrar los hombros."
    FramingEvaluator.Issue.MANOS_AL_BORDE -> "Mantené las manos dentro del cuadro."
    FramingEvaluator.Issue.MANO_PERDIDA -> "Volvé a mostrar las dos manos."
    FramingEvaluator.Issue.OK -> if (state.recognitionMode == RecognitionMode.PHRASE_EXPERIMENTAL) {
        when (state.phraseCapture) {
            PhraseCaptureState.READY -> "Presioná «Comenzar frase» cuando estés listo."
            PhraseCaptureState.CAPTURING -> "Hacé la frase y presioná «Terminar frase»."
            PhraseCaptureState.PROCESSING -> "Procesando la frase…"
            PhraseCaptureState.CANDIDATE -> "Revisá la frase sugerida."
            PhraseCaptureState.UNAVAILABLE -> "El modo frase no está disponible."
        }
    } else {
        when (state.visual) {
            VisualChannelState.ARMADO -> "Mostrá una seña."
            VisualChannelState.ESPERANDO_REPOSO -> "Dejá las manos quietas un momento."
            VisualChannelState.CAPTURANDO_SENA -> "Reconociendo tu seña…"
            VisualChannelState.REARMANDO -> "Procesando la seña…"
            else -> null
        }
    }
}
