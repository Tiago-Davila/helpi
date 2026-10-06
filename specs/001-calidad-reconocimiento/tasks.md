---

description: "Tareas de la iteración de evaluación de 001-calidad-reconocimiento"
---

# Tasks: Medición de calidad del reconocimiento de Eva — iteración de evaluación

**Input**: `specs/001-calidad-reconocimiento/`: [plan.md](plan.md), [spec.md](spec.md),
[research.md](research.md), [data-model.md](data-model.md), [contracts/](contracts/),
[quickstart.md](quickstart.md).

**Alcance**: solo el build de **evaluación** en `helpi-android`. El camino de producción está
diferido (plan.md, "Diferido"); el receptor pertenece a `helpi-firebase`; el rendimiento de la
app, a la spec 002.

**Tests**: pedidos explícitamente. En las fases 1 y 2 el test se escribe **antes** que el
componente que lo consume y queda como bloqueante de CI.

**Revisión**: incorpora los 10 hallazgos principales de `/speckit-analyze` del 2026-10-05:
C1, C2, D1, G1, R1, S1, S2, P1, P2 y F1. Siguen pendientes F2, F3, F4, F7, F10 (data-model) y
T3, T4, T5 (tests).

## Format: `[ID] [P?] [Story] Descripción`

- **[P]**: se puede hacer en paralelo (archivos distintos y sin dependencias pendientes).
- **[USn]**: historia de spec.md a la que sirve la tarea.
- **Deps**: tareas que tienen que estar terminadas antes.
- **Ref**: requisito o decisión que origina la tarea. Una tarea sin referencia se marca
  **⚑ REVISAR** (no hay ninguna).

## Estado de las enmiendas

E-1 (red en evaluación, FR-032a), E-2 (p95 de las diferencias pareadas, NFR-005 y SC-007) y
E-3 (opción a, cita de NFR-003) **están aplicadas en spec.md** desde el 2026-10-05. Ninguna
tarea queda bloqueada por la spec.

Sigue vigente una condición operativa: **el primer envío real** al receptor requiere la
revisión bajo la Ley N.º 25.326 (spec, Dependencias y riesgos). Las tareas de envío se
implementan y prueban contra un destino simulado sin esa revisión.

## Path Conventions

- `app/`: aplicación (sabores `evaluacion` / `produccion`).
- `domain/`: Java, existente.
- `evaluacion-dominio/`: Java sin Android, nuevo.
- `evaluacion/`: biblioteca Android, nueva.
- Paquetes: `com.helpi.evaluacion.dominio.*` y `com.helpi.evaluacion.*`.

---

## Phase 1: Separación de builds (US2, P1)

**Goal**: dos variantes. El código de recolección solo puede existir en `evaluacion`, y una
verificación automatizada y bloqueante demuestra que el APK de producción no lo contiene.

**Independent Test**: quickstart §1. `produccionRelease` pasa la verificación, y el canario
(`-PcanarioEvaluacion=true`) la hace fallar.

