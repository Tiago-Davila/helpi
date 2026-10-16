# Research: Medición de calidad del reconocimiento de Eva — iteración de evaluación

**Feature**: `001-calidad-reconocimiento` | **Fecha**: 2026-10-05 | **Plan**: [plan.md](plan.md)

Cada decisión indica qué se eligió, por qué y qué se descartó. Las referencias `FR-`, `NFR-`,
`SC-` apuntan a [spec.md](spec.md). Las referencias `CLAUDE.md §N` apuntan a las reglas del
repositorio, que hoy cumplen el papel de constitución (ver plan.md, Constitution Check).

---

## R-01 Separación de builds: variante de producto + módulos que producción no compila

**Decisión**

- Una dimensión de sabor `registro` con dos sabores: `evaluacion` y `produccion`.
- Todo el código de recolección detallada vive en dos módulos nuevos que solo se agregan con
  `evaluacionImplementation(...)`:
  - `:evaluacion-dominio` — Java puro (`java-library`), sin Android: generador del protocolo,
    reglas de intento, máquina de estados de la sesión, reglas de retención.
  - `:evaluacion` — biblioteca Android: Room, WorkManager, pantallas Compose del protocolo y
    del consentimiento, serialización del contrato y cliente HTTP.
- `:app` solo conoce un **puerto** neutro, `RecognitionObserver`, definido en `:domain`
  (Java). Cada sabor aporta su enlace en su propio *source set*:
  - `app/src/produccion/…/VariantBindings.kt` → observador vacío, sin entrada de menú ni
    distintivo.
  - `app/src/evaluacion/…/VariantBindings.kt` → observador real de `:evaluacion`, la entrada
    "Validación" y el distintivo permanente (FR-005).
- El código de `main` no puede importar nada de `:evaluacion*`, porque esos módulos no están
  en el *classpath* de compilación de `produccion`. Una referencia accidental no compila.

**Verificación automatizable (FR-004, SC-003, NFR-013)** — tres capas, todas bloqueantes:

1. **Grafo de dependencias.** Una tarea Gradle, `verificarProduccionSinEvaluacion`,
   resuelve `produccionReleaseRuntimeClasspath` y falla si aparece alguno de
   `:evaluacion`, `:evaluacion-dominio`, `androidx.room:*`, `androidx.work:*` o
   `com.google.android.datatransport:transport-backend-cct`.
2. **Artefacto.** `scripts/ci/verify.py apk` (ya existe y ya exige que no haya `INTERNET`)
   se extiende con una lista de prohibidos sobre el APK de `produccionRelease`:
   - paquetes dex (`apkanalyzer dex packages`) `com.helpi.evaluacion`, `androidx.room` y
     `androidx.work`;
   - permisos `INTERNET` y `ACCESS_NETWORK_STATE`;
   - recursos o assets del consentimiento y de los videos de referencia.
3. **Canario.** Un job de CI compila `produccionRelease` con `-PcanarioEvaluacion=true`, una
   propiedad que solo existe para este fin y agrega `:evaluacion` a `produccion`. El job
   **espera** que las capas 1 y 2 fallen y falla si no lo hacen. Además,
   `scripts/ci/test_verify.py` cubre la capa 2 con listados sintéticos que contienen cada
   tipo de inserción. Así SC-003 se mide en cada integración, no se afirma.

`isMinifyEnabled = false` en release mantiene los nombres de paquete, así que la capa 2 lee
nombres reales. Si en el futuro se activa R8, la capa 1 sigue siendo la garantía primaria y la
capa 2 debe pasar a usar `mapping.txt`. Queda anotado como riesgo en plan.md.

**Alternativas descartadas**

- *Bandera de ejecución o `BuildConfig.DEBUG`*: la spec lo excluye (FR-003). El código queda
  en el APK.
- *Solo source sets por sabor (`src/evaluacion/java`) sin módulos*: separa igual, pero Room y
  WorkManager entrarían como `evaluacionImplementation` de `:app`. El grafo es más difícil de
  verificar y el código Java puro no quedaría separado de Android (CLAUDE.md §8).
- *Usar los build types `debug`/`release` como variantes*: mezcla "con símbolos" con "qué se
  registra". Un `debug` de producción es necesario para los tests de UI actuales.
