package com.helpi.conversation.phrase;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public class PhraseCompatibilityTest {

    private static final String MODEL = "eva-lsa94-v4";
    private static final String CATALOG = "3.0";

    private static List<String> deployedCatalog() {
        JSONArray array = new JSONObject(LsaAssets.read("catalogo_senas.json")).getJSONArray("glosas");
        List<String> glosses = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            glosses.add(array.getString(i));
        }
        return glosses;
    }

    private static Lexicon deployedLexicon() {
        return Lexicon.parse(LsaAssets.read("lexico_senas.json"));
    }

    private static TemplateSet deployedTemplates() {
        return TemplateSet.parse(LsaAssets.read("plantillas_frases.json"));
    }

    private static TemplateSet templates(String lexiconVersion, String patternElement) {
        return TemplateSet.parse("{\"version\":\"1\",\"lexicoVersion\":\"" + lexiconVersion + "\","
                + "\"plantillas\":[{\"id\":\"t\",\"patron\":[\"" + patternElement + "\"],\"salida\":\"{0}.\"}]}");
    }

    @Test
    public void losAssetsDesplegadosSonUnaUnidad() {
        PhraseCompatibility result = PhraseCompatibility.check(
                MODEL, CATALOG, deployedCatalog(), deployedLexicon(), deployedTemplates());
        assertTrue(result.reason(), result.compatible());
        assertNull(result.reason());
    }

    @Test
    public void cualquierDesfasajeDeshabilitaElArmado() {
        List<String> catalog = deployedCatalog();
        List<String> shifted = new ArrayList<>(catalog);
        Collections.swap(shifted, 1, 2);
        List<String> shorter = catalog.subList(0, catalog.size() - 1);

        Object[][] cases = {
            // modelo, catálogo, glosas del catálogo, plantillas
            {"eva-lsa94-v5", CATALOG, catalog, deployedTemplates()},
            {MODEL, "4.0", catalog, deployedTemplates()},
            {MODEL, CATALOG, catalog, templates("1", "expresion")},
            {MODEL, CATALOG, shorter, deployedTemplates()},
            {MODEL, CATALOG, shifted, deployedTemplates()},
            {MODEL, CATALOG, catalog, templates("2", "glosa:Pescado")},
        };
        for (Object[] c : cases) {
            @SuppressWarnings("unchecked")
            List<String> glosses = (List<String>) c[2];
            PhraseCompatibility result = PhraseCompatibility.check(
                    (String) c[0], (String) c[1], glosses, deployedLexicon(), (TemplateSet) c[3]);
            assertFalse("debió deshabilitar: " + Arrays.toString(c), result.compatible());
            assertTrue(result.reason() != null && !result.reason().isEmpty());
        }
    }
}
