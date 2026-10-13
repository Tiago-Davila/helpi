package com.helpi.evaluacion.ui.protocolo

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test

class ReferenciaVideoPlayerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun placeholderSoloSeSolicitaConAccionYNoSeMarcaComoVisto() {
        composeRule.setContent {
            ReferenciaVideoPlayer()
        }

        composeRule.onNodeWithText("El clip no se reproduce automáticamente.").assertIsDisplayed()
        composeRule.onNodeWithTag("referenceVideoAttribution").assertIsDisplayed()
        composeRule.onNodeWithTag("referenceVideoRequestButton").performClick()
        composeRule.onNodeWithText(
            "El clip de referencia no está incluido en esta build. No se marcó como visto."
        ).assertIsDisplayed()
    }
}
