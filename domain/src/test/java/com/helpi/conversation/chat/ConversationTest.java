package com.helpi.conversation.chat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class ConversationTest {

    @Test
    public void parcialesReemplazanElMismoTurno() {
        Conversation c = new Conversation();
        Turn t = c.open(Speaker.HEARING, 100);
        c.updatePartial(t.id(), "vengo");
        c.updatePartial(t.id(), "vengo por el");
        Turn done = c.finalize(t.id(), "vengo por el trámite");

        assertEquals(1, c.size());
        assertEquals(TurnState.FINAL, done.state());
        assertEquals("vengo por el trámite", done.text());
        assertTrue(done.revision() > 0);
    }

    @Test
    public void ordenPorInicioNoPorLlegada() {
        Conversation c = new Conversation();
        // la seña empezó antes pero su resultado llega después
        Turn sign = c.open(Speaker.DEAF, 100);
        Turn voice = c.open(Speaker.HEARING, 200);
        c.finalize(voice.id(), "hola");
        c.publishSign(sign.id(), "Seña reconocida: casa");

        List<Turn> ordered = c.ordered();
        assertEquals(sign.id(), ordered.get(0).id());
        assertEquals(voice.id(), ordered.get(1).id());
    }

    @Test
    public void empatesSeResuelvenPorSecuenciaEstable() {
        Conversation c = new Conversation();
        Turn a = c.open(Speaker.DEAF, 100);
        Turn b = c.open(Speaker.HEARING, 100);
        List<Turn> ordered = c.ordered();
        assertEquals(a.id(), ordered.get(0).id());
        assertEquals(b.id(), ordered.get(1).id());
    }

    @Test
    public void senaAceptadaQuedaConVozPendiente() {
        Conversation c = new Conversation();
        Turn t = c.open(Speaker.DEAF, 100);
        Turn published = c.publishSign(t.id(), "Seña reconocida: agua");
        assertEquals(VoiceState.PENDING, published.voiceState());
        assertEquals(TurnState.FINAL, published.state());
    }

    @Test
    public void textoEscritoQuedaFinalYPendienteDeVoz() {
        Conversation c = new Conversation();
        Turn t = c.open(Speaker.DEAF, 100);
        Turn published = c.publishTyped(t.id(), "  Necesito ayuda  ");
        assertEquals("Necesito ayuda", published.text());
        assertEquals(TurnState.FINAL, published.state());
        assertEquals(VoiceState.PENDING, published.voiceState());
    }

    @Test
    public void interrupcionMarcaIncompletoNoFinal() {
        Conversation c = new Conversation();
        Turn t = c.open(Speaker.HEARING, 100);
        c.updatePartial(t.id(), "vengo por");
        Turn marked = c.markIncomplete(t.id());
        assertEquals(TurnState.INCOMPLETE, marked.state());
        assertEquals("vengo por", marked.text());
    }

    @Test
    public void correccionQuedaVisibleYAgregaNotaDelSistema() {
        Conversation c = new Conversation();
        Turn t = c.open(Speaker.DEAF, 100);
        c.publishSign(t.id(), "Seña reconocida: fideos");
        Turn marked = c.markIncorrect(t.id(), 300);

        assertTrue(marked.markedIncorrect());
        assertEquals("Seña reconocida: fideos", marked.text()); // no se sustituye
        List<Turn> ordered = c.ordered();
        assertEquals(2, ordered.size());
        assertEquals(Speaker.SYSTEM, ordered.get(1).speaker());
        assertEquals(VoiceState.PENDING, ordered.get(1).voiceState());
    }

    @Test
    public void rechazoEsEventoDelSistemaNoFraseDeLaPersona() {
        Conversation c = new Conversation();
        Turn t = c.open(Speaker.DEAF, 100);
        Turn rejected = c.markRejected(t.id());
        assertEquals(TurnState.REJECTED, rejected.state());
        assertEquals("", rejected.text()); // nunca se publica la adivinanza
    }

    @Test
    public void alLimiteSeExigeCerrarSinDescartarTurnos() {
        Conversation c = new Conversation();
        for (int i = 0; i < Conversation.MAX_TURNS; i++) {
            c.systemNote("n" + i, i);
        }
        assertTrue(c.atCapacity());
        assertThrows(IllegalStateException.class, () -> c.open(Speaker.HEARING, 999));
        assertEquals(Conversation.MAX_TURNS, c.size()); // nada se descartó
    }

    @Test
    public void clearDescartaTodo() {
        Conversation c = new Conversation();
        c.open(Speaker.HEARING, 1);
        c.clear();
        assertEquals(0, c.size());
    }
}
