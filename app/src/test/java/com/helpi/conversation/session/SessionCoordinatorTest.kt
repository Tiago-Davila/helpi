package com.helpi.conversation.session

import android.Manifest
import com.helpi.conversation.chat.VoiceState
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
}
