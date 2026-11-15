# Feature Specification: Armado de frases para LSE y pronombres por señalamiento

**Feature Branch**: `007-frases-lse`

**Created**: 2026-11-15

**Status**: Draft

**Input**: User description: "Con LSE no se pueden armar frases: no hay verbos, adjetivos ni
pronombres para hacerlo. Buscar entre los dos lenguajes una forma de formar frases con las
señas."

## Contexto

La app reconoce dos lenguas de señas, una por vez, con el selector de lengua (funcionalidad 003):

| Lengua | Modelo | Clases | Armado de frases |
|---|---|---|---|
| LSA | `eva-lsa94-v4` (LSA64 + abecedario) | 94 | Sí: `lsa/lexico_senas.json` + `lsa/plantillas_frases.json` |
| LSE | `eva-lse300-v1` (SWL-LSE, vocabulario médico) | 299 | **No**: la conversación muestra glosas sueltas |

`PhraseAssets` lee rutas fijas de `lsa/`. Con LSE seleccionada no encuentra léxico ni
plantillas, deshabilita el armado y cada seña aparece como glosa suelta.

El vocabulario de LSE tiene más gramática de la que parece:

- **Verbos**: Comer, Dormir, Respirar, Toser, Vomitar, Ayudar, Curar, Cuidar, Sentir,
  Sufrir, Olvidar, Caer, Fumar, Operar, Revisar, Relajar, Evitar.
- **Cualidades**: Débil, Fuerte, Sano, Normal, Nervioso, Cansado, Enfermo, Urgente.
- **Partes del cuerpo**: Estómago, Espalda, Garganta, Pierna, Pie, Piel, Pulmón, Corazón.
- **Síntomas y estados**: Dolor, Fiebre, Mareo, Náusea, Diarrea, Ansiedad, Insomnio.
- **Clases compuestas** (glosa de más de una palabra con un único significado): «Dormir
  insomnio», «Comer sin», «Mente olvidar», «Tension (CT) alta», «Pulmon enfermo».
- **Variantes**: 86 de las 299 clases son variantes de otra glosa («Diarrea», «Diarrea
  (variante 2)», «Diarrea (variante 3)»).

Lo que **ninguna** de las dos lenguas tiene en el vocabulario del modelo son los
pronombres. En LSA y en LSE los pronombres personales no son señas léxicas: se realizan
**señalando con el dedo índice** (hacia el propio pecho = yo; hacia el interlocutor = usted; hacia un costado = él / ella). Ningún clasificador de vocabulario cerrado los va a
aprender como clase, pero son detectables geométricamente con los landmarks que
HolisticLandmarker ya entrega.

### Qué significa "entre los dos lenguajes"

Esta funcionalidad **no mezcla señas de LSA y LSE en una misma oración**: una persona seña
en una sola lengua, y las salidas de los dos modelos no son comparables (LSE entrega logits
con umbral 0,50; LSA, probabilidades con umbral 0,90). Lo que se comparte entre las dos
lenguas es:

1. **El motor de armado** (`phrase/`): mismas categorías, mismas reglas, mismo algoritmo.
   Cada lengua aporta su léxico y sus plantillas.
2. **Los pronombres por señalamiento**: el mismo detector sirve para las dos lenguas,
   porque el señalamiento funciona igual en ambas y no depende del modelo.

### Reglas vigentes que esta funcionalidad respeta

De `docs/armado-frases.md` (funcionalidad 002):

- Las plantillas solo agregan **palabras de función** (`PhraseTemplate.FUNCTION_WORDS`).
  «HAMBRE» → «Quiero comer.» no es válido.
- **Verbos en infinitivo, siempre.** La persona y el tiempo no se infieren.
- **Glosa en MAYÚSCULAS** = parte no construida. **`¿?`** = seña bajo el umbral.

Un pronombre detectado por señalamiento **no es una inferencia**: es una seña observada.
Por eso puede ocupar el lugar de `persona` en una plantilla sin romper estas reglas.

