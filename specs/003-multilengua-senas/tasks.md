# Tasks: Reconocimiento en varias lenguas de señas

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)

## Fase 1 — Especificación (#225)

- [x] T001 Escribir `spec.md`, `plan.md` y `tasks.md`.
- [x] T002 Actualizar `CLAUDE.md` (estado del modelo y limitaciones por lengua).

## Fase 2 — Carga por lengua (#226)

- [x] T003 [FR-003, FR-004, FR-005] `ModelBundle.load(context, language)` con verificación
  de `language`.
- [x] T004 [FR-010] Leer `license` y `attribution` del manifiesto y exponerlos.
- [x] T005 [FR-003] Copiar el manifiesto de LSA-94 regenerado con `language`.
- [x] T006 [FR-003, FR-004, FR-005] Ampliar `ModelBundleTest`.

## Fase 3 — Elección de lengua (#227)

- [x] T007 [FR-001, FR-008] `SignLanguage` en `domain` con umbral recomendado.
- [x] T008 [FR-002, FR-008] `SignLanguageStore` persistido; LSA por defecto.
- [x] T009 [FR-006] `ClassifierSlot`: un solo intérprete, cierre del anterior.
- [x] T010 [FR-006, FR-007, FR-009, FR-012] `SessionCoordinator.setSignLanguage`.
- [x] T011 [FR-001, FR-010, FR-014] Selector de lengua, atribución y limitaciones en
  ajustes.
- [ ] T012 [SC-002] Medir memoria en dispositivo con una lengua cargada.

## Fase 4 — Paquetes de ASL y LSE (depende de `entrenamiento-modelo`)

- [ ] T013 [FR-003] Copiar `asl/` y `lse/` exportados.
- [ ] T014 [FR-013] Leer `texto_es` del catálogo de ASL.
