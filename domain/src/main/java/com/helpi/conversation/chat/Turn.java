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
    private final String glosses;
    private final TurnState state;
    private final VoiceState voiceState;
    private final long startTimestampMs;
    private final long sequence;
    private final int revision;
    private final boolean markedIncorrect;

    /** Crea una revisión inmutable de un turno; glosses vacío si no proviene de señas. */
    public Turn(
            long id,
            Speaker speaker,
            String text,
            String glosses,
            TurnState state,
            VoiceState voiceState,
            long startTimestampMs,
            long sequence,
            int revision,
            boolean markedIncorrect) {
        this.id = id;
        this.speaker = Objects.requireNonNull(speaker);
        this.text = Objects.requireNonNull(text);
        this.glosses = Objects.requireNonNull(glosses);
        this.state = Objects.requireNonNull(state);
        this.voiceState = Objects.requireNonNull(voiceState);
        this.startTimestampMs = startTimestampMs;
        this.sequence = sequence;
        this.revision = revision;
        this.markedIncorrect = markedIncorrect;
    }

    /** Devuelve el identificador estable del turno. */
    public long id() {
        return id;
    }

    /** Devuelve quién produjo el turno. */
    public Speaker speaker() {
        return speaker;
    }

    /** Devuelve el texto visible del turno. */
    public String text() {
        return text;
    }

    /** Glosas que originaron el texto, para que la persona sorda verifique la frase. */
    public String glosses() {
        return glosses;
    }

    /** Devuelve el estado textual del turno. */
    public TurnState state() {
        return state;
    }

    /** Devuelve el estado de salida de voz del turno. */
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

    /** Devuelve cuántas revisiones se hicieron al turno. */
    public int revision() {
        return revision;
    }

    /** Indica si la persona marcó el turno como incorrecto. */
    public boolean markedIncorrect() {
        return markedIncorrect;
    }

    Turn withText(String newText, TurnState newState) {
        return new Turn(id, speaker, newText, glosses, newState, voiceState,
                startTimestampMs, sequence, revision + 1, markedIncorrect);
    }

    Turn withSentence(String newText, String newGlosses) {
        return new Turn(id, speaker, newText, newGlosses, TurnState.FINAL, voiceState,
                startTimestampMs, sequence, revision + 1, markedIncorrect);
    }

    Turn withState(TurnState newState) {
        return new Turn(id, speaker, text, glosses, newState, voiceState,
                startTimestampMs, sequence, revision + 1, markedIncorrect);
    }

    Turn withVoiceState(VoiceState newVoiceState) {
        return new Turn(id, speaker, text, glosses, state, newVoiceState,
                startTimestampMs, sequence, revision + 1, markedIncorrect);
    }

    Turn asIncorrect() {
        return new Turn(id, speaker, text, glosses, state, voiceState,
                startTimestampMs, sequence, revision + 1, true);
    }
}
