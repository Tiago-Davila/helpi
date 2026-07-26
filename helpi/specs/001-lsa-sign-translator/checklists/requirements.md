# Specification Quality Checklist: Traductor LSA de señas aisladas (LSA64) con confianza explícita

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-23
**Last validated**: 2026-07-25 (iteración 5, sesión de `/speckit-clarify` sobre no funcionales)
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Constitutional Gates *(see `.specify/memory/constitution.md`)*

- [x] Keypoint data contract covered (Principle IV) — NFR-014
- [x] Per-subject evaluation against 0.85 baseline (Principle V) — NFR-001, NFR-002, SC-001
- [x] Privacy: video y keypoints nunca salen del dispositivo; solo las glosas, hacia el servicio de
      pulido, desactivable (Principle VII) — DD-005, FR-002, FR-041, NFR-006, NFR-007, NFR-017,
      NFR-024, NFR-025, SC-004, SC-013, SC-023
- [x] Explicit confidence and assistance-not-interpreter framing (Principle VIII) — FR-013, FR-017,
      FR-029, SC-005
- [x] Latency < 2 s (Principle IX) — NFR-003, SC-002
- [x] LSA64 non-commercial attribution (Principle X) — FR-030, NFR-012, NFR-013
- [x] Inherited POC debt not worsened (Principle XII) — DD-002 **asume la deuda completa**: la
      segmentación temporal continua pasa de fuera de alcance a trabajo central (FR-008), con
      respaldo manual (FR-037) y puerta de decisión medida (NFR-022).

## Resolved Ambiguities

### Session 1 — `/speckit-specify` (2026-07-23)

| # | Pregunta | Decisión | Impacto |
|---|----------|----------|---------|
| Q1 | Delimitación de la seña | Manual, marcada por la persona señante | Revisada en la sesión 2 |
| Q2 | Conectividad | Reconocimiento en el dispositivo, sin conexión | Parcialmente revertida en la sesión 2 |
| Q3 | Umbral de robustez | ≥ 0.70 en los tres entornos | NFR-005, SC-003 |

### Session 2 — `/speckit-clarify` (2026-07-25)

| # | Pregunta | Decisión | Impacto |
|---|----------|----------|---------|
| Q1 | Disposición física del dispositivo | El interlocutor lo sostiene y apunta la cámara frontal a la persona señante, que se ve a sí misma; luego lo gira para leer, o escucha la voz | Contexto de uso, US1, US2, FR-011, FR-026, NFR-011, NFR-016, SC-007, 3 casos borde |
| Q2 | Quién marca el fin de la seña | Lo determina el sistema: inicio manual del interlocutor, fin automático, máximo 3 intentos de reconocimiento | FR-007–FR-010, FR-018, US1, US3, US4, NFR-003, SC-002, SC-011, SC-012, Key Entities, Out of Scope |
| Q3 | Recolección de datos de campo | Build de evaluación separado, con consentimiento, sin video | NFR-006, NFR-017, NFR-004, SC-004, SC-013 |
| Q4 | Techo de descarga de la primera carga | Sin efecto: el funcionamiento sin conexión sale de alcance | US8 eliminada, FR-031 reescrito, FR-032 y SC-011 eliminados, Out of Scope |
| Q5 | Criterio del subconjunto de evaluación | Muestra aleatoria de 10 señas con semilla registrada, congelada antes de medir | NFR-018, SC-003 |

## Notes

- Terminología de dominio conservada deliberadamente (keypoints, LSA64, split por sujeto, 201
  coordenadas): son restricciones constitucionales heredadas, no decisiones de implementación de
  esta feature.
- **Numeración no secuencial**: NFR-016/017/018 y SC-013 están ubicados en su bloque temático, no al
  final. Es intencional — los identificadores de requisito son referencias estables y renumerarlos
  rompería trazabilidad.
- La spec pasó de 8 a **7 historias de usuario**: US8 (funcionamiento sin conexión) fue eliminada al
  salir de alcance.

### Riesgos abiertos que la fase de plan debe resolver

Documentados como supuestos en la spec, no como ambigüedades — no cambian el alcance, pero sí
condicionan el diseño:

