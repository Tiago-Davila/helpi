package com.helpi.conversation

import com.helpi.conversation.session.SessionCoordinator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VariantBindingsTest {
    @Test
    fun evaluationChatPublishesWhileTheObserverKeepsRecording() {
        assertEquals(SessionCoordinator.DeliveryMode.CONVERSATION, VariantBindings.deliveryMode)
        assertSame(VariantBindings.registroObserver, VariantBindings.recognitionObserver)
    }
}
