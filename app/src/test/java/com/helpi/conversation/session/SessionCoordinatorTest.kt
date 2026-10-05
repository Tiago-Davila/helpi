package com.helpi.conversation.session

import android.Manifest
import com.helpi.conversation.chat.VoiceState
import com.helpi.conversation.lsa.SignAcceptancePolicy
import com.helpi.conversation.lsa.SignDecision
import com.helpi.conversation.observation.NoResultCause
import com.helpi.conversation.observation.RecognitionObserver
import java.time.Duration
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
