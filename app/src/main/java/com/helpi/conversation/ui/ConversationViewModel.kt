package com.helpi.conversation.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.helpi.conversation.session.SessionCoordinator

/**
 * Expone la sesión a Compose. Sobrevive rotaciones sin serializar la
 * conversación: el historial vive solo en memoria del proceso.
 */
class ConversationViewModel(application: Application) : AndroidViewModel(application) {

    val coordinator = SessionCoordinator(application)
    val uiState = coordinator.uiState

    fun prepare() = coordinator.prepare()
    fun start() = coordinator.startConversation()
    fun pause() = coordinator.pause()
    fun markIncorrect(turnId: Long) = coordinator.markIncorrect(turnId)
    fun closeSession() = coordinator.closeSession()

    override fun onCleared() {
        coordinator.shutdown()
    }
}
