package com.helpi.conversation.session

import android.Manifest
import android.content.Context
import com.helpi.conversation.lsa.ModelBundle
import com.helpi.conversation.lsa.ModelBundleResult
import com.helpi.conversation.lsa.SignLanguage
import com.helpi.conversation.lsa.SignLanguageStore
import com.helpi.conversation.lsa.VisualClassifier
import com.helpi.conversation.vision.LandmarkFrame
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SessionCoordinatorLanguageTest {
    /** Clasificador sin LiteRT: registra para qué lengua se creó y cuándo se cerró. */
    private class FakeClassifier(val language: String) : VisualClassifier {
        val closed = CountDownLatch(1)
        override fun classify(inputTensor: FloatArray): FloatArray = FloatArray(1)
        override fun close() = closed.countDown()
    }

    private lateinit var context: Context
    private lateinit var coordinator: SessionCoordinator
    private val created = CopyOnWriteArrayList<FakeClassifier>()
    private val invalid = mutableSetOf<SignLanguage>()

    private fun load(language: SignLanguage): ModelBundleResult {
        if (language in invalid) return ModelBundleResult.Invalid("artefactos de prueba inválidos")
        return ModelBundle.load(language) { path ->
            File("src/main/assets/$path").takeIf { it.exists() }?.readBytes()
        }
    }

    private fun newCoordinator() = SessionCoordinator(
        context,
        languageStore = SignLanguageStore(context),
        loadBundle = ::load,
        createClassifier = { bundle ->
            FakeClassifier(bundle.manifest.language).also { created += it }
        }
    )

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        shadowOf(RuntimeEnvironment.getApplication())
            .denyPermissions(Manifest.permission.RECORD_AUDIO)
        context.getSharedPreferences("lengua_senas", Context.MODE_PRIVATE).edit().clear().commit()
        coordinator = newCoordinator()
    }

    @After
    fun tearDown() {
        coordinator.shutdown()
    }

    private fun awaitState(
        predicate: (SessionCoordinator.UiState) -> Boolean
    ): SessionCoordinator.UiState =
        runBlocking { withTimeout(5000) { coordinator.uiState.first(predicate) } }

    private fun startWithVision(): SessionCoordinator.UiState {
        coordinator.prepare(autoStart = true)
        return awaitState { it.session == SessionState.ACTIVA_LIMITADA }
    }

    private fun feedFrames(count: Int) {
        val pose = FloatArray(33 * 3) { 0.5f }
        repeat(count) { i ->
            coordinator.onLandmarks(LandmarkFrame(i * 40L, null, null, pose, 960, 540))
        }
    }

    @Test
    fun lsaIsTheInitialLanguageWithItsThreshold() {
        val state = startWithVision()
        assertEquals(SignLanguage.LSA, state.signLanguage)
        assertEquals(0.90f, state.confidenceThreshold)
        assertTrue(state.capabilities.vision)
        assertEquals("lsa", created.single().language)
    }

    @Test
    fun switchingLanguageReleasesTheInterpreterAndLoadsTheNewModel() {
        startWithVision()
        coordinator.setSignLanguage(SignLanguage.LSE)
        val state = awaitState { it.signLanguage == SignLanguage.LSE }
        assertTrue(created.first().closed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("lsa", "lse"), created.map { it.language })
        assertEquals(1L, created.last().closed.count)
        assertTrue(state.capabilities.vision)
        assertEquals(0.50f, state.confidenceThreshold)
    }

    @Test
    fun switchingLanguageDiscardsTheWindowInProgress() {
        startWithVision()
        feedFrames(5)
        assertTrue(coordinator.bufferedVisionFrames() > 0)
        coordinator.setSignLanguage(SignLanguage.LSE)
        awaitState { it.signLanguage == SignLanguage.LSE }
        assertEquals(0, coordinator.bufferedVisionFrames())
    }

    @Test
    fun invalidModelDisablesVisionInsteadOfFallingBackToLsa() {
        invalid += SignLanguage.LSE
        startWithVision()
        coordinator.setSignLanguage(SignLanguage.LSE)
        val state = awaitState { it.signLanguage == SignLanguage.LSE }
        assertTrue(created.single().closed.await(5, TimeUnit.SECONDS))
        assertFalse(state.capabilities.vision)
        assertEquals("artefactos de prueba inválidos", state.capabilities.visionDetail)
        assertEquals(VisualChannelState.NO_DISPONIBLE, state.visual)
    }

    @Test
    fun languageWithoutModelIsReportedAsMissing() {
        startWithVision()
        coordinator.setSignLanguage(SignLanguage.ASL)
        val state = awaitState { it.signLanguage == SignLanguage.ASL }
        assertFalse(state.capabilities.vision)
        assertEquals("falta asl/manifest.json", state.capabilities.visionDetail)
    }

    @Test
    fun eachLanguageKeepsItsOwnThreshold() {
        startWithVision()
        coordinator.setConfidenceThreshold(0.75f)
        awaitState { it.confidenceThreshold == 0.75f }
        coordinator.setSignLanguage(SignLanguage.LSE)
        assertEquals(0.50f, awaitState { it.signLanguage == SignLanguage.LSE }.confidenceThreshold)
        coordinator.setSignLanguage(SignLanguage.LSA)
        assertEquals(0.75f, awaitState { it.signLanguage == SignLanguage.LSA }.confidenceThreshold)
    }

    @Test
    fun choiceSurvivesANewCoordinator() {
        coordinator.setSignLanguage(SignLanguage.LSE)
        awaitState { it.signLanguage == SignLanguage.LSE }
        coordinator.shutdown()
        coordinator = newCoordinator()
        assertEquals(SignLanguage.LSE, coordinator.uiState.value.signLanguage)
        val state = startWithVision()
        assertEquals(SignLanguage.LSE, state.signLanguage)
        assertEquals("lse", created.last().language)
    }
}
