package com.helpi.evaluacion.ui.protocolo

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.helpi.evaluacion.datos.entidades.ResultadoIntento
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProtocoloScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun estadoBajoUmbralNoMuestraLaPrediccionNiLaExactitudAcumulada() {
        composeRule.setContent {
            ProtocoloScreen(
                state = estado(
                    etapa = EtapaProtocolo.RESULTADO,
                    resultado = ResultadoIntento.BAJO_UMBRAL,
                    glosaPredicha = null
                ),
                camara = ProtocoloCamaraUiState(
                    "Mostrá los hombros y las manos.",
                    "Mostrá una seña."
                ),
                cameraPreview = { Box(Modifier.testTag("cameraPreview")) },
                acciones = acciones()
            )
        }

        composeRule.onNodeWithTag("protocoloExpectedGloss").assertIsDisplayed()
        composeRule.onNodeWithText("No reconocida").assertIsDisplayed()
        assertEquals(0, composeRule.onAllNodesWithText("SENA_PRIVADA").fetchSemanticsNodes().size)
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Exactitud acumulada").fetchSemanticsNodes().size
        )
        composeRule.onNodeWithTag("cameraPreview").assertIsDisplayed()
    }

    @Test
    fun propuestaSobreUmbralPermiteDescartarSinIndicarAcierto() {
        var descartes = 0
        composeRule.setContent {
            ProtocoloScreen(
                state = estado(
                    etapa = EtapaProtocolo.RESULTADO_PENDIENTE,
                    resultado = ResultadoIntento.SOBRE_UMBRAL,
                    glosaPredicha = "CASA",
                    puedeDescartar = true
                ),
                camara = ProtocoloCamaraUiState("Encuadre correcto.", "Reconociendo tu seña…"),
                cameraPreview = {},
                acciones = acciones().copy(onDescartar = { descartes++ })
            )
        }

        composeRule.onNodeWithText("Eva propone: CASA").assertIsDisplayed()
        composeRule.onNodeWithTag("protocoloDiscardButton").performClick()
        assertEquals(1, descartes)
        assertEquals(0, composeRule.onAllNodesWithText("Correcto").fetchSemanticsNodes().size)
    }

    @Test
    fun pausaEntreBloquesOfreceSeguirORetomarOtroDia() {
        var seguir = 0
        var retomar = 0
        composeRule.setContent {
            ProtocoloScreen(
                state = estado(etapa = EtapaProtocolo.PAUSA_BLOQUE, posicion = 48, bloque = 1),
                camara = ProtocoloCamaraUiState("", ""),
                cameraPreview = {},
                acciones = acciones().copy(
                    onSeguir = { seguir++ },
                    onRetomarOtroDia = { retomar++ }
                )
            )
        }

        composeRule.onNodeWithTag("protocoloContinueBlockButton").performClick()
        composeRule.onNodeWithTag("protocoloResumeTomorrowButton").performClick()
        assertEquals(1, seguir)
        assertEquals(1, retomar)
    }

    private fun estado(
        etapa: EtapaProtocolo,
        resultado: ResultadoIntento? = null,
        glosaPredicha: String? = null,
        puedeDescartar: Boolean = false,
        posicion: Int = 1,
        bloque: Int = 1
    ) = ProtocoloUiState(
        etapa = etapa,
        posicion = posicion,
        bloque = bloque,
        numeroIntento = 1,
        senaEsperadaIndice = 0,
        glosaEsperada = "CASA",
        resultado = resultado,
        glosaPredicha = glosaPredicha,
        puedeDescartar = puedeDescartar
    )

    private fun acciones() = ProtocoloAcciones(
        onDescartar = {},
        onRepetir = {},
        onLoHiceMal = {},
        onContinuar = {},
        onSeguir = {},
        onRetomarOtroDia = {},
        onPausar = {},
        onTerminarSesion = {},
        onSalir = {}
    )
}
