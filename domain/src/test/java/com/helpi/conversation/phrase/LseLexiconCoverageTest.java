package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Test;

/** Cobertura del léxico de LSE contra su catálogo (SC-001, FR-010 a FR-012). */
public class LseLexiconCoverageTest {

    private static final List<String> CATALOG = LseAssets.catalog();
    private static final Lexicon LEXICON = Lexicon.parse(LseAssets.read("lexico_senas.json"));

    @Test
    public void cadaClaseDelCatalogoTieneSuEntrada() {
        assertEquals(299, CATALOG.size());
        assertEquals(CATALOG.size(), LEXICON.size());
        for (int i = 0; i < CATALOG.size(); i++) {
            LexicalEntry entry = LEXICON.entry(i);
            assertNotNull("sin entrada para " + i, entry);
            assertEquals("glosa del índice " + i, CATALOG.get(i), entry.gloss());
        }
    }

    @Test
    public void ningunaFormaArrastraMarcasDeGlosado() {
        for (int i = 0; i < LEXICON.size(); i++) {
            LexicalEntry entry = LEXICON.entry(i);
            for (String form : new String[] {entry.form(), entry.firstPersonForm()}) {
                if (form == null) {
                    continue;
                }
                String lower = form.toLowerCase(Locale.ROOT);
                assertFalse(entry.gloss() + ": " + form,
                        lower.contains("(") || lower.contains(")") || lower.contains("variante"));
            }
        }
    }

    @Test
    public void lasVariantesDeUnaGlosaCompartenFormaYCategoria() {
        // Una variante es la misma glosa sin sus paréntesis: «Diarrea (variante 2)» → «Diarrea».
        Map<String, LexicalEntry> firstOfGroup = new HashMap<>();
        for (int i = 0; i < LEXICON.size(); i++) {
            LexicalEntry entry = LEXICON.entry(i);
            String base = entry.gloss().replaceAll("\\([^)]*\\)", " ").trim().replaceAll("\\s+", " ");
            LexicalEntry first = firstOfGroup.putIfAbsent(base, entry);
            if (first == null) {
                continue;
            }
            assertEquals(entry.gloss() + " vs " + first.gloss(), first.form(), entry.form());
            assertEquals(entry.gloss() + " vs " + first.gloss(), first.category(), entry.category());
            assertEquals(entry.gloss() + " vs " + first.gloss(),
                    first.firstPersonForm(), entry.firstPersonForm());
        }
    }
}
