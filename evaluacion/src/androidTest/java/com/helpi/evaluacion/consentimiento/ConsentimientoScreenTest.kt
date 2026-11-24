package com.helpi.evaluacion.consentimiento

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsentimientoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun elBotonDeAceptarEstaALaVistaYHabilitadoSinDesplazarElTexto() {
        var aceptado = false

        composeRule.setContent {
            MaterialTheme {
                ConsentimientoScreen(
                    consentimientoVigente = false,
                    onAceptar = { aceptado = true },
                    onVolver = {}
                )
            }
        }

        composeRule.onNodeWithTag("consentAcceptButton")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()
        composeRule.runOnIdle { assertTrue(aceptado) }
    }
}
