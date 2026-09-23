package com.helpi.conversation.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.helpi.conversation.R
import com.helpi.conversation.ui.theme.HelpiColors
import com.helpi.conversation.ui.theme.HelpiType

/**
 * Secciones de la barra inferior.
 *
 * Solo dos. La aplicación hace una cosa —traducir— y una barra con dos
 * pestañas es honesta al respecto; agregar destinos vacíos para que la barra
 * «se vea completa» sería decoración.
 */
enum class SeccionHotbar(val etiqueta: String, val icono: Int) {
    TRADUCTOR("Traductor", R.drawable.ic_tab_traductor),
    CUENTA("Cuenta", R.drawable.ic_tab_cuenta),
}

/**
 * Barra inferior de navegación. Solo aparece fuera de una conversación: una
 * vez que la sesión está activa la pantalla es del interlocutor, y una barra
 * de pestañas ahí abajo invita a irse justo cuando hay alguien esperando.
 *
 * El texto de cada pestaña siempre está presente, nunca solo el icono.
 */
@Composable
fun Hotbar(
    seleccionada: SeccionHotbar,
    onSeleccionar: (SeccionHotbar) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalDivider(color = HelpiColors.Divider, thickness = 1.dp)
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = HelpiColors.BgBase,
        tonalElevation = 0.dp,
    ) {
        SeccionHotbar.entries.forEach { seccion ->
            NavigationBarItem(
                selected = seccion == seleccionada,
                onClick = { onSeleccionar(seccion) },
                icon = {
                    Icon(
                        painter = painterResource(seccion.icono),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = { Text(seccion.etiqueta, style = HelpiType.LabelHotbar) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = HelpiColors.LedSoft,
                    selectedTextColor = HelpiColors.LedSoft,
                    indicatorColor = HelpiColors.BrandCore,
                    unselectedIconColor = HelpiColors.LedMuted,
                    unselectedTextColor = HelpiColors.LedMuted,
                ),
            )
        }
    }
}
