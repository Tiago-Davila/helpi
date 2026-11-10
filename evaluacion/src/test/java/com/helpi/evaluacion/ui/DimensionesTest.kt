package com.helpi.evaluacion.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DimensionesTest {
    @Test
    fun `el margen lateral respeta los topes`() {
        assertEquals(16.dp, 200.dp.margenLateral())
        assertEquals(32.dp, 1200.dp.margenLateral())
        assertEquals(24.dp, 400.dp.margenLateral())
    }

    @Test
    fun `apaisado solo si es mas ancho que alto`() {
        assertTrue(esApaisado(800.dp, 360.dp))
        assertFalse(esApaisado(360.dp, 800.dp))
    }
}
