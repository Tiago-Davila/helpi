package com.helpi.conversation.chat;

import java.util.Objects;

/**
 * Un turno de la conversación. Inmutable: cada cambio produce una revisión
 * nueva, de modo que la interfaz pueda mostrar que hubo una corrección.
 *
 * Vive exclusivamente en memoria (Ley 25.326): esta clase no debe adquirir
 * nunca capacidades de serialización a disco.
 */
public final class Turn {

    private final long id;
    private final Speaker speaker;
    private final String text;
    private final TurnState state;
    private final VoiceState voiceState;
    private final long startTimestampMs;
    private final long sequence;
    private final int revision;
    private final boolean markedIncorrect;

    public Turn(
            long id,
            Speaker speaker,
            String text,
            TurnState state,
            VoiceState voiceState,
            long startTimestampMs,
            long sequence,
            int revision,
            boolean markedIncorrect) {
        this.id = id;
        this.speaker = Objects.requireNonNull(speaker);
        this.text = Objects.requireNonNull(text);
        this.state = Objects.requireNonNull(state);
        this.voiceState = Objects.requireNonNull(voiceState);
        this.startTimestampMs = startTimestampMs;
        this.sequence = sequence;
        this.revision = revision;
        this.markedIncorrect = markedIncorrect;
    }

    public long id() {
        return id;
    }

    public Speaker speaker() {
        return speaker;
    }

    public String text() {
        return text;
    }

    public TurnState state() {
        return state;
    }

    public VoiceState voiceState() {
        return voiceState;
    }

    /** Inicio físico de la intervención; define el orden del chat. */
    public long startTimestampMs() {
        return startTimestampMs;
    }

    /** Desempate estable para inicios simultáneos. */
    public long sequence() {
        return sequence;
    }

    public int revision() {
        return revision;
    }

    public boolean markedIncorrect() {
        return markedIncorrect;
    }

    Turn withText(String newText, TurnState newState) {
        return new Turn(id, speaker, newText, newState, voiceState,
                startTimestampMs, sequence, revision + 1, markedIncorrect);
    }

    Turn withState(TurnState newState) {
        return new Turn(id, speaker, text, newState, voiceState,
                startTimestampMs, sequence, revision + 1, markedIncorrect);
    }

    Turn withVoiceState(VoiceState newVoiceState) {
        return new Turn(id, speaker, text, state, newVoiceState,
                startTimestampMs, sequence, revision + 1, markedIncorrect);
    }

    Turn asIncorrect() {
        return new Turn(id, speaker, text, state, voiceState,
                startTimestampMs, sequence, revision + 1, true);
    }
}
