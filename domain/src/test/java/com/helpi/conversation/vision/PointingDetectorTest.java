package com.helpi.conversation.vision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Detector de señalamiento sobre vectores de 168 construidos en el test, en
 * el mismo formato crudo que produce la cámara (x, y normalizados; z de la
 * mano relativa a la muñeca).
 */
public class PointingDetectorTest {

    private static final int W = 960;
    private static final int H = 540;
    // Hombros: centro (480, 250) y ancho de 200 px.
    private static final float CX = 480f;
    private static final float CY = 250f;
    private static final float SHOULDERS = 200f;
    private static final float PALM = 60f;
    private static final long FRAME_MS = 50;

    private final PointingDetector detector = new PointingDetector(PointingConfig.defaults());

    @Test
    public void indiceHaciaElPechoEsYo() {
        Hand hand = Hand.pointing(480, 420, 0, -0.3f, 1f);
        assertEquals(Pointing.Type.YO, detect(segment(null, hand, 10)).type());
    }

    @Test
    public void indiceHaciaLaCamaraEsUsted() {
        Hand hand = Hand.pointing(560, 380, 0, 0f, -1f);
        assertEquals(Pointing.Type.USTED, detect(segment(null, hand, 10)).type());
    }

    @Test
    public void indiceHaciaUnCostadoEsElOElla() {
        Hand right = Hand.pointing(650, 330, 1f, 0f, 0f);
        assertEquals(Pointing.Type.EL_ELLA, detect(segment(null, right, 10)).type());
        Hand left = Hand.pointing(310, 330, -1f, 0f, 0f);
        assertEquals(Pointing.Type.EL_ELLA, detect(segment(left, null, 10)).type());
    }

    @Test
    public void laManoIzquierdaSeñalaIgualQueLaDerecha() {
        Hand hand = Hand.pointing(560, 380, 0, 0f, -1f);
        assertEquals(Pointing.Type.USTED, detect(segment(hand, null, 10)).type());
    }

    @Test
    public void dosIndicesExtendidosNoSeClasifican() {
        Hand left = Hand.pointing(400, 380, 0, 0f, -1f);
        Hand right = Hand.pointing(560, 380, 0, 0f, -1f);
        assertEquals(Pointing.Type.NONE, detect(segment(left, right, 10)).type());
    }

    @Test
    public void manoAbiertaOPunoNoSonSeñalamiento() {
        assertEquals(Pointing.Type.NONE, detect(segment(null, Hand.open(560, 380), 10)).type());
        assertEquals(Pointing.Type.NONE, detect(segment(null, Hand.fist(560, 380), 10)).type());
    }

    @Test
    public void unaDireccionEntreDosZonasEsAmbigua() {
        Hand hand = Hand.pointing(650, 330, 1f, 0f, -1f);
        assertEquals(Pointing.Type.AMBIGUOUS, detect(segment(null, hand, 10)).type());
    }

    @Test
    public void unSeñalamientoBreveNoAlcanza() {
        Hand hand = Hand.pointing(560, 380, 0, 0f, -1f);
        assertEquals(Pointing.Type.NONE, detect(segment(null, hand, 3)).type());
    }

