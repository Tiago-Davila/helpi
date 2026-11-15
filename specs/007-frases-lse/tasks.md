---

description: "Tareas de 007-frases-lse"
---

# Tasks: Armado de frases para LSE y pronombres por señalamiento

**Input**: `specs/007-frases-lse/`: [plan.md](plan.md), [spec.md](spec.md).

**Alcance**: solo `helpi-android`. No hay cambios en `helpi-ml` ni en el modelo. Funcionalidad
experimental, sin publicación.

**Tests**: en las fases del motor y del detector, el test se escribe **antes** que el
componente y debe fallar hasta que la tarea siguiente lo cumpla.

## Format: `[ID] [P?] [Story] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos y sin dependencias pendientes).
- **[USn]**: historia de spec.md a la que sirve la tarea.
- **Deps**: tareas que tienen que estar terminadas antes.
- **Ref**: requisito o decisión que origina la tarea. Una tarea sin referencia se marca
  **⚑ REVISAR** (no hay ninguna).

## Estado de las enmiendas

E-1 a E-5 del plan **están aplicadas en spec.md** desde el 2026-11-15 (FR-022, FR-040,
FR-040a, FR-050, FR-050a, US4, US5). Ninguna tarea queda bloqueada por la spec.

## Path Conventions

- `domain/src/main/java/com/helpi/conversation/` → `domain/…/` (Java puro).
- `domain/src/test/java/com/helpi/conversation/` → `domain-test/…/`.
- `app/src/main/java/com/helpi/conversation/` → `app/…/`.
- `app/src/test/java/com/helpi/conversation/` → `app-test/…/`.
- Assets: `app/src/main/assets/`.

---

## Notas de ejecución (2026-11-15)

- **T105**: la carpeta sale de `manifest.language`, ya validado por `ModelBundle` contra la
  lengua elegida; `SessionCoordinator` no necesitó cambios.
- **T106**: el borrador se generó con un script fuera del repositorio; el JSON versionado
  es la fuente de verdad y no queda ningún script en el flujo.
- **T107 + T202**: se hicieron juntas: el léxico ya nació con variantes unificadas,
  compuestos reescritos y `formaYo`.
- **T109, T201, T203**: los tests viven en `domain` (`LsePhraseTest`,
  `LseLexiconCoverageTest`), sin emulador, leyendo los assets reales con la propiedad
  `lse.assets`, igual que `LsaAssets`. `PhraseAssetsTest` (app) suma la carga real de LSE.
- **Redacción**: «DOLOR» con parte del cuerpo produce «Tengo dolor en el estómago.» y no
  «Me duele el estómago.», para que también sea gramatical con plurales («Tengo dolor en
  las amígdalas.»). Spec, plan y tareas se actualizaron con ese texto.
- **T302**: `LexiconTest` ahora espera `entry(-99) == null`; los índices -1..-3 son los
  pronombres. `PhraseResult.hasSynthetic()` no se agregó: la regla de confirmación de la
  fase 4 puede mirar los índices de la oración (`SyntheticSigns.isSynthetic`).
- **T306**: la regla modelo / señalamiento / hueco vive en
  `SessionCoordinator.segmentSign`, probada sin cámara. El recorrido completo desde
  `onLandmarks` con un señalamiento real queda para T307 en el dispositivo.
- **T307, T308** (pendientes): requieren grabar segmentos y medir en el dispositivo de
  referencia. `PointingConfig.defaults()` todavía no está calibrado.
- **T309**: `KeypointContractTest` y `SequenceKeypointContractTest` pasan sin cambios en
  producción. `ModelReferenceTest` es instrumentado: no se corrió sin dispositivo.
- **Variante evaluación**: falla por Robolectric + SQLite (`Illegal connection pointer`)
  también en `develop` sin estos cambios (34 de 84 tests). No es de esta funcionalidad.

---

## Phase 0: Preparación

- [X] T000 Crear la rama `007-frases-lse` desde `develop`. — Deps: — · Ref: CLAUDE.md §8

---

## Phase 1: Motor por lengua y primera persona (US1, P1)

**Goal**: con LSE seleccionada, el armado se habilita y «ESTOMAGO DOLOR» produce «Tengo dolor en el
estómago.». LSA produce exactamente lo mismo que antes.

