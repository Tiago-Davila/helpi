package com.helpi.conversation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.helpi.conversation.R
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiType

/** Los tres modos de entrada del diseño (`Entrada / Botón`). */
enum class ModoEntrada(val etiqueta: String, val icono: Int) {
    ESCRIBIR("Escribir", R.drawable.ic_entrada_teclado),
    HABLAR("Hablar", R.drawable.ic_entrada_microfono),
    MOSTRAR("Mostrar", R.drawable.ic_entrada_camara),
}

/**
 * Fila de los tres modos de entrada.
 *
 * `disponibles` decide cuáles se pueden usar: un modo cuyo canal no arrancó
 * se muestra apagado y se anuncia como no disponible, en vez de aceptar el
 * toque y no hacer nada. El motivo va escrito debajo, no solo en el color.
 */
@Composable
fun EntradaBar(
    seleccionado: ModoEntrada?,
    disponibles: Set<ModoEntrada>,
    onSeleccionar: (ModoEntrada) -> Unit,
    modifier: Modifier = Modifier,
    altura: androidx.compose.ui.unit.Dp = 64.dp,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(HelpiColors.Surface, RoundedCornerShape(22.dp))
            .border(1.dp, HelpiColors.Divider, RoundedCornerShape(22.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ModoEntrada.entries.forEach { modo ->
            EntradaBoton(
                modo = modo,
                activo = modo == seleccionado,
                habilitado = modo in disponibles,
                altura = altura,
                onClick = { onSeleccionar(modo) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Reposo = azul con contenido blanco. Activo = se invierte a blanco. El área
 * táctil es de 112 dp, muy por encima del mínimo accesible (documentado así
 * en el componente de Figma).
 */
@Composable
private fun EntradaBoton(
    modo: ModoEntrada,
    activo: Boolean,
    habilitado: Boolean,
    altura: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(18.dp)

    val fondo = when {
        !habilitado -> Color.Transparent
        activo -> HelpiColors.BrandCore
        else -> Color.Transparent
    }
    val contenido = when {
        !habilitado -> HelpiColors.LedMuted.copy(alpha = 0.38f)
        activo -> HelpiColors.LedSoft
        else -> HelpiColors.LedMuted
    }

    val estadoLeido = when {
        !habilitado -> "no disponible"
        activo -> "seleccionado"
        else -> "disponible"
    }

    Column(
        modifier = modifier
            .heightIn(min = altura)
            .height(altura)
            .background(fondo, forma)
            .then(
                if (habilitado) {
                    Modifier
                } else {
                    // Sin relleno, el botón apagado necesita contorno para no
                    // desaparecer del todo sobre el fondo negro.
                    Modifier.border(1.dp, HelpiColors.Divider, forma)
                },
            )
            .clickable(enabled = habilitado, onClick = onClick)
            .semantics {
                role = Role.Tab
                selected = activo
                if (!habilitado) disabled()
                contentDescription = "${modo.etiqueta}, $estadoLeido"
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(modo.icono),
            contentDescription = null,
            tint = contenido,
            modifier = Modifier.size(23.dp),
        )
        Text(
            text = modo.etiqueta,
            style = HelpiType.LabelHotbar,
            color = contenido,
            textAlign = TextAlign.Center,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
