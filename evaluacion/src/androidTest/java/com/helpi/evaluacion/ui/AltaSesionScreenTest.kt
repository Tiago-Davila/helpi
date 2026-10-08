package com.helpi.evaluacion.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.helpi.evaluacion.datos.CondicionesSesion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AltaSesionScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun empezarSeHabilitaSoloConCodigoYTodasLasCondiciones() {
        var condicionesRecibidas: CondicionesSesion? = null
        var codigoRecibido: String? = null
        composeRule.setContent {
            AltaSesionScreen(
                state = AltaSesionUiState(
                    cargando = false,
                    consentimientoVigente = true,
                    metadatosDisponibles = true
                ),
                onCrearSesion = { codigo, condiciones ->
                    codigoRecibido = codigo
                    condicionesRecibidas = condiciones
                },
                onVolver = {}
            )
        }

        composeRule.onNodeWithTag("altaEmpezar").assertIsNotEnabled()
        composeRule.onNodeWithTag("altaCodigoParticipante").performTextInput("P-007")
        seleccionar("entorno", 0)
        seleccionar("tipoEntorno", 1)
        seleccionar("iluminacion", 1)
        seleccionar("contraluz", 1)
        seleccionar("distancia", 1)
        seleccionar("manoDominante", 0)
        seleccionar("guantes", 1)
        seleccionar("soporteCamara", 0)
        seleccionar("perfilParticipante", 0)

        composeRule.onNodeWithTag("altaEmpezar").assertIsEnabled().performClick()

        assertEquals("P-007", codigoRecibido)
        assertEquals(false, condicionesRecibidas?.contraluz)
        assertEquals(false, condicionesRecibidas?.guantes)
    }

    @Test
    fun codigoDeParticipanteInvalidoMantieneEmpezarDeshabilitado() {
        composeRule.setContent {
            AltaSesionScreen(
                state = AltaSesionUiState(
                    cargando = false,
                    consentimientoVigente = true,
                    metadatosDisponibles = true
                ),
                onCrearSesion = { _, _ -> error("No debe aceptar un código inválido") },
                onVolver = {}
            )
        }

        composeRule.onNodeWithTag("altaCodigoParticipante").performTextInput("P-7")
        composeRule.onNodeWithTag("altaEmpezar").assertIsNotEnabled()
    }

    private fun seleccionar(campo: String, indiceOpcion: Int) {
        composeRule.onNodeWithTag("condicion-$campo").performScrollTo().performClick()
        composeRule.onNodeWithTag("opcion-$campo-$indiceOpcion").performClick()
    }
}
