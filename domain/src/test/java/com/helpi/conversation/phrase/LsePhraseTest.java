package com.helpi.conversation.phrase;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Frases armadas con los léxico y plantillas reales de LSE (funcionalidad 007). */
public class LsePhraseTest {

    private static final float THRESHOLD = 0.5f;
    private static final float HIGH = 0.9f;
    private static final float LOW = 0.3f;

    private static final List<String> CATALOG = LseAssets.catalog();
    private static final Lexicon LEXICON = Lexicon.parse(LseAssets.read("lexico_senas.json"));
    private static final TemplateSet TEMPLATES = TemplateSet.parse(LseAssets.read("plantillas_frases.json"));
    private static final PhraseBuilder BUILDER = PhraseBuilder.enabled(LEXICON, TEMPLATES, THRESHOLD);
    private static final Map<String, Integer> SYNTHETIC = new HashMap<>();

    static {
        SYNTHETIC.put("YO", SyntheticSigns.YO);
        SYNTHETIC.put("USTED", SyntheticSigns.USTED);
        SYNTHETIC.put("EL_ELLA", SyntheticSigns.EL_ELLA);
    }

    @Test
    public void elLexicoEsCompatibleConElModeloYElCatalogo() {
        String[] versions = LseAssets.manifestVersions();
        PhraseCompatibility compatibility =
                PhraseCompatibility.check(versions[0], versions[1], CATALOG, LEXICON, TEMPLATES);
        assertTrue(compatibility.reason(), compatibility.compatible());
        assertEquals(299, LEXICON.size());
    }

    @Test
    public void tablaDeOracionesDeLse() {
        String[][] table = {
            // glosas separadas por | (* = bajo el umbral), frase, plantillas aplicadas
            // primera persona por defecto
            {"Estomago|Dolor", "Tengo dolor en el estómago.", "cuerpo-sintoma"},
            {"Dolor|Espalda", "Tengo dolor en la espalda.", "sintoma-cuerpo"},
            {"Amigdalas|Dolor", "Tengo dolor en las amígdalas.", "cuerpo-sintoma"},
            {"Garganta (variante 2)|Ardor", "Tengo ardor en la garganta.", "cuerpo-sintoma"},
            {"Pierna|Bulto", "Tengo un bulto en la pierna.", "cuerpo-sintoma"},
            {"Dolor", "Tengo dolor.", "sintoma"},
            {"Dolor de cabeza", "Me duele la cabeza.", "sintoma"},
            {"Fiebre (variante 2)", "Tengo fiebre.", "sintoma"},
            {"Hambre", "Tengo hambre.", "sintoma"},
            {"Tension (CT) alta", "Tengo la tensión alta.", "sintoma"},
            {"Embarazo", "Estoy embarazada.", "sintoma"},
            {"Nausea", "Tengo náuseas.", "sintoma"},
            {"Mente olvidar", "Me olvido.", "verbo"},
            {"Toser", "Toso.", "verbo"},
            {"Respirar (variante 2)", "Respiro.", "verbo"},
            {"Debil", "Estoy débil.", "cualidad"},
            {"Nervioso", "Tengo nervios.", "cualidad"},
            // sin forma en primera persona: forma base, sin conjugar
            {"Ictus", "Ictus.", "sintoma"},
            {"Ayudar", "Ayudar.", "verbo"},
            {"Enfermo", "Enfermo.", "cualidad"},
            // compuestos reescritos y variantes
            {"Comer sin", "Sin comer.", "sintoma"},
            {"Dormir insomnio", "Tengo insomnio.", "sintoma"},
            {"Diarrea", "Tengo diarrea.", "sintoma"},
            {"Diarrea (variante 2)", "Tengo diarrea.", "sintoma"},
            {"Diarrea (variante 3)", "Tengo diarrea.", "sintoma"},
            {"Caca diarrea (variante 2)", "Tengo diarrea.", "sintoma"},
            // objetos, lugares y verbos con complemento
            {"Hospital", "El hospital.", "lugar"},
            {"Ambulancia", "La ambulancia.", "objeto"},
            {"Analisis de sangre", "El análisis de sangre.", "objeto"},
            {"Hambre|Comer|Dieta", "Tengo hambre. Comer la dieta.", "sintoma verbo-objeto"},
            {"Fiebre (variante 2)|Hospital", "Tengo fiebre. El hospital.", "sintoma lugar"},
            {"Operar|Pierna", "Operar la pierna.", "verbo-cuerpo"},
            {"Revisar|Resultado", "Revisar el resultado.", "verbo-objeto"},
            {"Ayudar|Farmacia", "Ayudar en la farmacia.", "verbo-lugar"},
            {"Insulina|Normal", "La insulina es normal.", "objeto-cualidad"},
            {"Auxiliar|Ayudar", "El auxiliar: ayudar.", "persona-verbo"},
            {"Auxiliar|Revisar|Vacuna", "El auxiliar: revisar la vacuna.", "persona-verbo-objeto"},
            {"Auxiliar|Cansado (variante 2)", "El auxiliar cansado.", "persona-cualidad"},
            // expresiones: se conservan, sin censura
            {"Urgente", "Urgente.", "expresion"},
            {"Mierda", "Mierda.", "expresion"},
            {"Urgente|Ambulancia", "Urgente. La ambulancia.", "expresion objeto"},
            // pronombres detectados por señalamiento
            {"YO|Dolor", "Tengo dolor.", "yo-sintoma"},
            {"YO|Estomago|Dolor", "Tengo dolor en el estómago.", "yo-cuerpo-sintoma"},
            {"YO|Toser", "Toso.", "yo-verbo"},
            {"YO|Debil", "Estoy débil.", "yo-cualidad"},
            {"USTED|Ayudar", "Usted: ayudar.", "persona-verbo"},
            {"USTED|Revisar|Resultado", "Usted: revisar el resultado.", "persona-verbo-objeto"},
            {"EL_ELLA|Fiebre (variante 2)", "Él o ella: fiebre.", "persona-sintoma"},
            {"EL_ELLA|Espalda|Dolor", "Él o ella: dolor en la espalda.", "persona-cuerpo-sintoma"},
            // sin plantilla: glosa en mayúsculas
            {"Contra", "CONTRA", ""},
            // bajo el umbral: ninguna plantilla ni primera persona
            {"Estomago|*Dolor", "ESTOMAGO ¿?", ""},
            {"*Fiebre (variante 2)", "¿?", ""},
        };
        for (String[] row : table) {
            PhraseResult result = BUILDER.build(sentence(row[0]));
            assertEquals(row[0], row[1], result.text());
            assertEquals(row[0], row[2], String.join(" ", result.appliedTemplates()));
        }
        assertTrue("SC-002 pide al menos 40 oraciones", table.length >= 40);
    }

    // Busca cada glosa en el catálogo real; un asterisco la deja bajo el umbral.
    private static List<RecognizedSign> sentence(String spec) {
        List<RecognizedSign> signs = new ArrayList<>();
        for (String token : spec.split("\\|")) {
            boolean low = token.startsWith("*");
            String gloss = low ? token.substring(1) : token;
            int index = SYNTHETIC.containsKey(gloss) ? SYNTHETIC.get(gloss) : CATALOG.indexOf(gloss);
            assertTrue("glosa inexistente: " + gloss, index != -1 || SYNTHETIC.containsKey(gloss));
            assertNotNull(LEXICON.entry(index));
            signs.add(new RecognizedSign(index, gloss, low ? LOW : HIGH));
        }
        return signs;
    }
}
