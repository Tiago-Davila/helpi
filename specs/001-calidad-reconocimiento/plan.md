# Implementation Plan: Medición de calidad del reconocimiento de Eva — iteración de evaluación

**Branch**: `001-calidad-reconocimiento` (hoy se trabaja en `feature/rendimiento-modelo`; ver
Constitution Check, G-11) | **Date**: 2026-10-05 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-calidad-reconocimiento/spec.md`. El pedido
nombraba `specs/001-rendimiento-modelo/spec.md`, que no existe; es la única spec 001 del
repositorio.

## Summary

Instrumentar la app para medir la exactitud real de Eva fuera del laboratorio, con sesiones de
validación con verdad de referencia. **Esta iteración construye solo el build de evaluación.**
Es instrumentación para desarrollo y para sesiones de validación, no una funcionalidad de la
app publicada.

Enfoque técnico:

- **Separación por construcción.** Hay una dimensión de sabor `registro`
  (`evaluacion` / `produccion`). Todo el código de recolección vive en dos módulos que solo
  `evaluacion` compila. Una verificación de tres capas (grafo de dependencias, APK y canario)
  bloquea la CI (research R-01).
- **Puerto neutro.** La cadena de reconocimiento existente emite eventos a un
  `RecognitionObserver`, que en producción es un objeto vacío (R-02).
- **Persistencia anticipada.** Cada intento del protocolo se escribe en Room, en una
  transacción, apenas se resuelve. La escritura ocurre en un hilo dedicado y nunca en el de
  reconocimiento. Una sesión interrumpida conserva todo lo resuelto y puede retomarse (R-03).
- **Envío explícito.** La exportación es un único documento JSON versionado, que sirve de
  archivo y de cuerpo del envío. El envío al receptor de `helpi-firebase` lo pide siempre una
  persona y pasa por una cola outbox de WorkManager. La copia local se borra solo con una
  confirmación verificable por hash (R-04 a R-07, [contrato](contracts/envio-sesion-evaluacion.md)).
- **Sobrecosto medido.** Un A/B pareado e intercalado sobre el mismo dispositivo y las mismas
  secuencias de referencia, con criterio p95(dᵢ) ≤ 5 ms y control A/A (R-08).
- **Métricas fuera del dispositivo.** El dispositivo no calcula ninguna métrica reportable. El
  receptor solo valida y guarda. El análisis, con partición por sujeto, vive en un
  repositorio aparte (R-10).

---

## Alcance de esta iteración

### Incluido

Build de evaluación completo: US1 (sesión de validación), US2 (garantía de que producción no
registra), US4 (descarte, **dentro del protocolo**) y US7 (revocación y borrado), más el envío de
sesiones de evaluación con cola outbox.

### Diferido: camino de producción

La spec define un camino de producción: agregados numéricos sin glosas, opt-out, envío
diferido de filas. **No se implementa en esta iteración**, pero tampoco se elimina: los
requisitos siguen vigentes en spec.md y quedan trazados acá para retomarlos.

| Requisito | Estado | Qué deja preparado esta iteración |
|---|---|---|
| US3, FR-016 a FR-021, FR-017a (fila agregada, contadores, identificador aleatorio, definición de reconocimiento) | **Diferido** | El puerto `RecognitionObserver` emite los mismos eventos que necesitará el acumulador de producción |
| FR-022 (aviso de primer uso y ajuste opt-out) | **Diferido** | — |
| FR-023, FR-024, FR-024a **en la pantalla de conversación** (ventana de deshacer para todas las personas usuarias) | **Diferido** | La ventana de deshacer existe en el protocolo. Llevarla a la conversación cambia la app publicada y va con producción |
| US6, FR-027 a FR-032 para **filas de producción** | **Diferido** | La cola de esta iteración aplica el mismo patrón a sesiones de evaluación. Las diferencias de política (FR-031 descarta la más antigua, NFR-012 fija 30 días) quedan anotadas en research R-05 |
| FR-032a (red solo para filas de producción) | **Requiere enmienda** (E-1) | El APK de producción sigue **sin `INTERNET`** y `verify.py` lo exige |
| NFR-001, NFR-012, NFR-013 (reglas de agregación) | **Diferido** | NFR-001 se cumple trivialmente: producción no persiste ni envía nada |
| SC-004, SC-005, SC-008 | **Diferido** | — |

Por qué se difiere: el camino de producción necesita la revisión legal del opt-out (spec,
Dependencias y riesgos) y un receptor de filas que todavía no existe. Además, llevar la ventana de
deshacer a la conversación es un cambio de la app publicada. Nada de eso hace falta para obtener
la primera cifra de exactitud real, que es el objetivo de P1.

Condiciones para retomarlo:

- revisión legal hecha;
- enmienda E-1 aprobada con la variante de producción incluida;
- contrato de filas de producción definido en `contracts/` con la misma disciplina que el de
  evaluación;
- nuevo `/speckit-plan` sobre esta misma spec.

### Diferido: reconocimientos fuera del protocolo en evaluación

FR-006 pide registrar todo reconocimiento del build de evaluación, también en una conversación
libre. Esta iteración registra **solo intentos del protocolo**. Una conversación libre no
tiene verdad de referencia y su registro equivale a una transcripción, que pide su propio flujo
de consentimiento. El modelo de datos permite agregarlo después como otro tipo de sesión.

### Fuera de alcance (no se planifica)

- Rendimiento de la app (latencia en uso real, fps, consumo, temperatura, crashes, trazas):
  spec 002.
- Implementación del receptor (funciones, reglas de seguridad, esquema de base,
  autenticación, paneles): vive en `helpi-firebase`. Acá solo se define el
  [contrato](contracts/envio-sesion-evaluacion.md).
- Reentrenamiento con los datos recolectados.
- El análisis en sí (FR-033 a FR-038, FR-045): repositorio de análisis, todavía inexistente
  (FR-039).

---

## Enmiendas a la spec

**Estado: E-1, E-2 y E-3 (opción a) aplicadas en spec.md el 2026-10-05**, tras
`/speckit-analyze`. Queda pendiente ratificar la constitución con `/speckit-constitution`.
La tabla conserva lo que cambió y por qué:

| Id | Spec actual | Plan | Propuesta |
|---|---|---|---|
| **E-1** | FR-032a: la red se usa *exclusivamente* para filas de producción. A-05: evaluación exporta sin red. Dependencias: "Cambio de postura de red" | El build de evaluación envía sesiones al receptor del equipo (research R-04) | FR-032a: "En la variante de evaluación, la red MUST usarse exclusivamente para enviar sesiones por acción explícita de la persona (NFR-004). En producción, solo para filas agregadas. La cadena de reconocimiento no usa red en ninguna variante". A-05: se resuelve con el contrato `helpi.evaluacion.sesion` |
| **E-2** | NFR-005, SC-007 y la aclaración del 2026-10-05: "diferencia del percentil 95" (p95(con) − p95(sin)) | p95 de las diferencias pareadas, p95(dᵢ), con control A/A (research R-08) | Reescribir NFR-005 y SC-007 con p95(dᵢ) ≤ 5 ms y el resultado "no concluyente" cuando el A/A supera 5 ms |
| **E-3** | NFR-003 cita "Constitución, Principio VI"; `.specify/memory/constitution.md` es una plantilla sin completar | Las reglas efectivas están en CLAUDE.md | Ratificar la constitución con `/speckit-constitution` o cambiar la cita por CLAUDE.md §1 y §3 |

Las tres eran de redacción y ninguna cambió el diseño de este plan. E-1 cambia la postura de
privacidad del build de evaluación: el primer envío real sigue sujeto a la revisión legal
(Riesgos).

---

## Technical Context

**Language/Version**: Kotlin 2.0.21 (UI, ciclo de vida, Room, WorkManager); Java 17 (módulos
de dominio sin Android, CLAUDE.md §8)

**Primary Dependencies**:

- Existentes y sin cambios: Compose, CameraX 1.4.1, MediaPipe Tasks Vision 0.10.32 y LiteRT
  1.1.2.
- Nuevas, **solo en `:evaluacion`**:
  - Room (`room-runtime`, `room-ktx`, compilador vía KSP);
  - WorkManager (`work-runtime-ktx`);
  - para tests: `work-testing`, `room-testing`, `okhttp3:mockwebserver` y
    `com.networknt:json-schema-validator`.
- Versiones exactas: las que estén vigentes y compatibles con Kotlin 2.0.21 al generar las
  tareas (catálogo `gradle/libs.versions.toml`).
- Sin bibliotecas de red ni de serialización en `main`: el cliente usa `HttpsURLConnection` y
  el JSON se escribe con `android.util.JsonWriter` (R-04, R-07).

**Storage**: Room/SQLite `helpi_evaluacion.db`, solo en evaluación ([data-model.md](data-model.md)).
Producción: sin almacenamiento nuevo.

**Testing**:

- JUnit 4 en la JVM: `:evaluacion-dominio`.
- Robolectric, `work-testing` y `MockWebServer`: `:evaluacion`.
- Instrumentados por sabor: `androidTestEvaluacion`.
- `scripts/ci/test_verify.py`: verificación del APK.

**Target Platform**: Android minSdk 26 / targetSdk 35. Dispositivos de evaluación con Android
≥ 10 y almacenamiento cifrado (quickstart §0).

**Project Type**: app móvil Android con módulos Gradle (`:app`, `:domain`, `:evaluacion`,
`:evaluacion-dominio`).

**Performance Goals**: sobrecosto del registro con p95(dᵢ) ≤ 5 ms en el dispositivo de
referencia (NFR-005, E-2). La latencia absoluta es de la spec 002.

**Constraints**:

- Nada de recolección en el APK de producción (FR-003).
- Nada de E/S en el hilo de reconocimiento (NFR-006).
- Ni video ni keypoints persistidos o enviados (FR-010).
- Envío solo por acción explícita (NFR-004).
- Reconocimiento sin red (FR-032a).

**Scale/Scope**:

- Por sesión: 192 intentos y ~100 KB de JSON.
- Por dispositivo: decenas de sesiones y ≤ 20 envíos pendientes.
- Objetivo de SC-001: ≥ 5 personas en ≥ 2 entornos.
- Pantallas nuevas: consentimiento, lista de sesiones, alta de sesión y condiciones, protocolo,
  pausa entre bloques, ajustes de evaluación y borrado.

Sin `NEEDS CLARIFICATION`: todo lo abierto se resolvió en [research.md](research.md) o quedó
como enmienda (E-1 a E-3).

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

`.specify/memory/constitution.md` **no está ratificada**: es la plantilla sin completar. Hasta
que exista, este plan usa como compuertas las reglas no negociables de `CLAUDE.md`, que la spec
ya trata como constitución. Se recomienda correr `/speckit-constitution` (E-3).

| Id | Compuerta (fuente) | Antes del diseño | Después del diseño |
|---|---|---|---|
| G-01 | Reconocimiento en el dispositivo, sin red (CLAUDE.md §1, §3) | ✅ | ✅ La red existe solo en evaluación y solo en `EnvioWorker` (R-04) |
| G-02 | El video crudo nunca sale del dispositivo; tampoco cuadros ni keypoints (§3; FR-010) | ✅ | ✅ El esquema rechaza estructuralmente keypoints y campos extra |
| G-03 | El contrato de keypoints no cambia (§2) | ✅ | ✅ Solo se registra `contratoKeypoints` |
| G-04 | LiteRT, MediaPipe Holistic y CameraX sin reemplazo (§3) | ✅ | ✅ |
| G-05 | Modelo y catálogo son una unidad (§3) | ✅ | ✅ Cada sesión guarda los dos hashes; el análisis no mezcla versiones |
| G-06 | Confianza explícita: bajo el umbral no se muestra la adivinanza (§5) | ✅ | ✅ El protocolo muestra "No reconocida"; el top-3 solo se registra |
| G-07 | Orden de pruebas: modelo → keypoints → cadena (§6) | ✅ | ✅ El sobrecosto se mide desde el tensor, con el fixture de la etapa 1 |
| G-08 | Java puro para el dominio sin Android (§8) | ✅ | ✅ `:evaluacion-dominio` |
| G-09 | Límites del repo: sin análisis ni entrenamiento acá (§7) | ✅ | ✅ El análisis es externo (FR-039); los videos de referencia los produce `helpi-ml` (R-12) |
| G-10 | App sin permiso `INTERNET` (manifiesto, "R07") | ⚠️ | ⚠️ **Violación justificada** solo en el sabor `evaluacion`. Ver Complexity Tracking. Producción sigue verificada sin `INTERNET` |
| G-11 | Ramas `NNN-nombre` (§8) | ⚠️ | ⚠️ La rama actual es `feature/rendimiento-modelo`. Renombrar a `001-calidad-reconocimiento` antes de implementar. No bloquea el diseño |
| G-12 | Atribución LSA64, CC BY-NC-SA (§9) | ✅ | ✅ Atribución en cada reproducción de video de referencia |
| G-13 | Herramienta de asistencia; limitaciones a la vista (§1, §4) | ✅ | ✅ El aviso y el protocolo no presentan a Eva como certera; las limitaciones van en el reporte (FR-045, externo) |

**Resultado**: pasa con una violación justificada (G-10) y una observación de proceso (G-11).
No hay violaciones sin justificar.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-calidad-reconocimiento/
├── spec.md
├── plan.md                         # este archivo
├── research.md                     # decisiones R-01..R-13
├── data-model.md                   # tablas Room de evaluación
├── quickstart.md                   # sesión de validación de punta a punta
├── contracts/
│   ├── envio-sesion-evaluacion.md          # contrato normativo (semántica, respuestas, obligaciones)
│   ├── envio-sesion-evaluacion.schema.json # estructura (JSON Schema 2020-12)
│   ├── CONTRATO_SHA256                     # hashes compartidos con helpi-firebase
│   └── ejemplos/
│       ├── sesion-evaluacion-v1.ejemplo.json
│       └── negativos/*.json                # 9 casos que deben rechazarse
├── checklists/
└── tasks.md                        # lo genera /speckit-tasks
```

