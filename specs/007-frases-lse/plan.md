# Implementation Plan: Armado de frases para LSE y pronombres por señalamiento

**Branch**: `007-frases-lse` | **Date**: 2026-11-15 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/007-frases-lse/spec.md`, con las cinco
ambigüedades resueltas en la sesión del 2026-11-15.

## Summary

Habilitar el armado de frases en LSE con el motor que ya usa LSA, y agregar pronombres
detectados por señalamiento, comunes a las dos lenguas. Es una funcionalidad experimental,
sin validación lingüística y sin publicación.

Enfoque técnico:

- **Datos, no código, por lengua.** `PhraseAssets` carga léxico y plantillas desde la
  carpeta de la lengua elegida. LSE suma `lse/lexico_senas.json` y
  `lse/plantillas_frases.json`; el motor (`domain/.../phrase/`) es el mismo.
- **Primera persona desde el léxico.** Cada entrada puede declarar `formaYo`. Un marcador
  nuevo, `{i.yo}`, la usa en las plantillas. La "primera persona por defecto" de LSE no es
  un interruptor en el código: son plantillas de LSE escritas con `{i.yo}`. Las plantillas
  de LSA no lo usan y por eso no cambian (E-3).
- **Señas sintéticas.** Pronombres y zonas del cuerpo son entradas con índice negativo,
  compartidas por las dos lenguas, que el léxico resuelve sin pasar por el catálogo.
- **Detector de señalamiento en `domain`.** Java puro, sobre los mismos cuadros del segmento
  que recibe el clasificador. Lee el vector de 168 **sin modificarlo**: ya contiene las dos
  manos completas y los hombros. El contrato v3 queda intacto.
- **Confirmación con el diálogo existente.** El modo seña reusa el diálogo «¿Usar esta
  frase?» que hoy usa el modo frase, en lugar de la ventana de deshacer (E-1).

---

## Enmiendas a la spec

Hallazgos del código que cambian requisitos. Se aplican a spec.md antes de generar tareas.

| ID | Requisito | Hallazgo | Enmienda |
|---|---|---|---|
| E-1 | FR-050 | La ventana de deshacer de la 001 (FR-024a) existe solo en el protocolo de evaluación; en la conversación quedó **diferida** (plan 001). El modo seña publica la oración directo (`deliverSentence`). Sí existe un diálogo de confirmación editable para el modo frase (`phraseCandidate`, «¿Usar esta frase?»). | La frase sugerida usa ese diálogo: Confirmar / Descartar, con texto editable. |
| E-2 | FR-050 | Pedir confirmación para toda frase cambia el flujo de LSA, que hoy publica directo. | Se confirma **siempre en LSE** y **en LSA solo si la oración tiene una seña sintética** (pronombre o zona). Una oración de LSA sin señalamiento se publica igual que hoy. |
| E-3 | FR-022 | Las plantillas ya son datos por lengua. Un marcador `{i.yo}` en las plantillas de LSE expresa la primera persona por defecto sin un interruptor aparte. | FR-022 se cumple con las plantillas: «activar por lengua desde los datos» = usar o no `{i.yo}`. |
| E-4 | FR-040 | Tocarse el pecho con el índice **es** la seña YO en LSA y LSE. Una zona "pecho" la contradice. | Zonas: cabeza, garganta y abdomen. Pecho = `YO`. |
| E-5 | FR-040 | El vector de 168 descarta pose 0..10 (cara). Cabeza y garganta necesitan nariz y boca. | El búfer del segmento conserva, además del vector, pose 0, 9 y 10 crudos. El vector y el tensor del modelo no cambian. Abdomen usa las caderas (pose 23 y 24), que ya están en el vector. |

---

## Technical Context

**Language/Version**: Kotlin (app), Java 17 puro (domain), sin cambios de versiones.

**Primary Dependencies**: las existentes. No se agrega ninguna librería.

**Storage**: assets JSON versionados junto al modelo.

**Testing**: JUnit en `domain` (sin emulador), tests JVM en `app/src/test`.

**Target Platform**: Android, orientación horizontal, análisis 960×540.

**Performance Goals**: detector de señalamiento < 2 ms p95 por segmento (NFR-001). Se mide
por segmento, no por cuadro: se ejecuta una vez al cerrar el segmento, igual que el
clasificador.

**Constraints**: sin red; contrato v3 intacto; sin delegado Flex; modelo, catálogo, léxico y
plantillas como unidad versionada.

### Puntos de integración relevados

| Pieza | Archivo | Estado hoy |
|---|---|---|
| Carga de léxico y plantillas | `app/.../lsa/PhraseAssets.kt` | Rutas fijas `lsa/...`; con LSE queda `Disabled` |
| Lengua elegida | `domain/.../lsa/SignLanguage.java` | `LSA("lsa", 0.90f)`, `LSE("lse", 0.50f)` |
| Marcadores de plantilla | `PhraseTemplate.PLACEHOLDER` | `{i}`, `{i.art}`, `{i.concuerda:j}` |
| Palabras de función | `PhraseTemplate.FUNCTION_WORDS` | el, la, los, las, de, a, en, con, y, o, para, es, son, está |
| Entrada del léxico | `LexicalEntry` | índice, glosa, categoría, forma, género, plural, femenino, artículo |
| Verificación glosa por índice | `PhraseBuilder.entries` | descarta todo si la glosa no coincide |
| Glosas literales en plantillas | `PhraseCompatibility` | exige que existan en el catálogo |
| Cierre de segmento y clasificación | `SessionCoordinator` (≈ línea 720) | segmento = lista de cuadros con `vector` (168) y tamaño de imagen |
| Seña rechazada | `addToSentence(null, …)` | agrega un hueco `¿?` |
| Publicación de la oración | `deliverSentence` | publica y vocaliza sin confirmar |
| Diálogo de confirmación | `ConversationScreen.kt` (≈ línea 498) | solo modo frase (`phraseCandidate`) |

---

## Constitution Check

Contra `CLAUDE.md`:

| Regla | Cumplimiento |
|---|---|
| Contrato de keypoints en un solo archivo, sin divergencias | El detector **lee** el vector; no lo arma ni lo normaliza. `KeypointContract.kt` no se toca. El test de contrato no cambia. |
| Todo en el dispositivo, sin red | Sí. |
| Modelo y catálogo son una unidad | El léxico de LSE declara `modelVersion: eva-lse300-v1` y `catalogVersion: 1.0`; `PhraseCompatibility` lo verifica. |
| Confianza explícita, nunca forzar | Señalamiento ambiguo → `¿?`. El detector no es un clasificador: o cumple el criterio geométrico, o no hay pronombre. |
| Java puro en `domain` | Detector, señas sintéticas y cambios del motor van en `domain`. |
| Comentario de una línea por método en `domain` | Se respeta en todas las clases nuevas. |
| La spec manda | E-1 a E-5 se aplican a spec.md antes de las tareas. |

Sin violaciones; no hay entradas en Complexity Tracking.

---

## Project Structure

### Documentation (this feature)

```
specs/007-frases-lse/
├── spec.md
├── plan.md          ← este archivo
└── tasks.md         ← siguiente paso
```

### Source Code

```
domain/src/main/java/com/helpi/conversation/
├── phrase/
│   ├── SignCategory.java        MOD  + CUERPO, SINTOMA
│   ├── LexicalEntry.java        MOD  + formaYo (opcional)
│   ├── Lexicon.java             MOD  lee formaYo; entry(i<0) → SyntheticSigns
│   ├── PhraseTemplate.java      MOD  marcador {i.yo}
│   ├── TemplateRenderer.java    MOD  resuelve {i.yo}: formaYo o forma
│   ├── PhraseCompatibility.java MOD  acepta glosas literales sintéticas (glosa:YO)
│   ├── PhraseResult.java        MOD  + hasSynthetic() para la regla E-2
│   └── SyntheticSigns.java      NUEVO  YO, USTED, EL_ELLA, CABEZA, GARGANTA, ABDOMEN
└── vision/
    ├── PointingDetector.java    NUEVO  segmento → Pointing
    ├── Pointing.java            NUEVO  tipo + confianza geométrica
    └── PointingConfig.java      NUEVO  umbrales (como SegmenterConfig)