- [X] T001 [US2] Agregar la dimensión de sabor `registro` con `evaluacion` (`applicationIdSuffix ".evaluacion"`, `versionNameSuffix "-evaluacion"`, nombre "Helpi Evaluación") y `produccion` en `app/build.gradle.kts`. Mover las marcas `tools:node="remove"` de `INTERNET` y `ACCESS_NETWORK_STATE` de `app/src/main/AndroidManifest.xml` a un nuevo `app/src/produccion/AndroidManifest.xml`. Crear `app/src/evaluacion/AndroidManifest.xml` con **las mismas** marcas `remove`: `transport-runtime` de MediaPipe y WorkManager declaran `ACCESS_NETWORK_STATE`, y las marcas se quitan recién en T018. Sin cambio de comportamiento. — Deps: — · Ref: FR-001, research R-01, R-04
- [X] T002 [P] [US2] Migrar la CI a las tareas por sabor (`lintProduccionDebug`/`lintEvaluacionDebug`, `testProduccionDebugUnitTest`/`testEvaluacionDebugUnitTest`, `connected{Produccion,Evaluacion}DebugAndroidTest`, `jacoco*Report`) en `.github/workflows/ci.yml`, `scripts/ci/android-tests.sh` y las rutas de resultados de `scripts/ci/verify.py`. `ModelReferenceTest` corre en ambos sabores. — Deps: T001 · Ref: plan.md (Riesgos: renombrado de variantes), CLAUDE.md §6
- [X] T003 [P] [US2] **Test primero**: en `scripts/ci/test_verify.py`, casos con listados sintéticos de `apkanalyzer` (manifiesto, `dex packages`, entradas del zip). Un APK limpio pasa. Fallan: cada paquete prohibido (`com.helpi.evaluacion`, `androidx.room`, `androidx.work`), cada permiso (`INTERNET`, `ACCESS_NETWORK_STATE`) y cada asset bajo `assets/evaluacion/`. Debe fallar hasta T004. — Deps: — · Ref: FR-004, SC-003, NFR-013, research R-01 (capa 2)
- [X] T004 [US2] Extender `apk()` en `scripts/ci/verify.py` con la lista de prohibidos de T003 (paquetes vía `apkanalyzer dex packages`, `ACCESS_NETWORK_STATE`, assets `evaluacion/`). Agregar a `ci.yml` el paso que ensambla `produccionRelease` y corre `verify.py apk` sobre ese APK como bloqueante. — Deps: T001, T003 · Ref: FR-004, NFR-013, research R-01 (capa 2)
- [X] T005 [US2] Crear en `app/build.gradle.kts` la tarea `verificarProduccionSinEvaluacion`, que resuelve `produccionReleaseRuntimeClasspath` y falla si aparece `:evaluacion`, `:evaluacion-dominio`, `androidx.room:*`, `androidx.work:*` o `com.google.android.datatransport:transport-backend-cct`; la misma verificación de `transport-backend-cct` corre sobre `evaluacionReleaseRuntimeClasspath`. Agregar la propiedad `canarioEvaluacion`, que suma `:evaluacion` a `produccion`. Agregar a `ci.yml` un job `canario-produccion` que **exige** que esta tarea y `verify.py apk` fallen con el canario. — Deps: T001, T004 · Ref: FR-004, SC-003, research R-01 (capas 1 y 3), R-04
- [X] T006 [US2] Crear los módulos `evaluacion-dominio/` (`java-library`, Java 17, JUnit, SpotBugs igual que `domain/build.gradle.kts`) y `evaluacion/` (`com.android.library`, namespace `com.helpi.evaluacion`, Compose, ktlint y detekt), incluirlos en `settings.gradle.kts`, conectarlos con `evaluacionImplementation(project(":evaluacion"))` en `app/build.gradle.kts` y agregar al catálogo `gradle/libs.versions.toml`: Room, KSP, WorkManager, `work-testing`, `room-testing`, `mockwebserver` y `json-schema-validator` (solo los de test como `testImplementation`). Con T005 en verde, demostrar que `produccionRelease` sigue pasando. — Deps: T005 · Ref: FR-002, FR-003, research R-01
- [X] T007 [US2] Crear el puerto `RecognitionObserver` y el enum `NoResultCause` (`SIN_HOMBROS`, `SEGMENTO_CORTO`, `SEGMENTO_LARGO`, `SEGUIMIENTO_PERDIDO`, `ORIENTACION_CAMBIO`, `ERROR_MODELO`) en `domain/src/main/java/com/helpi/conversation/observation/`. Hacer que `app/src/main/java/com/helpi/conversation/session/SessionCoordinator.kt` emita `onSegmentStarted`, `onNoResult` y `onDecision(SignDecision, thresholdUsed, segmentEndMs)`, y agregar el modo de entrega `OBSERVER_ONLY` (sin publicar ni vocalizar). El contrato del puerto queda documentado: sin E/S ni bloqueo. Tests en `app/src/test/…/SessionCoordinator*Test.kt` con un observador falso. — Deps: T001 · Ref: FR-006, NFR-006, research R-02
- [X] T008 [US2] Crear `VariantBindings.kt` en `app/src/produccion/java/com/helpi/conversation/` (observador vacío, sin entrada de menú ni distintivo) y en `app/src/evaluacion/java/com/helpi/conversation/` (entrada "Validación" hacia el grafo de `:evaluacion` y distintivo permanente "EVALUACIÓN — registra lo que se seña" sobre toda la UI). Usarlo desde `MainActivity.kt` y `ui/AccountScreen.kt`. Test de UI en `app/src/androidTestProduccion/…/SinValidacionTest.kt`: producción no muestra ningún control de registro. — Deps: T006, T007 · Ref: FR-005, US2-3, research R-01

**Checkpoint F1**: CI en verde con los dos sabores; `verify.py apk` y
`verificarProduccionSinEvaluacion` bloqueantes; el job canario falla como se espera;
`ModelReferenceTest` pasa en ambos sabores. **Condición para pasar**: ninguna tarea posterior
puede agregar código de recolección fuera de `:evaluacion*` sin que CI falle.

---

## Phase 2: Contrato de envío (US1, P1)

**Goal**: la app produce documentos `helpi.evaluacion.sesion` v1.0.0 idénticos en semántica al
ejemplo de referencia compartido con `helpi-firebase`.

**Independent Test**: el test de contrato pasa contra `contracts/` sin tocar los archivos
canónicos.

- [X] T009 [US1] **Test primero**: `evaluacion/src/test/java/com/helpi/evaluacion/contrato/ContratoSesionTest.kt`, que suma `../specs/001-calidad-reconocimiento/contracts` como recursos de test en `evaluacion/build.gradle.kts`. Verifica:
  - los hashes de `CONTRATO_SHA256`;
  - que el ejemplo valida contra `envio-sesion-evaluacion.schema.json`;
  - que **cada** archivo de `ejemplos/negativos/` es rechazado;
  - que el esquema admite estructuralmente más de 1.000 intentos y más de 500 interrupciones;
  - las reglas **S-02 a S-08** del contrato sobre el ejemplo (S-01 compara encabezados y va en T019);
  - que la serialización de una sesión fija (UUID, semilla `20261005` y reloj fijos, los 5 intentos y la interrupción del ejemplo) es **semánticamente igual** a `ejemplos/sesion-evaluacion-v1.ejemplo.json`.

  Bloqueante de CI; falla hasta T010. — Deps: T006 · Ref: FR-009, FR-039, research R-07, R-11
