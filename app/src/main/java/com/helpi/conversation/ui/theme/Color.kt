package com.helpi.conversation.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Tokens de color del diseño (variables de Figma, archivo Helpi).
 *
 * Son los tres colores del sistema. La esfera de Eva es justamente el
 * degradado entre los tres, así que no se agregan colores intermedios:
 * cualquier tono nuevo rompe esa lectura.
 */
object HelpiColors {

    /** `bg/base` — fondo de toda la aplicación. */
    val BgBase = Color(0xFF090C14)

    /** `brand/core` — superficies llenas: botones en reposo, burbuja del oyente. */
    val BrandCore = Color(0xFF0A84FF)

    /** `led/soft` — texto e iconos sobre fondo oscuro; superficie de estado activo. */
    val LedSoft = Color(0xFFF5F5F7)

    /** Superficies oscuras elevadas, inspiradas en la jerarquía visual de iOS. */
    val Surface = Color(0xFF171B26)
    val SurfaceRaised = Color(0xFF222735)

    /**
     * Atenuado para texto secundario. Es `led/soft` con alfa, no un gris
     * nuevo: mantiene el tinte del sistema y el contraste sobre `bg/base`.
     */
    val LedMuted = LedSoft.copy(alpha = 0.72f)

    /** Borde de separación (hotbar, contornos tenues). */
    val Divider = LedSoft.copy(alpha = 0.12f)

    /**
     * Estados que la interfaz no puede comunicar solo con los tres colores:
     * advertencia de canal caído o de traducción no reconocida. Se eligió un
     * ámbar cálido porque debe distinguirse del azul del sistema incluso con
     * deuteranopía, donde azul y rojo se confunden menos que rojo y verde.
     */
    val Warning = Color(0xFFFF9F0A)
    val Success = Color(0xFF30D158)
    val Danger = Color(0xFFFF453A)
}
