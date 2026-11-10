package com.helpi.conversation.ui

import com.helpi.conversation.session.SessionState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimacionDeEntradaTest {

    @Test
    fun `se muestra en arranque en frio con animaciones activas`() {
        assertTrue(debeMostrarAnimacionDeEntrada(true, SessionState.INICIO, true))
    }

    @Test
    fun `no se muestra con una conversacion pausada`() {
        assertFalse(debeMostrarAnimacionDeEntrada(true, SessionState.PAUSADA, true))
    }

    @Test
    fun `no se muestra al recrear la actividad`() {
        assertFalse(debeMostrarAnimacionDeEntrada(false, SessionState.INICIO, true))
    }

    @Test
    fun `no se muestra con las animaciones del sistema desactivadas`() {
        assertFalse(debeMostrarAnimacionDeEntrada(true, SessionState.INICIO, false))
    }

    @Test
    fun `se muestra en cualquier estado de sesion que no sea pausada`() {
        SessionState.values().filter { it != SessionState.PAUSADA }.forEach {
            assertTrue(it.name, debeMostrarAnimacionDeEntrada(true, it, true))
        }
    }
}
