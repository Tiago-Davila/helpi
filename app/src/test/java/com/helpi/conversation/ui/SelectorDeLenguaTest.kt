package com.helpi.conversation.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.session.SessionCapabilities
import com.helpi.conversation.session.SessionState
import com.helpi.conversation.session.SessionUiState
import com.helpi.conversation.ui.theme.HelpiTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w800dp-h480dp-land")
class SelectorDeLenguaTest {
    @get:Rule val compose = createComposeRule()

    private val state = mutableStateOf(SessionUiState())
    private val elegidas = mutableListOf<SignLanguage>()

    private fun render(active: Boolean = false) {
        if (active) {
            state.value = state.value.copy(
                session = SessionState.ACTIVA,
                capabilities = SessionCapabilities(vision = true)
            )
        }
        compose.setContent {
            HelpiTheme {
                ConversationContent(
                    state.value,
                    ConversationActions(
                        signLanguage = {
                            elegidas += it
                            state.value = state.value.copy(signLanguage = it)
                        }
                    ),
                    Participantes(interlocutor = "Lucía")
                )
            }
        }
    }

    private fun abrirCuenta() {
        compose.onNodeWithText("Cuenta").performClick()
    }

    @Test
    fun elegirLseDesdeCuentaCambiaLaLenguaYSuVocabulario() {
        render()
        abrirCuenta()
        compose.onNodeWithTag("lengua_lsa").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("lengua_lse").performScrollTo().performClick()

        assertEquals(listOf(SignLanguage.LSE), elegidas)
        compose.onNodeWithTag("lengua_lse").assertIsSelected()
        compose.onNodeWithTag("lengua_lsa").assertIsNotSelected()
        compose.onNodeWithText("Qué reconoce Helpi en LSE").performScrollTo()
        compose.onNodeWithText("ámbito de la salud", substring = true).performScrollTo()
    }

    @Test
    fun cadaOpcionSeAnunciaComoBotonDeOpcionParaTalkBack() {
        render()
        abrirCuenta()
        SignLanguage.values().forEach {
            compose.onNodeWithTag("lengua_${it.code()}")
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        }
        compose.onNodeWithText("LSE · España", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("LSA · Argentina", useUnmergedTree = true).assertExists()
    }

    @Test
    fun laAtribucionDeCadaLenguaFiguraEnCuenta() {
        render()
        abrirCuenta()
        compose.onNodeWithTag("credito_lsa").performScrollTo()
            .assert(textoContiene("LSA64: Ronchetti et al."))
            .assert(textoContiene("CC BY-NC-SA 4.0"))
        compose.onNodeWithTag("credito_lse").performScrollTo()
            .assert(textoContiene("SWL-LSE"))
            .assert(textoContiene("CC BY 4.0"))
    }

    @Test
    fun unaLenguaSinModeloLoAvisa() {
        compose.setContent {
            HelpiTheme {
                PantallaCuenta(
                    state.value.copy(signLanguage = SignLanguage.LSE),
                    Participantes(interlocutor = "Lucía"),
                    ConversationActions(),
                    creditos = creditosDeLenguas { null }
                )
            }
        }
        compose.onNodeWithText("todavía no trae el modelo de LSE", substring = true)
            .performScrollTo()
    }

    @Test
    fun laLenguaActivaSeVeSobreLaCamaraSinAbrirMenus() {
        state.value = state.value.copy(signLanguage = SignLanguage.LSE)
        render(active = true)
        compose.onNodeWithTag("activeSignLanguage")
            .assertContentDescriptionEquals(
                "Lengua de señas activa: LSE · España. Las frases son experimentales."
            )
    }

    @Test
    fun laLenguaActivaQuedaArribaDelOrbeSinTaparlo() {
        listOf(SignLanguage.LSA, SignLanguage.LSE).forEach { lengua ->
            state.value = state.value.copy(signLanguage = lengua)
            if (lengua == SignLanguage.LSA) render(active = true)
            compose.waitForIdle()
            val etiqueta = compose.onNodeWithTag("activeSignLanguage").getUnclippedBoundsInRoot()
            val orbe = compose.onNodeWithTag("evaOrb").getUnclippedBoundsInRoot()
            assertTrue(
                "$lengua: etiqueta $etiqueta, orbe $orbe",
                etiqueta.bottom <= orbe.top && etiqueta.top >= 0.dp
            )
            assertEquals(orbe.left, etiqueta.left)
        }
    }

    private fun textoContiene(texto: String) = SemanticsMatcher("texto contiene «$texto»") { node ->
        node.config.getOrNull(SemanticsProperties.Text).orEmpty()
            .any { it.text.contains(texto) }
    }
}