- [X] T010 [US1] Implementar en `evaluacion/src/main/java/com/helpi/evaluacion/contrato/` el modelo `SesionDocumento.kt` (independiente de Room) y `SesionContratoSerializer.kt`. El serializador usa `android.util.JsonWriter` con orden de claves fijo, **todas las claves presentes con `null` explícito**, confianzas con 6 decimales vía `BigDecimal` `HALF_EVEN`, `semilla` como texto decimal, instantes RFC 3339 UTC con `Z`, y salida UTF-8 sin BOM. Debe hacer pasar T009. — Deps: T009 · Ref: FR-009, FR-039, contrato §Tipos y convenciones, research R-07

**Checkpoint F2**: T009 en verde y bloqueante en CI. **Condición para pasar**: cualquier cambio
en `contracts/` o en el serializador que rompa el ejemplo hace fallar CI.

---

## Phase 3: Modelo de datos local (US1, P1)

**Goal**: base Room `helpi_evaluacion.db`, solo en `:evaluacion`, según [data-model.md](data-model.md).

**Independent Test**: tests de DAO con Room en memoria: cascadas, índice único e invariantes.

- [X] T011 [US1] Crear las entidades, los enums y los `TypeConverters` en `evaluacion/src/main/java/com/helpi/evaluacion/datos/entidades/`: `ConsentimientoEntity`, `ParticipanteEntity`, `SesionEntity`, `CondicionesPruebaEntity`, `IntentoEntity`, `PrediccionTopEntity`, `InterrupcionEntity`, `EnvioEntity` y `EstadoDepuracionEntity`. Restricciones literales de data-model:
  - `participante.codigo`: "patrón `^P-[0-9]{3,4}$`";
  - `sesion.id`: "String PK (UUID v4)";
  - `EstadoSesion`: `CREADA`, `EN_CURSO`, `PAUSADA`, `INTERRUMPIDA`, `COMPLETA`, `TERMINADA_ANTES`;
  - `condiciones_prueba`: "1:1 con `sesion`; inmutable"; todos sus enums tal como figuran en data-model;
  - `intento.posicion`: "Int 1..192"; `bloque`: "Int 1..4"; índices "Int 0..63"; `confianza`: "Float? [0,1]";
  - `causaSinResultado`: "Solo si `SIN_RESULTADO`";
  - "Índice único `(sesionId, posicion, numeroIntento)`";
  - `prediccion_top`: "PK compuesta con `rango`", `rango` "Int 1..3";
  - `envio.estado`: `PENDIENTE`, `RECHAZADO`, `BLOQUEADO`; `payload` "BLOB"; `sha256` "String(64)";
  - FKs `ON DELETE CASCADE` como en data-model.

  **No** hay columnas para video, cuadros ni keypoints. — Deps: T006 · Ref: FR-006, FR-007, FR-008, FR-010, FR-014, FR-015, data-model.md
- [X] T012 [US1] Crear los DAOs (`SesionDao`, `IntentoDao`, `ConsentimientoDao`, `ParticipanteDao`, `EnvioDao`, `DepuracionDao`) y `HelpiEvaluacionDatabase.kt` en `evaluacion/src/main/java/com/helpi/evaluacion/datos/`, con el esquema exportado en `evaluacion/schemas/`. Las transacciones son las de la tabla "Qué se escribe y cuándo" de data-model, entre ellas "iniciar bloque solo si no hay otra sesión `EN_CURSO`", "intento + sus tres `prediccion_top` (o ninguna si `SIN_RESULTADO`) + sesion.ultimoIntentoEn" y "repetición marca el anterior `reemplazado = true`". Tests en `evaluacion/src/test/java/com/helpi/evaluacion/datos/DaoTest.kt` (Robolectric, Room en memoria): borrar un participante borra todo en cascada; el índice único rechaza duplicados; no se inicia una segunda sesión `EN_CURSO`; por posición solo queda un intento no reemplazado y es el de mayor `numeroIntento`; rangos top-3 completos y ordenados; predicción de rango 1 igual a la guardada; ningún top-3 para `SIN_RESULTADO`. — Deps: T011 · Ref: FR-013a, NFR-006, data-model.md, contrato S-04, S-07

**Checkpoint F3**: DAO y tests en verde; `verificarProduccionSinEvaluacion` sigue en verde
(Room solo en evaluación). **Condición para pasar**: esquema exportado y versionado.

---

## Phase 4: Registro de la sesión (US1, P1)

**Goal**: registrar cada intento sin tocar el hilo de reconocimiento, y no perder nada de lo
resuelto cuando la sesión se interrumpe.