    @Test
    public void unaManoQueSeDesplazaNoSeñala() {
        List<float[]> frames = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            frames.add(vector(null, Hand.pointing(500 + i * 20, 380, 0, 0f, -1f), true));
        }
        assertEquals(Pointing.Type.NONE, detect(frames).type());
    }

    @Test
    public void sinHombrosNoHayReferencia() {
        List<float[]> frames = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            frames.add(vector(null, Hand.pointing(560, 380, 0, 0f, -1f), false));
        }
        assertEquals(Pointing.Type.NONE, detect(frames).type());
    }

    @Test
    public void elSeñalamientoSostenidoGanaSobreElRestoDelSegmento() {
        // Sube la mano abierta y después señala hacia la cámara.
        List<float[]> frames = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            frames.add(vector(null, Hand.open(560, 450 - i * 20), true));
        }
        for (int i = 0; i < 8; i++) {
            frames.add(vector(null, Hand.pointing(560, 380, 0, 0f, -1f), true));
        }
        assertEquals(Pointing.Type.USTED, detect(frames).type());
    }

    // Cara de prueba: nariz en (480, 120) y comisuras de la boca a la altura 160.
    private static final float[] HEAD = {
        480f / W, 120f / H, 0, 465f / W, 160f / H, 0, 495f / W, 160f / H, 0,
    };
    // Zonas en píxeles: garganta a mitad de camino entre boca y hombros, abdomen bajo el pecho.
    private static final float[] TOWARD_BODY = {0f, -0.3f, 1f};

    @Test
    public void tocarseLaCabezaLaGargantaOElAbdomenEsUnaZona() {
        assertEquals(Pointing.Type.CABEZA,
                detectWithHead(Hand.touching(480, 125, 0f, -0.6f, 0.8f), HEAD).type());
        assertEquals(Pointing.Type.GARGANTA, detectWithHead(Hand.touching(480, 205, TOWARD_BODY), HEAD).type());
        assertEquals(Pointing.Type.ABDOMEN, detectWithHead(Hand.touching(480, 458, TOWARD_BODY), HEAD).type());
    }

    @Test
    public void tocarseElPechoSigueSiendoYo() {
        assertEquals(Pointing.Type.YO, detectWithHead(Hand.touching(480, 330, TOWARD_BODY), HEAD).type());
    }

    @Test
    public void sinLaReferenciaDeUnaZonaNoSeAgregaEsaZona() {
        // Sin cara detectada, la cabeza no se evalúa.
        Pointing head = detectWithHead(Hand.touching(480, 125, 0f, -0.6f, 0.8f), null);
        assertFalse(head.isBodyZone());
        assertFalse(head.isPronoun());
        // Sin caderas, tampoco el abdomen; y el abdomen no se confunde con el pecho.
        List<float[]> frames = new ArrayList<>();
        List<float[]> heads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            float[] v = vector(null, Hand.touching(480, 458, TOWARD_BODY), true);
            for (int k = 162; k < 168; k++) {
                v[k] = 0f;
            }
            frames.add(v);
            heads.add(HEAD);
        }
        Pointing belly = detect(frames, heads);
        assertFalse(belly.isBodyZone());
        assertFalse(belly.isPronoun());
    }

    private Pointing detectWithHead(Hand hand, float[] head) {
        List<float[]> frames = new ArrayList<>();
        List<float[]> heads = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            frames.add(vector(null, hand, true));
            heads.add(head);
        }
        return detect(frames, heads);
    }

    private Pointing detect(List<float[]> frames) {
        return detect(frames, null);
    }

    private Pointing detect(List<float[]> frames, List<float[]> heads) {
        long[] timestamps = new long[frames.size()];
        for (int i = 0; i < timestamps.length; i++) {
            timestamps[i] = 1000 + i * FRAME_MS;
        }
        return heads == null
                ? detector.detect(frames, timestamps, W, H)
                : detector.detect(frames, heads, timestamps, W, H);
    }

    private static List<float[]> segment(Hand left, Hand right, int count) {
        List<float[]> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            frames.add(vector(left, right, true));
        }
        return frames;
    }

    // Vector crudo de 168: manos en [0,63) y [63,126), pose 11..24 en [126,168).
    private static float[] vector(Hand left, Hand right, boolean shoulders) {
        float[] v = new float[168];
        if (left != null) {
            left.writeTo(v, 0);
        }
        if (right != null) {
            right.writeTo(v, 63);
        }
        if (shoulders) {
            put(v, 126, CX - SHOULDERS / 2, CY, 0);
            put(v, 129, CX + SHOULDERS / 2, CY, 0);
            put(v, 162, CX - SHOULDERS / 3, CY + 1.6f * SHOULDERS, 0);
            put(v, 165, CX + SHOULDERS / 3, CY + 1.6f * SHOULDERS, 0);
        }
        return v;
    }

    // Guarda un landmark en píxeles con la normalización de MediaPipe.
    private static void put(float[] v, int offset, float x, float y, float z) {
        v[offset] = x / W;
        v[offset + 1] = y / H;
        v[offset + 2] = z / W;
    }

    /** Mano sintética de 21 landmarks, en píxeles. */
    private static final class Hand {
        private final float[][] points = new float[21][];

        // Índice extendido en la dirección (dx, dy, dz); el resto de los dedos, plegados.
        static Hand pointing(float wx, float wy, float dx, float dy, float dz) {
            float[] d = unit(dx, dy, dz);
            Hand hand = new Hand();
            hand.fillPalm(wx, wy, d);
            float[] mcp = hand.points[5];
            for (int k = 1; k <= 3; k++) {
                hand.points[5 + k] = add(mcp, scale(d, PALM * 0.3f * k));
            }
            hand.foldFingers(wx, wy, d, 9, 13, 17);
            return hand;
        }

        // Mano que señala con la punta del índice apoyada en (tx, ty).
        static Hand touching(float tx, float ty, float... direction) {
            float[] d = unit(direction[0], direction[1], direction[2]);
            // La punta queda a 1,9 palmas de la muñeca a lo largo del dedo.
            return pointing(tx - d[0] * PALM * 1.9f, ty - d[1] * PALM * 1.9f, d[0], d[1], d[2]);
        }

        // Todos los dedos extendidos hacia arriba.
        static Hand open(float wx, float wy) {
            float[] up = {0, -1, 0};
            Hand hand = new Hand();
            hand.fillPalm(wx, wy, up);
            for (int base : new int[] {5, 9, 13, 17}) {
                for (int k = 1; k <= 3; k++) {
                    hand.points[base + k] = add(hand.points[base], scale(up, PALM * 0.3f * k));
                }
            }
            return hand;
        }

        // Todos los dedos plegados, índice incluido.
        static Hand fist(float wx, float wy) {
            float[] up = {0, -1, 0};
            Hand hand = new Hand();
            hand.fillPalm(wx, wy, up);
            hand.foldFingers(wx, wy, up, 5, 9, 13, 17);
            return hand;
        }

        // Muñeca, pulgar y nudillos: la palma apunta en la dirección del antebrazo.
        private void fillPalm(float wx, float wy, float[] axis) {
            float[] wrist = {wx, wy, 0};
            float[] side = Math.abs(axis[0]) > 0.9f ? new float[] {0, 1, 0} : new float[] {1, 0, 0};
            points[0] = wrist;
            for (int k = 1; k <= 4; k++) {
                points[k] = add(wrist, add(scale(axis, PALM * 0.2f * k), scale(side, -PALM * 0.3f)));
            }
            int lane = 0;
            for (int base : new int[] {5, 9, 13, 17}) {
                points[base] = add(add(wrist, scale(axis, PALM)), scale(side, PALM * 0.2f * (lane++ - 1)));
            }
        }

        // Dedos plegados: las puntas vuelven hacia el centro de la palma.
        private void foldFingers(float wx, float wy, float[] axis, int... bases) {
            float[] palm = add(new float[] {wx, wy, 0}, scale(axis, PALM * 0.6f));
            for (int base : bases) {
                points[base + 1] = add(points[base], scale(axis, PALM * 0.2f));
                points[base + 2] = add(palm, scale(axis, PALM * 0.1f));
                points[base + 3] = palm;
            }
        }

        void writeTo(float[] v, int offset) {
            float[] wrist = points[0];
            for (int i = 0; i < 21; i++) {
                float[] p = points[i];
                // La z de la mano de MediaPipe es relativa a la muñeca.
                put(v, offset + i * 3, p[0], p[1], p[2] - wrist[2]);
            }
        }

        private static float[] unit(float x, float y, float z) {
            float n = (float) Math.sqrt(x * x + y * y + z * z);
            return new float[] {x / n, y / n, z / n};
        }

        private static float[] add(float[] a, float[] b) {
            return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
        }

        private static float[] scale(float[] a, float k) {
            return new float[] {a[0] * k, a[1] * k, a[2] * k};
        }
    }
}
