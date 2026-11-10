package com.helpi.conversation.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiType
import com.helpi.conversation.ui.theme.fraccion
import kotlinx.coroutines.delay

/** Duraciones del giro, en milisegundos. */
private const val MS_ESPERA_INICIAL = 250L
private const val MS_GIRO = 700
private const val MS_ESPERA_FINAL = 300L

/** Ángulo final del teléfono dibujado: un cuarto de vuelta hacia la izquierda. */
private const val GRADOS_GIRO = -90f

/** Proporciones del teléfono dibujado, relativas al lado menor de la pantalla. */
private const val FRACCION_TELEFONO = 0.22f
private const val PROPORCION_TELEFONO = 1.9f
private const val FRACCION_CAMARA = 0.06f
private const val FRACCION_LENTE = 0.12f
private const val FRACCION_ESQUINA = 0.18f
private const val FRACCION_SEPARACION = 0.25f

/**
 * Pantalla de transición hacia la conversación: un teléfono que gira de
 * vertical a horizontal y la indicación de girar el propio. Si [animar] es
 * falso se muestra ya girado, sin movimiento. Al terminar llama a [alTerminar].
 */
@Composable
internal fun AnimacionDeGiro(
    animar: Boolean,
    alTerminar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val terminar by rememberUpdatedState(alTerminar)
    val angulo = remember { Animatable(if (animar) 0f else GRADOS_GIRO) }

    LaunchedEffect(animar) {
        if (animar) {
            delay(MS_ESPERA_INICIAL)
            angulo.animateTo(GRADOS_GIRO, tween(MS_GIRO, easing = FastOutSlowInEasing))
            delay(MS_ESPERA_FINAL)
            terminar()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(HelpiColors.BgBase)
            .testTag("animacionDeGiro")
            .semantics { contentDescription = "Girá el teléfono para conversar" },
        contentAlignment = Alignment.Center
    ) {
        val ancho = minOf(maxWidth, maxHeight).fraccion(FRACCION_TELEFONO, 56.dp, 120.dp)
        val alto = ancho * PROPORCION_TELEFONO
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(alto * FRACCION_SEPARACION)
        ) {
            TelefonoDibujado(ancho, alto, angulo.value)
            Text(
                "Girá el teléfono",
                style = HelpiType.TitleM,
                color = HelpiColors.LedSoft,
                textAlign = TextAlign.Center
            )
            Text(
                "La cámara reconoce las señas en horizontal.",
                style = HelpiType.BodyM,
                color = HelpiColors.LedMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

/**
 * Silueta de un teléfono con su cámara, girada [grados]. Ocupa un cuadrado del
 * lado de su diagonal para que el giro no se superponga al texto de abajo.
 */
@Composable
private fun TelefonoDibujado(ancho: Dp, alto: Dp, grados: Float) {
    Box(Modifier.size(alto), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = ancho, height = alto)
                .graphicsLayer { rotationZ = grados }
                .border(3.dp, HelpiColors.LedSoft, RoundedCornerShape(ancho * FRACCION_ESQUINA)),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                Modifier
                    .padding(top = alto * FRACCION_CAMARA)
                    .size(ancho * FRACCION_LENTE)
                    .background(HelpiColors.BrandCore, CircleShape)
            )
        }
    }
}