> **Nota de diseño (plan, research R-03)**: "acumulación en memoria y escritura al cerrar la
> sesión" es el diseño de **producción** (FR-016, diferido). En evaluación, en memoria solo
> vive el intento en curso, y cada intento resuelto se escribe en una transacción desde un
> hilo dedicado. Así una sesión interrumpida conserva todo lo resuelto. **El intento lo
> resuelve el `ProtocoloViewModel` (T028)**; `RegistroObserver` solo reenvía eventos.

**Independent Test**: un cierre forzado a mitad de un bloque deja la sesión `INTERRUMPIDA`
con todos los intentos resueltos, y se retoma en el primer intento sin respuesta.

- [X] T013 [P] [US1] Implementar en `evaluacion-dominio/src/main/java/com/helpi/evaluacion/dominio/sesion/` y `…/dominio/intento/` la máquina de estados `EstadoSesion` (transiciones de data-model; una sola `EN_CURSO`; `COMPLETA` y `TERMINADA_ANTES` finales), las reglas del intento (`correcto = resultado == SOBRE_UMBRAL && predicho == esperado`; `SIN_RESULTADO ⇔ causa != null ⇔ predicho == null`; sin predicción top-3 ni confianza para `SIN_RESULTADO`; top-3 con tres rangos ordenados y rango 1 igual a la predicción para resultados clasificados; `superoUmbral = confianza >= umbral`; `descartado ⇒ SOBRE_UMBRAL`) y el cálculo de la posición de retoma (primer intento sin respuesta del bloque). Tests JVM en `evaluacion-dominio/src/test/java/…`. — Deps: T006 · Ref: FR-007, FR-025, FR-040a, FR-044, contrato S-04 a S-08, Edge Cases
- [X] T014 [US1] Implementar en `evaluacion/src/main/java/com/helpi/evaluacion/registro/`:
  - `EscritorRegistro`: un hilo, cola acotada de 64, `trySend`. Si la cola está llena o ocurre `SQLiteFullException`, acumula el intento perdido en memoria sin bloquear ni hacer E/S en el hilo de reconocimiento; suma los pendientes a `sesion.intentosPerdidos` en la próxima transacción exitosa. Si el proceso termina antes del vaciado, el contador puede subestimar esa ejecución y la posición sin intento persistido se vuelve a ofrecer al retomar.
  - `RegistroObserver`: implementa `RecognitionObserver` y **solo reenvía** los eventos del puerto a un flujo en memoria. No tiene estado de intento ni escribe.

  Enlazarlo en `app/src/evaluacion/java/com/helpi/conversation/VariantBindings.kt`. Tests JVM: ninguna llamada del observador ni del encolado hace E/S en el hilo que la invoca; la cola llena y el disco lleno no lanzan excepciones al llamador. — Deps: T007, T008, T012 · Ref: FR-006, NFR-006, Edge Cases (almacenamiento lleno), research R-02, R-03
- [X] T015 [US1] Implementar la recuperación en `evaluacion/src/main/java/com/helpi/evaluacion/registro/RecuperacionSesiones.kt`:
  - al arrancar, `EN_CURSO` → `INTERRUMPIDA` con `interrupcion(PROCESO_TERMINADO)`;
  - en `ON_STOP` → `INTERRUMPIDA` con `SEGUNDO_PLANO`;
  - el intento en curso se descarta;
  - la retoma conserva participante, condiciones y semilla.

  Test instrumentado `app/src/androidTestEvaluacion/java/com/helpi/conversation/evaluacion/SesionInterrumpidaTest.kt`: termina el proceso entre intentos, reabre y verifica el estado, los intentos conservados y la posición de retoma. — Deps: T013, T014 · Ref: FR-040a, Edge Cases (sesión interrumpida), research R-03
- [X] T016 [US1] **Test de sobrecosto del registro**: `app/src/androidTestEvaluacion/java/com/helpi/conversation/evaluacion/RegistroSobrecostoTest.kt`.
  - **Entradas**: los casos de `app/src/androidTest/assets/fixture_android.json`, repetidos hasta tener **n ≥ 200 pares** en el mismo dispositivo, tras 20 iteraciones de calentamiento descartadas.
  - **Par**: cada repetición pasa la **misma** secuencia por la cadena de clasificación con el registro **activado** y **desactivado**, en orden AB/BA sorteado con semilla fija.
    - Activado: `RegistroObserver` real, y cada decisión se convierte de inmediato en un intento resuelto y se entrega a `EscritorRegistro` (base temporal en disco, sin vaciarla entre iteraciones).
    - Desactivado: observador vacío.
  - **Ventana**: desde el tensor listo hasta que vuelve `onDecision`, medida con `elapsedRealtimeNanos`; la ventana de deshacer queda fuera.
  - **Criterio**: **p95(dᵢ = t_con − t_sin) ≤ 5 ms**.
  - **Control A/A**: vacío contra vacío; si su p95 supera 5 ms, el resultado es "no concluyente".
  - **No mide ni informa como criterio la latencia absoluta**.
  - **Reporte**: `app/build/reports/qa-stages/sobrecosto/`.
  - **Ejecución**: entrada en `scripts/ci/android-tests.sh`, informativa en el emulador.

  — Deps: T014 · Ref: NFR-005, SC-007, research R-08

