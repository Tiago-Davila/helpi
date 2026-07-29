# Puerto de clasificación y protocolo WebSocket del arnés dev/eval

**Versión**: `1.0.0`

**Estado**:

- **§1 Puerto de clasificación** — Normativo. Es la frontera de producción entre el cliente y el
  modelo (Principio XI, NFR-015).
- **§2 Protocolo WebSocket** — **SOLO desarrollo y evaluación.** Prohibido en el build de
  producción.

---

## Advertencia de alcance

El canal WebSocket de §2 transporta **keypoints por la red**. En producción eso está prohibido:

| Regla | Fuente |
|---|---|
| Extracción y clasificación íntegramente en el dispositivo | FR-002 |
| Los keypoints MUST NOT abandonar el dispositivo | NFR-006, DD-005 |
| "Inferencia en servidor y cualquier servicio remoto de reconocimiento" | spec §Out of Scope |
| Cero keypoints en el tráfico saliente, verificable por inspección | SC-004 |

El Principio VII de la constitution **permitiría** que los keypoints viajen; la spec es más estricta
y decide que no viajen, porque el clasificador es local y no hay motivo para transmitirlos.

Por eso este canal vive en `backend/devinfer`, un servicio **separado** de `backend/polish`, y el
build de producción **no contiene su código cliente**. La propiedad de SC-004 no puede depender de
una bandera de configuración: tiene que ser verificable por inspección del artefacto distribuido
(SC-013 aplica el mismo criterio a la instrumentación de evaluación).

**Para qué existe entonces**

1. Iterar el modelo en PyTorch contra el cliente real antes de que exista el export a ONNX
   (Fase C del [plan](../plan.md)).
2. Sostener la medición empírica del **Nivel 2** del contrato de keypoints (NFR-014): permite
   procesar el mismo video por el camino de Python y por el del navegador y comparar los vectores
   resultantes en un mismo lugar.
3. Reproducir en un entorno controlado la demo del Exp 5 de la fase exploratoria.

---

## 1. Puerto de clasificación (normativo, producción)

El cliente consume el modelo por un puerto. Ningún módulo aguas arriba conoce la arquitectura, los
pesos ni el índice de clases (Principio XI).

```text
Classifier {
  classify(batch: SecuenciaKeypoints[]) -> Prediccion[]
  metadata() -> { model_id, kp_contract_version, seq_len, input_dim, n_classes }
}

SecuenciaKeypoints = float32[SEQ_LEN, 201]        # conforme a contracts/keypoints.md

Prediccion {
  topk!  : { etiqueta: GlosaId, confianza: float32 }[]   # ordenado desc, k configurable (>= 3)
  # la confianza está normalizada a [0, 1] y suma <= 1 sobre las 64 clases
}
```

**Reglas**

- `classify` acepta un **lote**. Las 3 segmentaciones candidatas de un evento (FR-009) se clasifican
  en una sola llamada, no en tres llamadas sucesivas. Es lo que hace que los 3 intentos casi no
  consuman presupuesto de latencia ([research.md R-008](../research.md)).
- El puerto **no conoce el umbral** y no decide nada. Devuelve confianza; la política de confianza
  decide (AD-04). Un clasificador que devolviera "no entendí" mezclaría dos responsabilidades y
  volvería intesteable la puerta G2.
- El puerto **rechaza** toda entrada cuya forma no sea `(SEQ_LEN, 201)`.
- `metadata().kp_contract_version` se valida contra la versión que implementa el cliente al cargar
  el modelo. Si no coinciden, **falla de forma ruidosa** en vez de clasificar
  ([data-model.md §7](../data-model.md)).
- El vocabulario cerrado se respeta por construcción: `topk` solo contiene glosas del vocabulario.
  Nada fuera de él se fuerza a la clase más cercana — eso lo resuelve el umbral, no el modelo
  (FR-003, FR-017).

**Implementaciones**

| Adaptador | Entorno | Estado |
|---|---|---|
| `OnnxRuntimeWebClassifier` | Producción | Predeterminado ([research.md R-003](../research.md)) |
| `WebSocketClassifier` | Dev / eval | §2. Nunca se incluye en el build de producción |

---

## 2. Protocolo WebSocket (dev/eval)

**Endpoint**: `ws://<host>:<port>/ws/infer` · **Codificación**: JSON UTF-8 · **Versión**: `1`

Todo mensaje lleva `type` y `v`. Los mensajes desconocidos se ignoran con un `error` de severidad
`warning`, nunca cierran la conexión.

### 2.1 Cliente → servidor

**`hello`** — apertura de sesión

```json
{
  "v": 1,
  "type": "hello",
  "client": "frontend-dev",
  "kp_contract_version": "2.0.0",
  "seq_len": 40,
  "input_dim": 201,
  "session_id": "ephemeral-uuid"
}
```

`session_id` es efímero, generado por conexión, y **no identifica a una persona**. No se persiste en
el servidor.

**`classify`** — solicitud de clasificación de hasta 3 candidatas del mismo evento