- *R8 con reglas `-assumenosideeffects`*: elimina código por optimización, no por
  construcción. Una regla mal escrita deja todo dentro sin que nada falle.

**Impacto en CI**: las tareas `lintDebug`, `testDebugUnitTest`, `connectedDebugAndroidTest` y
`jacocoDebugReport` pasan a llamarse `…ProduccionDebug…` y `…EvaluacionDebug…`. Hay que
actualizar `ci.yml`, `android-tests.sh` y `verify.py`, y correr los dos sabores.

---

## R-02 Puerto de observación en la cadena de reconocimiento

**Decisión**: `RecognitionObserver` (Java, `:domain`), con eventos sin efectos secundarios:

| Evento | Cuándo lo emite `SessionCoordinator` |
|---|---|
| `onSegmentStarted(startMs)` | `SegmentEvent.STARTED` |
| `onNoResult(cause)` | segmento abortado (`TOO_SHORT`, `TOO_LONG`, `TRACKING_LOST`), sin hombros, cambio de orientación, error del modelo |
| `onDecision(SignDecision, thresholdUsed, segmentEndMs)` | resultado del clasificador, sobre o bajo el umbral |

Contrato del puerto: la implementación **no puede hacer E/S ni bloquear**. Debe copiar el
dato y volver. La implementación de producción es un objeto vacío.

Para el protocolo, `SessionCoordinator` acepta un modo de entrega `OBSERVER_ONLY`. En ese modo
las señas aceptadas no se publican en la conversación ni se vocalizan: la pantalla del protocolo
maneja la ventana de deshacer. Este modo existe en el código de producción, pero no activa
ninguna recolección; sin `:evaluacion` no hay nadie que lo use.

**Alternativas descartadas**: duplicar la cadena de cámara y clasificador en `:evaluacion`
divergiría con el tiempo y mediría otra cosa que la que usa la app. Leer `lastRecognition`
por *polling* pierde eventos y no ve los "sin resultado".

---

## R-03 Persistencia en evaluación: escritura anticipada por intento, fuera del camino de reconocimiento

**Problema**: si la sesión se acumula en memoria y se escribe al cerrarla, una sesión
interrumpida (cierre forzado, batería, revocación) se pierde entera. Las sesiones que fallan son
justamente las que más interesa ver.

**Decisión**

- El intento terminado (con su top-3 y su resolución de la ventana de deshacer) se escribe
  **de inmediato y en una sola transacción** de Room.
- La escritura ocurre en un **escritor dedicado**: un único hilo con una cola acotada (64
  elementos). El hilo de reconocimiento solo hace `trySend` (NFR-006).
- Si la cola está llena (no debería: hay un intento cada ~5 s) o la escritura falla por disco
  lleno, el escritor incrementa un contador pendiente en memoria y nunca bloquea el
  reconocimiento. Suma ese contador a `sesion.intentosPerdidos` en la próxima transacción que
  sí se confirme. Si el proceso termina antes de ese vaciado, el campo persistido puede
  subestimar los fallos de esa ejecución; el intento ausente se vuelve a ofrecer al retomar.
- El estado de la sesión vive en la base: `EN_CURSO` se escribe al empezar un bloque. Al
  arrancar la app, toda sesión que siga en `EN_CURSO` pasa a `INTERRUMPIDA` con causa
  `PROCESO_TERMINADO`. Solo puede haber una `EN_CURSO`, y solo mientras la pantalla del
  protocolo está viva.
- **Quién resuelve un intento** (aclarado tras `/speckit-analyze`): el `ProtocoloViewModel`.
  Aplica la ventana de deshacer, el tiempo agotado, la repetición y "Lo hice mal", y entrega el
  intento resuelto al escritor. `RegistroObserver` no tiene estado de intento: solo reenvía
  los eventos del puerto (R-02) a un flujo en memoria.
- El intento **en curso** al interrumpirse (seña pedida, sin resultado todavía) se descarta y
  no se registra. Al retomar, el bloque sigue desde su primer intento sin respuesta (spec,
  Edge Cases).

**Por qué no aplica FR-016** (una fila al cerrar): FR-016 es un requisito de **producción**,
donde escribir por reconocimiento crearía un registro de actividad. En evaluación ese registro
es justamente el objetivo y está consentido (FR-006, FR-011). La spec ya exige conservar los
intentos completados de una sesión interrumpida (Edge Cases), y eso solo es posible si se
escriben antes del cierre.