**Excepción de esta funcionalidad (solo LSE):** si la oración no tiene persona, se asume la
primera. La conjugación no la agregan las plantillas, que siguen limitadas a palabras de
función: la aporta el léxico con una forma en primera persona por entrada («Dolor» → «me
duele»; «Fiebre» → «tengo fiebre»). Al implementarse, `docs/armado-frases.md` debe
documentar esta excepción. LSA no cambia.

## Clarifications

### Session 2026-11-15

- Q: ¿Se mezclan señas de LSA y LSE en una misma oración? → A: No. Cada sesión usa una
  sola lengua; se comparten el motor de armado y el detector de señalamiento.
- Q: ¿Se usa un modelo de lenguaje para completar la frase? → A: No. Agregaría contenido
  que nadie señó y contradice el principio de preferir silencio a una traducción
  incorrecta.
- Q: ¿Se usa la primera persona por defecto? → A: Sí, en LSE. Si la oración no tiene
  persona, se arma en primera persona («ESTOMAGO DOLOR» → «Me duele el estómago.»). Es una
  excepción explícita a la regla de no inferir la persona de la funcionalidad 002, limitada
  a LSE (FR-015, FR-022).
- Q: ¿Cómo se muestran las clases compuestas? → A: Reescritas para leerse mejor en
  español: «Comer sin» → «Sin comer» (FR-013).
- Q: ¿Quién valida lingüísticamente el léxico? → A: Nadie por ahora: la funcionalidad es
  experimental y no se publica. La interfaz lo indica (FR-052).
- Q: ¿Cómo se elige la mano que señala? → A: No se lee la mano dominante: es la que tenga
  el índice extendido. Si las dos lo tienen, no se clasifica (FR-036).
- Q: ¿«Vos» o «usted»? → A: «Usted» (FR-034).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Frases en LSE con el motor actual (Priority: P1)

Una persona sorda que seña en LSE selecciona LSE y seña «ESTOMAGO DOLOR». Hoy ve dos glosas
sueltas. Con esta funcionalidad ve una frase armada con el motor de LSA y en primera
persona: «Me duele el estómago.».

**Why this priority**: sin léxico ni plantillas, LSE no arma ninguna frase. Es la condición
mínima para que la lengua nueva sea usable en una conversación, y no requiere ningún
componente nuevo: solo datos y la carga por lengua.

**Independent Test**: con LSE seleccionada, pasar al armador secuencias de índices
conocidas (sin cámara) y verificar la frase producida contra una tabla de casos esperados.

**Acceptance Scenarios**:

1. **Given** LSE seleccionada y su léxico y plantillas válidos, **When** se cierra la
   oración «ESTOMAGO DOLOR», **Then** la conversación muestra «Me duele el estómago.» y
   debajo las glosas originales.
2. **Given** LSE seleccionada y una oración sin persona, **When** se arma «FIEBRE», **Then**
   produce «Tengo fiebre.», con la forma en primera persona del léxico.
3. **Given** LSE seleccionada y una entrada sin forma en primera persona, **When** se arma
   sin persona, **Then** se usa su forma base, sin conjugar, igual que en LSA.
4. **Given** LSA seleccionada, **When** se cierra cualquier oración, **Then** el resultado es
   idéntico al de antes de esta funcionalidad: sin primera persona por defecto y sin
   regresión.
5. **Given** LSE seleccionada y un léxico con una versión que no corresponde al modelo,
   **When** se carga la sesión, **Then** el armado se deshabilita, se muestran glosas
   sueltas y el reconocimiento sigue funcionando.
6. **Given** una seña bajo el umbral en la oración, **When** se arma, **Then** no se aplica
   ninguna plantilla ni la primera persona (`ESTOMAGO ¿?`), igual que en LSA.

---

### User Story 2 - Clases compuestas y variantes (Priority: P1)

La persona seña «DORMIR INSOMNIO», que el modelo reconoce como **una** clase. La frase
muestra su significado como una unidad, no como dos palabras sueltas. Si seña «DIARREA
(variante 2)», el texto muestra «diarrea», igual que la variante 1.

**Why this priority**: casi un tercio de las clases de LSE son variantes o compuestos. Sin
esto, la frase muestra «Diarrea (variante 2)» o «Dormir insomnio» literal.

