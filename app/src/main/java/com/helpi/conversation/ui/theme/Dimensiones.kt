package com.helpi.conversation.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Medidas derivadas del espacio disponible.
 *
 * Las pantallas no fijan tamaños en dp: toman una fracción del ancho o del alto
 * que reciben (`BoxWithConstraints`) y la acotan entre un mínimo y un máximo,
 * para que se lean igual en un teléfono chico, en uno grande y en una tablet.
 */

/** Ancho máximo del contenido de una pantalla de lectura o de formulario. */
val AnchoMaximoContenido = 600.dp

/** Devuelve la fracción de esta medida, acotada entre un mínimo y un máximo en dp. */
fun Dp.fraccion(fraccion: Float, minimo: Dp, maximo: Dp): Dp =
    (this * fraccion).coerceIn(minimo, maximo)

private const val FRACCION_MARGEN_LATERAL = 0.06f

/** Margen lateral estándar: cerca del 6 % del ancho, entre 16 y 32 dp. */
fun Dp.margenLateral(): Dp = fraccion(FRACCION_MARGEN_LATERAL, 16.dp, 32.dp)

/** Indica si el espacio disponible es más ancho que alto. */
fun esApaisado(ancho: Dp, alto: Dp): Boolean = ancho > alto
