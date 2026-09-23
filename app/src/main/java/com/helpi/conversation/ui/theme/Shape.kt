package com.helpi.conversation.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Formas del sistema.
 *
 * La burbuja es la marca. No es un círculo: un círculo perfecto es la forma
 * por defecto de cualquier indicador y no dice nada sobre el producto. Acá el
 * radio de cada esquina es distinto y la figura va inclinada, de modo que se
 * lee como una gota de vidrio y no como un punto de carga.
 *
 * Es una sola silueta usada a tres escalas —héroe de inicio, estado sobre la
 * cámara y marca del encabezado— y es el único elemento de la interfaz que se
 * permite ser llamativo. Todo lo demás usa rectángulos redondeados sobrios.
 */
object HelpiShapes {

    /** Marca de Helpi. Recorta y dibuja igual porque es un `Shape` real. */
    val Burbuja: Shape = BurbujaShape()

    /** Panel de cámara y superficies grandes. */
    val Panel = RoundedCornerShape(28.dp)

    /** Tarjetas de la pantalla de cuenta. */
    val Tarjeta = RoundedCornerShape(20.dp)

    /** Chips: instrucción de encuadre, nota del sistema. */
    val Chip = RoundedCornerShape(14.dp)

    /** Botones de ancho completo y campos de texto. */
    val Pastilla = RoundedCornerShape(percent = 50)

    /** Turno propio: la cola apunta abajo a la derecha, hacia quien lo escribió. */
    val BurbujaPropia = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomEnd = 6.dp,
        bottomStart = 22.dp,
    )

    /** Turno de la otra persona: la cola apunta abajo a la izquierda. */
    val BurbujaAjena = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomEnd = 22.dp,
        bottomStart = 6.dp,
    )
}

/**
 * Radios por esquina, como fracción del lado. Dos esquinas van completamente
 * redondeadas y las otras dos quedan cortas: eso inclina la figura sobre una
 * diagonal y la aleja del círculo, que es la forma por defecto de cualquier
 * indicador de carga y no dice nada sobre el producto.
 */
private const val RADIO_SUP_IZQ = 0.50f
private const val RADIO_SUP_DER = 0.30f
private const val RADIO_INF_DER = 0.50f
private const val RADIO_INF_IZQ = 0.35f

/** Inclinación en grados. Suficiente para notarse, poca para no parecer torcida. */
private const val INCLINACION = -14f

/**
 * Cuánto se extiende la silueta más allá del radio del cuadrado que la
 * contiene, por culpa de las esquinas cortas. Quien llama usa esta constante
 * para elegir escalas que entren en el marco incluso después de rotar.
 */
const val EXTENSION_BURBUJA = 0.583f

private class BurbujaShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(rutaBurbuja(size, escala = 0.5f / EXTENSION_BURBUJA))
}

/**
 * Construye la silueta centrada en `size`, sobre un cuadrado de lado
 * `min(size) * escala`.
 *
 * `escala` permite dibujar contornos concéntricos —los anillos de estado de
 * Eva— sin recalcular la forma: son la misma burbuja, más grande.
 */
fun rutaBurbuja(size: Size, escala: Float = 1f): Path {
    val lado = min(size.width, size.height) * escala
    val centro = Offset(size.width / 2f, size.height / 2f)
    val izquierda = centro.x - lado / 2f
    val arriba = centro.y - lado / 2f

    val ruta = Path().apply {
        addRoundRect(
            RoundRect(
                left = izquierda,
                top = arriba,
                right = izquierda + lado,
                bottom = arriba + lado,
                topLeftCornerRadius = CornerRadius(lado * RADIO_SUP_IZQ),
                topRightCornerRadius = CornerRadius(lado * RADIO_SUP_DER),
                bottomRightCornerRadius = CornerRadius(lado * RADIO_INF_DER),
                bottomLeftCornerRadius = CornerRadius(lado * RADIO_INF_IZQ),
            ),
        )
    }

    val matriz = Matrix().apply {
        translate(centro.x, centro.y)
        rotateZ(INCLINACION)
        translate(-centro.x, -centro.y)
    }
    ruta.transform(matriz)
    return ruta
}
