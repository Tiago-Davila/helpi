package com.helpi.conversation.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.helpi.conversation.lsa.sequence.RecognitionMode
import com.helpi.conversation.session.SessionCoordinator
import com.helpi.conversation.vision.CameraCaptureMetrics
import com.helpi.conversation.vision.LandmarkExtractionMetrics
import com.helpi.conversation.vision.LandmarkFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Expone la sesión a Compose. Sobrevive rotaciones sin serializar la
 * conversación: el historial vive solo en memoria del proceso.
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    val coordinator = SessionCoordinator(application)
    val uiState = coordinator.uiState

    private val _participantes = MutableStateFlow(Participantes())
    internal val participantes: StateFlow<Participantes> = _participantes.asStateFlow()

    private val _landmarks = MutableStateFlow<LandmarkFrame?>(null)
    internal val landmarks: StateFlow<LandmarkFrame?> = _landmarks.asStateFlow()

    fun prepare() = coordinator.prepare(autoStart = true)
    fun toggleMicrophone() = coordinator.toggleMicrophone()
    fun toggleCamera() {
        if (uiState.value.cameraEnabled) clearLandmarks()
        coordinator.toggleCamera()
    }
    fun dismissNotice() = coordinator.dismissNotice()
    fun start() = coordinator.startConversation()
    fun pause() {
        clearLandmarks()
        coordinator.pause()
    }
    fun resume() = coordinator.resume()
    fun markIncorrect(turnId: Long) = coordinator.markIncorrect(turnId)
    fun setConfidenceThreshold(value: Float) = coordinator.setConfidenceThreshold(value)
    fun setRecognitionMode(mode: RecognitionMode) = coordinator.setRecognitionMode(mode)
    fun startPhraseCapture() = coordinator.startPhraseCapture()
    fun finishPhraseCapture() = coordinator.finishPhraseCapture()
    fun cancelPhraseCapture() = coordinator.cancelPhraseCapture()
    fun confirmPhraseCandidate(text: String? = null) = coordinator.confirmPhraseCandidate(text)
    fun rejectPhraseCandidate() = coordinator.rejectPhraseCandidate()
    fun submitTyped(text: String) = coordinator.submitTyped(text)
    fun repeatTurn(turnId: Long) = coordinator.repeatTurn(turnId)

    /** Publica el mismo resultado crudo tanto al overlay como al pipeline de inferencia. */
    fun onLandmarks(frame: LandmarkFrame) {
        _landmarks.value = frame
        coordinator.onLandmarks(frame)
    }

    fun onCameraMetrics(metrics: CameraCaptureMetrics) = coordinator.onCameraMetrics(metrics)
    fun onLandmarkMetrics(metrics: LandmarkExtractionMetrics) = coordinator.onLandmarkMetrics(metrics)

    fun clearLandmarks() {
        _landmarks.value = null
    }

    fun closeSession() {
        clearLandmarks()
        coordinator.closeSession()
        _participantes.update { it.copy(interlocutor = SIN_NOMBRE, solicitarNombre = false) }
    }

    /**
     * Terminar con una persona y seguir con otra. Cierra la sesión igual que
     * finalizar —los mensajes anteriores no pueden quedar a la vista de quien
     * llega después— pero deja pedido el nombre nuevo, así el camino de vuelta
     * a la conversación es directo.
     */
    fun nuevaConversacion() {
        clearLandmarks()
        coordinator.closeSession()
        _participantes.update { it.copy(interlocutor = SIN_NOMBRE, solicitarNombre = true) }
    }

    fun nombrarInterlocutor(nombre: String) {
        val limpio = nombre.trim().take(30)
        _participantes.update {
            it.copy(interlocutor = limpio.ifBlank { SIN_NOMBRE }, solicitarNombre = false)
        }
    }

    fun renombrarCuenta(nombre: String) {
        val limpio = nombre.trim().take(30)
        _participantes.update { it.copy(cuenta = limpio.ifBlank { CUENTA_DEMO }) }
    }

    fun cancelarPedidoDeNombre() = _participantes.update { it.copy(solicitarNombre = false) }

    override fun onCleared() {
        coordinator.shutdown()
    }
}