### Source Code (repository root)

```text
settings.gradle.kts                 # + include(":evaluacion", ":evaluacion-dominio")
gradle/libs.versions.toml           # + room, work, ksp, mockwebserver, json-schema-validator

domain/                             # Java, existente
└── src/main/java/com/helpi/conversation/observation/
    ├── RecognitionObserver.java    # puerto neutro (R-02)
    └── NoResultCause.java

evaluacion-dominio/                 # NUEVO · java-library · sin Android
└── src/
    ├── main/java/com/helpi/evaluacion/dominio/
    │   ├── protocolo/              # generador con semilla, bloques, adyacencias (R-12)
    │   ├── sesion/                 # máquina de estados de la sesión e intentos
    │   ├── intento/                # reglas: correcto, invariantes, reemplazo
    │   └── retencion/              # NFR-011, vencimiento de envíos, saltos de reloj (R-13)
    └── test/java/…

evaluacion/                         # NUEVO · com.android.library · solo sabor evaluacion
└── src/
    ├── main/
    │   ├── AndroidManifest.xml     # FileProvider de exportación; sin permisos (los pone :app)
    │   ├── java/com/helpi/evaluacion/
    │   │   ├── datos/              # Room: entidades, DAOs y base (data-model.md)
    │   │   ├── registro/           # observador real y escritor dedicado (R-03)
    │   │   ├── contrato/           # serializador JSON v1 y reglas S-xx (contrato)
    │   │   ├── envio/              # EnvioWorker, cliente HTTPS, DepuracionWorker (R-05, R-06)
    │   │   ├── consentimiento/     # aviso con texto + video LSA, revocación, borrado (R-09)
    │   │   └── ui/                 # pantallas del protocolo, sesiones, condiciones, distintivo
    │   ├── assets/evaluacion/      # video del aviso y paquete de referencias de helpi-ml (con manifiesto)
    │   └── res/
    └── test/                       # Robolectric, MockWebServer, work-testing, contrato
        # resources: srcDir("../specs/001-calidad-reconocimiento/contracts")

app/
├── build.gradle.kts                # flavorDimensions("registro"); evaluacion { applicationIdSuffix ".evaluacion" }
│                                   # evaluacionImplementation(project(":evaluacion"))
│                                   # tarea verificarProduccionSinEvaluacion; propiedad canarioEvaluacion
└── src/
    ├── main/                       # SessionCoordinator: emite a RecognitionObserver; modo OBSERVER_ONLY
    │   └── AndroidManifest.xml     # sin las marcas de permisos (pasan a produccion/)
    ├── produccion/
    │   ├── AndroidManifest.xml     # tools:node="remove" de INTERNET y ACCESS_NETWORK_STATE (igual que hoy)
    │   └── java/…/VariantBindings.kt   # observador vacío; sin menú ni distintivo
    ├── evaluacion/
    │   ├── AndroidManifest.xml     # INTERNET + ACCESS_NETWORK_STATE; networkSecurityConfig
    │   ├── res/xml/network_security_config.xml
    │   └── java/…/VariantBindings.kt   # observador real, entrada "Validación", distintivo
    └── androidTestEvaluacion/java/com/helpi/conversation/evaluacion/
        ├── RegistroSobrecostoTest.kt   # A/B pareado + A/A (R-08)
        └── SesionInterrumpidaTest.kt

scripts/ci/
├── verify.py                       # apk: + prohibidos dex/assets/permisos (R-01 capa 2)
├── test_verify.py                  # + listados sintéticos con inserciones
└── android-tests.sh                # variantes renombradas; + sobrecosto (informativo)
.github/workflows/ci.yml            # tareas por sabor; job canario (espera fallo)
```