**Producción (diferida)**: allí la spec acepta perder la fila de una sesión interrumpida y
prohíbe escribir filas parciales (Edge Cases). Esta iteración no lo cambia. Ver plan.md,
"Producción diferida".

**Alternativas descartadas**

- *Escribir al cerrar el bloque*: pierde hasta 48 intentos.
- *Escribir en el hilo de reconocimiento*: viola NFR-006 y contamina la medición de
  sobrecosto.
- *SQLCipher para cifrar la base*: agrega una dependencia nativa. El cifrado de disco por
  archivo de Android (obligatorio en dispositivos lanzados con Android 10 o posterior) más
  `allowBackup=false` cubren el riesgo en dispositivos del equipo. El quickstart exige que
  los dispositivos de evaluación tengan el almacenamiento cifrado. Queda como mejora si el
  build se distribuye a terceros.

---

## R-04 Red solo en evaluación y solo para enviar sesiones

**Decisión**

- `INTERNET` y `ACCESS_NETWORK_STATE` se declaran **solo** en
  `app/src/evaluacion/AndroidManifest.xml`. Las marcas `tools:node="remove"` que hoy están
  en `main` pasan a `app/src/produccion/AndroidManifest.xml`. El APK de producción sigue
  exactamente igual que hoy, y `verify.py apk` sigue exigiéndolo.
- El cliente HTTP es `HttpsURLConnection`, de la plataforma. No se agregan bibliotecas de red.
- `network_security_config` del sabor evaluación: sin texto plano y con anclas de confianza
  solo del sistema.
- La exclusión de `transport-backend-cct` de MediaPipe se mantiene en ambos sabores. La capa 1
  de R-01 también la verifica en `evaluacion`: con `INTERNET` habilitado, ese backend enviaría
  telemetría.
- La cadena de reconocimiento no toca la red en ningún sabor (FR-032a, NFR-007).

**Tensión con la spec**: FR-032a dice que la red se usa *exclusivamente* para filas de
producción, y A-05 asume que evaluación exporta sin red. Enviar sesiones de evaluación al
receptor de `helpi-firebase` contradice ambos. El plan lo resuelve **sin eventos
automáticos**: el envío es siempre una acción explícita de la persona sobre una sesión
(NFR-004). Aun así, FR-032a y A-05 **deben enmendarse antes de `/speckit-tasks`** (plan.md,
Enmiendas requeridas).

**Alternativas descartadas**: exportar solo como archivo por el mecanismo de compartir del
sistema (A-05). Se conserva como segundo canal con el **mismo** formato, pero no basta: en
sesiones con varios dispositivos, el traspaso manual de archivos pierde sesiones y no deja
confirmación de recepción.

---

## R-05 Cola de envío (outbox) con WorkManager

**Decisión**

1. "Enviar al equipo" serializa la sesión **en ese momento** y guarda en la tabla `envio`
   los bytes exactos que se enviarán, su SHA-256, un `envioId` (UUID v4) y la `revision`.
   Todo ocurre en la misma transacción que marca la sesión con un envío pendiente. Lo que se
   envía es lo que existía cuando la persona lo pidió.
2. Se encola `OneTimeWorkRequest<EnvioWorker>` con:
   - trabajo único `envio-sesiones-evaluacion`, política `APPEND_OR_REPLACE`;
   - `Constraints(NetworkType.CONNECTED)`;
   - `setBackoffCriteria(EXPONENTIAL, 60 s)`. WorkManager limita el retroceso a 5 h
     (`MAX_BACKOFF_MILLIS`), y ese es el intervalo máximo de FR-028.
