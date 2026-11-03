# Feature Specification: Reconocimiento en varias lenguas de señas (LSA, ASL, LSE)

**Feature Branch**: `003-multilengua-senas`

**Created**: 2026-11-03

**Status**: Approved

**Input**: Issue #225. Permitir elegir la lengua de señas que Eva reconoce: Lengua de
Señas Argentina (LSA), American Sign Language (ASL) y Lengua de Signos Española (LSE).

## Contexto

Hasta ahora la app carga siempre `assets/lsa/`. El repositorio de entrenamiento produce
un modelo por lengua con el mismo contrato de keypoints v3 (40 × 168) y un manifiesto
común que declara `language`, `license` y `attribution`. Cada modelo es un clasificador
de vocabulario cerrado distinto: un índice de clase solo significa algo junto al
catálogo de su misma lengua.

## User Scenarios & Testing

### User Story 1 — Elegir la lengua de señas (P1)

Una persona que señas en LSE elige "LSE" en los ajustes; desde ese momento Eva
reconoce señas de LSE y la elección se conserva al volver a abrir la app.

**Acceptance Scenarios**:

1. **Given** la app recién instalada, **When** se abre la conversación, **Then** la
   lengua activa es LSA.
2. **Given** LSA activa, **When** la persona elige LSE, **Then** se descarta la seña en
   curso, se libera el modelo de LSA y se carga el de LSE.
3. **Given** LSE elegida, **When** se cierra y se vuelve a abrir la app, **Then** la
   lengua activa sigue siendo LSE.

### User Story 2 — Lengua no disponible (P1)

**Acceptance Scenarios**:

1. **Given** que el paquete de ASL falta o es inválido, **When** la persona elige ASL,
   **Then** el canal visual queda inhabilitado con el motivo, y la app **no** vuelve a
   LSA en silencio.
2. **Given** que el paquete de ASL es inválido, **When** la persona elige LSA o LSE,
   **Then** esas lenguas cargan normalmente.

### User Story 3 — Atribución y limitaciones por lengua (P2)

La interfaz muestra la licencia y la atribución del dataset de la lengua activa, y sus
limitaciones conocidas.

### Edge Cases

- Una ventana de keypoints capturada con una lengua nunca se clasifica con el modelo de
  otra, aunque la inferencia ya estuviera en curso al cambiar.
- Un manifiesto de otra lengua copiado en la carpeta equivocada se rechaza.
- Una seña fuera del vocabulario de la lengua activa se resuelve como no reconocida.

## Requirements

### Functional Requirements

- **FR-001**: La persona puede elegir la lengua de señas entre LSA, ASL y LSE.
- **FR-002**: La elección se persiste en el dispositivo; el valor inicial es LSA.
- **FR-003**: Cada lengua tiene su carpeta de assets (`lsa/`, `asl/`, `lse/`) con modelo,
  catálogo y manifiesto, verificados juntos como hoy: SHA-256 del modelo y del catálogo,
  `contractVersion`, forma 40 × 168 y `numClasses`.
- **FR-004**: El manifiesto declara `language`; si no coincide con la carpeta, la carga
  falla como inválida.
- **FR-005**: La falta o invalidez del paquete de una lengua afecta solo a esa lengua.
- **FR-006**: Hay un solo clasificador cargado en memoria a la vez. Al cambiar de lengua
  se cierra el intérprete anterior antes de usar el nuevo.
- **FR-007**: Al cambiar de lengua se descarta la ventana de keypoints en curso y se
  ignora cualquier resultado de una inferencia lanzada con la lengua anterior.
- **FR-008**: Cada lengua tiene su propio umbral de confianza, con el valor recomendado
  por su entrenamiento como valor inicial. La regla de confianza explícita (silencio
  antes que adivinanza) aplica igual a las tres lenguas.
- **FR-009**: Si el paquete de la lengua elegida es inválido, el canal visual se
  inhabilita con el motivo; nunca se cae a otra lengua en silencio.
- **FR-010**: Se exponen `license` y `attribution` del manifiesto de la lengua activa y
  la interfaz los muestra.
- **FR-011**: El reconocimiento sigue ocurriendo en el dispositivo. Ni el video ni los
  keypoints salen del teléfono; no hay servidor de inferencia.
- **FR-012**: Modo frase, léxico y plantillas siguen siendo solo de LSA. Con otra lengua
  activa, el modo frase no está disponible y las señas se muestran como glosas sueltas.
- **FR-013**: Las glosas de ASL se muestran y se pronuncian en español (campo `texto_es`
  del catálogo de ASL).
- **FR-014**: La interfaz comunica las limitaciones de cada lengua (ver tabla).

### Limitaciones por lengua (FR-014)

| Lengua | Dataset | Licencia | Limitaciones que se comunican |
|---|---|---|---|
| LSA | LSA-94 (LSA64 + grabaciones propias) | CC BY-NC-SA 4.0 | Vocabulario cerrado de 94 señas; señantes oyentes y diestros; laboratorio con guantes; zurdos fallan; sin señas de emergencia. |
| ASL | Google ISLR + PopSign ASL | CC BY 4.0 | Vocabulario cerrado infantil; grabado con teléfono por señantes variados; glosas traducidas al español. |
| LSE | SWL-LSE (SignaMed) | CC BY 4.0 | Vocabulario cerrado de 300 señas de salud; regionalismos de España sin validar. |

### Key Entities

- **SignLanguage**: LSA, ASL o LSE; código de carpeta y umbral recomendado.
- **ModelBundle**: modelo, catálogo y manifiesto de una lengua, verificados juntos.

## Decisiones

- Los tres modelos van dentro del APK (unos 20 MB en total); solo el de la lengua
  elegida se carga en memoria. Play Asset Delivery queda fuera de alcance salvo que el
  APK crezca más de lo aceptable.
- `lsa/lsa-manifest.json` conserva su nombre; `asl/` y `lse/` usan `manifest.json`.
- Umbrales recomendados: LSA 0,90 (actual); LSE 0,47 según su entrenamiento, que la app
  eleva al mínimo permitido de 0,50 (más conservador); ASL 0,90 hasta que su
  entrenamiento recomiende otro.

## Success Criteria

- **SC-001**: Cambiar de lengua nunca clasifica una ventana con el modelo de otra
  lengua (verificado por tests).
- **SC-002**: Con una lengua cargada, el consumo de memoria es igual al actual (±10 %).
- **SC-003**: Un paquete inválido inhabilita solo su lengua.
