package com.helpi.conversation.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conversación en memoria. No conoce almacenamiento: no persiste, no
 * serializa, no exporta. El historial se descarta con {@link #clear()}.
 *
 * No es thread-safe: debe usarse solo desde el executor serial del
 * coordinador de sesión.
 */
public final class Conversation {

    /** Límite duro de turnos por sesión (riesgo K15). */
    public static final int MAX_TURNS = 2000;

    private final Map<Long, Turn> turns = new LinkedHashMap<>();
    private long nextId = 1;
    private long nextSequence = 1;

    /** Crea una reserva de turno (inicio de voz o de seña detectado). */
    public Turn open(Speaker speaker, long startTimestampMs) {
        requireCapacity();
        Turn turn = new Turn(nextId++, speaker, "", TurnState.PENDING,
                VoiceState.NONE, startTimestampMs, nextSequence++, 0, false);
        turns.put(turn.id(), turn);
        return turn;
    }

    /** Reemplaza el texto provisional de un turno del oyente. */
    public Turn updatePartial(long turnId, String partialText) {
        return replace(get(turnId).withText(partialText, TurnState.PARTIAL));
    }

    /** Fija el texto definitivo de un turno. */
    public Turn finalize(long turnId, String finalText) {
        return replace(get(turnId).withText(finalText, TurnState.FINAL));
    }

    /**
     * Publica una seña aceptada: texto final y voz pendiente de arbitraje.
     */
    public Turn publishSign(long turnId, String recognizedText) {
        Turn t = get(turnId).withText(recognizedText, TurnState.FINAL)
                .withVoiceState(VoiceState.PENDING);
        return replace(t);
    }

    /** Publica texto escrito por la persona sorda y lo deja listo para TTS. */
    public Turn publishTyped(long turnId, String text) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("el texto escrito no puede estar vacío");
        }
        Turn t = get(turnId).withText(text.trim(), TurnState.FINAL)
                .withVoiceState(VoiceState.PENDING);
        return replace(t);
    }

    /** El canal se interrumpió: el parcial queda marcado, no consolidado. */
    public Turn markIncomplete(long turnId) {
        return replace(get(turnId).withState(TurnState.INCOMPLETE));
    }

    /** Segmento rechazado o intento no procesado. */
    public Turn markRejected(long turnId) {
        return replace(get(turnId).withState(TurnState.REJECTED));
    }

    public Turn updateVoice(long turnId, VoiceState voiceState) {
        return replace(get(turnId).withVoiceState(voiceState));
    }

    /**
     * "No quise decir eso": el turno queda visible como incorrecto y se
     * agrega una corrección del sistema. No se sustituye por otra clase.
     */
    public Turn markIncorrect(long turnId, long nowMs) {
        Turn marked = replace(get(turnId).asIncorrect());
        requireCapacity();
        Turn note = new Turn(nextId++, Speaker.SYSTEM,
                "La traducción anterior fue marcada como incorrecta",
                TurnState.FINAL, VoiceState.PENDING, nowMs, nextSequence++, 0, false);
        turns.put(note.id(), note);
        return marked;
    }

    /** Mensaje del sistema sin voz asociada. */
    public Turn systemNote(String text, long nowMs) {
        requireCapacity();
        Turn note = new Turn(nextId++, Speaker.SYSTEM, text, TurnState.FINAL,
                VoiceState.NONE, nowMs, nextSequence++, 0, false);
        turns.put(note.id(), note);
        return note;
    }

    public Turn get(long turnId) {
        Turn t = turns.get(turnId);
        if (t == null) {
            throw new IllegalArgumentException("turno inexistente: " + turnId);
        }
        return t;
    }

    /**
     * Turnos ordenados por inicio de la intervención (no por llegada del
     * resultado), con desempate estable por secuencia.
     */
    public List<Turn> ordered() {
        List<Turn> list = new ArrayList<>(turns.values());
        list.sort(Comparator.comparingLong(Turn::startTimestampMs)
                .thenComparingLong(Turn::sequence));
        return Collections.unmodifiableList(list);
    }

    public int size() {
        return turns.size();
    }

    public boolean atCapacity() {
        return turns.size() >= MAX_TURNS;
    }

    /** Descarta todo el historial. Única forma de terminar la sesión. */
    public void clear() {
        turns.clear();
    }

    private void requireCapacity() {
        if (turns.size() >= MAX_TURNS) {
            // No se descartan turnos viejos en silencio: se exige cerrar sesión.
            throw new IllegalStateException("límite de turnos alcanzado: cerrar la sesión");
        }
    }

    private Turn replace(Turn t) {
        turns.put(t.id(), t);
        return t;
    }
}