**Independent Test**: recorrer las 299 clases del catálogo y verificar que todas tienen
entrada en el léxico, que ninguna forma contiene paréntesis ni la palabra "variante", y que
las variantes de una glosa comparten forma.

**Acceptance Scenarios**:

1. **Given** las tres variantes de «Diarrea», **When** se arma cada una sola, **Then** las
   tres producen el mismo texto.
2. **Given** «Comer sin», **When** se arma sola, **Then** produce «Sin comer.»: la glosa
   reordenada para leerse en español.
3. **Given** una clase compuesta, **When** forma parte de una oración más larga, **Then** se
   trata como una sola seña con la categoría declarada en el léxico.

---

### User Story 3 - Pronombres por señalamiento (Priority: P2)

La persona señala hacia la cámara con el índice y después seña «AYUDAR». La frase muestra
«Usted: ayudar.». Si se señala el pecho antes de «DOLOR», el resultado es el mismo que sin
señalar en LSE («Me duele.»), pero en LSA la persona queda explícita: «Yo: …».

**Why this priority**: es la única forma de expresar quién hace o siente algo, en las dos
lenguas. Requiere un componente nuevo (detector geométrico) y por eso va después de P1.

**Independent Test**: alimentar el detector con secuencias de landmarks grabadas
(señalamiento al pecho, a la cámara, a cada costado, mano en reposo, seña léxica con el
índice extendido) y verificar la clase de señalamiento sin cámara ni modelo.

**Acceptance Scenarios**:

1. **Given** una mano con el índice extendido y los demás dedos flexionados,
   apuntando al pecho durante al menos el tiempo mínimo, **When** termina el segmento,
   **Then** se agrega a la oración un pronombre `YO` con categoría `persona`.
2. **Given** el mismo gesto apuntando hacia la cámara, **When** termina el segmento,
   **Then** se agrega `USTED`, con cualquiera de las dos manos.
3. **Given** el gesto apuntando a un costado, **When** termina el segmento, **Then** se
   agrega `EL/ELLA`, que se muestra como «Él o ella» porque el género no es observable.
4. **Given** un segmento que el detector de señalamiento clasifica, **When** el mismo
   segmento también se envía al modelo, **Then** gana el señalamiento solo si supera su
   propio criterio de confianza geométrica; si no, se usa la predicción del modelo.
5. **Given** un señalamiento ambiguo (dirección entre dos zonas), **When** termina el
   segmento, **Then** se muestra `¿?` y no se agrega ningún pronombre.
6. **Given** LSA o LSE, **When** se detecta un señalamiento, **Then** el comportamiento es
   el mismo en las dos lenguas.

---

### User Story 4 - Señalar una parte del cuerpo (Priority: P3)

En una consulta médica, la persona se toca el abdomen con el índice y seña «DOLOR». La
frase muestra «Dolor en el abdomen.».

**Why this priority**: es muy útil en el vocabulario médico de LSE, pero depende de la
historia 3 y necesita zonas del cuerpo confiables.

**Independent Test**: secuencias grabadas de la punta del índice en contacto con cada zona
definida, verificadas contra la zona esperada.

**Acceptance Scenarios**:

1. **Given** la punta del índice a menos de la distancia de contacto de una zona definida
   (cabeza, garganta, abdomen), **When** termina el segmento, **Then** se agrega la zona
   con categoría `cuerpo`. Tocarse el pecho es la seña `YO`, no una zona.
2. **Given** que la zona cae fuera del cuadro o la cara no se detecta, **When** termina el
   segmento, **Then** no se agrega ninguna zona.

---

### User Story 5 - Confirmar la frase antes de publicarla (Priority: P2)

La persona ve la frase armada como **frase sugerida**, con las glosas debajo. La publica en
la conversación (y se lee en voz alta) solo si la confirma; puede editarla o descartarla.
En LSE se confirma siempre; en LSA, solo si la oración tiene un señalamiento (FR-050a).

**Why this priority**: con pronombres detectados y formas de clases compuestas, el texto
contiene más interpretación que una glosa suelta. Quien seña debe poder rechazarla antes
de que llegue al oyente.

