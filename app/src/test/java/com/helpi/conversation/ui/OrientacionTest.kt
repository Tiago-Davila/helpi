package com.helpi.conversation.ui

import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrientacionTest {
    @Test fun lasPantallasDeLecturaPidenVerticalYLaConversacionHorizontal() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT,
            Orientacion.VERTICAL.aActivityInfo()
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            Orientacion.HORIZONTAL.aActivityInfo()
        )
    }

    @Test fun elGiroSeAnimaAlEntrarALaSalaDesdeVertical() {
        assertTrue(debeAnimarGiro(enSala = true, apaisado = false, animaciones = true))
    }

    @Test fun elGiroNoSeAnimaSiLaPantallaYaEstaApaisada() {
        assertFalse(debeAnimarGiro(enSala = true, apaisado = true, animaciones = true))
    }

    @Test fun elGiroNoSeAnimaFueraDeLaSala() {
        assertFalse(debeAnimarGiro(enSala = false, apaisado = false, animaciones = true))
    }

    @Test fun elGiroNoSeAnimaConLasAnimacionesDelSistemaDesactivadas() {
        assertFalse(debeAnimarGiro(enSala = true, apaisado = false, animaciones = false))
    }
}