**Checkpoint F4**: T013 a T015 en verde; T016 aprobado en el dispositivo de referencia.
**Condición para pasar**: el registro existe, no bloquea el reconocimiento y sobrevive a
interrupciones.

---

## Phase 5: Cola de envío (US1, P1)

**Goal**: guardar antes de enviar, enviar en segundo plano con red y backoff, borrar solo tras
una confirmación verificable y depurar lo que no se envía. Termina en el contrato y en un
destino simulado (`MockWebServer`); **no** implementa el receptor.

**Independent Test**: tests JVM de la cola contra `MockWebServer` cubren la tabla de
respuestas del contrato.

- [ ] T017 [US1] Implementar `evaluacion/src/main/java/com/helpi/evaluacion/datos/SesionDocumentoMapper.kt`, que arma un `SesionDocumento` desde Room (sesión, condiciones, intentos con top-3 en orden de registro incluidos los reemplazados, interrupciones, versiones de app, modelo, hashes de modelo y catálogo, contrato de keypoints y la `secuencia` regenerada desde `semilla`). Test: el documento mapeado cumple las reglas S-02 a S-08 y el esquema. — Deps: T010, T012, T026 · Ref: FR-008, FR-009, FR-043, contrato §Reglas semánticas
- [X] T018 [US1] Habilitar la red **solo en evaluación**:
  - en `app/src/evaluacion/AndroidManifest.xml`, quitar los `remove` de T001 y declarar `INTERNET` y `ACCESS_NETWORK_STATE`;
  - crear `app/src/evaluacion/res/xml/network_security_config.xml` (sin texto plano, anclas de confianza del sistema) y referenciarlo desde el manifiesto;
  - leer `helpi.evaluacion.urlReceptor` y `helpi.evaluacion.claveEnvio` hacia `BuildConfig` en `evaluacion/build.gradle.kts` (sin ellas, el envío queda deshabilitado);
  - agregar a `scripts/ci/verify.py` el comando `apk-evaluacion`, que exige `INTERNET` presente, `usesCleartextTraffic` ausente o `false` y ausencia de `transport-backend-cct`, con sus casos en `scripts/ci/test_verify.py` y un paso en `ci.yml`.

  `verify.py apk` sobre producción sigue exigiendo que no haya `INTERNET`. — Deps: T001, T006 · Ref: FR-032a, research R-04, R-06
- [X] T019 [US1] Implementar `evaluacion/src/main/java/com/helpi/evaluacion/envio/ReceptorCliente.kt` con `HttpsURLConnection`:
  - `POST {urlReceptor}/v1/sesiones-evaluacion` con los encabezados del contrato;
  - timeouts de 15 s y 30 s, sin redirecciones;
  - clasificación de la respuesta en `Confirmado` (200 `RECIBIDO`/`DUPLICADO` con `envioId` y `sha256` coincidentes), `Rechazado`, `Bloqueado` o `Transitorio`, según la tabla del contrato, guardando el `estado` de la respuesta como `motivo`.

  Tests con `MockWebServer`: uno por fila de la tabla de respuestas; el 200 con `sha256` distinto queda `Transitorio`; S-01 (`X-Helpi-Contrato` e `Idempotency-Key` coinciden con el cuerpo). — Deps: T018 · Ref: FR-030, FR-032 (canal cifrado), NFR-004, contrato §Transporte y §Respuestas, research R-06
- [ ] T020 [US1] Implementar `evaluacion/src/main/java/com/helpi/evaluacion/envio/EnvioRepositorio.kt`, que encola: en **una** transacción serializa la sesión, guarda en `envio` los bytes exactos, el `sha256`, un `envioId` UUID v4 y la `revision` (incrementada), y encola el trabajo. Rechaza el encolado con más de **20** `PENDIENTE` o sin consentimiento vigente (regla de T023). Tests JVM: lo guardado es byte a byte lo que se enviará; con 20 pendientes, el encolado número 21 se rechaza; sin consentimiento vigente se rechaza. — Deps: T012, T017, T019, T023 · Ref: FR-027, NFR-004, research R-05
- [ ] T021 [US1] Implementar `evaluacion/src/main/java/com/helpi/evaluacion/envio/EnvioWorker.kt`:
  - `OneTimeWorkRequest` único `envio-sesiones-evaluacion`, `APPEND_OR_REPLACE`;
  - `Constraints(NetworkType.CONNECTED)`;
  - `setBackoffCriteria(EXPONENTIAL, 60 s)` (WorkManager limita a 5 h);
  - procesa del más antiguo al más nuevo;
  - **borra la fila solo con `Confirmado`** y pone `sesion.ultimaExportacionEn` en la misma transacción;
  - `Rechazado` → `RECHAZADO`; `Bloqueado` → `BLOQUEADO`, ambos con `motivo`; `Transitorio` → `Result.retry()`.

  Tests en `evaluacion/src/test/…/envio/EnvioWorkerTest.kt` con `work-testing` y `MockWebServer`: sin conexión, reintentos, confirmación, `sha256` distinto y 4xx sin reintento. — Deps: T019, T020 · Ref: FR-028, FR-029, FR-030, research R-05