**Structure Decision**: módulos Gradle separados para lo que producción no debe contener
(`:evaluacion`, `:evaluacion-dominio`) y source sets por sabor en `:app`, solo para el enlace
(`VariantBindings`) y los manifiestos. `main` conoce únicamente el puerto de `:domain`. Es la
estructura mínima con la que el compilador garantiza FR-003 (research R-01).

---

## Respuesta a los puntos del pedido

| # | Tema | Dónde se resuelve |
|---|---|---|
| 1 | Separación de builds y su verificación automatizable | research R-01 (sabores + módulos; grafo, APK y canario), R-02 (puerto); quickstart §1 |
| 2 | Modelo de datos local; qué se escribe cuándo; sesión interrumpida | [data-model.md](data-model.md) (tablas y "Qué se escribe y cuándo"); research R-03 (escritura anticipada por intento: una sesión interrumpida conserva todo lo resuelto y se retoma) |
| 3 | Contrato de envío | [contracts/envio-sesion-evaluacion.md](contracts/envio-sesion-evaluacion.md) + esquema + ejemplo + negativos; research R-07, R-11 |
| 4 | Cola de envío (outbox) | research R-05; data-model `envio`; contrato §Respuestas y §Confirmación |
| 5 | Sobrecosto del registro | research R-08 (A/B pareado intercalado, p95(dᵢ) ≤ 5 ms, control A/A); enmienda E-2; quickstart §2 |
| 6 | Consentimiento: qué se muestra, revocación, qué se elimina, datos ya enviados | research R-09; contrato §Obligaciones del receptor (supresión) |
| 7 | Dónde se calcula cada métrica; partición por sujeto | research R-10; contrato §Reglas para el análisis |
| 8 | Protocolo guiado sin audio; asociación de condiciones | research R-12; data-model `sesion` + `condiciones_prueba`; quickstart §5 a §7 |

