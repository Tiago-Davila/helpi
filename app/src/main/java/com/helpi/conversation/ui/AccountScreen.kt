package com.helpi.conversation.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.helpi.conversation.R
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.ui.components.EvaEstado
import com.helpi.conversation.ui.components.EvaOrb
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiShapes
import com.helpi.conversation.ui.theme.HelpiType
import kotlin.math.roundToInt

internal fun porcentaje(value: Float) = "${(value * 100).roundToInt()} %"

/**
 * Pestaña «Cuenta».
 *
 * Reúne lo que dura más que una conversación: quién usa el teléfono, cuánta
 * confianza se le exige al modelo antes de comunicar una seña, y qué puede y
 * qué no puede hacer Helpi. Los límites del modelo están acá y no escondidos
 * en un «acerca de», porque condicionan cómo se usa la herramienta.
 */
@Composable
internal fun PantallaCuenta(
    state: SessionCoordinator.UiState,
    participantes: Participantes,
    onThreshold: (Float) -> Unit,
    onRenombrarCuenta: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editando by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        TarjetaDePerfil(participantes.cuenta) { editando = true }

        Seccion("Reconocimiento") {
            ControlDeUmbral(state, onThreshold)
        }

        Seccion("Qué reconoce Helpi") {
            Text(
                "64 señas aisladas de lengua de señas argentina, sin conexión y sin que el " +
                    "video salga del teléfono. Una seña que no esté en esas 64 se informa como " +
                    "no reconocida: Helpi no la fuerza a la más parecida.",
                style = HelpiType.BodyS,
                color = HelpiColors.LedMuted,
            )
            Text(
                "El modelo aprendió de personas oyentes y diestras grabadas en un laboratorio, " +
                    "así que todavía falla de forma sistemática con señantes zurdos y puede " +
                    "comportarse distinto fuera de esas condiciones.",
                style = HelpiType.BodyS,
                color = HelpiColors.LedMuted,
            )
            Text(
                "Helpi ayuda a comunicarse. No reemplaza a una persona intérprete y no sirve " +
                    "para una emergencia: su vocabulario no incluye ninguna seña de auxilio.",
                style = HelpiType.BodyS,
                color = HelpiColors.Warning,
            )
        }

        Seccion("Licencias") {
            Text(
                "LSA64 · LIDI, Universidad Nacional de La Plata · CC BY-NC-SA 4.0. Uso no " +
                    "comercial. Vosk español 0.42 · Apache 2.0. Atkinson Hyperlegible Next y " +
                    "Lexend · SIL Open Font License.",
                style = HelpiType.CaptionDisclaimer,
                color = HelpiColors.LedMuted,
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    if (editando) {
        DialogoDeNombre(
            titulo = "Tu nombre",
            explicacion = "Aparece arriba de los mensajes que decís por el micrófono.",
            inicial = participantes.cuenta,
            confirmar = "Guardar",
            onConfirmar = { onRenombrarCuenta(it); editando = false },
            onDismiss = { editando = false },
        )
    }
}

@Composable
private fun TarjetaDePerfil(nombre: String, onEditar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HelpiColors.Surface, HelpiShapes.Tarjeta)
            .border(1.dp, HelpiColors.Divider, HelpiShapes.Tarjeta)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EvaOrb(EvaEstado.REPOSO, Modifier.size(46.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(nombre, style = HelpiType.TitleM, color = HelpiColors.LedSoft)
            Text(
                "Persona oyente · cuenta de demostración",
                style = HelpiType.BodyS,
                color = HelpiColors.LedMuted,
            )
        }
        IconButton(onClick = onEditar, modifier = Modifier.size(48.dp)) {
            Icon(
                painterResource(R.drawable.ic_editar),
                contentDescription = "Cambiar tu nombre",
                tint = HelpiColors.LedMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun Seccion(titulo: String, contenido: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color = HelpiColors.Divider)
        Text(
            titulo,
            style = HelpiType.LabelBoton,
            color = HelpiColors.LedSoft,
            modifier = Modifier.padding(top = 6.dp),
        )
        contenido()
    }
}

/**
 * Compuerta de confianza. Es el control más importante de la aplicación:
 * decide cuándo Helpi prefiere decir «no entendí» antes que arriesgar una
 * traducción, y una traducción equivocada puede dañar más una conversación
 * que la ausencia de traducción.
 */
@Composable
internal fun ControlDeUmbral(
    state: SessionCoordinator.UiState,
    onThreshold: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Umbral de confianza", style = HelpiType.BodyM, color = HelpiColors.LedSoft)
            Text(
                porcentaje(state.confidenceThreshold),
                style = HelpiType.BodyM.copy(fontWeight = FontWeight.Bold),
                color = HelpiColors.LedSoft,
            )
        }
        Slider(
            value = state.confidenceThreshold,
            onValueChange = onThreshold,
            valueRange = SessionCoordinator.MIN_THRESHOLD..SessionCoordinator.MAX_THRESHOLD,
            modifier = Modifier.semantics { contentDescription = "Umbral de confianza" },
        )
        Text(
            "Subí el umbral para exigir más confianza antes de comunicar una seña.",
            style = HelpiType.BodyS,
            color = HelpiColors.LedMuted,
        )
        state.lastRecognition?.let {
            Text(
                "Último intento: ${porcentaje(it.confidence)}. " +
                    "Umbral utilizado: ${porcentaje(it.threshold)}.",
                style = HelpiType.BodyS,
                color = HelpiColors.LedMuted,
            )
        }
    }
}

/** Canales que no arrancaron. Se explica qué hacer, no solo que falló. */
@Composable
internal fun AvisosDeCapacidades(state: SessionCoordinator.UiState) {
    if (!state.capabilities.stt) {
        Text(
            "Permití el acceso al micrófono en los ajustes del teléfono y volvé a iniciar la " +
                "conversación.",
            style = HelpiType.BodyS,
            color = HelpiColors.Warning,
        )
    }
    if (!state.capabilities.tts) {
        Text(
            "Instalá una voz española en los ajustes de texto a voz del teléfono para escuchar " +
                "los mensajes.",
            style = HelpiType.BodyS,
            color = HelpiColors.Warning,
        )
    }
}

/**
 * Pedido de nombre. Se usa tanto para la cuenta como para la persona con la
 * que se va a conversar; cambian el título, la explicación y el botón.
 */
@Composable
internal fun DialogoDeNombre(
    titulo: String,
    explicacion: String,
    inicial: String,
    confirmar: String,
    onConfirmar: (String) -> Unit,
    onDismiss: () -> Unit,
    omitir: String? = null,
    onOmitir: (() -> Unit)? = null,
) {
    var texto by remember { mutableStateOf(if (inicial == SIN_NOMBRE) "" else inicial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = HelpiColors.Surface,
        title = { Text(titulo, style = HelpiType.LabelBoton) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(explicacion, style = HelpiType.BodyS, color = HelpiColors.LedMuted)
                OutlinedTextField(
                    value = texto,
                    onValueChange = { texto = it.take(30) },
                    singleLine = true,
                    shape = HelpiShapes.Chip,
                    textStyle = HelpiType.BodyM,
                    placeholder = { Text("Nombre", style = HelpiType.BodyM) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("nameInput"),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirmar(texto) }) { Text(confirmar) } },
        dismissButton = omitir?.let {
            { TextButton(onClick = { onOmitir?.invoke() }) { Text(it) } }
        },
    )
}