**Independent Test**: `./gradlew :domain:test` y los tests JVM de `app` en verde, con los
tests de LSA existentes sin modificar.

- [X] T101 [P] [US1] **Test primero**: en `domain-test/…/phrase/TemplateSetTest.java` y `TemplateRendererTest.java`, casos para el marcador `{i.yo}`: se acepta al parsear; resuelve a `formaYo` si la entrada la tiene y a `forma` si no; se combina con `{i.art}` en la misma salida; una plantilla con `{i.yo}` y una palabra de contenido sigue rechazándose. Deben fallar hasta T103. — Deps: T000 · Ref: FR-015, FR-022, FR-020
- [X] T102 [P] [US1] **Test primero**: en `domain-test/…/phrase/LexiconTest.java`, una entrada con `formaYo` se lee; sin `formaYo` queda null; las categorías `cuerpo` y `sintoma` se aceptan; una categoría desconocida sigue fallando. Deben fallar hasta T103. — Deps: T000 · Ref: FR-014, FR-015
- [X] T103 [US1] Implementar en `domain/…/phrase/`: `SignCategory` + `CUERPO`, `SINTOMA`; `LexicalEntry` + `firstPersonForm` (opcional); `Lexicon.parseEntry` lee `formaYo`; `PhraseTemplate.PLACEHOLDER` acepta `yo`; `TemplateRenderer.resolve` lo resuelve. Comentario de una línea por método nuevo. — Deps: T101, T102 · Ref: FR-014, FR-015, FR-022
- [X] T104 [P] [US1] **Test primero**: en `app-test/…/lsa/PhraseAssetsTest.kt`, con lectura inyectada: LSA carga `lsa/…` como hoy; LSE carga `lse/lexico_senas.json` y `lse/plantillas_frases.json`; LSE sin léxico → `Disabled("falta lse/lexico_senas.json")` y LSA sigue `Ready`; léxico de LSE con `modelVersion` distinto de `eva-lse300-v1` → `Disabled`. Debe fallar hasta T105. — Deps: T000 · Ref: FR-001, FR-002, FR-003
- [X] T105 [US1] `app/…/lsa/PhraseAssets.kt`: `load` recibe `SignLanguage` y arma las rutas con su código. Actualizar la llamada en `app/…/session/SessionCoordinator.kt` (`loadVisionChannel`) para pasar `signLanguage`. Actualizar el KDoc de rutas esperadas. — Deps: T104 · Ref: FR-001, FR-002
- [X] T106 [US1] Script de una sola vez `scripts/lse_lexico_borrador.py`: lee `app/src/main/assets/lse/catalogo_senas.json` y escribe un borrador de `lse/lexico_senas.json` con `version`, `modelVersion: eva-lse300-v1`, `catalogVersion: 1.0` e `indice`/`glosa` de las 299 clases, con `categoria`/`forma` por completar. No se conecta al build. Correr con `python -I`. — Deps: T000 · Ref: plan.md Fase 2.2, FR-010
- [X] T107 [US1] Completar en `app/src/main/assets/lse/lexico_senas.json` la **categoría y la forma base** de las 299 entradas (sin todavía unificar variantes ni reescribir compuestos), más `formaYo` para las entradas que aparecen en las plantillas de T108 y en los casos de T109. — Deps: T103, T106 · Ref: FR-010, FR-011, FR-015
- [X] T108 [US1] Crear `app/src/main/assets/lse/plantillas_frases.json` con `lexicoVersion` del léxico y las plantillas de plan.md Fase 1.5 (sin las que usan `glosa:YO`, que llegan en T305). Mínimo FR-021: `sintoma`, `cuerpo`+`sintoma`, `persona`+`sintoma`, `persona`+`verbo`, `verbo`+`cuerpo`, `cualidad`, `persona`+`cualidad`. — Deps: T107 · Ref: FR-020, FR-021, FR-022
- [X] T109 [US1] **Test de la cadena de datos**: `app-test/…/lsa/LsePhraseTest.kt` carga los assets reales de LSE y verifica US1: «ESTOMAGO DOLOR» → «Tengo dolor en el estómago.»; «FIEBRE (variante 2)» sola → «Tengo fiebre.»; entrada sin `formaYo` → forma base; oración con `¿?` → sin plantilla ni primera persona. — Deps: T105, T108 · Ref: US1-1, US1-2, US1-3, US1-6
- [X] T110 [US1] Verificar NFR-003: correr los tests de armado de LSA existentes (`PhraseBuilderTest`, `TemplateMatcherTest`, `PhraseAssetsTest` de LSA) **sin editar sus expectativas**. Si alguno cambia, es una regresión de T103 o T105. — Deps: T103, T105 · Ref: NFR-003, SC-003, US1-4

