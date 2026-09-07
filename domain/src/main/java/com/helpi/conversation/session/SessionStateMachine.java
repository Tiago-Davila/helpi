package com.helpi.conversation.session;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados global de la sesión. Solo valida transiciones; los
 * efectos (liberar sensores, descartar historial) los ejecuta el coordinador.
 *
 * La muerte del proceso no pasa por CERRANDO: la privacidad no depende de
 * recibir ese callback.
 */
public final class SessionStateMachine {

    private static final Map<SessionState, Set<SessionState>> ALLOWED =
            new EnumMap<>(SessionState.class);

    static {
        ALLOWED.put(SessionState.INICIO, EnumSet.of(SessionState.PREPARANDO, SessionState.CERRANDO));
        ALLOWED.put(SessionState.PREPARANDO, EnumSet.of(
                SessionState.LISTA, SessionState.ACTIVA_LIMITADA,
                SessionState.BLOQUEADA, SessionState.CERRANDO));
        ALLOWED.put(SessionState.LISTA, EnumSet.of(
                SessionState.ACTIVA, SessionState.ACTIVA_LIMITADA,
                SessionState.PAUSADA, SessionState.BLOQUEADA, SessionState.CERRANDO));
        ALLOWED.put(SessionState.ACTIVA, EnumSet.of(
                SessionState.ACTIVA_LIMITADA, SessionState.PAUSADA,
                SessionState.BLOQUEADA, SessionState.CERRANDO));
        ALLOWED.put(SessionState.ACTIVA_LIMITADA, EnumSet.of(
                SessionState.ACTIVA, SessionState.PAUSADA,
                SessionState.BLOQUEADA, SessionState.CERRANDO));
        ALLOWED.put(SessionState.PAUSADA, EnumSet.of(
                SessionState.PREPARANDO, SessionState.BLOQUEADA, SessionState.CERRANDO));
        ALLOWED.put(SessionState.BLOQUEADA, EnumSet.of(
                SessionState.PREPARANDO, SessionState.CERRANDO));
        ALLOWED.put(SessionState.CERRANDO, EnumSet.of(SessionState.CERRADA));
        ALLOWED.put(SessionState.CERRADA, EnumSet.noneOf(SessionState.class));
    }

    private SessionState current = SessionState.INICIO;

    public SessionState current() {
        return current;
    }

    public boolean canTransition(SessionState to) {
        return ALLOWED.get(current).contains(to);
    }

    public void transition(SessionState to) {
        if (!canTransition(to)) {
            throw new IllegalStateException(
                    "transición inválida: " + current + " -> " + to);
        }
        current = to;
    }
}