1. **Parada opcional (optional stopping)**: grabar "hasta que el clasificador esté seguro" hace que
   el sistema busque activamente una ventana que produzca confianza alta, lo que infla la confianza
   aparente y aumenta los falsos positivos. Presiona directamente el Principio VIII. El tope de 3
   intentos lo acota pero no lo elimina: el plan debe cuantificar la inflación con 3 evaluaciones y
   compensar el umbral, midiéndolo con split por sujeto.
2. **Tiempo muerto inicial**: entre el inicio de la captura y el comienzo real de la seña hay un
   tramo sin movimiento que no existe en LSA64 (versión cut, señas ya recortadas). Incluirlo sin más
   desalinearía el contrato del Principio IV.
3. **Reconocimiento local vs. baseline**: ejecutar el modelo en el dispositivo puede exigir una
   versión más liviana que la validada. NFR-001 aplica a la configuración que realmente corre en el
   dispositivo; toda caída bajo 0.85 requiere justificación escrita (Principio V).
4. **Presupuesto de latencia con 3 intentos**: los hasta 3 reconocimientos se consumen dentro de los
   2 s de NFR-003. El plan debe verificar que el presupuesto cierra en el dispositivo de referencia.
5. **Números provisionales**: el 0.70 de NFR-005 y el dispositivo de referencia de NFR-003 se fijan
   sin evidencia de campo previa.

### Iteración 4 — segunda sesión de `/speckit-clarify` (2026-07-25)

Cinco decisiones que ampliaron el alcance de forma sustantiva: DD-002 (segmentación continua),
DD-003 (voz agrupada + LLM), DD-004 (LLM restringido a palabras funcionales), DD-005 (frontera de
privacidad redefinida), más el respaldo manual de FR-037.

**Regresión: "Requirements are testable and unambiguous" pasó a NO cumplido.** Dos valores nuevos
quedaron sin cuantificar del todo:

1. **NFR-023 — "condiciones de red móvil típicas"**: no está definido. El presupuesto de 3 s para la
   frase hablada no es medible sin declarar contra qué red se mide (tipo de conexión, latencia base
   asumida).
2. **FR-038 — máximo de señas por bloque**: se exige que exista un tope pero no se fija el número.

El resto de los términos nuevos sí quedó medible: la pausa de 1,5 s (provisional, con criterio de
calibración), y la restricción del LLM verificable por trazado de lemas (FR-040, SC-021).

**Contradicción corregida durante la revalidación**: US2 seguía describiendo la voz disparándose por
seña, lo que contradecía FR-038. Se reescribieron su narrativa y sus cinco escenarios.

Todos los demás ítems siguen pasando. La spec **no** está lista para `/speckit-plan` hasta cerrar
los dos valores de arriba.

### Iteración 5 — `/speckit-clarify` sobre requisitos no funcionales (2026-07-25)

**Regresión cerrada.** "Requirements are testable and unambiguous" vuelve a cumplirse: NFR-023
declara red de referencia 4G con RTT 50–150 ms, y FR-038 fija el máximo en 5 señas por bloque.

Además se cerraron tres huecos que la revisión anterior no había detectado y que el servidor de
DD-003 había dejado abiertos:

1. **NFR-001b no tenía umbral.** El requisito que describe "el número que la persona usuaria
   experimenta" solo obligaba a reportarlo, mientras que el que sí tenía puerta (NFR-001a, 0.85)
   mide un componente que nadie usa directamente. Ahora exige >= 0.70, el mismo número de NFR-005 y
   de la puerta de NFR-022, para que incumplirlo y disparar el repliegue de FR-037 sean el mismo
   evento.
2. **El servicio de pulido no tenía ningún requisito operativo.** Nuevo NFR-026: disponibilidad
   >= 95% solo en ventanas de evaluación (puede ser modesta porque la caída degrada a glosa cruda),
   límite de 30 peticiones por minuto y origen, tamaño máximo de 5 glosas, y validación contra el
   vocabulario cerrado — que vuelve al endpoint inútil como LLM de propósito general sin necesidad
   de cuentas ni claves.
3. **NFR-024 dejaba el servicio operativamente ciego.** Nuevo NFR-027: métricas agregadas sin
   contenido, para poder diagnosticar una caída durante una ronda con participantes sordos sin
   guardar qué dijo nadie.
4. **L1 no tenía umbral** y por lo tanto no podía hacer fallar a CI. Ahora < 1 s, provisional,
   derivado de dejar margen dentro de los 2 s de L2.

Todos los ítems pasan. La spec está lista para `/speckit-plan`.