```json
{
  "v": 1,
  "type": "classify",
  "request_id": "r-17",
  "event_id": "e-4",
  "candidates": [
    { "attempt": 1, "sequence": [[0.0, 0.0, "... 201 floats"], "... SEQ_LEN filas"] },
    { "attempt": 2, "sequence": [] },
    { "attempt": 3, "sequence": [] }
  ]
}
```

- `candidates` tiene entre 1 y **3** elementos. Más de 3 se rechaza con `error.code = "too_many_candidates"`
  (SC-012 se verifica en el cliente, y el servidor lo refuerza).
- Cada `sequence` es exactamente `[SEQ_LEN][201]` float. Cualquier otra forma se rechaza con
  `error.code = "bad_shape"`; el servidor **no** rellena, no recorta y no reordena.

**`bye`**

```json
{ "v": 1, "type": "bye" }
```

### 2.2 Servidor → cliente

**`ready`**

```json
{
  "v": 1,
  "type": "ready",
  "model_id": "exp-2026-08-12-a",
  "kp_contract_version": "2.0.0",
  "seq_len": 40,
  "input_dim": 201,
  "n_classes": 64
}
```

Si `kp_contract_version` no coincide con la del `hello`, el servidor responde `error` con
`code = "contract_mismatch"` y **cierra la conexión**. Clasificar con contratos distintos es
precisamente el fallo silencioso que el Principio IV declara catastrófico: no hay ninguna
degradación aceptable aquí.

**`prediction`**

```json
{
  "v": 1,
  "type": "prediction",
  "request_id": "r-17",
  "event_id": "e-4",
  "results": [
    { "attempt": 1, "topk": [ { "label_id": 12, "confidence": 0.91 },
                              { "label_id": 40, "confidence": 0.04 },
                              { "label_id":  7, "confidence": 0.02 } ] },
    { "attempt": 2, "topk": [] },
    { "attempt": 3, "topk": [] }
  ],
  "inference_ms": 12.4
}
```

- El servidor devuelve **las tres** predicciones. **No decide** cuál gana ni aplica umbral: eso es
  del cliente (AD-04). Un servidor que aplicara el umbral rompería la separación que hace verificable
  SC-005 y SC-016.
- `label_id` es un índice de clase; la traducción a etiqueta legible es del cliente, con el
  `labels.json` del **mismo** artefacto de modelo.
- `inference_ms` es diagnóstico. No es L1: L1 se mide en el cliente y sobre el camino de producción.

**`error`**

```json
{
  "v": 1,
  "type": "error",
  "request_id": "r-17",
  "code": "bad_shape",
  "severity": "error",
  "message": "expected (40, 201), got (37, 201)"
}
```

| `code` | Severidad | Efecto |
|---|---|---|
| `contract_mismatch` | fatal | El servidor cierra la conexión |
| `bad_shape` | error | Se rechaza la petición; la conexión sigue |
| `too_many_candidates` | error | Se rechaza la petición |
| `model_unavailable` | fatal | El servidor cierra la conexión |
| `unknown_message` | warning | Se ignora el mensaje |

`message` es para desarrolladores y **nunca** se muestra a la persona usuaria (FR-023 exige mensajes
que nombren causa y acción; un detalle técnico no lo es).

### 2.3 Estados de la conexión y degradación

```text
DESCONECTADO ──conectar──► ABIERTO ──hello/ready──► LISTO ──classify──► ESPERANDO
     ▲                                                 ▲                    │
     └──────────── cierre / error fatal ───────────────┴─── prediction ─────┘
```

**Reglas de resiliencia** (con tests, según el [plan](../plan.md) §Estrategia de pruebas):

1. **Reconexión** con backoff exponencial y tope: 250 ms, 500 ms, 1 s, 2 s, 4 s, máximo 5 intentos.
   Tras el tope, el adaptador se declara no disponible.
2. **Pérdida de conexión durante un reconocimiento**: el `request_id` en vuelo se resuelve como
   **no-reconocido**. El cliente no queda esperando, no reintenta la clasificación con datos
   posiblemente rancios, y **jamás** inventa un resultado. Comunicar "no entendí" ante una falla de
   transporte es la única respuesta compatible con el Principio VIII.
3. **Timeout por petición**: vencido, mismo camino que el punto 2.
4. **Reanudación**: al reconectar se envía un `hello` nuevo. No hay reanudación de sesión: las
   peticiones en vuelo ya se resolvieron como no-reconocidas.
5. El buffer de keypoints del cliente **no se retransmite** tras una reconexión.

### 2.4 Lo que este canal NO transporta, nunca

- Frames, imágenes, miniaturas o cualquier derivado visual (Principio VII, NFR-007).
- Identificadores de persona, de dispositivo o correlacionables entre sesiones.
- Preferencias, historial o registro de descartes.

Y, para que quede sin ambigüedad: **este canal completo no existe en producción.** Lo anterior
aplica a su uso en desarrollo y evaluación, donde igualmente rige la prohibición de video.