Revocación, en resumen: detiene todo registro, interrumpe la sesión en curso y **borra solo los
envíos pendientes** (copias que todavía no salieron del dispositivo). Sesiones e intentos se
conservan hasta su retención (FR-013). Lo ya enviado no se toca desde la app: se pide al equipo,
y el receptor está obligado a poder suprimirlo por sesión o por participante.

---

## Estrategia de pruebas

| Prueba | Tipo | Bloquea CI | Requisito |
|---|---|---|---|
| Artefacto de producción sin recolección: grafo, APK y canario que debe fallar | Gradle + `verify.py` + job canario | **Sí** | FR-004, SC-003, NFR-013 |
| `test_verify.py` con inserciones sintéticas (paquete, permiso, asset) | Python | **Sí** | SC-003 |
| Sobrecosto pareado con control A/A | Instrumentado (`androidTestEvaluacion`) | No en CI (emulador, informativo). **Sí** antes de distribuir, en el dispositivo de referencia | NFR-005, SC-007 |
| Cola: sin conexión → pendiente; transitorio → `retry`; confirmación → borrado; `sha256` distinto → no borra; 4xx → `RECHAZADO`; 401 → `BLOQUEADO`; 30 días → vencido; revocación → cancelado | JVM (`work-testing`, Room en memoria, `MockWebServer`) | **Sí** | FR-027 a FR-030, R-05 |
| Sesión interrumpida: proceso terminado entre intentos; reapertura → `INTERRUMPIDA`; retoma en el primer intento sin respuesta, misma semilla | Instrumentado + JVM (máquina de estados) | **Sí** (la parte JVM) | Edge Cases, FR-040a |
| Contrato: serialización = ejemplo de referencia (semántico); esquema; reglas S-01 a S-08; 9 negativos rechazados; hashes de `CONTRATO_SHA256` | JVM | **Sí** | FR-039, R-11 |
| Generador del protocolo: 192, 3 por seña, sin adyacentes, la semilla `20261005` reproduce la `secuencia` del ejemplo | JVM | **Sí** | FR-040a, FR-041 |
| Etapa 1 del modelo (`ModelReferenceTest`) en ambos sabores | Instrumentado | **Sí** (ya existe) | CLAUDE.md §6 |
| Usabilidad del aviso y del protocolo con personas sordas | Manual, 5 participantes | — | SC-006 |

