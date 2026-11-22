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
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.observation.RecognitionObserver
import com.helpi.conversation.phrase.PhraseResult
import com.helpi.conversation.phrase.RecognizedSign
import com.helpi.conversation.phrase.SyntheticSigns
import com.helpi.conversation.vision.Pointing
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
            language = "lsa",
            modelVersion = "eva-lsa94-v4",
            catalogVersion = "3.0",
            modelSha256 = "",
            catalogSha256 = "",
            numClasses = 94,
            outputsProbabilities = false,
            frames = 40,
            coords = 168,
            contractVersion = 3,
            license = "CC BY-NC-SA 4.0",
            attribution = "LSA64"
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
        assertEquals("La mujer es sorda.", turn.text())
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
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 1_000L, 1_500L)
        coordinator.recognizeForSentence(RecognizedSign(41, "Sordo", 0.95f), 2_000L, 2_500L)

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
        assertEquals("La mujer es sorda.", closed.text())
        assertEquals("MUJER SORDO", closed.glosses())
        assertEquals(building.id(), closed.id())
    }

    @Test
    fun aSignAfterThePauseStartsANewPhrase() {
        startSentenceSession()
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 1_000L, 1_500L)
        coordinator.advanceVisionClock(1_500L + SessionCoordinator.SENTENCE_PAUSE_MS)
        coordinator.recognizeForSentence(RecognizedSign(41, "Sordo", 0.95f), 9_000L, 9_500L)

        val turns = awaitState { it.turns.size == 2 }.turns
        assertEquals(TurnState.FINAL, turns[0].state())
        assertEquals(TurnState.PARTIAL, turns[1].state())
        assertEquals("SORDO", turns[1].text())
    }

    private fun lsePhraseAssets(): PhraseAssets {
        val catalog = SignCatalog.parse(File("src/main/assets/lse/catalogo_senas.json").readText())
        val manifest = ModelBundle.Manifest(
            language = "lse",
            modelVersion = "eva-lse300-v1",
            catalogVersion = "1.0",
            modelSha256 = "",
            catalogSha256 = "",
            numClasses = 299,
            outputsProbabilities = false,
            frames = 40,
            coords = 168,
            contractVersion = 3,
            license = "CC BY 4.0",
            attribution = "SWL-LSE"
        )
        // Un asset opcional que falta, como el contexto de oración en LSE, se lee como null.
        return PhraseAssets.load(manifest, catalog) { path ->
            File("src/main/assets/$path").takeIf { it.exists() }?.readBytes()
        }
    }

    private fun startLseSentenceSession() {
        coordinator.prepare(autoStart = true)
        awaitState { it.session == SessionState.ACTIVA_LIMITADA }
        coordinator.setSignLanguage(SignLanguage.LSE)
        awaitState { it.signLanguage == SignLanguage.LSE }
        coordinator.usePhraseAssets(lsePhraseAssets())
    }

    // ESTOMAGO (141) DOLOR (110) en LSE.
    private fun signLseStomachPain() {
        coordinator.recognizeForSentence(RecognizedSign(141, "Estomago", 0.9f), 1_000L, 1_500L)
        coordinator.recognizeForSentence(RecognizedSign(110, "Dolor", 0.9f), 2_000L, 2_500L)
        coordinator.advanceVisionClock(10_000L)
    }

    @Test
    fun anLseSentenceWaitsForConfirmationBeforeReachingTheChat() {
        startLseSentenceSession()
        signLseStomachPain()

        val waiting = awaitState { it.sentenceCandidate != null }
        assertEquals("Tengo dolor en el estómago.", waiting.sentenceCandidate!!.text)
        assertEquals("ESTOMAGO DOLOR", waiting.sentenceCandidate!!.glosses)
        val turn = waiting.turns.single()
        assertTrue("no se publica sin confirmar", turn.state() != TurnState.FINAL)
        assertEquals(VoiceState.NONE, turn.voiceState())

        coordinator.confirmSentenceCandidate(null)
        val published = awaitState { it.turns.singleOrNull()?.state() == TurnState.FINAL }
        assertNull(published.sentenceCandidate)
        assertEquals("Tengo dolor en el estómago.", published.turns.single().text())
        assertEquals("ESTOMAGO DOLOR", published.turns.single().glosses())
    }

    @Test
    fun anEditedLseSentenceIsPublishedWithTheEditedText() {
        startLseSentenceSession()
        signLseStomachPain()
        awaitState { it.sentenceCandidate != null }

        coordinator.confirmSentenceCandidate("Me duele mucho la panza.")
        val published = awaitState { it.turns.singleOrNull()?.state() == TurnState.FINAL }
        assertEquals("Me duele mucho la panza.", published.turns.single().text())
        assertEquals("ESTOMAGO DOLOR", published.turns.single().glosses())
    }

    @Test
    fun aDiscardedLseSentenceIsNeitherPublishedNorSpoken() {
        startLseSentenceSession()
        signLseStomachPain()
        awaitState { it.sentenceCandidate != null }

        coordinator.rejectSentenceCandidate()
        val after = awaitState { it.sentenceCandidate == null }
        val turn = after.turns.single()
        assertEquals(TurnState.REJECTED, turn.state())
        assertEquals(VoiceState.NONE, turn.voiceState())
    }

    @Test
    fun anLsaSentenceWithAPointedPronounAlsoWaitsForConfirmation() {
        startSentenceSession()
        val yo = pointed(Pointing.Type.YO)!!
        coordinator.recognizeForSentence(yo, 1_000L, 1_500L)
        coordinator.recognizeForSentence(RecognizedSign(60, "Correr", 0.95f), 2_000L, 2_500L)
        coordinator.advanceVisionClock(10_000L)

        val waiting = awaitState { it.sentenceCandidate != null }
        assertEquals("Yo corrí.", waiting.sentenceCandidate!!.text)
        assertEquals("YO CORRER", waiting.sentenceCandidate!!.glosses)
    }

    @Test
    fun headLandmarksKeepOnlyNoseAndMouth() {
        val pose = FloatArray(99) { it.toFloat() }
        val head = SessionCoordinator.headLandmarks(pose)!!
        // pose 0 (nariz), 9 y 10 (comisuras de la boca)
        assertEquals(listOf(0f, 1f, 2f, 27f, 28f, 29f, 30f, 31f, 32f), head.toList())
        assertNull(SessionCoordinator.headLandmarks(null))
    }

    @Test
    fun confirmationRuleDependsOnLanguageAndPointing() {
        val correr = RecognizedSign(60, "Correr", 0.95f)
        val yo = pointed(Pointing.Type.YO)!!
        assertFalse(SessionCoordinator.needsConfirmation(SignLanguage.LSA, listOf(correr)))
        assertTrue(SessionCoordinator.needsConfirmation(SignLanguage.LSA, listOf(yo, correr)))
        assertTrue(SessionCoordinator.needsConfirmation(SignLanguage.LSE, listOf(correr)))
    }

    @Test
    fun theModelWinsOverPointingAndPointingFillsARejectedSegment() {
        val correr = RecognizedSign(60, "Correr", 0.95f)
        val usted = Pointing(Pointing.Type.USTED, 400L)

        assertEquals(correr, SessionCoordinator.segmentSign(correr, usted))

        val pronoun = SessionCoordinator.segmentSign(null, usted)!!
        assertEquals(SyntheticSigns.USTED, pronoun.classIndex())
        assertEquals("USTED", pronoun.gloss())
        assertEquals(SyntheticSigns.YO, pointed(Pointing.Type.YO)?.classIndex())
        assertEquals(SyntheticSigns.EL_ELLA, pointed(Pointing.Type.EL_ELLA)?.classIndex())

        assertEquals(SyntheticSigns.ABDOMEN, pointed(Pointing.Type.ABDOMEN)?.classIndex())
        assertEquals("GARGANTA", pointed(Pointing.Type.GARGANTA)?.gloss())

        assertNull(pointed(Pointing.Type.AMBIGUOUS))
        assertNull(pointed(Pointing.Type.NONE))
    }

    private fun pointed(type: Pointing.Type): RecognizedSign? =
        SessionCoordinator.segmentSign(null, Pointing(type, 400L))

    @Test
    fun aPointedPronounJoinsTheSentence() {
        startSentenceSession()
        val yo = pointed(Pointing.Type.YO)!!
        coordinator.recognizeForSentence(yo, 1_000L, 1_500L)
        coordinator.recognizeForSentence(RecognizedSign(60, "Correr", 0.95f), 2_000L, 2_500L)
        coordinator.advanceVisionClock(10_000L)

        val waiting = awaitState { it.sentenceCandidate != null }
        assertEquals("YO CORRER", waiting.sentenceCandidate!!.glosses)
        coordinator.confirmSentenceCandidate(null)
        val closed = awaitState { it.turns.singleOrNull()?.state() == TurnState.FINAL }
            .turns.single()
        assertEquals("Yo corrí.", closed.text())
        assertEquals("YO CORRER", closed.glosses())
    }

    @Test
    fun anUnrecognizedSignDoesNotEnterTheSentenceAndThePhraseIsSpoken() {
        startSentenceSession()
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 1_000L, 1_500L)
        // rechazada aunque la confianza fuera alta: nunca entra a la oración
        coordinator.recognizeForSentence(null, 2_000L, 2_500L)
        coordinator.recognizeForSentence(RecognizedSign(41, "Sordo", 0.95f), 3_000L, 3_500L)
        coordinator.advanceVisionClock(3_500L + SessionCoordinator.SENTENCE_PAUSE_MS)

        val closed = awaitState { it.turns.singleOrNull()?.state() == TurnState.FINAL }
            .turns.single()
        assertEquals("La mujer es sorda.", closed.text())
        assertEquals("MUJER SORDO", closed.glosses())
        // Robolectric no tiene TTS: se verifica que la voz se ofrecería (sin hueco)
        val result =
            PhraseResult(closed.text(), listOf("MUJER", "SORDO"), listOf("persona-cualidad"))
        assertEquals("La mujer es sorda.", SessionCoordinator.sentenceVoice(result))
    }

    @Test
    fun aFailureExtendsThePauseBeforeClosingTheSentence() {
        startSentenceSession()
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 1_000L, 1_500L)
        coordinator.recognizeForSentence(null, 2_000L, 2_500L)

        coordinator.advanceVisionClock(2_500L + SessionCoordinator.SENTENCE_PAUSE_MS)
        val afterNormalPause = barrier(0.91f).turns.single()
        assertEquals("la falla extiende la pausa", TurnState.PARTIAL, afterNormalPause.state())

        coordinator.advanceVisionClock(
            2_500L + SessionCoordinator.SENTENCE_PAUSE_AFTER_FAILURE_MS - 1
        )
        assertEquals(TurnState.PARTIAL, barrier(0.92f).turns.single().state())

        coordinator.advanceVisionClock(2_500L + SessionCoordinator.SENTENCE_PAUSE_AFTER_FAILURE_MS)
        val closed = awaitState { it.turns.singleOrNull()?.state() == TurnState.FINAL }
            .turns.single()
        assertEquals("MUJER", closed.glosses())
    }

    @Test
    fun aFailureOnAnEmptySentenceLeavesNoDanglingTurn() {
        startSentenceSession()
        coordinator.recognizeForSentence(null, 1_000L, 1_500L)
        coordinator.advanceVisionClock(100_000L)

        val state = barrier(0.93f)
        assertTrue(
            state.turns.none {
                it.state() == TurnState.PENDING ||
                    it.state() == TurnState.PARTIAL
            }
        )
    }

    @Test
    fun pausingTheSessionPublishesTheSentenceInsteadOfLosingIt() {
        startSentenceSession()
        coordinator.recognizeForSentence(RecognizedSign(8, "Mujer", 0.95f), 1_000L, 1_500L)
        coordinator.recognizeForSentence(RecognizedSign(41, "Sordo", 0.95f), 2_000L, 2_500L)
        coordinator.pause()

        val paused = awaitState { it.session == SessionState.PAUSADA }
        assertEquals("La mujer es sorda.", paused.turns.single().text())
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
