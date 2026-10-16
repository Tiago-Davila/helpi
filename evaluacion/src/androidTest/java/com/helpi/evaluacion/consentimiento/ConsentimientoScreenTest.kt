package com.helpi.evaluacion.consentimiento

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConsentimientoScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aceptarNoExigeReproducirElVideoDePrueba() {
        val instrumentacion = InstrumentationRegistry.getInstrumentation()
        val archivo = File(instrumentacion.targetContext.cacheDir, "aviso-ui-test.mp4")
        instrumentacion.context.assets.open("evaluacion/aviso/aviso-v1-test.mp4").use { entrada ->
            archivo.outputStream().use(entrada::copyTo)
        }
        val video = AvisoVideoAsset(archivo, requireNotNull(AvisoVideoAssetLoader.hash(archivo)))
        var aceptado = false

        composeRule.setContent {
            MaterialTheme {
                ConsentimientoScreen(
                    video = video,
                    consentimientoVigente = false,
                    onAceptar = { aceptado = true },
                    onVolver = {}
                )
            }
        }

        composeRule.onNodeWithTag("consentPlayVideoButton").assertIsDisplayed()
        composeRule.onNodeWithTag("consentAcceptButton").performScrollTo().performClick()
        composeRule.runOnIdle { assertTrue(aceptado) }
        archivo.delete()
    }
}