**Independent Test**: armar una frase, descartarla y verificar que no se publica ni se
vocaliza; armarla y confirmarla y verificar que sí. Una oración de LSA sin señalamiento se
publica sin pasar por el diálogo.

**Acceptance Scenarios**:

1. **Given** una frase armada, **When** se muestra, **Then** se identifica visualmente como
   sugerida, distinta de un mensaje publicado.
2. **Given** una frase sugerida, **When** la persona la descarta, **Then** no se publica ni
   se vocaliza.

---

### Edge Cases

- **Zurdos**: no se configura la mano dominante. La mano que señala es la que tiene el
  índice extendido, así que el detector funciona igual con cualquiera de las dos. Si las
  dos lo tienen, no se clasifica.
- **Seña léxica con índice extendido**: varias señas de LSA y LSE tienen la forma de
  señalar (por ejemplo, la letra «D» del abecedario de LSA). El detector exige además
  quietud y dirección sostenida; si el modelo supera su umbral con una clase léxica, gana
  el modelo (escenario 3.4).
- **Hombros ausentes**: sin hombros no hay sistema de referencia para la dirección. No se
  clasifica señalamiento.
- **Oración solo con pronombre**: «YO» solo produce «Yo.».
- **Otra persona en LSE**: con `USTED` o `EL_ELLA` no se usa la forma en primera persona;
  el verbo queda en infinitivo («Usted: ayudar.»).
- **Clase grosera** («Mierda»): se conserva la glosa del catálogo; no se censura ni se
  reemplaza.
- **Cambio de lengua a mitad de una oración**: la oración en curso MUST descartarse; no se
  arma con el léxico de la lengua nueva.

## Requirements *(mandatory)*

### Functional Requirements

**Carga por lengua**

- **FR-001**: `PhraseAssets` MUST cargar el léxico y las plantillas desde la carpeta de la
  lengua seleccionada (`<lengua>/lexico_senas.json`, `<lengua>/plantillas_frases.json`),
  en lugar de rutas fijas de `lsa/`.
- **FR-002**: Si una lengua no tiene léxico o plantillas, el armado MUST deshabilitarse solo
  para esa lengua, con el motivo registrado; el reconocimiento sigue funcionando.
- **FR-003**: `PhraseCompatibility` MUST validar el léxico de LSE contra el `modelVersion` y
  el `catalogVersion` de `lse/manifest.json` con las mismas reglas que LSA.

**Léxico de LSE**

- **FR-010**: MUST existir `lse/lexico_senas.json` con una entrada por cada una de las 299
  clases del catálogo, en orden de índice y con la glosa exacta del catálogo.
- **FR-011**: Cada entrada MUST tener categoría y forma en español sin paréntesis ni
  marcas de variante.
- **FR-012**: Las variantes de una misma glosa MUST compartir forma y categoría, salvo que
  la variante tenga un significado distinto (por ejemplo, «Tension (CT) alta»).
- **FR-013**: Las clases compuestas MUST tener una forma única reescrita para leerse en
  español, sin conjugar: «Comer sin» → «sin comer»; «Dormir insomnio» → «insomnio»;
  «Mente olvidar» → «olvidar».
- **FR-014**: MUST agregarse la categoría `cuerpo` (partes del cuerpo) y `sintoma`
  (estados y malestares), si las categorías actuales no alcanzan para escribir las
  plantillas de LSE sin casos especiales por glosa. Agregar una categoría no cambia el
  comportamiento de LSA.
- **FR-015**: Las entradas de LSE con categoría `sintoma`, `verbo` o `cualidad` MAY
  declarar una forma en primera persona (`formaYo`: «me duele», «tengo fiebre», «estoy
  débil», «no puedo dormir»). La conjugación vive en el léxico, nunca en las plantillas.

**Plantillas de LSE**

- **FR-020**: MUST existir `lse/plantillas_frases.json`, sujeto a la regla de palabras de
  función. Si una plantilla necesita una palabra nueva, se agrega a `FUNCTION_WORDS` solo
  si es una palabra de función (por ejemplo, «en», «sin»), nunca un verbo conjugado.