- [X] T022 [US1] Implementar las reglas de retención en `evaluacion-dominio/src/main/java/com/helpi/evaluacion/dominio/retencion/`:
  - sesión: 90 días desde `ultimaExportacionEn`, o 180 desde `ultimoIntentoEn`;
  - envío `PENDIENTE` vence a los 30 días;
  - `RECHAZADO` y `BLOQUEADO` se conservan 30 días;
  - guarda de reloj: no depura si la hora retrocedió o saltó más de 400 días.

  Implementar también `DepuracionWorker` periódico diario, sin restricción de red, más la corrida al abrir la app, en `evaluacion/src/main/java/com/helpi/evaluacion/envio/DepuracionWorker.kt`, con la marca "envío vencido" en la sesión. Tests JVM de las reglas y de los saltos de reloj. — Deps: T012, T013 · Ref: NFR-011, FR-031 (variante de evaluación), Edge Cases (reloj incorrecto), research R-05, R-13

**Checkpoint F5**: los tests de la cola, del cliente y de `verify.py apk-evaluacion` están en
verde contra el destino simulado; la retención pasa sus tests. **Condición para pasar**:
`verify.py apk` sobre producción sigue exigiendo que no haya `INTERNET`.

---

## Phase 6: Consentimiento (US1 escenario 1, US7, P3)

**Goal**: nada se registra sin un consentimiento explícito y vigente; revocar detiene todo sin
borrar; el borrado es una acción aparte.

**Independent Test**: escenarios de US7. Con dos participantes: revocar → no se registra más,
pero las sesiones siguen; borrar uno → solo quedan las del otro.

- [X] T023 [US1] Implementar en `evaluacion/src/main/java/com/helpi/evaluacion/consentimiento/` la pantalla del aviso:
  - texto llano y video en LSA lado a lado; el video se reproduce solo a pedido;
  - el botón "Acepto que se registre" no exige ver el video;
  - el contenido es el listado en research R-09, con `strings.xml` de `:evaluacion`;
  - `AVISO_VERSION_ACTUAL` y verificación del hash del asset `assets/evaluacion/aviso/aviso-v{n}.mp4` contra `avisoVideoSha256`;
  - la compuerta impide iniciar, retomar, exportar y enviar sin consentimiento vigente.

  Test de UI con un asset de prueba en `evaluacion/src/androidTest/`. El video real es un asset externo: sin él, el build no se usa con participantes. — Deps: T008, T012 · Ref: FR-011, FR-012, FR-014, US1-1, research R-09
- [X] T024 [US7] Implementar la revocación en `evaluacion/src/main/java/com/helpi/evaluacion/consentimiento/Revocacion.kt` y Ajustes de evaluación. En una transacción:
  - completa `revocadoEn`;
  - pasa la sesión `EN_CURSO` a `INTERRUMPIDA` con causa `REVOCACION` y descarta el intento en curso;
  - borra los `envio` en `PENDIENTE`;
  - **no** borra sesiones ni intentos.

  Tests JVM con Room en memoria para US7-1 y US7-2. — Deps: T015, T023 · Ref: FR-013, US7-1, US7-2, research R-09
- [ ] T025 [US7] Implementar la acción de borrado en `evaluacion/src/main/java/com/helpi/evaluacion/consentimiento/BorradoDatos.kt` y su pantalla:
  - elegir "participante P-xxx" o "todas";
  - resumen previo (N sesiones, M intentos, K envíos pendientes);
  - confirmación y borrado en cascada;
  - disponible con o sin consentimiento;
  - lista de `sesionId` **ya enviados o exportados** y el texto para pedir su supresión al equipo.

  Tests de US7-3 y US7-4. — Deps: T012, T023 · Ref: FR-013a, US7-3, US7-4, contrato §Obligaciones del receptor (supresión)

**Checkpoint F6**: los escenarios de US7 pasan. **Condición para pasar**: ningún camino de
registro ni de exportación funciona sin consentimiento vigente.

---

## Phase 7: Protocolo de validación (US1, US4, P1)

**Goal**: guiar una sesión de 192 intentos sin depender del audio, con las condiciones
registradas, y permitir descartar dentro de la ventana de deshacer.

**Independent Test**: quickstart §5 a §7 en el dispositivo de referencia.

- [ ] T026 [P] [US1] Implementar el generador en `evaluacion-dominio/src/main/java/com/helpi/evaluacion/dominio/protocolo/GeneradorProtocolo.java`:
  - 64 × 3 = 192 intentos;
  - `Collections.shuffle(lista, new Random(semilla))`;
  - reparación determinística de adyacencias, intercambiando con el primer elemento posterior que no cree otra adyacencia;
  - 4 bloques de 48;
  - versión `"1.0.0"`.

  Test JVM: cada índice aparece 3 veces, no hay iguales consecutivos y la semilla `20261005` reproduce exactamente `protocolo.secuencia` de `contracts/ejemplos/sesion-evaluacion-v1.ejemplo.json`. Conviene hacerla apenas existe T006, porque T017 la usa. — Deps: T006 · Ref: FR-040, FR-040a, FR-041, FR-043, research R-12
