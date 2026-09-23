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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.LocalAnimacionesActivas
import com.helpi.conversation.ui.theme.rutaBurbuja

/**
 * Estados de la burbuja de Eva.
 *
 * El estado se lee por tamaño, cantidad de luz y elemento circundante. Las
 * tres diferencias son estáticas y simultáneas, así que el estado sigue siendo
 * legible con las animaciones del sistema desactivadas y sin depender del
 * color.
 */
enum class EvaEstado {
    /** Nada en curso. Burbuja chica, poca luz, sin anillos. */
    REPOSO,

    /** Micrófono abierto. Más luz que reposo, menos que hablando; un anillo. */
    ESCUCHANDO,

    /** Clasificando una seña. Burbuja chica y anillo punteado que gira. */
    PENSANDO,

    /** Reproduciendo voz. Burbuja grande, muy iluminada, dos anillos. */
    HABLANDO,
}

/**
 * La burbuja de Helpi. Se dibuja proporcional al tamaño que recibe, de modo
 * que la misma composable sirve para el logotipo de inicio, el indicador sobre
 * la cámara y la marca del encabezado.
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
            dibujarEva(estado, pulsoEfectivo, giroEfectivo)
        }
    }
}

/**
 * Lado del cuadrado que genera la burbuja, como fracción del marco.
 *
 * Las esquinas cortas hacen que la silueta sobresalga de ese cuadrado, así
 * que los valores dejan margen para que los anillos de estado tampoco se
 * recorten al girar (ver `EXTENSION_BURBUJA`).
 */
private fun escalaDe(estado: EvaEstado) = when (estado) {
    EvaEstado.REPOSO -> 0.50f
    EvaEstado.PENSANDO -> 0.455f
    EvaEstado.ESCUCHANDO -> 0.525f
    EvaEstado.HABLANDO -> 0.575f
}

/** Dónde termina el blanco del degradado: la «cantidad de luz» del estado. */
private fun luzDe(estado: EvaEstado) = when (estado) {
    EvaEstado.REPOSO -> 0.12f
    EvaEstado.PENSANDO -> 0.18f
    EvaEstado.ESCUCHANDO -> 0.34f
    EvaEstado.HABLANDO -> 0.58f
}

private fun alfaResplandorDe(estado: EvaEstado) = when (estado) {
    EvaEstado.REPOSO -> 0.20f
    EvaEstado.PENSANDO -> 0.30f
    EvaEstado.ESCUCHANDO -> 0.40f
    EvaEstado.HABLANDO -> 0.60f
}

