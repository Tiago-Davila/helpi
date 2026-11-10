package com.helpi.conversation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helpi.conversation.session.SessionState
import com.helpi.conversation.ui.components.EvaEstado
import com.helpi.conversation.ui.components.EvaOrb
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Duraciones de cada fase de la animación de entrada, en milisegundos. */
private const val MS_APARICION_ORBE = 450
private const val MS_APARICION_TEXTO = 350
private const val MS_PAUSA = 300L
private const val MS_DESVANECIDO = 350

/** Escala inicial del orbe, que crece hasta su tamaño natural al aparecer. */
private const val ESCALA_INICIAL_ORBE = 0.6f

/** Fracción del lado menor de la pantalla que ocupa el orbe, con topes en dp. */
private const val FRACCION_ORBE = 0.5f
private val ORBE_MINIMO = 160.dp
private val ORBE_MAXIMO = 280.dp

/** Separación entre el orbe y el texto, relativa al tamaño del orbe. */
private const val FRACCION_SEPARACION = 0.04f

/**
 * Decide si hay que mostrar la animación de entrada. Solo se muestra al abrir
 * la aplicación en frío, nunca con una conversación pausada por reanudar ni
 * cuando el sistema tiene las animaciones desactivadas.
 */
fun debeMostrarAnimacionDeEntrada(
    arranqueEnFrio: Boolean,
    sesion: SessionState,
    animacionesDelSistema: Boolean
): Boolean = arranqueEnFrio && sesion != SessionState.PAUSADA && animacionesDelSistema

/**
 * Animación de entrada a pantalla completa: el orbe aparece con fundido y
 * escala, luego el nombre, y todo se desvanece hacia la aplicación. Un toque
 * la saltea. Llama a [alTerminar] al completarse o al saltearla.
 */
@Composable
fun AnimacionDeEntrada(alTerminar: () -> Unit, modifier: Modifier = Modifier) {
    val terminar by rememberUpdatedState(alTerminar)
    val alfaOrbe = remember { Animatable(0f) }
    val escalaOrbe = remember { Animatable(ESCALA_INICIAL_ORBE) }
    val alfaTexto = remember { Animatable(0f) }
    val alfaGeneral = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        // Fundido y escala del orbe corren a la par; el texto espera a que terminen.
        val aparicion = tween<Float>(MS_APARICION_ORBE, easing = FastOutSlowInEasing)
        launch { alfaOrbe.animateTo(1f, aparicion) }
        escalaOrbe.animateTo(1f, aparicion)
        alfaTexto.animateTo(1f, tween(MS_APARICION_TEXTO, easing = LinearEasing))
        delay(MS_PAUSA)
        alfaGeneral.animateTo(0f, tween(MS_DESVANECIDO, easing = LinearEasing))
        terminar()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .alpha(alfaGeneral.value)
            .background(HelpiColors.BgBase)
            // Un toque en cualquier parte saltea la animación, sin ondas visuales.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = "Omitir animación",
                onClick = { terminar() }
            )
            .semantics { contentDescription = "Helpi" },
        contentAlignment = Alignment.Center
    ) {
        val ladoMenor = minOf(maxWidth, maxHeight)
        val tamanoOrbe: Dp = (ladoMenor * FRACCION_ORBE).coerceIn(ORBE_MINIMO, ORBE_MAXIMO)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tamanoOrbe * FRACCION_SEPARACION),
            // El contenido es decorativo: el nombre ya lo anuncia el contenedor.
            modifier = Modifier.clearAndSetSemantics { }
        ) {
            EvaOrb(
                estado = EvaEstado.REPOSO,
                modifier = Modifier
                    .size(tamanoOrbe)
                    .graphicsLayer {
                        alpha = alfaOrbe.value
                        scaleX = escalaOrbe.value
                        scaleY = escalaOrbe.value
                    }
            )
            Text(
                text = "Helpi",
                style = HelpiType.Display,
                color = HelpiColors.LedSoft,
                modifier = Modifier.alpha(alfaTexto.value)
            )
        }
    }
}