3. El worker procesa los envíos `PENDIENTE` del más antiguo al más nuevo:
   - **confirmado** (definido en el [contrato](contracts/envio-sesion-evaluacion.md#confirmación-de-recepción)):
     borra la fila de `envio` y actualiza `sesion.ultimaExportacionEn` en una transacción;
   - **rechazo permanente** (400, 409, 413, 415, 422): `envio.estado = RECHAZADO` con el
     motivo; no se reintenta;
   - **credencial inválida** (401, 403): `BLOQUEADO`; no se reintenta hasta instalar un build
     con otra credencial;
   - **transitorio** (429, 5xx, timeout, DNS, TLS de red): corta el ciclo y devuelve
     `Result.retry()`.
4. **Retención de lo no enviado**: un envío `PENDIENTE` vence a los **30 días** de creado. Se
   borra de la cola y la sesión muestra "envío vencido". La sesión no se borra: sigue su
   retención propia (NFR-011) y puede reenviarse. Un envío `RECHAZADO` o `BLOQUEADO` se
   conserva 30 días para diagnóstico.
5. **Tamaño máximo**: 20 envíos pendientes por dispositivo. Al llegar al límite se rechaza el
   nuevo envío con un mensaje. A diferencia de producción (FR-031), no se descarta el más
   antiguo: cada sesión de evaluación cuesta horas de una persona participante.
6. Un `DepuracionWorker` periódico diario, sin restricción de red, aplica la retención de
   sesiones y de envíos. También corre al abrir la app.

**Revocación y borrado**: revocar cancela los envíos `PENDIENTE` (se borran de la cola) y deja
las sesiones. Borrar sesiones (FR-013a) borra también sus envíos. Ver R-09.

**Alternativas descartadas**

- *Enviar directamente desde la pantalla*: se pierde si no hay red o si la app se cierra.
- *Firebase SDK (Firestore offline)*: agrega dependencias de Google con telemetría propia,
  ata el formato a Firestore y saca el contrato de este repositorio.
- *Generar el payload recién al enviar*: lo enviado podría diferir de lo que la persona
  aprobó, y el hash no podría verificarse contra un estado fijo.

---

## R-06 Credencial de envío

**Decisión**: una clave por build de evaluación, inyectada en tiempo de compilación desde la
propiedad Gradle `helpi.evaluacion.claveEnvio` (en `local.properties` o como secreto de CI;
nunca en el repositorio). Se envía en el encabezado `X-Helpi-Clave-Evaluacion`. La URL sale de
`helpi.evaluacion.urlReceptor`. Sin clave o sin URL, "Enviar al equipo" queda deshabilitado y
solo se ofrece exportar a archivo.

Es suficiente porque el build de evaluación se distribuye solo al equipo (A-08) y la clave se
rota con cada build. El receptor rechaza claves revocadas con 401.

**Alternativas descartadas**: *Firebase App Check* requiere el SDK de Firebase y Play
Integrity. *Cuentas por facilitador* requieren un flujo de autenticación que la app no tiene y
que la spec excluye ("la app sigue sin cuentas").

---

## R-07 Formato del contrato: un JSON, dos canales

**Decisión**

- Un único documento JSON por sesión, `helpi.evaluacion.sesion` v`1.0.0`, que es a la vez el
  **cuerpo del envío** y el **archivo exportado**. Se cumple FR-039 (contrato versionado en
  este repositorio) y el análisis no depende de por dónde llegó la sesión.
- El JSON lo escribe `android.util.JsonWriter`, de la plataforma, con orden de claves fijo. Las
  confianzas van con 6 decimales vía `BigDecimal` (`HALF_EVEN`), así se evita el ruido de
  convertir `float` a `double`.
- Los tests validan contra un JSON Schema (draft 2020-12) con
  `com.networknt:json-schema-validator`, solo como `testImplementation`.
- El esquema, el ejemplo de referencia y los ejemplos negativos viven en `contracts/`. El test
  de contrato los usa directamente (R-11), y `helpi-firebase` copia los mismos archivos con su
  hash fijado.

**Alternativas descartadas**

- *kotlinx.serialization*: agrega un plugin del compilador. El problema del redondeo es el
  mismo y no hay una ganancia que lo justifique.
- *Protobuf*: el binario no es legible para el análisis ni para la persona que exporta.
- *CSV*: no representa top-3 ni interrupciones sin columnas ad hoc.

---

## R-08 Sobrecosto del registro: A/B pareado intercalado

**Decisión** (NFR-005, SC-007, aclaración 2026-10-05):

- Test instrumentado `RegistroSobrecostoTest`, en `app/src/androidTestEvaluacion/`.
- **Entradas**: los casos de `fixture_android.json` (8 secuencias reales), repetidos en rondas
  hasta llegar a **n ≥ 200 pares**. Antes se descartan 20 iteraciones de calentamiento.
- **Par**: para cada repetición *i*, la misma secuencia pasa por el camino de clasificación
  con el observador **real** (escritor de Room sobre una base temporal en disco) y con el
  observador **vacío**. El orden dentro del par (AB o BA) se sortea con una semilla fija para
  cancelar efectos de orden y de calentamiento térmico.
- **Ventana medida**: desde que el tensor de entrada está listo hasta que `onDecision` vuelve,
  con `SystemClock.elapsedRealtimeNanos()`. La normalización previa es idéntica en las dos
  ramas y se cancela en la diferencia. La ventana de deshacer no entra.
- **El escritor no se vacía entre iteraciones**: su competencia por CPU y E/S con el
  reconocimiento es justamente lo que se quiere medir.
- **Estadístico**: dᵢ = t_conᵢ − t_sinᵢ. **Criterio: p95(d) ≤ 5 ms** (rango más cercano).
  También se informan p50(d), y p95 y p50 de cada rama solo como contexto, sin criterio.
- **Control A/A**: la misma corrida mide vacío contra vacío. Si el p95 de las diferencias A/A
  supera 5 ms, el ruido del dispositivo no permite concluir: el test se informa como **no
  concluyente**, nunca como aprobado.
- **Dónde bloquea**: en el **dispositivo de referencia**, antes de distribuir cada build de
  evaluación (quickstart.md, paso 2). En CI corre sobre el emulador de API 35 y publica el
  reporte, pero no bloquea hasta que el control A/A muestre un piso de ruido estable bajo 5 ms.

**Tensión con la spec**: la aclaración y SC-007 hablan de "la diferencia del percentil 95"
(p95(t_con) − p95(t_sin)). El pedido de planificación pide "el percentil 95 de la diferencia"
(p95(dᵢ)). Son estadísticos distintos, y p95(dᵢ) aprovecha el pareo, que es la razón de hacer
A/B. El plan usa p95(dᵢ) y pide **enmendar NFR-005 y SC-007** para que digan lo mismo.

**Alternativas descartadas**: *dos corridas separadas* (A completa, luego B) no cancelan la
deriva térmica ni la de la frecuencia de CPU. Un *presupuesto absoluto* sobre la latencia total
corresponde a la spec 002.

---

## R-09 Consentimiento, revocación y borrado

**Decisión**

- **Qué se muestra** (FR-011, FR-012): una pantalla con el texto llano y el **video en LSA**
  del aviso completo, uno al lado del otro. El aviso explica:
  - qué se registra (seña pedida, seña predicha, confianza, top-3, condiciones,
    versiones);
  - qué **no** se registra (video, imagen, keypoints);
  - para qué se usa (medir exactitud fuera del laboratorio);
  - dónde queda (en el dispositivo y, si alguien lo envía, en el receptor del equipo);
  - por cuánto tiempo (NFR-011 en el dispositivo; retención del receptor);
  - quién accede (el equipo);
  - que el consentimiento cubre a todas las personas que participen en ese dispositivo;
  - cómo revocar y cómo pedir el borrado.

  La aceptación es un botón explícito ("Acepto que se registre") que se habilita sin exigir
  ver el video: ver el video no equivale a consentir (FR-012).
- El texto y el video tienen una versión común, `avisoVersion`. El video se versiona con el
  aviso: un cambio material sube la versión y vuelve a pedir el consentimiento (FR-014).
- **Revocar** (FR-013), desde Ajustes de evaluación:
  - el intento en curso se descarta y la sesión pasa a `INTERRUMPIDA`, causa `REVOCACION`;
  - los envíos `PENDIENTE` se cancelan y se borran de la cola, porque todavía no salieron
    del dispositivo y la revocación retira el permiso de seguir tratándolos;
  - **no se borran** sesiones ni intentos: se conservan hasta su retención (NFR-011);
  - mientras no haya un consentimiento vigente, no se puede iniciar, retomar, exportar ni
    enviar.
- **Borrar** (FR-013a): una acción separada, disponible con o sin consentimiento. Se elige
  "participante P-xxx" o "todas"; la pantalla muestra qué se borra (N sesiones, M intentos,
  K envíos pendientes) y pide confirmar. Borra en cascada sesiones, intentos, top-3,
  interrupciones y envíos.
- **Datos ya enviados**: ni revocar ni borrar en el dispositivo los alcanza. La pantalla de
  borrado lista los `sesionId` enviados de lo que se borra y explica cómo pedir su eliminación
  al equipo (US7-4). El contrato obliga al receptor a poder borrar por `sesionId` y por
  `participante.codigo` ([contrato](contracts/envio-sesion-evaluacion.md#obligaciones-del-receptor)).
  El plazo legal para esa respuesta se confirma en la revisión bajo la Ley N.º 25.326 que pide
  la spec (Dependencias y riesgos).

**Dependencia**: grabar el video en LSA requiere una persona intérprete o señante y se repite
con cada versión material del aviso. Sin el video, el build de evaluación no puede usarse con
participantes sordos (SC-006). La pantalla de consentimiento exige que el asset exista y que su
hash coincida con `avisoVersion`.

---

## R-10 Dónde se calcula cada métrica

| Lugar | Qué calcula | Qué no calcula |
|---|---|---|
| **Dispositivo** (`:evaluacion`) | Por intento: `correcto` (índice esperado = índice predicho y superó el umbral), estado del intento, causa de "sin resultado". Por sesión: avance (intento N de M, bloques). | Ninguna métrica agregada: ni exactitud, ni matriz, ni tasas. No se muestran resultados acumulados a quien participa, para no influir en cómo seña. |
| **Receptor** (`helpi-firebase`) | Nada. Valida contra el esquema, guarda, deduplica y permite exportar en bloque. | Métricas: el receptor no interpreta el contenido. |
| **Análisis local** (repositorio de análisis, todavía inexistente; FR-039) | Todo FR-033 a FR-038 y FR-042a, FR-044 y FR-045 sobre exportaciones (archivos o descarga en bloque del receptor). | — |

Reglas que el contrato le deja al análisis, documentadas en el [contrato](contracts/envio-sesion-evaluacion.md#reglas-para-el-análisis):

- **Partición por sujeto** (FR-035, NFR-008). Todo participante es un sujeto no visto por Eva,
  así que la exactitud se calcula sobre todos los intentos. Los intervalos de confianza
  (NFR-010) salen de un **bootstrap por participante**: se remuestrean personas, no intentos.
  La calibración de umbral (FR-034) usa **leave-one-subject-out**: se elige el umbral sin la
  persona *k* y se evalúa con ella. Cualquier corte por intentos se rechaza.
- **Agrupación**: nunca mezclar `versiones.modelo`, `versiones.catalogoSha256`,
  `versiones.contratoKeypoints` ni `vioVideo` (FR-037, FR-042a).
- **Identidad del sujeto**: `participante.codigo` lo asigna el equipo (R-12), así que es
  consistente entre dispositivos.
- **Última revisión**: por `sesionId` se usa la de mayor `revision`.

**Alternativa descartada**: *calcular métricas en el receptor*. Ataría el análisis a la
plataforma del receptor (Cloud Functions) y lo sacaría del alcance de FR-039, donde la spec lo
ubica.

---

## R-11 Test de contrato compartido con helpi-firebase

**Decisión**

- Los archivos canónicos viven en `specs/001-calidad-reconocimiento/contracts/`:
  - `envio-sesion-evaluacion.schema.json`;
  - `ejemplos/sesion-evaluacion-v1.ejemplo.json`;
  - `ejemplos/negativos/*.json`.
- `:evaluacion` los suma como recursos de test (`sourceSets.test.resources.srcDir`). No se
  copian.
- El test de contrato:
  1. arma una sesión fija (UUID, semilla y reloj fijos) y la serializa;
  2. compara **semánticamente** el resultado con el ejemplo de referencia (árbol JSON
     igual), no byte a byte;
  3. valida el resultado y el ejemplo contra el esquema;
  4. confirma que cada ejemplo negativo **falla** la validación.
- `CONTRATO_SHA256` en `contracts/` fija el hash de cada archivo. `helpi-firebase` copia los
  mismos archivos y corre los mismos ejemplos contra su receptor. Un hash distinto en
  cualquiera de los dos repositorios hace fallar su CI.
- Un test del cliente con `MockWebServer` (solo `testImplementation`) cubre la tabla de
  respuestas del contrato (§Respuestas): confirmación, duplicado, conflicto, rechazos y
  transitorios.

---

## R-12 Protocolo de validación

**Decisión** (FR-040 a FR-044, aclaraciones 2026-10-04 y 2026-10-05):

- **Generador** (`:evaluacion-dominio`, Java):
  - lista de 64 × 3 = 192 intentos;
  - `java.util.Random(semilla)`, cuyo algoritmo está fijado por la especificación de Java y es
    reproducible;
  - mezcla de Fisher–Yates; reparación determinística de adyacencias, intercambiando con el
    primer elemento posterior que no repita, para que ninguna seña aparezca dos veces seguidas
    (FR-041), tampoco entre bloques;
  - corte en 4 bloques de 48.
- La exportación incluye la secuencia completa, así que el análisis no necesita reproducir el
  generador.
- **Participante**: un código **asignado por el equipo** (`P-001`, …), que el facilitador carga
  en el dispositivo. Agrupa a una persona entre dispositivos y no contiene datos personales
  (FR-015).
- **Condiciones** (FR-008): un formulario obligatorio antes del primer intento. Todos los
  campos son enumerados, no texto libre, para que el análisis pueda agruparlos y para no
  capturar datos identificatorios. Si las condiciones cambian, se abre otra sesión (FR-040a).
  Cada sesión cubre un entorno; el conjunto debe cubrir al menos 2 (FR-040b).
- **Flujo de un intento, sin audio** (FR-042):
  1. **Seña pedida**: la glosa en tipografía grande, "Intento N de 192 · Bloque b de 4", la
     vista de cámara y la guía de encuadre existente. El botón "Ver seña de referencia"
     reproduce el clip de LSA64 con atribución y marca `vioVideo = true`; nunca arranca solo.
  2. **Esperando la seña**: el estado visual del segmentador (ya existe en la app). Sin
     segmento en 15 s, termina como `SIN_RESULTADO / TIEMPO_AGOTADO`.
  3. **Resultado**:
     - **sobre el umbral**: la glosa predicha queda pendiente 3 s con "Descartar" (FR-024a);
     - **bajo el umbral**: "No reconocida" (no se muestra la adivinanza);
     - **sin resultado**: la causa en lenguaje llano y "Repetir intento".
  4. **Siempre**: "Lo hice mal" (FR-044) hasta que empieza el intento siguiente; "Pausar";
     "Terminar sesión".
- "Repetir intento" solo se ofrece ante un "sin resultado" técnico. El intento original queda
  registrado como `reemplazado` y el nuevo ocupa la misma posición con `numeroIntento + 1`.
- Entre bloques hay una pantalla de pausa con "Seguir" y "Retomar otro día".
- **Videos de referencia**: un paquete versionado que **produce `helpi-ml`** (transcodifica
  LSA64 a 640×360 H.264, unos 20 MB) con manifiesto y hash, siguiendo el patrón del modelo y
  el catálogo (CLAUDE.md §3, §7). Vive solo en assets de `:evaluacion`. La atribución
  CC BY-NC-SA 4.0 se muestra en cada reproducción (CLAUDE.md §9).

**Alternativas descartadas**

- *Identificador generado en el dispositivo*: no agrupa a la misma persona entre dispositivos
  y rompe la partición por sujeto.
- *Mostrar acierto o error acumulado a quien participa*: sesga el señado.
- *Pictogramas en lugar de glosa*: no existen para las 64 señas.

---

## R-13 Retención y reloj

**Decisión**:

- Las fechas de retención usan el reloj de pared (UTC) guardado al registrar, porque
  sobreviven reinicios. Las duraciones dentro de la sesión usan
  `SystemClock.elapsedRealtime()` (spec, Edge Cases).
- Para no borrar de más ante saltos de reloj, `DepuracionWorker` guarda la última hora de
  pared vista:
  - si la hora actual es **anterior**, no depura en esa corrida;
  - si saltó **más de 400 días hacia adelante** respecto de la última vista, tampoco depura y
    lo registra en el diagnóstico local.
- Sesiones: el borrado ocurre a los 90 días de `ultimaExportacionEn`, si existe. Si no, a los
  180 días de `ultimoIntentoEn`; si la sesión todavía no tiene intentos registrados, a los
  180 días de `creadaEn`. En una sesión retomada, `ultimoIntentoEn` representa el último bloque
  que dejó intentos persistidos (NFR-011).
- Una exportación cuenta tanto el envío confirmado como el archivo compartido.

**Alternativa descartada**: retención solo por reloj monótono. Se reinicia al apagar el
teléfono.