- **FR-021**: Las plantillas MUST cubrir como mínimo: `sintoma` sola, `cuerpo` + `sintoma`
  («Me duele el estómago.»), `persona` + `sintoma`, `persona` + `verbo`, `verbo` +
  `cuerpo`, `cualidad` sola, `persona` + `cualidad`.
- **FR-022**: En LSE, si la oración no contiene ninguna `persona`, o si contiene `YO`, las
  plantillas MUST usar la forma en primera persona (`{i.yo}`) cuando la entrada la tenga,
  y la forma base cuando no. La primera persona por defecto se activa por lengua desde los
  datos: las plantillas de LSE usan `{i.yo}` y las de LSA no, así que en LSA queda
  apagada sin ningún interruptor en el código.

**Pronombres por señalamiento**

- **FR-030**: MUST existir un detector de señalamiento en `domain` (Java puro, sin
  dependencias de Android) que reciba landmarks de un segmento y devuelva `YO`, `USTED`,
  `EL_ELLA`, una zona del cuerpo o "sin señalamiento".
- **FR-031**: El detector MUST trabajar sobre los landmarks crudos de HolisticLandmarker.
  **No MUST modificar** el vector de 168 coordenadas, la normalización v3 ni la entrada del
  modelo. El contrato de keypoints queda intacto.
- **FR-032**: La dirección MUST calcularse en el sistema de referencia de los hombros (la
  misma escala de "anchos de hombro" del contrato v3), para que no dependa de la distancia
  a la cámara.
- **FR-033**: Un señalamiento MUST requerir: índice extendido, demás dedos flexionados,
  dirección sostenida un tiempo mínimo configurable y mano sin desplazamiento mayor a un
  umbral configurable.
- **FR-034**: Los pronombres MUST entrar al léxico como entradas sintéticas con categoría
  `persona`, compartidas por las dos lenguas, sin índice de modelo. La segunda persona se
  muestra como «usted».
- **FR-035**: Un señalamiento ambiguo MUST mostrarse como `¿?`; nunca se fuerza a la zona
  más cercana.
- **FR-036**: La mano que señala MUST ser la que tenga el índice extendido, izquierda o
  derecha. No se configura ni se infiere la mano dominante. Si ambas manos tienen el índice
  extendido, el segmento no se clasifica como señalamiento.

**Partes del cuerpo por contacto** (P3)

- **FR-040**: El detector MUST reconocer contacto de la punta del índice con las zonas
  cabeza, garganta y abdomen, definidas sobre landmarks de pose. El pecho no es una zona:
  tocarlo es la seña `YO` (FR-030).
- **FR-040a**: Para las zonas cabeza y garganta, el segmento MUST conservar pose 0, 9 y 10
  crudos aparte del vector de 168. El vector, la normalización v3 y el tensor del modelo no
  cambian (FR-031).
- **FR-041**: La zona detectada MUST entrar a la oración con categoría `cuerpo` y la forma
  correspondiente.

**Confirmación**

- **FR-050**: Una frase armada MUST mostrarse como sugerida antes de publicarse, en el
  diálogo de confirmación que ya usa el modo frase («¿Usar esta frase?»: texto editable,
  Confirmar / Descartar). La ventana de deshacer de la funcionalidad 001 no existe en la
  conversación y no se usa.
- **FR-050a**: La confirmación MUST pedirse siempre en LSE, y en LSA solo si la oración
  contiene una seña sintética (pronombre o zona). Una oración de LSA sin señalamiento se
  publica como hoy.
- **FR-051**: Debajo de la frase MUST mostrarse las glosas originales y los pronombres
  detectados en MAYÚSCULAS (`YO DOLOR`), para que quien seña vea qué se reconoció. La
  primera persona asumida por defecto no aparece como glosa, porque nadie la señó.
- **FR-052**: Con LSE seleccionada, la interfaz MUST indicar que el armado de frases es
  experimental, sin validación lingüística.

### Non-Functional Requirements

