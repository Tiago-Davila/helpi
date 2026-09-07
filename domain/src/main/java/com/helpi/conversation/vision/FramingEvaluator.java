package com.helpi.conversation.vision;

/**
 * Evalúa el encuadre para dar mensajes accionables. Distingue evidencia:
 * nunca afirma "sacaste las manos" si el extractor solo dejó de detectarlas.
 */
public final class FramingEvaluator {

    public enum Issue {
        OK,
        /** No hay persona utilizable en el cuadro. */
        SIN_PERSONA,
        /** No se ven los hombros: no se puede centrar ni traducir. */
        SIN_HOMBROS,
        /** Alguna mano está cerca del borde del cuadro. */
        MANOS_AL_BORDE,
        /** Se perdió de vista una mano que venía detectada. */
        MANO_PERDIDA
    }

    private boolean leftWasPresent;
    private boolean rightWasPresent;

    public Issue evaluate(FrameObservation obs) {
        if (!obs.shouldersVisible && !obs.left.present && !obs.right.present) {
            leftWasPresent = false;
            rightWasPresent = false;
            return Issue.SIN_PERSONA;
        }
        if (!obs.shouldersVisible) {
            return Issue.SIN_HOMBROS;
        }

        boolean lostLeft = leftWasPresent && !obs.left.present;
        boolean lostRight = rightWasPresent && !obs.right.present;
        leftWasPresent = obs.left.present;
        rightWasPresent = obs.right.present;
        if (lostLeft || lostRight) {
            return Issue.MANO_PERDIDA;
        }

        if ((obs.left.present && obs.left.nearEdge)
                || (obs.right.present && obs.right.nearEdge)) {
            return Issue.MANOS_AL_BORDE;
        }
        return Issue.OK;
    }
}
