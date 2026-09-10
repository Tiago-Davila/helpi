package com.helpi.conversation.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.LocalAnimacionesActivas

/**
 * Estados de la esfera de Eva.
 *
 * Del componente en Figma (`Eva / Burbuja`): «el estado se lee por tamaño,
 * cantidad de luz y elemento circundante». Las tres diferencias son
 * estáticas y simultáneas, así que el estado sigue siendo legible con las
 * animaciones del sistema desactivadas y sin depender del color.
 */
enum class EvaEstado {
    /** Nada en curso. Esfera chica, poca luz, sin anillos. */
    REPOSO,

    /**
     * Micrófono abierto. No existe en el archivo de Figma: se extiende el
     * sistema siguiendo su propia regla (más luz que reposo, menos que
     * hablando; un anillo en lugar de dos).
     */
    ESCUCHANDO,

    /** Clasificando una seña. Esfera chica y anillo punteado que gira. */
    PENSANDO,

    /** Reproduciendo voz. Esfera grande, muy iluminada, dos anillos. */
    HABLANDO,
}

/**
 * Esfera de vidrio de Eva. Se dibuja proporcional al tamaño que recibe, de
 * modo que la misma composable sirve para el orbe principal y para el
 * indicador chico que acompaña a «Pensando…».
 *
 * Es decorativa: el estado va escrito al lado en texto, nunca solo en la
 * forma. Por eso no expone descripción de accesibilidad propia.
 */
@Composable
fun EvaOrb(estado: EvaEstado, modifier: Modifier = Modifier) {
    val animar = LocalAnimacionesActivas.current
    val transicion = rememberInfiniteTransition(label = "eva")

    val pulso by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulso",
    )
    val giro by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "giro",
    )

    val pulsoEfectivo = if (animar) pulso else 0.5f
    val giroEfectivo = if (animar) giro else 0f

    Box(modifier = modifier.aspectRatio(1f)) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val lado = size.minDimension
            val centro = Offset(size.width / 2f, size.height / 2f)
            dibujarEva(estado, lado, centro, pulsoEfectivo, giroEfectivo)
        }
    }
}

/**
 * Fracciones tomadas de los SVG exportados, normalizadas contra el lado del
 * marco: reposo 64/240, hablando 84/272, anillos 105/272 y 117/272.
 */
private fun DrawScope.dibujarEva(
    estado: EvaEstado,
    lado: Float,
    centro: Offset,
    pulso: Float,
    giro: Float,
) {
    val radio = lado * when (estado) {
        EvaEstado.REPOSO -> 0.2667f
        EvaEstado.PENSANDO -> 0.2400f
        EvaEstado.ESCUCHANDO -> 0.2800f
        EvaEstado.HABLANDO -> 0.3088f
    }

    // Parada de luz: dónde termina el blanco del degradado. Es la "cantidad
    // de luz" del componente — 0.10 en reposo, 0.58 hablando (del SVG).
    val luz = when (estado) {
        EvaEstado.REPOSO -> 0.10f
        EvaEstado.PENSANDO -> 0.16f
        EvaEstado.ESCUCHANDO -> 0.32f
        EvaEstado.HABLANDO -> 0.58f
    }

    val alfaResplandor = when (estado) {
        EvaEstado.REPOSO -> 0.20f
        EvaEstado.PENSANDO -> 0.30f
        EvaEstado.ESCUCHANDO -> 0.40f
        EvaEstado.HABLANDO -> 0.60f
    }

    dibujarResplandor(centro, radio, alfaResplandor)

    // Degradado vertical acotado a la esfera: blanco arriba, brand al medio,
    // fondo abajo. Son los tres colores del sistema, en ese orden.
    drawCircle(
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to HelpiColors.LedSoft,
                luz to HelpiColors.LedSoft,
                (luz + (1f - luz) * 0.62f) to HelpiColors.BrandCore,
                1.00f to HelpiColors.BgBase,
            ),
            startY = centro.y - radio,
            endY = centro.y + radio,
        ),
        radius = radio,
        center = centro,
    )

    // Borde de luz: evita que la base de la esfera se funda con el fondo.
    drawCircle(
        color = HelpiColors.LedSoft,
        radius = radio - lado * 0.003f,
        center = centro,
        style = Stroke(width = lado * 0.006f),
    )

    when (estado) {
        EvaEstado.HABLANDO -> {
            dibujarAnillo(centro, lado * 0.386f + lado * 0.012f * pulso, lado, 1f - pulso * 0.45f)
            dibujarAnillo(centro, lado * 0.430f + lado * 0.020f * pulso, lado, 0.75f - pulso * 0.45f)
        }
        EvaEstado.ESCUCHANDO -> {
            dibujarAnillo(centro, lado * 0.360f + lado * 0.016f * pulso, lado, 0.9f - pulso * 0.4f)
        }
        EvaEstado.PENSANDO -> {
            val trazo = lado * 0.014f
            rotate(degrees = giro, pivot = centro) {
                drawCircle(
                    color = HelpiColors.LedSoft,
                    radius = lado * 0.330f,
                    center = centro,
                    style = Stroke(
                        width = trazo,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(lado * 0.05f, lado * 0.035f),
                        ),
                    ),
                )
            }
        }
        EvaEstado.REPOSO -> Unit
    }
}

/** Halo de `brand/core`: en el SVG es un drop shadow dilatado y desenfocado. */
private fun DrawScope.dibujarResplandor(centro: Offset, radio: Float, alfa: Float) {
    val radioResplandor = radio * 1.9f
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to HelpiColors.BrandCore.copy(alpha = alfa),
                0.52f to HelpiColors.BrandCore.copy(alpha = alfa * 0.7f),
                1.00f to Color.Transparent,
            ),
            center = centro,
            radius = radioResplandor,
        ),
        radius = radioResplandor,
        center = centro,
    )
}

private fun DrawScope.dibujarAnillo(centro: Offset, radio: Float, lado: Float, alfa: Float) {
    drawCircle(
        color = HelpiColors.LedSoft.copy(alpha = alfa.coerceIn(0f, 1f)),
        radius = radio,
        center = centro,
        style = Stroke(width = lado * 0.0074f),
    )
}
