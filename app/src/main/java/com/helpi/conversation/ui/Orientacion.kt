package com.helpi.conversation.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.helpi.conversation.ui.theme.LocalAnimacionesActivas

/**
 * Orientación que necesita cada pantalla.
 *
 * Leer y elegir (inicio, cuenta, aviso de consentimiento, invitado) se hace
 * en vertical, que es como se sostiene el teléfono. La conversación va en
 * horizontal porque la cámara analiza en 16:9, el formato del dataset.
 */
enum class Orientacion { VERTICAL, HORIZONTAL }

/** Constante de [ActivityInfo] que pide esta orientación sin impedir girar 180°. */
internal fun Orientacion.aActivityInfo(): Int = when (this) {
    Orientacion.VERTICAL -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    Orientacion.HORIZONTAL -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
}

/** Pide a la actividad que muestra esta pantalla la orientación indicada. */
@Composable
fun FijarOrientacion(orientacion: Orientacion) {
    val actividad = LocalContext.current.actividadQueAloja()
    DisposableEffect(actividad, orientacion) {
        actividad?.requestedOrientation = orientacion.aActivityInfo()
        onDispose { }
    }
}

/**
 * Decide si al entrar a la conversación hay que mostrar el teléfono girando:
 * solo si se viene de una pantalla vertical y el sistema anima.
 */
internal fun debeAnimarGiro(enSala: Boolean, apaisado: Boolean, animaciones: Boolean): Boolean =
    enSala && !apaisado && animaciones

/**
 * Muestra el inicio en vertical y la sala en horizontal. Al entrar a la sala
 * desde vertical aparece el teléfono girando; la sala recién se compone con
 * la pantalla ya apaisada, para que la cámara arranque con la orientación con
 * la que va a analizar.
 */
@Composable
internal fun TransicionDeOrientacion(
    enSala: Boolean,
    sala: @Composable () -> Unit,
    inicio: @Composable () -> Unit
) {
    val apaisado = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val animaciones = LocalAnimacionesActivas.current
    val animarAlEntrar = debeAnimarGiro(enSala, apaisado, animaciones)
    var girando by remember(enSala) { mutableStateOf(animarAlEntrar) }
    FijarOrientacion(if (enSala && !girando) Orientacion.HORIZONTAL else Orientacion.VERTICAL)
    when {
        enSala && (girando || !apaisado) ->
            AnimacionDeGiro(animar = girando, alTerminar = { girando = false })
        enSala -> sala()
        else -> inicio()
    }
}

/** Actividad que aloja este contexto, atravesando los envoltorios de tema. */
private tailrec fun Context.actividadQueAloja(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.actividadQueAloja()
    else -> null
}