- **NFR-001**: El detector de señalamiento MUST sumar menos de 2 ms por cuadro en el
  percentil 95 sobre el dispositivo de referencia.
- **NFR-002**: Todo el procesamiento MUST seguir ocurriendo en el dispositivo, sin red.
- **NFR-003**: El armado de LSA MUST producir exactamente las mismas frases que antes para
  todos los casos de prueba existentes.

### Key Entities

- **Léxico por lengua**: índice → glosa, categoría, forma, género, número. Uno por lengua,
  versionado junto a su modelo y catálogo.
- **Entrada sintética**: pronombre o zona del cuerpo sin índice de modelo, común a las dos
  lenguas.
- **Señalamiento**: resultado del detector para un segmento (tipo, dirección, confianza
  geométrica).
- **Frase sugerida**: `PhraseResult` todavía no publicado, con sus glosas y su origen
  (modelo o señalamiento) por posición.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Las 299 clases de LSE tienen entrada válida en el léxico y el armado se
  habilita con LSE seleccionada.
- **SC-002**: Una tabla de al menos 40 oraciones de LSE con frase esperada pasa en tests
  unitarios de `domain`, sin emulador.
- **SC-003**: Los tests de armado de LSA existentes pasan sin cambios.
- **SC-004**: Sobre secuencias grabadas de señalamiento, el detector clasifica bien al menos
  el 90 % de los señalamientos claros y produce un pronombre en menos del 5 % de los
  segmentos que son señas léxicas.
- **SC-005**: Ninguna plantilla de LSE agrega una palabra de contenido (lo verifica la carga
  del conjunto).

## Enmiendas del plan (2026-11-15)

Aplicadas desde [plan.md](plan.md) tras relevar el código:

- **E-1, E-2**: la confirmación usa el diálogo del modo frase; se pide siempre en LSE y en
  LSA solo con señalamiento (FR-050, FR-050a, US5).
- **E-3**: la primera persona por defecto la activan las plantillas con `{i.yo}` (FR-022).
- **E-4**: el pecho no es una zona; tocarlo es `YO` (FR-040, US4).
- **E-5**: cabeza y garganta usan pose 0, 9 y 10 guardados aparte del vector (FR-040a).

## Ambigüedades abiertas

Ninguna. Las cinco de la primera versión se resolvieron en la sesión del 2026-11-15 (ver
Clarifications).

## Assumptions

- **AS-01**: HolisticLandmarker entrega en cada cuadro los 21 landmarks de cada mano y la
  pose completa (incluida la cara), aunque el contrato v3 solo use una parte.
- **AS-02**: El segmentador actual (`SignSegmenter`) ya delimita los segmentos de seña; el
  detector de señalamiento trabaja sobre esos mismos segmentos.
- **AS-03**: La licencia de SWL-LSE (CC BY 4.0) permite el léxico derivado; la atribución
  ya figura en `lse/manifest.json`.

## Fuera de alcance

- Mezclar señas de LSA y LSE en una misma oración.
- Correr los dos modelos a la vez.
- Modelos de lenguaje, generativos o de traducción.
- Marcadores no manuales: negación con la cabeza y pregunta con las cejas. Son la evolución
  natural de esta funcionalidad, pero requieren leer la cara de forma continua.
- Tiempo verbal en LSE: el vocabulario de LSE no trae Hoy / Ayer / Mañana.
- Reentrenar modelos en `helpi-ml`.
- Validación lingüística del léxico y publicación: la funcionalidad es experimental.
- Primera persona por defecto en LSA.
- Conjugar para «usted» o «él / ella»: esas personas usan el verbo en infinitivo.

## Dependencias y riesgos

- **Depende de** 002 (armado de frases), 003 (selector de lengua) y 001 (ventana de
  deshacer).
- **Riesgo**: señas léxicas con forma de señalar generan pronombres falsos. Mitigación:
  criterio geométrico estricto, prioridad del modelo cuando supera su umbral y SC-004.
- **Riesgo**: un léxico de 299 entradas escrito a mano puede desfasarse del catálogo.
  Mitigación: `PhraseCompatibility` compara glosa por índice y deshabilita el armado ante
  cualquier diferencia.
