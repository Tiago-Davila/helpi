package com.helpi.conversation.vision;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Detecta si un segmento es un señalamiento con el índice y hacia dónde:
 * al pecho (yo), a la cámara (usted) o a un costado (él o ella). Es geometría
 * sobre los landmarks, no un clasificador.
 *
 * <p>Lee el vector crudo de 168 del contrato de keypoints sin modificarlo ni
 * normalizarlo: calcula su propia escala (anchos de hombro) solo para medir.
 */
public final class PointingDetector {

    private static final int LEFT_HAND = 0;
    private static final int RIGHT_HAND = 63;
    private static final int LEFT_SHOULDER = 126;
    private static final int RIGHT_SHOULDER = 129;
    private static final int WRIST = 0;
    private static final int INDEX_MCP = 5;
    private static final int INDEX_PIP = 6;
    private static final int INDEX_TIP = 8;
    private static final int MIDDLE_MCP = 9;
    private static final int[] OTHER_TIPS = {12, 16, 20};

    private final PointingConfig config;

    /** Crea el detector con sus umbrales. */
    public PointingDetector(PointingConfig config) {
        this.config = config;
    }

    /**
     * Analiza los cuadros de un segmento: vectores crudos de 168, sus
     * timestamps y el tamaño de la imagen que analizó MediaPipe.
     */
    public Pointing detect(List<float[]> vectors, long[] timestampsMs, int width, int height) {
        if (vectors.size() != timestampsMs.length) {
            throw new IllegalArgumentException("cuadros y timestamps de distinto largo");
        }
        float shoulderWidth = medianShoulderWidth(vectors, width, height);
        if (shoulderWidth <= 0f) {
            return Pointing.NONE;
        }
        List<Sample> samples = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            samples.add(sample(vectors.get(i), timestampsMs[i], width, height));
        }
        Window best = longestStableWindow(samples, shoulderWidth);
        if (best == null || best.durationMs < config.minHoldMs) {
            return Pointing.NONE;
        }
        return new Pointing(classify(best, shoulderWidth), best.durationMs);
    }

    // Mediana del ancho de hombros en píxeles de los cuadros que los tienen; 0 si ninguno.
    private static float medianShoulderWidth(List<float[]> vectors, int width, int height) {
        List<Float> widths = new ArrayList<>();
        for (float[] v : vectors) {
            if (present(v, LEFT_SHOULDER) && present(v, RIGHT_SHOULDER)) {
                float dx = (v[LEFT_SHOULDER] - v[RIGHT_SHOULDER]) * width;
                float dy = (v[LEFT_SHOULDER + 1] - v[RIGHT_SHOULDER + 1]) * height;
                widths.add((float) Math.hypot(dx, dy));
            }
        }
        if (widths.isEmpty()) {
            return 0f;
        }
        float[] sorted = new float[widths.size()];
        for (int i = 0; i < sorted.length; i++) {
            sorted[i] = widths.get(i);
        }
        Arrays.sort(sorted);
        int mid = sorted.length / 2;
        return sorted.length % 2 == 1 ? sorted[mid] : (sorted[mid - 1] + sorted[mid]) / 2f;
    }

    // Un cuadro señala si exactamente una mano tiene la forma de señalar y hay hombros.
    private Sample sample(float[] v, long timestampMs, int width, int height) {
        if (!present(v, LEFT_SHOULDER) || !present(v, RIGHT_SHOULDER)) {
            return Sample.notPointing(timestampMs);
        }
        boolean left = isPointingShape(v, LEFT_HAND, width, height);
        boolean right = isPointingShape(v, RIGHT_HAND, width, height);
        if (left == right) {
            return Sample.notPointing(timestampMs);
        }
        int hand = left ? LEFT_HAND : RIGHT_HAND;
        float[] mcp = point(v, hand + INDEX_MCP * 3, width, height);
        float[] tip = point(v, hand + INDEX_TIP * 3, width, height);
        float[] shoulderCenter = {
            (v[LEFT_SHOULDER] + v[RIGHT_SHOULDER]) / 2f * width,
            (v[LEFT_SHOULDER + 1] + v[RIGHT_SHOULDER + 1]) / 2f * height,
        };
        return new Sample(timestampMs, hand, tip, sub(tip, mcp), shoulderCenter);
    }

    // Índice extendido y recto; medio, anular y meñique plegados hacia la palma.
    private boolean isPointingShape(float[] v, int hand, int width, int height) {
        if (!present(v, hand + WRIST * 3) || !present(v, hand + INDEX_TIP * 3)) {
            return false;
        }
        float[] wrist = point(v, hand + WRIST * 3, width, height);
        float palm = norm(sub(point(v, hand + MIDDLE_MCP * 3, width, height), wrist));
        if (palm <= 0f) {
            return false;
        }
        float[] mcp = point(v, hand + INDEX_MCP * 3, width, height);
        float[] pip = point(v, hand + INDEX_PIP * 3, width, height);
        float[] tip = point(v, hand + INDEX_TIP * 3, width, height);
        if (norm(sub(tip, wrist)) < config.indexExtended * palm) {
            return false;
        }
        if (cosine(sub(pip, mcp), sub(tip, pip)) < config.minStraightness) {
            return false;
        }
        for (int fingerTip : OTHER_TIPS) {
            float[] other = point(v, hand + fingerTip * 3, width, height);
            if (norm(sub(other, wrist)) > config.fingerFolded * palm) {
                return false;
            }
        }
        return true;
    }

    // Tramo más largo de cuadros que señalan con la misma mano y la punta quieta.
    private Window longestStableWindow(List<Sample> samples, float shoulderWidth) {
        float maxDrift = config.maxDrift * shoulderWidth;
        Window best = null;
        for (int start = 0; start < samples.size(); start++) {
            Sample anchor = samples.get(start);
            if (!anchor.pointing) {
                continue;
            }
            int end = start;
            while (end + 1 < samples.size()) {
                Sample next = samples.get(end + 1);
                if (!next.pointing || next.hand != anchor.hand || distance2d(next.tip, anchor.tip) > maxDrift) {
                    break;
                }
                end++;
            }
            long duration = samples.get(end).timestampMs - anchor.timestampMs;
            if (best == null || duration > best.durationMs) {
                best = new Window(samples.subList(start, end + 1), duration);
            }
        }
        return best;
    }

    // Dirección promedio del tramo: punta en el torso hacia el cuerpo, o el eje dominante.
    private Pointing.Type classify(Window window, float shoulderWidth) {
        float[] direction = new float[3];
        float[] tip = new float[3];
        float[] center = new float[2];
        for (Sample s : window.samples) {
            float[] unit = scale(s.direction, 1f / Math.max(norm(s.direction), 1e-6f));
            direction = add(direction, unit);
            tip = add(tip, s.tip);
            center[0] += s.shoulderCenter[0];
            center[1] += s.shoulderCenter[1];
        }
        int n = window.samples.size();
        tip = scale(tip, 1f / n);
        center[0] /= n;
        center[1] /= n;
        float ax = Math.abs(direction[0]);
        float ay = Math.abs(direction[1]);
        float az = Math.abs(direction[2]);
        float relX = Math.abs(tip[0] - center[0]) / shoulderWidth;
        float relY = (tip[1] - center[1]) / shoulderWidth;
        boolean inTorso = relX <= config.torsoHalfWidth && relY >= config.torsoTop && relY <= config.torsoBottom;
        // z crece al alejarse de la cámara: el índice apunta hacia el propio cuerpo.
        if (inTorso && direction[2] > 0f) {
            return Pointing.Type.YO;
        }
        if (direction[2] < 0f && az >= config.dominanceRatio * Math.max(ax, ay)) {
            return Pointing.Type.USTED;
        }
        if (!inTorso && ax >= config.dominanceRatio * Math.max(ay, az)) {
            return Pointing.Type.EL_ELLA;
        }
        return Pointing.Type.AMBIGUOUS;
    }

    // Landmark en píxeles con la misma convención del contrato: X=x·W, Y=y·H, Z=z·W.
    private static float[] point(float[] v, int offset, int width, int height) {
        return new float[] {v[offset] * width, v[offset + 1] * height, v[offset + 2] * width};
    }

    // No detectado = ceros, por contrato.
    private static boolean present(float[] v, int offset) {
        return v[offset] != 0f || v[offset + 1] != 0f || v[offset + 2] != 0f;
    }

    private static float[] sub(float[] a, float[] b) {
        return new float[] {a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static float[] add(float[] a, float[] b) {
        return new float[] {a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static float[] scale(float[] a, float k) {
        return new float[] {a[0] * k, a[1] * k, a[2] * k};
    }

    private static float norm(float[] a) {
        return (float) Math.sqrt(a[0] * a[0] + a[1] * a[1] + a[2] * a[2]);
    }

    private static float distance2d(float[] a, float[] b) {
        return (float) Math.hypot(a[0] - b[0], a[1] - b[1]);
    }

    private static float cosine(float[] a, float[] b) {
        float na = norm(a);
        float nb = norm(b);
        if (na == 0f || nb == 0f) {
            return 0f;
        }
        return (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]) / (na * nb);
    }

    /** Un cuadro analizado: si señala, con qué mano, dónde está la punta y hacia dónde apunta. */
    private static final class Sample {
        final long timestampMs;
        final boolean pointing;
        final int hand;
        final float[] tip;
        final float[] direction;
        final float[] shoulderCenter;

        Sample(long timestampMs, int hand, float[] tip, float[] direction, float[] shoulderCenter) {
            this.timestampMs = timestampMs;
            this.pointing = true;
            this.hand = hand;
            this.tip = tip;
            this.direction = direction;
            this.shoulderCenter = shoulderCenter;
        }

        private Sample(long timestampMs) {
            this.timestampMs = timestampMs;
            this.pointing = false;
            this.hand = -1;
            this.tip = null;
            this.direction = null;
            this.shoulderCenter = null;
        }

        static Sample notPointing(long timestampMs) {
            return new Sample(timestampMs);
        }
    }

    /** Tramo de cuadros consecutivos que señalan quietos. */
    private static final class Window {
        final List<Sample> samples;
        final long durationMs;

        Window(List<Sample> samples, long durationMs) {
            this.samples = samples;
            this.durationMs = durationMs;
        }
    }
}
