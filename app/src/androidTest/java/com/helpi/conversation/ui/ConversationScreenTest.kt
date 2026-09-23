package com.helpi.conversation.ui

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.helpi.conversation.chat.Conversation
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.session.SessionState
import com.helpi.conversation.session.VisualChannelState
import com.helpi.conversation.ui.theme.HelpiTheme
import com.helpi.conversation.vision.FramingEvaluator
import com.helpi.conversation.vision.SigningDistanceGuide
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Prueba el flujo completo de UI sin depender de una cámara o voz presente en el AVD. */
class ConversationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val chat = Conversation()
    private val state = mutableStateOf(SessionCoordinator.UiState())
    private val participantes = mutableStateOf(Participantes())

    private fun render(active: Boolean = false) {
        compose.activityRule.scenario.onActivity {
            it.enableEdgeToEdge()
            it.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        if (active) {
            state.value = activeState()
            participantes.value = Participantes(interlocutor = "Lucía")
        }
        compose.setContent {
            HelpiTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    ConversationContent(
                        state.value,
                        ConversationActions(
                            start = { state.value = activeState() },
                            close = { cerrar() },
                            microphone = {
                                state.value = state.value.copy(
                                    microphoneEnabled = !state.value.microphoneEnabled,
                                )
                            },
                            camera = {
                                state.value = state.value.copy(
                                    cameraEnabled = !state.value.cameraEnabled,
                                )
                            },
                            threshold = { state.value = state.value.copy(confidenceThreshold = it) },
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
                                    solicitarNombre = false,
                                )
                            },
                            renombrarCuenta = { nombre ->
                                participantes.value = participantes.value.copy(cuenta = nombre)
                            },
                            cancelarPedidoDeNombre = {
                                participantes.value =
                                    participantes.value.copy(solicitarNombre = false)
                            },
                        ),
                        participantes.value,
                    )
                }
            }
        }
    }

    private fun cerrar() {
        chat.clear()
        state.value = SessionCoordinator.UiState(session = SessionState.CERRADA)
        participantes.value = Participantes()
    }

    private fun activeState() = SessionCoordinator.UiState(
        session = SessionState.ACTIVA,
        capabilities = SessionCoordinator.Capabilities(vision = true, stt = true, tts = true),
        turns = chat.ordered(),
    )

    private fun empezarConversacion(nombre: String) {
        compose.onNodeWithText("Iniciar conversación").performClick()
        compose.onNodeWithTag("nameInput").performTextInput(nombre)
        compose.onNodeWithText("Empezar").performClick()
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
        // La atribución llega a la tecnología de asistencia, no solo al color.
        compose.onNodeWithContentDescription("Lucía: Hola, ¿cómo estás?").assertIsDisplayed()
        compose.onNodeWithTag("camera").assertIsDisplayed()
        compose.onNodeWithContentDescription("Configuración").performClick()
        compose.onNodeWithContentDescription("Umbral de confianza")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0.85f) }
        compose.runOnIdle { assertEquals(0.85f, state.value.confidenceThreshold, 0.001f) }
        compose.onNodeWithText("Listo").performClick()
        compose.onNodeWithContentDescription("Lucía: Hola, ¿cómo estás?").assertIsDisplayed()
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

    @Test fun muestraCuandoEstaReconociendoYProcesandoUnaSena() {
        render(active = true)
        compose.runOnIdle {
            state.value = state.value.copy(
                framing = FramingEvaluator.Issue.OK,
                visual = VisualChannelState.CAPTURANDO_SENA,
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
                        SessionCoordinator.RecognitionPrediction("Aceptar", 0.06f),
                    ),
                ),
            )
        }

        compose.onNodeWithTag("distanceGuideOverlay").assertIsDisplayed()
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
        compose.onNodeWithText("Continuar").performClick()
        compose.onNodeWithText("Mensaje privado").assertIsDisplayed()
        compose.onNodeWithContentDescription("Finalizar conversación").performClick()
        compose.onNodeWithText("Finalizar", substring = false).performClick()
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
        compose.onNodeWithText("Empezar de nuevo").performClick()
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
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Contraído"),
        )
        nodo.performClick()
        nodo.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Desplegado"),
        )
    }

    /** La atribución de LSA64 tiene que estar en la interfaz, no solo en el repositorio. */
    @Test fun laCuentaMuestraElPerfilYLasLicencias() {
        render()
        compose.onNodeWithText("Cuenta").performClick()
        compose.onNodeWithText("Tiago").assertIsDisplayed()
        compose.onNodeWithContentDescription("Umbral de confianza").assertIsDisplayed()
        compose.onNodeWithText("LSA64", substring = true).assertIsDisplayed()
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
