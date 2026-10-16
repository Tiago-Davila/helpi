package com.helpi.evaluacion.dominio.protocolo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

public class GeneradorProtocoloTest {
    private static final long SEMILLA_EJEMPLO = 20261005L;
    private static final int CANTIDAD_SENAS = 64;
    private static final int REPETICIONES = 3;
    private static final int INTENTOS = CANTIDAD_SENAS * REPETICIONES;

    @Test
    public void generaTresRepeticionesSinAdyacenciasEnCuatroBloques() {
        GeneradorProtocolo.Protocolo protocolo = GeneradorProtocolo.generar(SEMILLA_EJEMPLO);

        assertEquals("1.0.0", protocolo.version());
        assertEquals(SEMILLA_EJEMPLO, protocolo.semilla());
        assertEquals(INTENTOS, protocolo.secuencia().size());

        int[] apariciones = new int[CANTIDAD_SENAS];
        for (int indice = 0; indice < protocolo.secuencia().size(); indice++) {
            int sena = protocolo.secuencia().get(indice);
            assertTrue("índice de seña fuera de rango: " + sena, sena >= 0 && sena < CANTIDAD_SENAS);
            apariciones[sena]++;
            if (indice > 0) {
                assertTrue("seña repetida en posiciones adyacentes: " + indice,
                        !protocolo.secuencia().get(indice).equals(protocolo.secuencia().get(indice - 1)));
            }
        }
        for (int cantidad : apariciones) {
            assertEquals(REPETICIONES, cantidad);
        }

        assertEquals(4, protocolo.bloques().size());
        List<Integer> secuenciaPorBloques = new ArrayList<>();
        for (List<Integer> bloque : protocolo.bloques()) {
            assertEquals(48, bloque.size());
            secuenciaPorBloques.addAll(bloque);
        }
        assertEquals(protocolo.secuencia(), secuenciaPorBloques);
    }

    @Test
    public void semillaDelEjemploReproduceLaSecuenciaDelContrato() throws IOException {
        GeneradorProtocolo.Protocolo protocolo = GeneradorProtocolo.generar(SEMILLA_EJEMPLO);
        assertEquals(secuenciaDelEjemplo(), protocolo.secuencia());
    }

    private static List<Integer> secuenciaDelEjemplo() throws IOException {
        InputStream recurso = GeneradorProtocoloTest.class.getResourceAsStream(
                "/ejemplos/sesion-evaluacion-v1.ejemplo.json"
        );
        assertNotNull("falta el ejemplo canónico del contrato", recurso);
        String json;
        try (InputStream stream = recurso) {
            json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        Matcher secuencia = Pattern.compile("\\\"secuencia\\\"\\s*:\\s*\\[([^]]*)\\]").matcher(json);
        assertTrue("el ejemplo no contiene protocolo.secuencia", secuencia.find());
        Matcher valores = Pattern.compile("\\d+").matcher(secuencia.group(1));
        List<Integer> resultado = new ArrayList<>();
        while (valores.find()) {
            resultado.add(Integer.parseInt(valores.group()));
        }
        return resultado;
    }
}
