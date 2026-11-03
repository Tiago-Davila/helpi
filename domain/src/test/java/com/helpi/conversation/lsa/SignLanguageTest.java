package com.helpi.conversation.lsa;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SignLanguageTest {

    @Test
    public void cadaLenguaTieneSuCarpeta() {
        assertEquals("lsa/modelo_lsa.tflite", SignLanguage.LSA.modelAsset());
        assertEquals("lsa/lsa-manifest.json", SignLanguage.LSA.manifestAsset());
        assertEquals("asl/manifest.json", SignLanguage.ASL.manifestAsset());
        assertEquals("lse/catalogo_senas.json", SignLanguage.LSE.catalogAsset());
    }

    @Test
    public void codigoDesconocidoNoEsNingunaLengua() {
        assertEquals(SignLanguage.LSE, SignLanguage.fromCode("LSE"));
        assertNull(SignLanguage.fromCode("libras"));
        assertNull(SignLanguage.fromCode(null));
    }
}
