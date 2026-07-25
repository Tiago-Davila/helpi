# Specification Quality Checklist: Traductor LSA de señas aisladas (LSA64) con confianza explícita

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-07-23
**Last validated**: 2026-07-25 (iteración 3, tras la sesión de `/speckit-clarify`)
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
- [x] Privacy: nothing leaves the device in the production build (Principle VII) — FR-002, NFR-006,
      NFR-007, NFR-017, SC-004, SC-013
- [x] Explicit confidence and assistance-not-interpreter framing (Principle VIII) — FR-013, FR-017,
      FR-029, SC-005
- [x] Latency < 2 s (Principle IX) — NFR-003, SC-002
- [x] LSA64 non-commercial attribution (Principle X) — FR-030, NFR-012, NFR-013
- [x] Inherited POC debt not worsened (Principle XII) — la feature resuelve *parcialmente* la deuda
      de segmentación temporal: detecta el fin de la seña con inicio conocido y tope de 3 intentos
      (FR-008). La segmentación continua sin delimitación humana sigue fuera de alcance.

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

Todos los ítems pasan. La spec está lista para `/speckit-plan`.
