package com.helpi.conversation.session

import android.Manifest
import com.helpi.conversation.chat.Speaker
import com.helpi.conversation.chat.TurnState
import com.helpi.conversation.lsa.ModelBundle
import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.lsa.VisualClassifier
import com.helpi.conversation.lsa.sequence.PhraseCaptureState
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.observation.RecognitionObserver
import com.helpi.conversation.vision.CameraCaptureMetrics
import com.helpi.conversation.vision.LandmarkExtractionMetrics
import com.helpi.conversation.vision.LandmarkFrame
import com.helpi.conversation.vision.SamplingProfile
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
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

/** Recorrido completo de la cámara al chat con landmarks sintéticos y un clasificador falso. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionCoordinatorRecognitionTest {
    private class FakeClassifier : VisualClassifier {
        @Volatile var logit = 20f

        @Volatile var failure: RuntimeException? = null

        @Volatile var gate: CountDownLatch? = null
        val started = CountDownLatch(1)

        override fun classify(inputTensor: FloatArray): FloatArray {
            started.countDown()
            gate?.await(5, TimeUnit.SECONDS)
            failure?.let { throw it }
            return FloatArray(CLASSES).also { it[MUJER] = logit }
        }

        override fun close() = Unit
    }

    private class Recorder : RecognitionObserver {
        val events = CopyOnWriteArrayList<String>()

        override fun onSegmentStarted(startMs: Long) {
            events += "inicio"
        }

        override fun onNoResult(cause: NoResultCause) {
            events += cause.name
        }

        override fun onDecision(decision: SignDecision, thresholdUsed: Float, segmentEndMs: Long) {
            events += if (decision.accepted) "aceptada" else "rechazada"
        }
    }

    private val classifier = FakeClassifier()
    private val recorder = Recorder()
    private lateinit var coordinator: SessionCoordinator
    private var clock = 1_000L

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        shadowOf(app).denyPermissions(Manifest.permission.RECORD_AUDIO)
        app.getSharedPreferences("lengua_senas", 0).edit().clear().commit()
        coordinator = SessionCoordinator(
            app,
            recorder,
            loadBundle = { language: SignLanguage ->
                ModelBundle.load(language) { path ->
                    File("src/main/assets/$path").takeIf { it.exists() }?.readBytes()
                }
            },
            createClassifier = { classifier }
        )
        coordinator.prepare(autoStart = true)
        await("sesión activa") { it.session == SessionState.ACTIVA_LIMITADA }
    }

    @After
    fun tearDown() {
        classifier.gate?.countDown()
        coordinator.shutdown()
    }

    private fun await(what: String, predicate: (SessionUiState) -> Boolean): SessionUiState =
        runBlocking {
            runCatching { withTimeout(5_000) { coordinator.uiState.first(predicate) } }
                .getOrElse { throw AssertionError("no llegó: $what; ${coordinator.uiState.value}") }
        }

    /** Mano con la muñeca en (x, y); los demás puntos quedan alrededor. */
    private fun hand(x: Float, y: Float) = FloatArray(21 * 3) { i ->
        when (i % 3) {
            0 -> x + (i / 3) * 0.002f
            1 -> y - (i / 3) * 0.002f
            else -> 0f
        }
    }

    private val pose = FloatArray(33 * 3) { 0.5f }.also {
        it[11 * 3] = 0.6f
        it[11 * 3 + 1] = 0.4f
        it[12 * 3] = 0.4f
        it[12 * 3 + 1] = 0.4f
    }

    private fun feed(count: Int, width: Int = 960, rightHand: (Int) -> FloatArray?) {
        repeat(count) { i ->
            clock += FRAME_MS
            coordinator.onLandmarks(LandmarkFrame(clock, null, rightHand(i), pose, width, 540))
        }
    }

    private fun rest(count: Int = 25) = feed(count) { hand(0.4f, 0.8f) }

    private fun move(count: Int = 20, width: Int = 960) =
        feed(count, width) { i -> hand(if (i % 2 == 0) 0.40f else 0.44f, 0.5f) }

    private fun hold(count: Int = 25) = feed(count) { hand(0.42f, 0.5f) }

    private fun sign() {
        rest()
        move()
        hold()
    }

    private fun awaitRuns(runs: Long) = await("$runs clasificaciones") {
        it.visionMetrics.classifierRuns == runs
    }

    @Test
    fun aRecognizedSignReachesTheChatAndIsPublishedAfterThePause() {
        sign()
        val state = awaitRuns(1)
        val turn = state.turns.single()
        assertEquals(Speaker.DEAF, turn.speaker())
        assertEquals(TurnState.PARTIAL, turn.state())
        assertEquals("MUJER", turn.text())
        assertEquals(listOf("inicio", "aceptada"), recorder.events)
        assertEquals("Mujer", state.lastRecognition!!.gloss)
        assertEquals(3, state.lastRecognition!!.predictions.size)
        assertEquals(1L, state.visionMetrics.segmentsStarted)
        assertEquals(1L, state.visionMetrics.segmentsCompleted)

        rest(count = 150)
        val closed = await("oración cerrada") {
            it.turns.singleOrNull()?.state() == TurnState.FINAL
        }
        assertEquals("MUJER", closed.turns.single().glosses())
    }

    @Test
    fun twoSignsJoinTheSameSentence() {
        sign()
        awaitRuns(1)
        sign()
        val turns = awaitRuns(2).turns
        assertEquals(1, turns.size)
        assertEquals("MUJER MUJER", turns.single().text())
    }

    @Test
    fun aRejectedSignLeavesANoteAndNoGuess() {
        classifier.logit = 0.5f
        sign()
        val state = awaitRuns(1)
        val visible = state.turns.filter { it.state() != TurnState.REJECTED }
        assertEquals(Speaker.SYSTEM, visible.single().speaker())
        assertTrue(visible.single().text().startsWith("No reconocí la seña."))
        assertTrue(visible.single().text().endsWith("umbral 90 %."))
        assertFalse(state.lastRecognition!!.accepted)
        assertNull(state.lastRecognition!!.gloss)
        assertEquals(listOf("inicio", "rechazada"), recorder.events)
    }

    @Test
    fun aModelFailureIsReportedWithoutLeavingABubble() {
        classifier.failure = IllegalStateException("roto")
        sign()
        val state = awaitRuns(1)
        assertEquals("No se pudo ejecutar el modelo LSA: roto", state.notice)
        assertEquals(TurnState.REJECTED, state.turns.single().state())
        assertEquals(listOf("inicio", "ERROR_MODELO"), recorder.events)
    }

    @Test
    fun aSegmentWithTwoImageSizesIsDiscarded() {
        rest()
        move(count = 10)
        move(count = 10, width = 720)
        feed(count = 25, width = 720) { hand(0.42f, 0.5f) }
        val state = await("nota de orientación") { it.turns.isNotEmpty() }
        assertEquals(
            "Ese intento no se procesó: cambió la orientación de la cámara.",
            state.turns.single().text()
        )
        assertEquals(listOf("inicio", "ORIENTACION_CAMBIO"), recorder.events)
        assertEquals(0L, state.visionMetrics.classifierRuns)
    }

    @Test
    fun losingTheHandsAbortsTheSegmentWithANote() {
        rest()
        move()
        feed(count = 10) { null }
        val state = await("segmento abortado") { it.visionMetrics.segmentsAborted == 1L }
        assertEquals(VisualChannelState.CALIDAD_INSUFICIENTE, state.visual)
        assertTrue(state.turns.single().text().startsWith("No vi bien tus manos."))
        assertEquals(listOf("inicio", "SEGUIMIENTO_PERDIDO"), recorder.events)
    }

    @Test
    fun aSegmentThatNeverEndsIsAborted() {
        rest()
        move(count = 120)
        await("segmento abortado") { it.visionMetrics.segmentsAborted == 1L }
        assertEquals(listOf("inicio", "SEGMENTO_LARGO"), recorder.events)
    }

    @Test
    fun theProtocolReportsDecisionsAndLeavesTheChatEmpty() {
        coordinator.setDeliveryMode(DeliveryMode.OBSERVER_ONLY)
        sign()
        assertTrue(awaitRuns(1).turns.isEmpty())
        assertEquals(listOf("inicio", "aceptada"), recorder.events)
    }

    @Test
    fun aSecondSegmentWhileClassifyingIsNotQueued() {
        classifier.gate = CountDownLatch(1)
        sign()
        assertTrue(classifier.started.await(5, TimeUnit.SECONDS))
        sign()
        val state = await("aviso de intento en curso") { s ->
            s.turns.any { it.text() == "Ese intento no se procesó: había otro en curso." }
        }
        assertEquals(0L, state.visionMetrics.classifierRuns)
        classifier.gate!!.countDown()
        assertTrue(awaitRuns(1).turns.any { it.text() == "MUJER" })
    }

    @Test
    fun aResultThatArrivesAfterTurningTheCameraOffIsDropped() {
        classifier.gate = CountDownLatch(1)
        sign()
        assertTrue(classifier.started.await(5, TimeUnit.SECONDS))
        coordinator.toggleCamera()
        await("cámara apagada") { !it.cameraEnabled }
        classifier.gate!!.countDown()
        val state = awaitRuns(1)
        assertEquals(TurnState.REJECTED, state.turns.single().state())
        assertNull(state.lastRecognition)
        assertEquals(listOf("inicio"), recorder.events)
        assertEquals(VisualChannelState.NO_DISPONIBLE, state.visual)
    }

    @Test
    fun aResultOfAClosedSessionIsDropped() {
        classifier.gate = CountDownLatch(1)
        sign()
        assertTrue(classifier.started.await(5, TimeUnit.SECONDS))
        coordinator.closeSession()
        await("sesión cerrada") { it.session == SessionState.CERRADA }
        classifier.gate!!.countDown()
        coordinator.prepare(autoStart = true)
        val restarted = await("sesión nueva") { it.session == SessionState.ACTIVA_LIMITADA }
        assertTrue(restarted.turns.isEmpty())
        assertEquals(0L, restarted.visionMetrics.classifierRuns)
        assertEquals(listOf("inicio"), recorder.events)
    }

    @Test
    fun samplingFollowsTheSegmenter() {
        rest()
        await("armado") {
            it.samplingProfile == SamplingProfile.ARMED && it.visual == VisualChannelState.ARMADO
        }
        move()
        await("capturando") {
            it.samplingProfile == SamplingProfile.ACTIVE &&
                it.visual == VisualChannelState.CAPTURANDO_SENA
        }
        coordinator.pause()
        val paused = await("pausa") { it.session == SessionState.PAUSADA }
        assertEquals(SamplingProfile.IDLE, paused.samplingProfile)
        assertEquals(0, coordinator.bufferedVisionFrames())
    }

    @Test
    fun thePhraseModeWithoutItsModelExplainsWhyAndDoesNotCapture() {
        coordinator.setRecognitionMode(RecognitionMode.PHRASE_EXPERIMENTAL)
        val state = await("aviso de modo frase") { it.notice != null }
        assertEquals(RecognitionMode.SINGLE_SIGN, state.recognitionMode)
        assertEquals(PhraseCaptureState.UNAVAILABLE, state.phraseCapture)
        assertEquals(state.capabilities.phraseVisionDetail, state.notice)

        coordinator.startPhraseCapture()
        coordinator.finishPhraseCapture()
        coordinator.confirmPhraseCandidate("hola")
        coordinator.rejectPhraseCandidate()
        val after = await("frase descartada") { it.notice == "Frase descartada." }
        assertEquals(PhraseCaptureState.UNAVAILABLE, after.phraseCapture)
        assertTrue(after.turns.isEmpty())

        coordinator.cancelPhraseCapture()
        coordinator.dismissNotice()
        val dismissed = await("sin aviso") { it.notice == null }
        assertEquals(VisualChannelState.BUSCANDO_ENCUADRE, dismissed.visual)
    }

    @Test
    fun aCameraFailureDisablesVisionWithItsReason() {
        coordinator.reportVisionUnavailable("sin cámara")
        val state = await("visión caída") { !it.capabilities.vision }
        assertEquals("sin cámara", state.notice)
        assertEquals("sin cámara", state.capabilities.phraseVisionDetail)
        assertEquals(VisualChannelState.NO_DISPONIBLE, state.visual)
        assertEquals(PhraseCaptureState.UNAVAILABLE, state.phraseCapture)
        sign()
        coordinator.submitTyped("hola")
        assertEquals("hola", await("texto") { it.turns.isNotEmpty() }.turns.single().text())
    }

    @Test
    fun telemetryKeepsCameraAndExtractorCountersApart() {
        coordinator.onCameraMetrics(CameraCaptureMetrics(SamplingProfile.ACTIVE, 24, 23.5f, 10, 2))
        coordinator.onLandmarkMetrics(LandmarkExtractionMetrics(20f, 9, 8, 7, 6, 5, 31))
        val metrics = await("telemetría") { it.visionMetrics.landmarkFrames == 8L }.visionMetrics
        assertEquals(24, metrics.targetFps)
        assertEquals(23.5f, metrics.cameraFps)
        assertEquals(10L, metrics.analyzedFrames)
        assertEquals(2L, metrics.rateLimitedFrames)
        assertEquals(20f, metrics.mediaPipeFps)
        assertEquals(7L, metrics.poseFrames)
        assertEquals(6L, metrics.leftHandFrames)
        assertEquals(5L, metrics.rightHandFrames)
        assertEquals(31L, metrics.lastMediaPipeLatencyMs)
    }

    @Test
    fun markingAMessageIncorrectLeavesTheSystemNote() {
        coordinator.submitTyped("hola")
        val typed = await("texto") { it.turns.size == 1 }.turns.single()
        coordinator.repeatTurn(typed.id())
        coordinator.toggleMicrophone()
        coordinator.markIncorrect(typed.id())
        val state = await("corrección") { it.turns.size == 2 }
        assertTrue(state.turns.first { it.id() == typed.id() }.markedIncorrect())
        assertEquals(
            "La traducción anterior fue marcada como incorrecta",
            state.turns.first { it.speaker() == Speaker.SYSTEM }.text()
        )
        assertTrue("sin micrófono no hay nada que apagar", state.microphoneEnabled)
    }

    private companion object {
        const val CLASSES = 94
        const val MUJER = 8
        const val FRAME_MS = 40L
    }
}
