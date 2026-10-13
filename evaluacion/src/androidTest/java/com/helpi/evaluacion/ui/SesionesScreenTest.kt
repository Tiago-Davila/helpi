package com.helpi.evaluacion.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.helpi.evaluacion.datos.entidades.EstadoSesion
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SesionesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun muestraAvanceEstadosExportacionYPermiteRetomarSoloSesionesPausadas() {
        var sesionRetomada: String? = null
        var sesionExportada: String? = null
        val pausada = sesion(
            id = ID_PAUSADA,
            estado = EstadoSesion.PAUSADA,
            avance = 48,
            envioPendiente = true,
            envioVencido = true,
            ultimaExportacionEn = 1L
        )
        val completa = sesion(ID_COMPLETA, EstadoSesion.COMPLETA, avance = 192)

        composeRule.setContent {
            SesionesScreen(
                state = SesionesUiState(
                    cargando = false,
                    sesiones = listOf(pausada, completa),
                    envioHabilitado = false
                ),
                acciones = SesionesAcciones(
                    onVolver = {},
                    onRetomar = { sesionRetomada = it },
                    onExportar = { sesionExportada = it },
                    onEnviar = {}
                )
            )
        }

        composeRule.onNodeWithText("Avance: 48 de 192 intentos").assertIsDisplayed()
        composeRule.onNodeWithText("Envío pendiente").assertIsDisplayed()
        composeRule.onNodeWithText("Envío vencido").assertIsDisplayed()
        composeRule.onNodeWithText("Última exportación:", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag("sessionResume-$ID_PAUSADA").performClick()
        composeRule.onNodeWithTag("sessionExport-$ID_PAUSADA").performClick()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("sessionSend-$ID_PAUSADA").fetchSemanticsNodes().size
        )
        assertEquals(ID_PAUSADA, sesionRetomada)
        assertEquals(ID_PAUSADA, sesionExportada)
    }

    @Test
    fun sesionCompletaNoMuestraAccionDeRetomar() {
        composeRule.setContent {
            SesionesScreen(
                state = SesionesUiState(
                    cargando = false,
                    sesiones = listOf(sesion(ID_COMPLETA, EstadoSesion.COMPLETA, avance = 192))
                ),
                acciones = SesionesAcciones(
                    onVolver = {},
                    onRetomar = {},
                    onExportar = {},
                    onEnviar = {}
                )
            )
        }

        composeRule.onNodeWithTag("sessionCard-$ID_COMPLETA").assertIsDisplayed()
        assertEquals(
            0,
            composeRule.onAllNodesWithTag("sessionResume-$ID_COMPLETA").fetchSemanticsNodes().size
        )
    }

    @Test
    fun muestraEnviarAlEquipoSoloCuandoLaConfiguracionEstaCompleta() {
        var sesionEnviada: String? = null
        composeRule.setContent {
            SesionesScreen(
                state = SesionesUiState(
                    cargando = false,
                    sesiones = listOf(sesion(ID_PAUSADA, EstadoSesion.INTERRUMPIDA, 2)),
                    envioHabilitado = true
                ),
                acciones = SesionesAcciones(
                    onVolver = {},
                    onRetomar = {},
                    onExportar = {},
                    onEnviar = { sesionEnviada = it }
                )
            )
        }

        composeRule.onNodeWithTag("sessionSend-$ID_PAUSADA").assertIsDisplayed()
        composeRule.onNodeWithTag("sessionSend-$ID_PAUSADA").performClick()
        assertEquals(ID_PAUSADA, sesionEnviada)
    }

    private fun sesion(
        id: String,
        estado: EstadoSesion,
        avance: Int,
        envioPendiente: Boolean = false,
        envioVencido: Boolean = false,
        ultimaExportacionEn: Long? = null
    ) = SesionListaItem(
        sesionId = id,
        participante = "P-001",
        estado = estado,
        avance = avance,
        envioPendiente = envioPendiente,
        envioVencido = envioVencido,
        ultimaExportacionEn = ultimaExportacionEn
    )

    private companion object {
        const val ID_PAUSADA = "2e14d7a6-3328-42b2-8fbc-087dcab8ee09"
        const val ID_COMPLETA = "b87af552-bf93-4a52-a3c9-892e1767ea77"
    }
}
