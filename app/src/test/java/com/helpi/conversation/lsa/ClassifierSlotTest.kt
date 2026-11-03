package com.helpi.conversation.lsa

import java.io.Closeable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassifierSlotTest {
    private class Fake : Closeable {
        var closed = false
        override fun close() {
            closed = true
        }
    }

    @Test
    fun replacingClosesThePreviousClassifierAndChangesTheEpoch() {
        val pending = mutableListOf<Runnable>()
        val slot = ClassifierSlot<Fake> { pending += it }
        val lsa = Fake()
        slot.replace(lsa)
        val epoch = slot.epoch

        val lse = Fake()
        slot.replace(lse)
        assertSame(lse, slot.current)
        assertNotEquals(epoch, slot.epoch)
        // se cierra en el executor de LiteRT, no en el hilo que cambió de lengua
        assertFalse(lsa.closed)
        pending.forEach { it.run() }
        assertTrue(lsa.closed)
        assertFalse(lse.closed)
    }

    @Test
    fun clearingLeavesNoClassifierLoaded() {
        val slot = ClassifierSlot<Fake> { it.run() }
        val only = Fake()
        slot.replace(only)
        slot.replace(null)
        assertNull(slot.current)
        assertTrue(only.closed)
        assertEquals(2, slot.epoch)
    }
}