app/src/main/
├── assets/lse/
│   ├── lexico_senas.json        NUEVO  299 entradas
│   └── plantillas_frases.json   NUEVO
├── java/com/helpi/conversation/
│   ├── lsa/PhraseAssets.kt      MOD  carga por lengua
│   ├── session/SessionCoordinator.kt  MOD  detector al cerrar segmento; confirmación E-2
│   └── ui/ConversationScreen.kt MOD  diálogo para oraciones; aviso "experimental" en LSE

docs/armado-frases.md            MOD  primera persona en LSE, {i.yo}, señas sintéticas
```

---

## Fases

Cada fase cierra con sus tests en verde y se puede entregar sola.

### Fase 1 — Motor por lengua y primera persona (US1, P1)

1. `PhraseAssets.load` recibe la lengua y arma las rutas `<codigo>/lexico_senas.json` y
   `<codigo>/plantillas_frases.json` (FR-001, FR-002).
2. `SignCategory`: `CUERPO`, `SINTOMA` (FR-014). Los JSON de LSA no las usan.
3. `LexicalEntry` y `Lexicon`: campo opcional `formaYo` (FR-015).
4. `PhraseTemplate.PLACEHOLDER`: `\{(\d+)(?:\.(art|yo|concuerda:(\d+)))?\}`.
   `TemplateRenderer` resuelve `{i.yo}` a `formaYo` o, si no hay, a `forma`.
5. Plantillas de LSE, en orden de especificidad (todas con solo palabras de función):

   ```json
   {"id": "cuerpo-sintoma",     "patron": ["cuerpo", "sintoma"],        "salida": "{1.yo} {0.art} {0}."},
   {"id": "yo-cuerpo-sintoma",  "patron": ["glosa:YO", "cuerpo", "sintoma"], "salida": "{2.yo} {1.art} {1}."},
   {"id": "persona-sintoma",    "patron": ["persona", "sintoma"],       "salida": "{0}: {1}."},
   {"id": "yo-sintoma",         "patron": ["glosa:YO", "sintoma"],      "salida": "{1.yo}."},
   {"id": "sintoma",            "patron": ["sintoma"],                  "salida": "{0.yo}."},
   {"id": "verbo",              "patron": ["verbo"],                    "salida": "{0.yo}."},
   {"id": "persona-verbo",      "patron": ["persona", "verbo"],         "salida": "{0}: {1}."},
   {"id": "cualidad",           "patron": ["cualidad"],                 "salida": "{0.yo}."}
   ```

   «ESTOMAGO DOLOR» → «Me duele el estómago.»; «FIEBRE» → «Tengo fiebre.»; «USTED
   AYUDAR» → «Usted: ayudar.».
6. Primera versión del léxico de LSE con las categorías y las formas base (las 299
   entradas; las formas finas de variantes y compuestos se completan en la fase 2).

**Tests**: `TemplateRendererTest` (`{i.yo}` con y sin `formaYo`); `PhraseAssetsTest`
(LSE habilitado, LSA sin cambios, LSE con versión equivocada → `Disabled`); los tests de
LSA existentes sin modificar (NFR-003, SC-003).

### Fase 2 — Variantes y compuestos (US2, P1)

1. Completar el léxico: variantes con forma compartida (FR-012); compuestos reescritos
   («Comer sin» → «sin comer», «Dormir insomnio» → «insomnio», «Mente olvidar» → «olvidar»)
   (FR-013); `formaYo` donde aplique («me duele», «tengo fiebre», «estoy débil», «no puedo
   dormir»).
2. Generar el borrador con un script de una sola vez (`scripts/`), que copia índice y glosa
   del catálogo y deja categoría y forma para completar a mano. El script no queda en el
   flujo de build.

**Tests** (`app/src/test`, leen los assets reales):
- cobertura: 299 entradas, glosa idéntica al catálogo por índice (SC-001);
- ninguna forma contiene `(`, `)` ni «variante» (FR-011);
- las variantes de una glosa base comparten forma y categoría, salvo una lista explícita de
  excepciones (FR-012);
- tabla de ≥ 40 oraciones de LSE con frase esperada (SC-002).

### Fase 3 — Pronombres por señalamiento (US3, P2)

1. `SyntheticSigns`: índices negativos fijos (`-1` YO, `-2` USTED, `-3` EL_ELLA), glosa,
   categoría `persona` y forma («yo», «usted», «él o ella») (FR-034). `Lexicon.entry(i)`
   con `i < 0` los resuelve; `PhraseCompatibility` acepta `glosa:YO` y las demás glosas
   sintéticas (FR-034).
2. `PointingDetector` sobre los cuadros del segmento (los mismos `vector` de 168):
   - **Escala propia**, no el contrato: centra en el punto medio de los hombros y divide por
     el ancho de hombros del segmento, en píxeles (FR-032). Sin hombros → sin señalamiento.
   - **Mano que señala** (FR-036): índice extendido (punta 8 lejos de la muñeca 0 respecto
     del nudillo 5, y 5-6-8 casi alineados) y dedos medio, anular y meñique flexionados
     (puntas 12, 16 y 20 cerca de la palma). Si las dos manos cumplen → sin señalamiento.
   - **Quietud** (FR-033): en los cuadros finales del segmento, la punta se desplaza menos de
     `maxDrift` anchos de hombro durante al menos `minHoldMs`.
   - **Dirección** = punta 8 − nudillo 5:
     - punta dentro del torso (entre hombros y caderas, a menos de `contactDistance`) → `YO`;
     - componente hacia la cámara dominante (z) → `USTED`;
     - componente lateral dominante (x) → `EL_ELLA`;
     - ninguna dominante por encima de `dominanceRatio` → ambiguo.
   - Los umbrales viven en `PointingConfig.defaults()`, como `SegmenterConfig`.
3. Integración en `SessionCoordinator`, al cerrar el segmento, en este orden de decisión
   (escenario 3.4 y caso borde de seña léxica):
   1. el modelo acepta la seña → se usa el modelo;
   2. el modelo no acepta y el detector devuelve un pronombre → seña sintética con
      confianza 1;
   3. el detector devuelve ambiguo, o nada → hueco `¿?`, como hoy.

   El detector corre en el ejecutor del clasificador, después de la inferencia; no toca el
   hilo de cámara.

**Tests** (`domain`, sin emulador):
- secuencias sintéticas construidas en el test (mano con forma de señalar en cada
  dirección, mano abierta, puño, dos índices, sin hombros, señalamiento en movimiento);
- secuencias reales: segmentos grabados con el build de evaluación, exportados como vectores
  de 168 y etiquetados a mano (YO, USTED, EL_ELLA, seña léxica con índice extendido como la
  letra «D», reposo). Con ellas se mide SC-004 (≥ 90 % y < 5 % de falsos pronombres);
- costo: p95 del detector por segmento < 2 ms (NFR-001), medido en el dispositivo de
  referencia.

### Fase 4 — Confirmación y aviso experimental (US5, P2)

1. `deliverSentence` decide según E-2: en LSE, o en LSA con `hasSynthetic()`, la frase va
   al diálogo de confirmación; si no, se publica como hoy.
2. El diálogo «¿Usar esta frase?» se generaliza para recibir una oración del modo seña
   (texto editable, Confirmar / Descartar). Descartar no publica ni vocaliza (US5.2).
3. Debajo del texto, las glosas en mayúsculas, incluidos los pronombres detectados; la
   primera persona por defecto no aparece como glosa (FR-051).
4. Con LSE seleccionada, un aviso fijo y breve: «Frases en LSE: experimental» (FR-052).

**Tests**: tests del coordinador con `usePhraseAssets` y un clasificador falso: oración LSE
→ candidata, no publicada; confirmar → publicada y en cola de voz; descartar → nada; oración
LSA sin sintéticos → publicada directo (sin regresión).

### Fase 5 — Zonas del cuerpo por contacto (US4, P3)

1. El búfer del segmento suma pose 0, 9 y 10 crudos (E-5). `KeypointContract` y el tensor
   no cambian.
2. `PointingDetector` agrega contacto de la punta con: cabeza (alrededor de la nariz),
   garganta (entre la boca y el punto medio de los hombros) y abdomen (entre el pecho y las
   caderas). El pecho sigue siendo `YO` (E-4). Sin el landmark de referencia de una zona,
   esa zona no se evalúa (US4.2).
3. Señas sintéticas `-10` CABEZA, `-11` GARGANTA, `-12` ABDOMEN, categoría `cuerpo`.
   «ABDOMEN DOLOR» → «Me duele el abdomen.» con la plantilla `cuerpo-sintoma` de la fase 1.

**Tests**: secuencias sintéticas por zona; zona fuera de cuadro; caderas ausentes.

### Fase 6 — Documentación

`docs/armado-frases.md`: excepción de primera persona en LSE, marcador `{i.yo}`, campo
`formaYo`, señas sintéticas y orden de decisión modelo / señalamiento.

---

## Estrategia de pruebas

En línea con el orden de `CLAUDE.md`, sin saltear etapas:

1. **Motor y datos sin cámara** (fases 1 y 2): todo en tests unitarios de `domain` y JVM de
   `app`. Si pasan, cualquier falla posterior es de captura o del detector.
2. **Detector sin modelo** (fase 3): secuencias sintéticas y grabadas, comparando la
   distribución de resultados (no solo que no haya excepciones).
3. **Cadena completa** (fases 4 y 5): recién cuando 1 y 2 pasan, en el dispositivo.

El test de contrato v3 debe seguir pasando sin cambios en todas las fases.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Señas léxicas con forma de señalar → pronombres falsos | El modelo gana si acepta; criterio de quietud; SC-004 con segmentos reales |
| LSE con umbral 0,50 acepta una clase léxica cuando la persona señalaba | Medir en las grabaciones de la fase 3 cuántos señalamientos acepta el modelo; si es alto, evaluar invertir el orden solo para segmentos con forma de señalar clara |
| La `z` de la mano es relativa a la muñeca y ruidosa | `USTED` exige dominancia de z por `dominanceRatio`; si no, ambiguo |
| 299 entradas a mano desfasadas del catálogo | Test de cobertura glosa por índice + `PhraseCompatibility` |
| Formas en primera persona incorrectas en contexto médico | Funcionalidad experimental (FR-052) y confirmación de cada frase (E-1, E-2) |

## Complexity Tracking

Sin entradas.
