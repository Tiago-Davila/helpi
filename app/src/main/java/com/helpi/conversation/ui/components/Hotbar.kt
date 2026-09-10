package com.helpi.conversation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.helpi.conversation.R
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiType

/**
 * Secciones de la barra inferior.
 *
 * En el archivo de Figma había además `Pictogramas` y `Rutinas`; son de un
 * modelo anterior del producto y no forman parte de esta aplicación (ver
 * CLAUDE.md §7), así que no se implementan.
 */
enum class SeccionHotbar(val etiqueta: String, val icono: Int) {
    TRADUCTOR("Traductor", R.drawable.ic_tab_traductor),
    CUENTA("Cuenta", R.drawable.ic_tab_cuenta),
}

/**
 * Barra inferior. La pestaña activa lleva una pastilla blanca con el icono
 * invertido; el texto siempre está presente, nunca solo icono (documentado
 * así en el componente `Hotbar / Item`).
 */
@Composable
fun Hotbar(
    seleccionada: SeccionHotbar,
    onSeleccionar: (SeccionHotbar) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = HelpiColors.Divider, thickness = 1.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            SeccionHotbar.entries.forEach { seccion ->
                HotbarItem(
                    seccion = seccion,
                    activa = seccion == seleccionada,
                    onClick = { onSeleccionar(seccion) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HotbarItem(
    seccion: SeccionHotbar,
    activa: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(76.dp)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Tab
                selected = activa
                contentDescription = seccion.etiqueta
            },
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(60.dp)
                .height(34.dp)
                .background(
                    color = if (activa) HelpiColors.LedSoft else Color.Transparent,
                    shape = RoundedCornerShape(17.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(seccion.icono),
                contentDescription = null,
                tint = if (activa) HelpiColors.BgBase else HelpiColors.LedSoft,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            text = seccion.etiqueta,
            style = HelpiType.LabelHotbar,
            color = if (activa) HelpiColors.LedSoft else HelpiColors.LedMuted,
            modifier = Modifier.clearAndSetSemantics { },
        )
    }
}
