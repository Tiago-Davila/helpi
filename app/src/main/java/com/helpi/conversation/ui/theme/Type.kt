package com.helpi.conversation.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.helpi.conversation.R

/**
 * Tipografía del diseño.
 *
 * Atkinson Hyperlegible Next para lectura: sus formas están diseñadas para
 * que caracteres parecidos (l/1/I, 0/O) no se confundan con baja visión.
 * Lexend para etiquetas de control.
 *
 * Ambas son OFL. Van embebidas en `res/font/` y no se descargan: la app
 * funciona sin red por diseño y la tipografía no puede ser la excepción.
 */
private val Atkinson = FontFamily(
    Font(R.font.atkinson_hyperlegible_next, FontWeight.Normal),
)

/**
 * Lexend es una fuente variable: se fija el eje `wght` en 500 en lugar de
 * dejar que el sistema sintetice la seminegrita, que deforma el trazo.
 */
@OptIn(ExperimentalTextApi::class)
private val Lexend = FontFamily(
    Font(
        resId = R.font.lexend,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

/**
 * Estilos nombrados como en Figma. Los tamaños van en `sp` para que el
 * ajuste de tamaño de fuente del sistema los siga escalando: en una
 * herramienta de accesibilidad ese ajuste no se puede anular.
 */
object HelpiType {

    /** `Body/S` — 14/21, +0.1. Pie de estado y textos auxiliares. */
    val BodyS = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.1.sp,
    )

    /** `Body/M` — 17/26. Estado de Eva. */
    val BodyM = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    )

    /** `Body/Chat` — 20/30. Contenido de la conversación. */
    val BodyChat = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    )

    /** `Caption/Disclaimer` — 13/18, +0.2. Aviso de límites. */
    val CaptionDisclaimer = TextStyle(
        fontFamily = Atkinson,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    )

    /** `Label/Boton` — Lexend Medium 18/24, +0.2. */
    val LabelBoton = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.2.sp,
    )

    /** `Label/Hotbar` — Lexend Medium 12/16, +0.4. */
    val LabelHotbar = TextStyle(
        fontFamily = Lexend,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp,
    )

    /** Autor del turno, sobre la burbuja. Nunca implícito. */
    val LabelAutor = LabelHotbar
}

internal val HelpiTypography = Typography(
    bodyLarge = HelpiType.BodyChat,
    bodyMedium = HelpiType.BodyM,
    bodySmall = HelpiType.BodyS,
    labelLarge = HelpiType.LabelBoton,
    labelSmall = HelpiType.LabelHotbar,
)