private fun DrawScope.dibujarEva(estado: EvaEstado, pulso: Float, giro: Float) {
    val escala = escalaDe(estado)
    val lado = size.minDimension
    val centro = Offset(size.width / 2f, size.height / 2f)

    dibujarResplandor(escala, alfaResplandorDe(estado), centro, lado)
    dibujarCuerpo(escala, luzDe(estado), centro, lado)
    dibujarBrilloEspecular(escala, centro, lado, estado)
    dibujarContornoDeLuz(escala, lado)

    when (estado) {
        EvaEstado.HABLANDO -> {
            dibujarAnillo(escala * (1.20f + 0.03f * pulso), lado, 1f - pulso * 0.45f)
            dibujarAnillo(escala * (1.34f + 0.05f * pulso), lado, 0.75f - pulso * 0.45f)
        }
        EvaEstado.ESCUCHANDO -> {
            dibujarAnillo(escala * (1.22f + 0.04f * pulso), lado, 0.9f - pulso * 0.4f)
        }
        EvaEstado.PENSANDO -> {
            rotate(degrees = giro, pivot = centro) {
                drawPath(
                    path = rutaBurbuja(size, escala * 1.30f),
                    color = HelpiColors.LedSoft,
                    style = Stroke(
                        width = lado * 0.014f,
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

/**
 * Halo de marca. Es la burbuja agrandada y desvanecida, no un círculo: si el
 * resplandor fuera redondo delataría la forma que la silueta justamente evita.
 */
private fun DrawScope.dibujarResplandor(
    escala: Float,
    alfa: Float,
    centro: Offset,
    lado: Float,
) {
    drawPath(
        path = rutaBurbuja(size, escala * 1.85f),
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to HelpiColors.BrandCore.copy(alpha = alfa),
                0.48f to HelpiColors.BrandCore.copy(alpha = alfa * 0.62f),
                1.00f to Color.Transparent,
            ),
            center = centro,
            // El halo se apaga antes del borde del marco: si llegara hasta el
            // recorte del lienzo se vería el corte en vez de un desvanecido.
            radius = lado * escala * 0.85f,
        ),
    )
}

/**
 * Degradado vertical acotado a la burbuja: blanco arriba, azul de marca al
 * medio, azul profundo abajo. La base es `brand/core` mezclado con `bg/base`,
 * no un color nuevo: la burbuja sigue siendo el degradado entre los tres
 * tokens del sistema.
 */
private fun DrawScope.dibujarCuerpo(escala: Float, luz: Float, centro: Offset, lado: Float) {
    // La silueta se extiende más que el cuadrado que la genera, así que el
    // degradado se estira para cubrirla entera y no dejar la base sin tono.
    val alto = lado * escala * 1.08f
    drawPath(
        path = rutaBurbuja(size, escala),
        brush = Brush.verticalGradient(
            colorStops = arrayOf(
                0.00f to HelpiColors.LedSoft,
                luz to HelpiColors.LedSoft,
                (luz + (1f - luz) * 0.58f) to HelpiColors.BrandCore,
                1.00f to lerp(HelpiColors.BrandCore, HelpiColors.BgBase, 0.62f),
            ),
            startY = centro.y - alto / 2f,
            endY = centro.y + alto / 2f,
        ),
    )
}

/**
 * Reflejo especular arriba a la izquierda: es lo que convierte el degradado en
 * una superficie de vidrio. Va achatado, porque un reflejo circular sobre una
 * forma que no es circular se lee como un error de dibujo.
 */
private fun DrawScope.dibujarBrilloEspecular(
    escala: Float,
    centro: Offset,
    lado: Float,
    estado: EvaEstado,
) {
    // Con mucha luz la burbuja ya es casi blanca arriba y el reflejo se pierde.
    val intensidad = if (estado == EvaEstado.HABLANDO) 0.30f else 0.55f
    val diametro = lado * escala
    val radio = diametro * 0.23f
    // Va justo debajo de donde termina el blanco del degradado: sobre el azul
    // se ve como un reflejo; dentro del blanco no se vería nada.
    val foco = Offset(centro.x - diametro * 0.21f, centro.y - diametro * 0.10f)
    scale(scaleX = 1f, scaleY = 0.58f, pivot = foco) {
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0.00f to Color.White.copy(alpha = intensidad),
                    0.55f to Color.White.copy(alpha = intensidad * 0.35f),
                    1.00f to Color.Transparent,
                ),
                center = foco,
                radius = radio,
            ),
            radius = radio,
            center = foco,
        )
    }
}

/** Evita que la base de la burbuja se funda con el fondo. */
private fun DrawScope.dibujarContornoDeLuz(escala: Float, lado: Float) {
    drawPath(
        path = rutaBurbuja(size, escala),
        color = HelpiColors.LedSoft.copy(alpha = 0.85f),
        style = Stroke(width = lado * 0.006f),
    )
}

private fun DrawScope.dibujarAnillo(escala: Float, lado: Float, alfa: Float) {
    drawPath(
        path = rutaBurbuja(size, escala),
        color = HelpiColors.LedSoft.copy(alpha = alfa.coerceIn(0f, 1f)),
        style = Stroke(width = lado * 0.0074f),
    )
}
