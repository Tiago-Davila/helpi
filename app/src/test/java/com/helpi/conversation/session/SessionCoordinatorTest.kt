package com.helpi.conversation.session

import android.Manifest
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.TurnState
import com.helpi.conversation.chat.VoiceState
import com.helpi.conversation.lsa.ModelBundle
import com.helpi.conversation.lsa.PhraseAssets
import com.helpi.conversation.lsa.SignAcceptancePolicy
import com.helpi.conversation.lsa.SignCatalog
import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.observation.RecognitionObserver
import com.helpi.conversation.phrase.PhraseResult
import com.helpi.conversation.phrase.RecognizedSign
import java.io.File
import java.time.Duration
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSystemClock

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionCoordinatorTest {
    private lateinit var coordinator: SessionCoordinator

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).denyPermissions(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        coordinator = SessionCoordinator(app)
    }

    @After
    fun tearDown() {
        coordinator.shutdown()
    }

    private fun awaitState(
        predicate: (SessionCoordinator.UiState) -> Boolean
    ): SessionCoordinator.UiState =
        runBlocking { withTimeout(5000) { coordinator.uiState.first(predicate) } }

    @Test
    fun deniedSensorsStillAllowKeyboardAndLifecycle() {
        coordinator.prepare(autoStart = true)
        val active = awaitState { it.session == SessionState.ACTIVA_LIMITADA }
        assertTrue(active.capabilities.keyboard)
        assertFalse(active.capabilities.vision)
        assertFalse(active.capabilities.stt)
        coordinator.submitTyped("  Hola  ")
        val typed = awaitState { it.turns.size == 1 }
        assertEquals("Hola", typed.turns.single().text())
        assertEquals(VoiceState.NOT_SPOKEN, typed.turns.single().voiceState())
        coordinator.pause()
        awaitState { it.session == SessionState.PAUSADA }
        coordinator.resume()
        awaitState { it.session == SessionState.ACTIVA_LIMITADA }
        coordinator.closeSession()
        val closed = awaitState { it.session == SessionState.CERRADA }
        assertTrue(closed.turns.isEmpty())
        coordinator.prepare(autoStart = true)
        val restarted = awaitState { it.session == SessionState.ACTIVA_LIMITADA }
        assertTrue(restarted.turns.isEmpty())
    }

    private fun realPhraseAssets(): PhraseAssets {
        val dir = File("src/main/assets/lsa")
        val catalog = SignCatalog.parse(File(dir, "catalogo_senas.json").readText())
        val manifest = ModelBundle.Manifest(
            modelVersion = "eva-lsa64-v3",
            catalogVersion = "2.0",
            modelSha256 = "",
            catalogSha256 = "",
            numClasses = 64,
            outputsProbabilities = false,
            frames = 40,
            coords = 168,
            contractVersion = 3
        )
        return PhraseAssets.load(manifest, catalog) { path ->
            File(dir, path.removePrefix("lsa/")).readBytes()
        }
    }

    @Test
    fun closedSentenceIsPublishedAsPhraseWithItsGlosses() {
        coordinator.prepare(autoStart = true)
        awaitState { it.session == SessionState.ACTIVA_LIMITADA }
        coordinator.usePhraseAssets(realPhraseAssets())
        coordinator.publishSentence(
            listOf(RecognizedSign(8, "Mujer", 0.95f), RecognizedSign(41, "Sordo", 0.95f)),
            100L
        )
        val turn = awaitState { it.turns.size == 1 }.turns.single()
        assertEquals(Speaker.DEAF, turn.speaker())
        assertEquals("La mujer sorda.", turn.text())
        assertEquals("MUJER SORDO", turn.glosses())
        // sin voz instalada en Robolectric: se muestra pero no se pronuncia
        assertEquals(VoiceState.NOT_SPOKEN, turn.voiceState())
    }

    @Test
    fun withoutModelTheSentenceShowsLooseGlossesAndGaps() {
        coordinator.prepare(autoStart = true)
        awaitState { it.session == SessionState.ACTIVA_LIMITADA }
        coordinator.publishSentence(
            listOf(RecognizedSign(8, "Mujer", 0.95f), RecognizedSign(41, "Sordo", 0.2f)),
            100L
        )
        val turn = awaitState { it.turns.size == 1 }.turns.single()
        assertEquals("MUJER ¿?", turn.text())
        assertEquals("MUJER ¿?", turn.glosses())
    }

    private fun startSentenceSession() {
        coordinator.prepare(autoStart = true)
        awaitState { it.session == SessionState.ACTIVA_LIMITADA }
        coordinator.usePhraseAssets(realPhraseAssets())
    }

    /** El executor es serial: cuando aparece este umbral, todo lo anterior ya corrió. */
    private fun barrier(threshold: Float): SessionCoordinator.UiState {
        coordinator.setConfidenceThreshold(threshold)
        return awaitState { it.confidenceThreshold == threshold }
    }

    @Test
    fun recognizedSignsReachTheChatAsOnePhraseAfterThePause() {
        startSentenceSession()
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 8, 1_000L, 1_500L)
        coordinator.recognizeForSentence(RecognizedSign(41, "Sordo", 0.95f), 41, 2_000L, 2_500L)

        val building = awaitState { it.turns.singleOrNull()?.text() == "MUJER SORDO" }
            .turns.single()
        assertEquals(Speaker.DEAF, building.speaker())
        assertEquals(TurnState.PARTIAL, building.state())
        assertEquals("sin voz mientras se arma", VoiceState.NONE, building.voiceState())

        coordinator.advanceVisionClock(2_500L + SessionCoordinator.SENTENCE_PAUSE_MS - 1)
        val beforePause = barrier(0.91f).turns.single()
        assertEquals("todavía no pasó la pausa", TurnState.PARTIAL, beforePause.state())

        coordinator.advanceVisionClock(2_500L + SessionCoordinator.SENTENCE_PAUSE_MS)
        val closed = awaitState { it.turns.singleOrNull()?.state() == TurnState.FINAL }
            .turns.single()
        assertEquals("La mujer sorda.", closed.text())
        assertEquals("MUJER SORDO", closed.glosses())
        assertEquals(building.id(), closed.id())
    }

    @Test
    fun aSignAfterThePauseStartsANewPhrase() {
        startSentenceSession()
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 8, 1_000L, 1_500L)
        coordinator.advanceVisionClock(1_500L + SessionCoordinator.SENTENCE_PAUSE_MS)
        coordinator.recognizeForSentence(RecognizedSign(41, "Sordo", 0.95f), 41, 9_000L, 9_500L)

        val turns = awaitState { it.turns.size == 2 }.turns
        assertEquals(TurnState.FINAL, turns[0].state())
        assertEquals(TurnState.PARTIAL, turns[1].state())
        assertEquals("SORDO", turns[1].text())
    }

    @Test
    fun anUnrecognizedSignIsAGapAndThePhraseIsNotSpoken() {
        startSentenceSession()
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 8, 1_000L, 1_500L)
        // rechazada aunque la confianza fuera alta: nunca se muestra la adivinanza
        coordinator.recognizeForSentence(null, 41, 2_000L, 2_500L)
        coordinator.advanceVisionClock(10_000L)

        val closed = awaitState { it.turns.singleOrNull()?.state() == TurnState.FINAL }
            .turns.single()
        assertEquals("MUJER ¿?", closed.text())
        assertEquals(VoiceState.NOT_SPOKEN, closed.voiceState())
    }

    @Test
    fun pausingTheSessionPublishesTheSentenceInsteadOfLosingIt() {
        startSentenceSession()
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 8, 1_000L, 1_500L)
        coordinator.recognizeForSentence(RecognizedSign(41, "Sordo", 0.95f), 41, 2_000L, 2_500L)
        coordinator.pause()

        val paused = awaitState { it.session == SessionState.PAUSADA }
        assertEquals("La mujer sorda.", paused.turns.single().text())
        assertEquals(TurnState.FINAL, paused.turns.single().state())
    }

    @Test
    fun voiceSaysThePhraseAndStaysSilentWithGaps() {
        val full = PhraseResult(
            "La mujer sorda.",
            listOf("MUJER", "SORDO"),
            listOf("persona-cualidad")
        )
        assertEquals("La mujer sorda.", SessionCoordinator.sentenceVoice(full))

        val withGap = PhraseResult("MUJER ¿?", listOf("MUJER", "¿?"), emptyList())
        assertNull(SessionCoordinator.sentenceVoice(withGap))
    }

    @Test
    fun expiredPauseDiscardsHistoryAndDoesNotResumeCapture() {
        coordinator.prepare(autoStart = true)
        awaitState { it.session == SessionState.ACTIVA_LIMITADA }
        coordinator.submitTyped("Mensaje temporal")
        awaitState { it.turns.size == 1 }
        coordinator.pause()
        awaitState { it.session == SessionState.PAUSADA }
        ShadowSystemClock.advanceBy(Duration.ofMinutes(3))
        coordinator.resume()
        val closed = awaitState { it.session == SessionState.CERRADA }
        assertTrue(closed.turns.isEmpty())
        assertFalse(closed.capabilities.vision)
    }

    @Test
    fun observerOnlyReportsRecognitionEventsWithoutConversationDelivery() {
        val observer = FakeRecognitionObserver()
        coordinator.shutdown()
        coordinator = SessionCoordinator(
            RuntimeEnvironment.getApplication(),
            observer,
            SessionCoordinator.DeliveryMode.OBSERVER_ONLY
        )

        coordinator.reportSegmentStarted(123L)
        coordinator.reportNoResult(NoResultCause.SEGMENTO_CORTO)
        val decision = SignAcceptancePolicy(0.5f, true, 2).evaluate(floatArrayOf(0.9f, 0.1f))
        var conversationDelivery = false
        coordinator.dispatchDecision(decision, 0.5f, 456L) { conversationDelivery = true }

        assertEquals(listOf(123L), observer.segmentStarts)
        assertEquals(listOf(NoResultCause.SEGMENTO_CORTO), observer.noResults)
        assertEquals(decision, observer.decisions.single().decision)
        assertEquals(0.5f, observer.decisions.single().thresholdUsed)
        assertEquals(456L, observer.decisions.single().segmentEndMs)
        assertFalse(conversationDelivery)
        assertTrue(coordinator.uiState.value.turns.isEmpty())
    }

    @Test
    fun conversationDeliveryModeRunsTheNormalDeliveryBlock() {
        val decision = SignAcceptancePolicy(0.5f, true, 2).evaluate(floatArrayOf(0.9f, 0.1f))
        var conversationDelivery = false

        coordinator.dispatchDecision(decision, 0.5f, 456L) { conversationDelivery = true }

        assertTrue(conversationDelivery)
    }

    private class FakeRecognitionObserver : RecognitionObserver {
        data class DecisionEvent(
            val decision: SignDecision,
            val thresholdUsed: Float,
            val segmentEndMs: Long
        )

        val segmentStarts = mutableListOf<Long>()
        val noResults = mutableListOf<NoResultCause>()
        val decisions = mutableListOf<DecisionEvent>()

        override fun onSegmentStarted(startMs: Long) {
            segmentStarts += startMs
        }

        override fun onNoResult(cause: NoResultCause) {
            noResults += cause
        }

        override fun onDecision(decision: SignDecision, thresholdUsed: Float, segmentEndMs: Long) {
            decisions += DecisionEvent(decision, thresholdUsed, segmentEndMs)
        }
    }
}
