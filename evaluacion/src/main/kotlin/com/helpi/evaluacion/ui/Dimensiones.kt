@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.helpi.evaluacion.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ancho máximo del contenido de un formulario, para que no se estire en pantallas grandes. */
internal val ANCHO_MAXIMO_CONTENIDO = 640.dp

/** Devuelve la fracción de esta medida, acotada entre un mínimo y un máximo en dp. */
internal fun Dp.fraccion(fraccion: Float, minimo: Dp, maximo: Dp): Dp =
    (this * fraccion).coerceIn(minimo, maximo)

private const val FRACCION_MARGEN_LATERAL = 0.06f

/** Margen lateral estándar: cerca del 6 % del ancho, entre 16 y 32 dp. */
internal fun Dp.margenLateral(): Dp = fraccion(FRACCION_MARGEN_LATERAL, 16.dp, 32.dp)

/** Indica si el espacio disponible es más ancho que alto. */
internal fun esApaisado(ancho: Dp, alto: Dp): Boolean = ancho > alto

/**
 * Columna de pantalla completa que dimensiona sus márgenes en porcentajes del
 * espacio disponible, respeta los márgenes seguros del sistema, limita su ancho
 * en pantallas grandes y, si [desplazable], permite recorrer el contenido cuando
 * no entra (por ejemplo en horizontal o con fuente grande).
 */
@Composable
internal fun ColumnaAdaptativa(
    modifier: Modifier = Modifier,
    desplazable: Boolean = true,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    contenido: @Composable ColumnScope.() -> Unit
) {
    val desplazamiento = rememberScrollState()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
                )
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        val base = Modifier.widthIn(max = ANCHO_MAXIMO_CONTENIDO).fillMaxWidth()
        val columna = if (desplazable) {
            base.heightIn(min = maxHeight).verticalScroll(desplazamiento)
        } else {
            base.fillMaxHeight()
        }
        Column(
            modifier = columna.padding(maxWidth.margenLateral()),
            verticalArrangement = verticalArrangement,
            horizontalAlignment = horizontalAlignment,
            content = contenido
        )
    }
}
