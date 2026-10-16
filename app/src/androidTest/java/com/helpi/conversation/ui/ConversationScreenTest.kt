package com.helpi.conversation.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.helpi.conversation.chat.Conversation
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.session.SessionState
import com.helpi.conversation.session.VisualChannelState
import com.helpi.conversation.ui.theme.HelpiTheme
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.SigningDistanceGuide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val VISIBILIDAD_MS = 5_000L

/** Prueba el flujo completo de UI sin depender de una cámara o voz presente en el AVD. */
class ConversationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val chat = Conversation()
    private val state = mutableStateOf(SessionCoordinator.UiState())
    private val participantes = mutableStateOf(Participantes())
    private fun render(active: Boolean = false) {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge(SystemBarStyle.dark(0), SystemBarStyle.dark(0))
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            it.window.decorView.isFocusableInTouchMode = true
            it.window.decorView.requestFocus()
        }
        if (active) {
            state.value = activeState()
            participantes.value = Participantes(interlocutor = "Lucía")
        }
        compose.setContent {
            HelpiTheme {
                Surface(Modifier.fillMaxSize()) {
                    ConversationContent(
                        state.value,
                        ConversationActions(
                            start = { state.value = activeState() },
                            close = { cerrar() },
                            microphone = {
                                state.value = state.value.copy(
                                    microphoneEnabled = !state.value.microphoneEnabled
                                )
                            },
                            camera = {
                                state.value = state.value.copy(
                                    cameraEnabled = !state.value.cameraEnabled
                                )
                            },
                            threshold = {
                                state.value = state.value.copy(confidenceThreshold = it)
                            },
                            send = {
                                val turn = chat.open(Speaker.DEAF, 0)
                                chat.publishTyped(turn.id(), it)
                                state.value = state.value.copy(turns = chat.ordered())
                            },
                            nuevaConversacion = {
                                cerrar()
                                participantes.value = Participantes(solicitarNombre = true)
                            },
                            nombrarInterlocutor = { nombre ->
                                participantes.value = participantes.value.copy(
                                    interlocutor = nombre.trim().ifBlank { SIN_NOMBRE },
                                    solicitarNombre = false
                                )
                            },
                            renombrarCuenta = { nombre ->
                                participantes.value = participantes.value.copy(cuenta = nombre)
                            },
                            cancelarPedidoDeNombre = {
                                participantes.value =
                                    participantes.value.copy(solicitarNombre = false)
                            }
                        ),
                        participantes.value
                    )
                }
            }
        }
        // Cada test arranca sin teclado: el de un test anterior achica la pantalla de CI.
        ocultarTeclado()
    }

    /** Oculta el teclado: en la pantalla de 640x320 de CI tapa casi toda la conversación. */
    private fun ocultarTeclado() {
        compose.activityRule.scenario.onActivity {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
                .hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitForIdle()
    }

    /** Espera a que el nodo quede visible; ocultar el teclado se anima de forma asíncrona. */
    private fun esperarVisible(nodo: SemanticsNodeInteraction) {
        compose.waitUntil(VISIBILIDAD_MS) { runCatching { nodo.assertIsDisplayed() }.isSuccess }
        nodo.assertIsDisplayed()
    }

    /**
     * Espera el nodo de un diálogo: su ventana se abre de forma asíncrona y, hasta
     * que recibe el foco, Compose no la incluye entre las raíces que busca.
     */
    private fun enDialogo(matcher: SemanticsMatcher): SemanticsNodeInteraction {
        compose.waitUntil(VISIBILIDAD_MS) {
            compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1
        }
        return compose.onNode(matcher)
    }

    private fun cerrar() {
        chat.clear()
        state.value = SessionCoordinator.UiState(session = SessionState.CERRADA)
        participantes.value = Participantes()
    }

    private fun activeState() = SessionCoordinator.UiState(
        session = SessionState.ACTIVA,
        capabilities = SessionCoordinator.Capabilities(vision = true, stt = true, tts = true),
        turns = chat.ordered()
    )

    private fun empezarConversacion(nombre: String) {
        compose.onNodeWithText("Iniciar conversación").performClick()
        enDialogo(hasTestTag("nameInput")).performTextInput(nombre)
        enDialogo(hasText("Empezar")).performClick()
    }

    @Test fun inicioSimpleYUnaSolaAccionParaConversar() {
        render()
        compose.onNodeWithText("Iniciar conversación").assertIsDisplayed()
        compose.onNodeWithContentDescription("Escribir mensaje").assertDoesNotExist()
        compose.onNodeWithContentDescription("Apagar cámara").assertDoesNotExist()
        compose.onNodeWithContentDescription("Silenciar micrófono").assertDoesNotExist()
        empezarConversacion("Lucía")
        compose.onNodeWithTag("camera").assertIsDisplayed()
        compose.onNodeWithTag("messages").assertIsDisplayed()
    }

    /** Sin nombre la conversación arranca igual: el dato es opcional, no un peaje. */
    @Test fun omitirElNombreNoImpideConversar() {
        render()
        compose.onNodeWithText("Iniciar conversación").performClick()
        compose.onNodeWithText("Sin nombre").performClick()
        compose.onNodeWithTag("camera").assertIsDisplayed()
        compose.runOnIdle { assertEquals(SIN_NOMBRE, participantes.value.interlocutor) }
    }

    @Test fun escribirYConfigurarNoReemplazaLaConversacion() {
        render(active = true)
        compose.onNodeWithContentDescription("Escribir mensaje").performClick()
        compose.onNodeWithTag("messageInput").performTextInput("Hola, ¿cómo estás?")
        compose.onNodeWithContentDescription("Enviar y leer en voz alta").performClick()
        ocultarTeclado()
        // La atribución llega a la tecnología de asistencia, no solo al color.
        esperarVisible(compose.onNodeWithContentDescription("Lucía: Hola, ¿cómo estás?"))
        compose.onNodeWithTag("camera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Configuración").performClick()
        compose.onNodeWithContentDescription("Umbral de confianza")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.85f) }
        compose.runOnIdle { assertEquals(0.85f, state.value.confidenceThreshold, 0.001f) }
        compose.onNodeWithText("Listo").performClick()
        // Al cerrar la hoja el foco vuelve al campo de texto y el teclado puede reabrirse.
        ocultarTeclado()
        esperarVisible(compose.onNodeWithContentDescription("Lucía: Hola, ¿cómo estás?"))
        compose.onNodeWithTag("camera").assertIsDisplayed()
    }

    @Test fun silenciarYApagarCamaraConservaElHistorial() {
        val turn = chat.open(Speaker.HEARING, 0)
        chat.finalize(turn.id(), "Bien, gracias")
        render(active = true)
        compose.onNodeWithContentDescription("Silenciar micrófono").performClick()
        compose.onNodeWithContentDescription("Activar micrófono").assertIsDisplayed()
        compose.onNodeWithContentDescription("Apagar cámara").performClick()
        compose.onNodeWithContentDescription("Activar cámara").assertIsDisplayed()
        compose.onNodeWithContentDescription("Tiago: Bien, gracias").assertIsDisplayed()
        compose.onNodeWithContentDescription("Activar cámara").performClick()
        compose.onNodeWithContentDescription("Apagar cámara").assertIsDisplayed()
    }

    @Test fun laFraseMuestraDebajoLasGlosasQueLaOriginaron() {
        val turn = chat.open(Speaker.DEAF, 0)
        chat.publishSentence(turn.id(), "La mujer sorda.", "MUJER SORDO")
        render(active = true)
        val frase = compose.onNodeWithText("La mujer sorda.", useUnmergedTree = true)
        val glosas = compose.onNodeWithTag("glosas", useUnmergedTree = true)
        frase.assertIsDisplayed()
        glosas.assertIsDisplayed().assertTextEquals("MUJER SORDO")
        assertTrue(glosas.getBoundsInRoot().top >= frase.getBoundsInRoot().bottom)
        compose.onNodeWithContentDescription("Lucía: La mujer sorda. Señas: MUJER SORDO")
            .assertIsDisplayed()
    }

    @Test fun laOracionEnCursoApareceEnElChatMientrasSeArma() {
        val turn = chat.open(Speaker.DEAF, 0)
        chat.updatePartial(turn.id(), "MUJER SORDO")
        render(active = true)
        compose.onNodeWithText("MUJER SORDO", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Armando la frase…", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun muestraCuandoEstaReconociendoYProcesandoUnaSena() {
        render(active = true)
        compose.runOnIdle {
            state.value = state.value.copy(
                framing = FramingEvaluator.Issue.OK,
                visual = VisualChannelState.CAPTURANDO_SENA
            )
        }
        compose.onNodeWithText("Reconociendo tu seña…").assertIsDisplayed()

        compose.runOnIdle {
            state.value = state.value.copy(visual = VisualChannelState.REARMANDO)
        }
        compose.onNodeWithText("Procesando la seña…").assertIsDisplayed()
    }

    @Test fun muestraGuiaDeDistanciaYTresPrediccionesConPorcentaje() {
        render(active = true)
        compose.runOnIdle {
            state.value = state.value.copy(
                signingDistance = SigningDistanceGuide.State.OPTIMAL,
                lastRecognition = SessionCoordinator.RecognitionFeedback(
                    confidence = 0.74f,
                    threshold = 0.90f,
                    accepted = false,
                    predictions = listOf(
                        SessionCoordinator.RecognitionPrediction("Gracias", 0.74f),
                        SessionCoordinator.RecognitionPrediction("Ayuda", 0.18f),
                        SessionCoordinator.RecognitionPrediction("Aceptar", 0.06f)
                    )
                )
            )
        }

        compose.onNodeWithTag("distanceGuideOverlay", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Distancia adecuada").assertIsDisplayed()
        compose.onNodeWithText("Predicciones sin confirmar").assertIsDisplayed()
        compose.onNodeWithText("1. Gracias").assertIsDisplayed()
        compose.onNodeWithText("74%").assertIsDisplayed()
        compose.onNodeWithText("2. Ayuda").assertIsDisplayed()
        compose.onNodeWithText("18%").assertIsDisplayed()
        compose.onNodeWithText("3. Aceptar").assertIsDisplayed()
        compose.onNodeWithText("6%").assertIsDisplayed()
    }

    @Test fun finalizarSolicitaConfirmacionYLimpiaElInicio() {
        val turn = chat.open(Speaker.HEARING, 0)
        chat.finalize(turn.id(), "Mensaje privado")
        render(active = true)
        compose.onNodeWithContentDescription("Finalizar conversación").performClick()
        enDialogo(hasText("Continuar")).performClick()
        compose.onNodeWithText("Mensaje privado").assertIsDisplayed()
        compose.onNodeWithContentDescription("Finalizar conversación").performClick()
        enDialogo(hasText("Finalizar")).performClick()
        compose.onNodeWithTag("welcome").assertIsDisplayed()
        compose.onNodeWithText("Mensaje privado").assertDoesNotExist()
        empezarConversacion("Ana")
        compose.onNodeWithText("Mensaje privado").assertDoesNotExist()
    }

    /**
     * Empezar de nuevo borra lo anterior y vuelve a preguntar el nombre solo:
     * quien llega después no puede encontrarse con la conversación de otra
     * persona en pantalla.
     */
    @Test fun nuevaConversacionBorraYVuelveAPreguntarElNombre() {
        val turn = chat.open(Speaker.HEARING, 0)
        chat.finalize(turn.id(), "Charla anterior")
        render(active = true)
        compose.onNodeWithContentDescription("Nueva conversación").performClick()
        enDialogo(hasText("Empezar de nuevo")).performClick()
        compose.onNodeWithTag("welcome").assertIsDisplayed()
        compose.onNodeWithText("Charla anterior").assertDoesNotExist()
        compose.onNodeWithTag("nameInput").assertIsDisplayed()
    }

    /**
     * El aviso del sistema llega contraído, pero el texto completo viaja
     * siempre en la descripción accesible: contraer es una decisión visual y
     * no puede sacarle información a un lector de pantalla.
     */
    @Test fun elAvisoDelSistemaSeDespliegaYNuncaEsconde() {
        val aviso = "No reconocí la seña. Volvé a intentarlo. Confianza 41 %, umbral 70 %."
        chat.systemNote(aviso, 0)
        render(active = true)
        val nodo = compose.onNodeWithContentDescription(aviso)
        nodo.assertIsDisplayed()
        nodo.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Contraído")
        )
        nodo.performSemanticsAction(SemanticsActions.OnClick) { it() }
        nodo.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Desplegado")
        )
    }

    /** La atribución de LSA64 tiene que estar en la interfaz, no solo en el repositorio. */
    @Test fun laCuentaMuestraElPerfilYLasLicencias() {
        render()
        compose.onNodeWithText("Cuenta").performClick()
        compose.onNodeWithText("Tiago").assertIsDisplayed()
        // La cuenta se desplaza: en la pantalla de 640x320 de CI el umbral queda abajo.
        compose.onNodeWithContentDescription("Umbral de confianza").performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("LSA64", substring = true).performScrollTo().assertIsDisplayed()
    }

    @Test fun cambiarElNombreDeCuentaSeVeEnLosMensajes() {
        val turn = chat.open(Speaker.HEARING, 0)
        chat.finalize(turn.id(), "Buenos días")
        render(active = true)
        compose.onNodeWithContentDescription("Tiago: Buenos días").assertIsDisplayed()
        compose.runOnIdle {
            participantes.value = participantes.value.copy(cuenta = "Camila")
        }
        compose.onNodeWithContentDescription("Camila: Buenos días").assertIsDisplayed()
    }
}
