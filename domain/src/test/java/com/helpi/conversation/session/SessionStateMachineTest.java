package com.helpi.conversation.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class SessionStateMachineTest {

    @Test
    public void flujoNominal() {
        SessionStateMachine m = new SessionStateMachine();
        m.transition(SessionState.PREPARANDO);
        m.transition(SessionState.LISTA);
        m.transition(SessionState.ACTIVA);
        m.transition(SessionState.PAUSADA);
        m.transition(SessionState.PREPARANDO); // reanudar re-verifica permisos
        m.transition(SessionState.LISTA);
        m.transition(SessionState.ACTIVA);
        m.transition(SessionState.CERRANDO);
        m.transition(SessionState.CERRADA);
        assertEquals(SessionState.CERRADA, m.current());
    }

    @Test
    public void degradacionYRecuperacion() {
        SessionStateMachine m = new SessionStateMachine();
        m.transition(SessionState.PREPARANDO);
        m.transition(SessionState.LISTA);
        m.transition(SessionState.ACTIVA);
        m.transition(SessionState.ACTIVA_LIMITADA);
        m.transition(SessionState.ACTIVA);
        assertEquals(SessionState.ACTIVA, m.current());
    }

    @Test
    public void cerradaEsTerminal() {
        SessionStateMachine m = new SessionStateMachine();
        m.transition(SessionState.CERRANDO);
        m.transition(SessionState.CERRADA);
        for (SessionState s : SessionState.values()) {
            assertFalse(m.canTransition(s));
        }
    }

    @Test
    public void transicionInvalidaLanza() {
        SessionStateMachine m = new SessionStateMachine();
        assertThrows(IllegalStateException.class,
                () -> m.transition(SessionState.ACTIVA));
    }
}
