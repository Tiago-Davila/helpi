package com.helpi.conversation

import com.helpi.conversation.session.DeliveryMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VariantBindingsTest {
    @Test
    fun evaluationChatPublishesWhileTheObserverKeepsRecording() {
        assertEquals(DeliveryMode.CONVERSATION, VariantBindings.deliveryMode)
        assertSame(VariantBindings.registroObserver, VariantBindings.recognitionObserver)
    }
}