- [ ] T027 [US1] Implementar en `evaluacion/src/main/java/com/helpi/evaluacion/ui/AltaSesionScreen.kt` el alta de sesión:
  - código de participante con validación "patrón `^P-[0-9]{3,4}$`";
  - formulario de condiciones, **solo enumerados**: `entorno`, `tipoEntorno`, `iluminacion`, `contraluz`, `distancia`, `manoDominante`, `guantes`, `soporteCamara`, `perfilParticipante`;
  - "Empezar" deshabilitado mientras falte un campo;
  - persiste `participante`, `sesion` `CREADA` (semilla nueva, versiones y dispositivo) y `condiciones_prueba` en una transacción.

  Test de UI de US1-2. — Deps: T012, T023 · Ref: FR-008, FR-015, FR-040b, US1-2, research R-12
- [ ] T028 [US1] Implementar `evaluacion/src/main/java/com/helpi/evaluacion/ui/protocolo/ProtocoloViewModel.kt`, **el único que resuelve un intento**:
  - consume los eventos de `RegistroObserver`;
  - ventana de deshacer de 3 s ("Descartar" solo sobre el umbral);
  - tiempo agotado de 15 s sin segmento (`TIEMPO_AGOTADO`);
  - "Repetir intento" solo ante "sin resultado" (el anterior queda `reemplazado`);
  - "Lo hice mal" hasta que empieza el intento siguiente;
  - pausa entre bloques ("Seguir" o "Retomar otro día");
  - entrega cada intento resuelto a `EscritorRegistro`.

  Tests JVM con reloj falso:
  - descartar → `descartado = true` y nada publicado ni vocalizado;
  - la ventana vence → se registra;
  - bajo el umbral → el estado de UI no contiene la glosa predicha;
  - 15 s → `TIEMPO_AGOTADO`;
  - `descartado == false` no altera `correcto` (FR-026).

  — Deps: T013, T014, T015, T026 · Ref: FR-023, FR-024, FR-024a, FR-025, FR-026, FR-042, FR-044, CLAUDE.md §5, research R-03, R-12
- [ ] T029 [P] [US1] Implementar `evaluacion/src/main/java/com/helpi/evaluacion/ui/protocolo/ReferenciaVideoPlayer.kt`: reproduce un clip del paquete de `helpi-ml` (`assets/evaluacion/referencias/`, verificando su manifiesto y hash) **solo a pedido**, nunca automáticamente, con la atribución CC BY-NC-SA 4.0 visible durante la reproducción, y notifica que se vio para marcar `vioVideo`. Test con un clip de prueba en `evaluacion/src/androidTest/`. El paquete real es un asset externo. — Deps: T006 · Ref: FR-042, FR-042a, CLAUDE.md §9, research R-12
- [ ] T030 [US1] Implementar `evaluacion/src/main/java/com/helpi/evaluacion/ui/protocolo/ProtocoloScreen.kt`: dibuja el estado de T028 (glosa grande con "Intento N de 192 · Bloque b de 4", guía de encuadre existente, botones "Descartar", "Repetir intento", "Lo hice mal", "Pausar" y "Terminar sesión") sobre `SessionCoordinator` en modo `OBSERVER_ONLY`, e integra T029 en "Ver seña de referencia". Nunca muestra exactitud acumulada. Test de UI de US1-3. — Deps: T027, T028, T029 · Ref: US1-3, US1-4, US1-5, US4, FR-042
- [ ] T031 [US1] Implementar en `evaluacion/src/main/java/com/helpi/evaluacion/ui/SesionesScreen.kt` la lista de sesiones:
  - estado, avance, "envío pendiente", "envío vencido" y última exportación;
  - "Retomar" para las sesiones `PAUSADA` o `INTERRUMPIDA`;
  - "Exportar archivo": `FileProvider` en `evaluacion/src/main/AndroidManifest.xml` + diálogo de compartir del sistema; actualiza `ultimaExportacionEn` y `revision`;
  - "Enviar al equipo", que llama a `EnvioRepositorio` y se oculta si falta la URL o la clave del receptor.

  Test de UI de US1-6. — Deps: T015, T017, T020, T023 · Ref: FR-009, NFR-004, US1-6, research R-05

**Checkpoint F7**: una sesión completa del protocolo se ejecuta, se interrumpe, se retoma y
se exporta en el dispositivo de referencia. **Condición para pasar**: la exportación valida
contra el esquema.

---

## Phase 8: Análisis de métricas

**Sin tareas en este repositorio.** El plan (research R-10; FR-039) ubica el cálculo de la
exactitud por clase, la matriz de confusión, los pares más confundidos y la distribución de
confianza de aciertos frente a errores, con partición por sujeto, **fuera de la app**, en un
repositorio de análisis que todavía no existe. El dispositivo no calcula ninguna métrica
reportable y el receptor no interpreta el contenido.

Lo que esta iteración le entrega a ese repositorio ya está en `contracts/`: el esquema, el
ejemplo, las reglas semánticas y §Reglas para el análisis (bootstrap por participante,
leave-one-subject-out, no mezclar versiones ni `vioVideo`). FR-033 a FR-038, FR-045, SC-001,
SC-002 y SC-009 quedan **bloqueados por la creación de ese repositorio**, no por tareas de esta
lista.