---

## Riesgos

| Riesgo | Mitigación |
|---|---|
| El video en LSA del aviso no está grabado: sin él no se puede hacer una sesión con participantes sordos (SC-006) | Dependencia explícita (R-09). El consentimiento exige el asset con su hash; se planifica antes de la primera sesión |
| El receptor de `helpi-firebase` todavía no existe | La exportación a archivo usa el mismo formato y no depende del receptor. El envío se prueba con `MockWebServer` hasta que exista |
| Transferencia de datos sensibles (glosas) a infraestructura en la nube, posiblemente fuera de Argentina (Ley N.º 25.326) | Fuera del alcance técnico. Se suma a la revisión legal que ya pide la spec, **antes del primer envío real**. Hasta entonces, se usa solo la exportación a archivo |
| Si se activa R8 en release, la capa 2 de la verificación deja de leer nombres reales | La capa 1 (grafo) sigue siendo la garantía; la capa 2 pasaría a usar `mapping.txt` (R-01) |
| Ruido del emulador en el test de sobrecosto | Control A/A y criterio bloqueante solo en el dispositivo de referencia (R-08) |
| Renombrar las variantes rompe los pasos actuales de CI (`lintDebug`, `connectedDebugAndroidTest`, …) | Primera tarea de implementación: migrar la CI a las variantes por sabor sin cambiar el comportamiento, en un commit aparte |
| Tamaño del APK de evaluación (~20 MB de videos) | Solo afecta a evaluación; no se distribuye por tiendas (A-08) |

