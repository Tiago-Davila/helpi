package com.helpi.conversation.ui.theme

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionesTest {
    @Test
    fun `la fraccion respeta el minimo en pantallas chicas`() {
        assertEquals(16.dp, 200.dp.margenLateral())
    }

    @Test
    fun `la fraccion respeta el maximo en pantallas grandes`() {
        assertEquals(32.dp, 1200.dp.margenLateral())
    }

    @Test
    fun `la fraccion es proporcional dentro de los topes`() {
        assertEquals(24.dp, 400.dp.margenLateral())
        assertEquals(50.dp, 200.dp.fraccion(0.25f, 10.dp, 100.dp))
    }

    @Test
    fun `apaisado solo si es mas ancho que alto`() {
        assertTrue(esApaisado(800.dp, 360.dp))
        assertFalse(esApaisado(360.dp, 800.dp))
        assertFalse(esApaisado(500.dp, 500.dp))
    }
}