**Checkpoint F1**: con LSE seleccionada en el dispositivo, una oración muestra frase armada en
lugar de glosas sueltas; LSA sin cambios.

---

## Phase 2: Variantes y compuestos (US2, P1)

**Goal**: ninguna frase muestra «(variante 2)» ni un compuesto literal.

**Independent Test**: `LseLexiconCoverageTest` y la tabla de ≥ 40 oraciones en verde.

- [X] T201 [P] [US2] **Test primero**: `app-test/…/lsa/LseLexiconCoverageTest.kt` sobre los assets reales: 299 entradas; glosa idéntica al catálogo por índice; ninguna `forma` ni `formaYo` contiene `(`, `)` ni «variante»; las clases que comparten glosa base (texto antes del primer paréntesis o de « (variante») comparten forma y categoría, salvo una lista explícita de excepciones en el test (por ejemplo «Tension (CT) alta»). Debe fallar hasta T202. — Deps: T107 · Ref: SC-001, FR-010, FR-011, FR-012
- [X] T202 [US2] Completar `lse/lexico_senas.json`: unificar variantes (FR-012), reescribir compuestos para leerse en español sin conjugar («Comer sin» → «sin comer», «Dormir insomnio» → «insomnio», «Mente olvidar» → «olvidar») (FR-013) y agregar `formaYo` donde aplique («tengo dolor», «tengo fiebre», «estoy débil», «no puedo dormir»). Subir `version` del léxico y `lexicoVersion` de las plantillas. — Deps: T201 · Ref: FR-012, FR-013, FR-015
- [X] T203 [US2] Ampliar `LsePhraseTest.kt` hasta **≥ 40 oraciones** con frase esperada, que cubran: cada plantilla de LSE, las tres variantes de «Diarrea» con el mismo texto, «Comer sin» → «Sin comer.», un compuesto dentro de una oración más larga, la seña «Mierda» conservada sin censura y glosas sin plantilla en MAYÚSCULAS. — Deps: T202 · Ref: SC-002, US2-1, US2-2, US2-3

**Checkpoint F2**: SC-001 y SC-002 en verde.

---

## Phase 3: Pronombres por señalamiento (US3, P2)

**Goal**: señalar hacia la cámara antes de «AYUDAR» produce «Usted: ayudar.», con cualquiera
de las dos manos, sin tocar el contrato v3.

**Independent Test**: `PointingDetectorTest` con secuencias sintéticas y grabadas; test de
contrato v3 sin cambios.

- [X] T301 [P] [US3] **Test primero**: `domain-test/…/phrase/SyntheticSignsTest.java`: `Lexicon.entry(-1|-2|-3)` devuelve YO / USTED / EL_ELLA con categoría `persona` y formas «yo», «usted», «él o ella»; un índice negativo desconocido devuelve null; `PhraseCompatibility` acepta `glosa:YO` aunque no esté en el catálogo y sigue rechazando una glosa literal inexistente. Debe fallar hasta T302. — Deps: T103 · Ref: FR-034
- [X] T302 [US3] Crear `domain/…/phrase/SyntheticSigns.java` (índices negativos fijos, compartidos por las dos lenguas); `Lexicon.entry` resuelve `i < 0` ahí; `PhraseCompatibility` suma las glosas sintéticas a las válidas para plantillas; `PhraseResult.hasSynthetic()`. — Deps: T301 · Ref: FR-034, FR-050a
- [X] T303 [P] [US3] **Test primero**: `domain-test/…/vision/PointingDetectorTest.java` con secuencias **sintéticas** de vectores de 168 construidas en el test: índice extendido hacia el torso → YO; hacia la cámara → USTED; a cada costado → EL_ELLA; misma forma con la mano izquierda → mismo resultado; dos índices extendidos → nada; mano abierta, puño → nada; dirección entre dos zonas → ambiguo; punta en movimiento (deriva > `maxDrift`) → nada; sin hombros → nada. Debe fallar hasta T304. — Deps: T000 · Ref: FR-030, FR-032, FR-033, FR-035, FR-036, US3-1, US3-2, US3-3, US3-5
- [X] T304 [US3] Crear `domain/…/vision/PointingConfig.java` (`defaults()`: `minHoldMs`, `maxDrift`, `contactDistance`, `dominanceRatio`), `Pointing.java` (tipo + confianza geométrica) y `PointingDetector.java` según plan.md Fase 3.2: escala propia en anchos de hombro, mano con el índice extendido, quietud, dirección punta 8 − nudillo 5. Lee los offsets del vector (mano izquierda 0, derecha 63, hombros 126/129); no modifica ni normaliza el vector. — Deps: T303 · Ref: FR-030 a FR-036
- [X] T305 [US3] Agregar a `lse/plantillas_frases.json` las plantillas con `glosa:YO` (`yo-sintoma`, `yo-cuerpo-sintoma`) y casos en `LsePhraseTest.kt`: «YO DOLOR» → «Tengo dolor.»; «USTED AYUDAR» → «Usted: ayudar.»; «EL_ELLA FIEBRE» → infinitivo / forma base, sin `formaYo`. — Deps: T203, T302 · Ref: FR-022, FR-034, caso borde "Otra persona en LSE"
- [X] T306 [US3] Integrar en `app/…/session/SessionCoordinator.kt`, en el ejecutor del clasificador y después de la inferencia: si el modelo acepta → modelo; si no acepta y el detector devuelve un pronombre → `RecognizedSign` sintético con confianza 1; si no → hueco, como hoy. Tests en `app-test/…/session/SessionCoordinatorTest.kt` con clasificador falso: los tres caminos, y en LSA el mismo comportamiento. — Deps: T302, T304 · Ref: US3-4, US3-6, FR-030
- [ ] T307 [US3] Grabar segmentos reales con el build de evaluación (YO, USTED, EL_ELLA, seña léxica con índice extendido como la letra «D» de LSA, reposo; con las dos manos), exportarlos como vectores de 168 a `domain/src/test/resources/pointing/` y etiquetarlos. Agregar a `PointingDetectorTest` la medición de SC-004 (≥ 90 % de aciertos en señalamientos claros, < 5 % de pronombres en señas léxicas) y registrar cuántos señalamientos acepta además el modelo de LSE con umbral 0,50. — Deps: T304 · Ref: SC-004, plan.md Riesgos
- [ ] T308 [US3] Medir en el dispositivo de referencia el p95 del detector por segmento; debe ser < 2 ms. Dejar el número en el PR. — Deps: T306 · Ref: NFR-001
- [ ] T309 [US3] Verificar que el test de contrato v3 (`contract_v3_fixture.json`) y `ModelReferenceTest` pasan sin cambios. — Deps: T306 · Ref: FR-031, CLAUDE.md §2 y §6

**Checkpoint F3**: SC-004 medido; contrato v3 intacto. Si los falsos pronombres superan el
5 %, ajustar `PointingConfig` antes de seguir.

---

## Phase 4: Confirmación y aviso experimental (US5, P2)

**Goal**: en LSE ninguna frase llega al oyente sin que quien seña la confirme.

**Independent Test**: tests del coordinador para los caminos de confirmación; prueba manual en
el dispositivo.

- [ ] T401 [US5] **Test primero**: en `app-test/…/session/SessionCoordinatorTest.kt`: oración LSE cerrada → queda como candidata, no se publica ni se encola voz; `confirmPhraseCandidate` → se publica y se encola; con texto editado → se publica el texto editado; `rejectPhraseCandidate` → nada; oración LSA sin sintéticos → se publica directo como hoy; oración LSA con YO → candidata. Debe fallar hasta T402. — Deps: T306 · Ref: FR-050, FR-050a, US5-2
- [ ] T402 [US5] `SessionCoordinator.deliverSentence`: aplicar la regla de FR-050a; la candidata reutiliza `phraseCandidate` y los métodos `confirmPhraseCandidate` / `rejectPhraseCandidate`, conservando las glosas de la oración. — Deps: T401 · Ref: FR-050, FR-050a
- [ ] T403 [US5] `app/…/ui/ConversationScreen.kt`: el diálogo «¿Usar esta frase?» muestra debajo del campo de texto las glosas en MAYÚSCULAS, incluidos los pronombres detectados; la primera persona por defecto no aparece como glosa. — Deps: T402 · Ref: FR-051, US5-1
- [ ] T404 [P] [US5] `ConversationScreen.kt`: aviso fijo y breve «Frases en LSE: experimental» cuando la lengua seleccionada es LSE. — Deps: T105 · Ref: FR-052

**Checkpoint F4**: en el dispositivo, con LSE, cada oración pasa por el diálogo; con LSA sin
señalamiento, el flujo es el de siempre.

---

## Phase 5: Zonas del cuerpo por contacto (US4, P3)

**Goal**: tocarse el abdomen y señar «DOLOR» produce «Tengo dolor en el abdomen.».

- [ ] T501 [US4] `SessionCoordinator.CapturedFrame`: conservar pose 0, 9 y 10 crudos junto al `vector`. `KeypointContract` y el tensor no cambian; T309 debe seguir en verde. — Deps: T306 · Ref: FR-040a
- [ ] T502 [P] [US4] **Test primero**: en `PointingDetectorTest.java`, contacto de la punta con cabeza, garganta y abdomen → zona; contacto con el pecho → YO; zona sin su landmark de referencia (cara no detectada, caderas fuera de cuadro) → nada. Debe fallar hasta T503. — Deps: T304 · Ref: FR-040, US4-1, US4-2
- [ ] T503 [US4] `PointingDetector` recibe los landmarks de cabeza opcionales y detecta las tres zonas; `SyntheticSigns` suma `-10` CABEZA, `-11` GARGANTA, `-12` ABDOMEN con categoría `cuerpo` (forma y género para el artículo). — Deps: T501, T502 · Ref: FR-040, FR-041
- [ ] T504 [US4] Casos en `LsePhraseTest.kt`: «ABDOMEN DOLOR» → «Tengo dolor en el abdomen.»; «YO GARGANTA DOLOR» → «Tengo dolor en la garganta.». — Deps: T503 · Ref: FR-041, US4-1

---

## Phase 6: Documentación y cierre

- [ ] T601 [P] `docs/armado-frases.md`: excepción de primera persona en LSE, marcador `{i.yo}`, campo `formaYo`, categorías `cuerpo` y `sintoma`, señas sintéticas e índices negativos, orden de decisión modelo / señalamiento, regla de confirmación. — Deps: T402, T503 · Ref: spec.md (Reglas vigentes, excepción)
- [ ] T602 Correr ktlint, detekt, SpotBugs y la suite completa (`domain` y `app`); abrir el PR hacia `develop` con el número 007 en el cuerpo. — Deps: todas · Ref: CLAUDE.md §8

---

## Dependencies & Execution Order

### Fases

- **F0** → **F1** → **F2**: el léxico se completa en dos pasos (T107, T202).
- **F3** empieza después de T103 (motor) y en paralelo con F2 para T303/T304 (el detector no
  depende del léxico). T305 espera a F2.
- **F4** necesita T306.
- **F5** necesita T304 y T306. Es P3: puede quedar para una segunda entrega.
- **F6** al final.

### Historias

| Historia | Tareas |
|---|---|
| US1 | T101–T110 |
| US2 | T201–T203 |
| US3 | T301–T309 |
| US4 | T501–T504 |
| US5 | T401–T404 |

### Paralelismo

- T101, T102, T104 y T106 al inicio.
- T201 junto con T301 y T303.
- T404 en cuanto T105 esté listo.

## Implementation Strategy

1. **MVP = F1 + F2**: LSE arma frases en primera persona. Ya resuelve el problema de fondo
   para el vocabulario médico, sin componentes nuevos de visión.
2. **F3 + F4**: pronombres y confirmación. Recién acá la frase contiene algo que el modelo no
   clasificó, y por eso la confirmación va en la misma entrega.
3. **F5**: zonas del cuerpo, si F3 cumple SC-004 en segmentos reales.

Una tarea, un diff, un commit (conventional commits, 007 en el cuerpo).