---

## Complexity Tracking

| Violación / complejidad | Por qué hace falta | Alternativa más simple descartada porque |
|---|---|---|
| `INTERNET` en el sabor `evaluacion` (G-10) | Enviar sesiones al receptor con confirmación de recepción (R-04, R-05) | Exportar solo a archivo (A-05) pierde sesiones en campañas con varios dispositivos y no deja confirmación. Se conserva como segundo canal. Producción sigue sin `INTERNET`, verificado en CI |
| Dos módulos Gradle nuevos | El compilador impide que `main` referencie la recolección (FR-003), y el grafo de dependencias se vuelve verificable | Con source sets por sabor dentro de `:app`, Room y WorkManager quedan como dependencias de `:app` y la verificación depende solo de escanear el APK |
| Room, WorkManager y KSP (dependencias nuevas, solo en evaluación) | Persistencia transaccional por intento y envío con restricciones y backoff que sobrevive a reinicios | SQLite crudo y `JobScheduler` reimplementan lo mismo con más superficie de error |
| Modo `OBSERVER_ONLY` en `SessionCoordinator` (código en `main`) | El protocolo reutiliza la misma cadena de cámara y clasificador que mide | Una cadena paralela en `:evaluacion` divergiría y mediría otra cosa (R-02). El modo no activa recolección: sin `:evaluacion` no tiene consumidor |
