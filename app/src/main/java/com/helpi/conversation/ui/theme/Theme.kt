package com.helpi.conversation.ui.theme

import android.provider.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * El diseño es oscuro y único: no hay variante clara. No es una preferencia
 * estética sino de uso: la pantalla se sostiene frente a otra persona durante
 * una conversación, y un fondo claro a brillo alto encandila al interlocutor.
 */
private val HelpiColorScheme = darkColorScheme(
    primary = HelpiColors.LedSoft,
    onPrimary = HelpiColors.BgBase,
    primaryContainer = HelpiColors.BrandCore,
    onPrimaryContainer = HelpiColors.LedSoft,
    background = HelpiColors.BgBase,
    onBackground = HelpiColors.LedSoft,
    surface = HelpiColors.BgBase,
    onSurface = HelpiColors.LedSoft,
    surfaceVariant = HelpiColors.BrandCore,
    onSurfaceVariant = HelpiColors.LedSoft,
    outline = HelpiColors.Divider,
    error = HelpiColors.Warning,
    onError = HelpiColors.BgBase,
)

/**
 * Si la persona desactivó las animaciones del sistema, los estados de Eva se
 * leen igual: cambian por tamaño, luz y elemento circundante, que son
 * diferencias estáticas. El movimiento solo las acompaña.
 */
val LocalAnimacionesActivas = staticCompositionLocalOf { true }

@Composable
fun HelpiTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val animacionesActivas = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    CompositionLocalProvider(LocalAnimacionesActivas provides animacionesActivas) {
        MaterialTheme(
            colorScheme = HelpiColorScheme,
            typography = HelpiTypography,
            content = content,
        )
    }
}