---

## Phase 9: Validación de punta a punta

- [ ] T032 Prueba de usabilidad con 5 personas sordas señantes, sin ayuda externa: comprensión del aviso (texto + video en LSA) y primera sesión del protocolo. Registrar por participante si comprendió el aviso, si completó el primer bloque y dónde se trabó, en `docs/qa/001-usabilidad-sc006.md`. Criterio: **≥ 4 de 5**. Requiere el video del aviso en LSA y el paquete de videos de referencia. — Deps: T023, T030, T031 · Ref: SC-006, FR-012
- [ ] T033 Ejecutar [quickstart.md](quickstart.md) §0 a §9 en el dispositivo de referencia y registrar el resultado de cada paso (aprobado, fallido o no aplicable, con evidencia) en `docs/qa/001-calidad-reconocimiento-e2e.md`: separación de builds y canario, sobrecosto (T016), consentimiento, alta, los casos a-f del bloque 1, cierre forzado y segundo plano, exportación validada contra el esquema, envío contra un destino de prueba y revocación y borrado. Adjuntar el resultado de T032. — Deps: T002, T005, T016, T021, T022, T024, T025, T030, T031, T032 · Ref: quickstart.md, SC-003, SC-006, SC-007, US1, US2, US4, US7

**Checkpoint F9**: el informe no tiene ningún paso fallido y T032 cumple el criterio.
**Condición para distribuir el build de evaluación**: T016 aprobado en el dispositivo de
referencia, video del aviso en LSA presente y revisión legal hecha antes del primer envío real
(plan.md, Riesgos).

---

## Dependencies & Execution Order

### Fases

```text
F1 Separación ──► F2 Contrato ──► F3 Datos ──► F4 Registro ──► F5 Cola ──► F6 Consentimiento ──► F7 Protocolo ──► F9 E2E
                                                    ▲              ▲
                                   T026 (generador) ┘──────────────┘ (puede adelantarse apenas existe T006)
```

El orden de fases es el pedido. Dentro de él, las dependencias reales son las de la columna
**Deps**. Dos dependencias cruzan fases hacia adelante:

- T017 (F5) usa el generador T026 (F7).
- T020 (F5) usa la regla de consentimiento de T023 (F6).

Conviene hacer T026 y T023 antes de terminar F5.

### Historias

- **US2** (F1) es prerrequisito de todo: nada de evaluación se escribe antes de que exista la
  verificación.
- **US1** (F2 a F5, F7) es el MVP.
- **US4** se cubre dentro del protocolo (T028, T030). Su parte de conversación está diferida
  con producción.
- **US7** (F6) depende de F3 y de la recuperación (T015).
- **US3, US5, US6** (producción y análisis): sin tareas. Diferidas o externas (plan.md).

### Paralelismo

| En paralelo | Condición |
|---|---|
| T002, T003 | Tras T001 (T003 ni siquiera lo necesita) |
| T013, T026, T029 | Tras T006 |
| T018 con F3–F4 | Tras T006 |
| T022 con T017–T021 | Tras T012 y T013 |
| T024, T025 | Tras T023 (archivos distintos) |

```bash
# Ejemplo: después de T006
tarea T013  # máquina de estados y reglas del intento (evaluacion-dominio)
tarea T026  # generador del protocolo (evaluacion-dominio, otro paquete)
tarea T029  # reproductor de referencia (evaluacion/ui/protocolo)
```

---

## Implementation Strategy

1. **F1 completa primero**: la garantía de privacidad (US2) es condición de todo lo demás.
2. **MVP** = F1 a F4, más T017, T023 y T026 a T030, y la exportación a archivo de T031
   **sin envío**. Alcanza para correr sesiones reales con exportación a archivo y obtener los
   primeros datos con verdad de referencia, aun sin receptor.
3. **Incremento 2**: T018 a T021 (envío), cuando `helpi-firebase` pase sus pruebas de contrato.
   El primer envío real, después de la revisión legal.
4. **Incremento 3**: T024, T025, T032 y T033.
5. Una tarea, un diff, un commit (CLAUDE.md §8), con conventional commits y `001` en el
   cuerpo.

## Fuera de esta lista (verificación)

- Ninguna tarea implementa el receptor (funciones, reglas, esquema de base, autenticación):
  T019 a T021 terminan en el contrato y en `MockWebServer`.
- Ninguna tarea implementa el camino de producción (FR-016 a FR-022, US3, US6 para filas).
  Ninguna tarea de esta lista lo necesita, así que ninguna quedó bloqueada por esa decisión.
- Ninguna tarea mide latencia en uso real, FPS, crashes ni trazas (spec 002). T016 mide solo la
  diferencia pareada.
- Ninguna tarea persiste video ni keypoints. T011 lo prohíbe en el esquema de Room, y el
  contrato lo rechaza (`negativos/keypoints-en-intento.json`).
- Tareas marcadas ⚑ REVISAR por falta de referencia: **ninguna**.
