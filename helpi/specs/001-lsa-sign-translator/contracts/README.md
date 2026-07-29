# Contratos — Traductor LSA (`001-lsa-sign-translator`)

Contratos de interfaz entre módulos y servicios. **Qué son los datos** está en
[data-model.md](../data-model.md); aquí está **cómo cruzan cada frontera**.

| Contrato | Frontera | Estado | Requisitos |
|---|---|---|---|
| [keypoints.md](./keypoints.md) | Todo productor de keypoints (Python de entrenamiento, cliente web) | **NORMATIVO — NO NEGOCIABLE** | Principio IV, NFR-014, SC-017 |
| [pipeline-stages.md](./pipeline-stages.md) | Etapas S1→S2→S3→S4 del pipeline de datos | Normativo | NFR-014, NFR-002 |
| [inference-ws.md](./inference-ws.md) | Puerto de clasificación + WebSocket del arnés dev/eval | Puerto: normativo · WS: **solo dev/eval** | NFR-015, NFR-014 Nivel 2 |
| [polish-service.md](./polish-service.md) | Cliente ↔ servicio de pulido glosa→frase | Normativo | DD-003/004/005, FR-039–FR-041, NFR-023–NFR-027 |

---

## Reglas de versionado

Todo contrato lleva versión semántica propia, declarada en su encabezado.

| Cambio | Bump | Consecuencia |
|---|---|---|
| Cambia la forma del vector, el orden de landmarks, el criterio de normalización o el productor | **MAJOR** | Obliga a regenerar el dataset y re-entrenar. Todo artefacto de modelo con la versión anterior queda inválido |
| Se agrega un campo opcional o un mensaje nuevo compatible hacia atrás | MINOR | Los consumidores viejos siguen funcionando |
| Aclaración de redacción sin cambio semántico | PATCH | — |

**Versión vigente del contrato de keypoints: `2.0.0`.** El salto desde `1.x` (implícito en la POC)
responde a dos cambios MAJOR simultáneos: productor MediaPipe Holistic → Tasks API
([research.md R-001](../research.md)) y criterio de muestreo temporal de punto flotante → aritmética
entera exacta ([research.md R-009](../research.md)).

El vector de **201 coordenadas no cambia** y no puede cambiar sin enmienda al Principio IV, que está
marcado NO NEGOCIABLE y cuyo procedimiento de enmienda exige evidencia empírica que contradiga la
razón original documentada.

## Fuente de verdad

La descripción normativa del contrato de keypoints vive en **archivos declarativos versionados en la
raíz del repositorio**, no en este documento ni en ninguna implementación:

```text
contracts/keypoints/
├── kp-contract.json      # parámetros: SEQ_LEN, dimensiones, tolerancia, orden de bloques
├── landmark-map.json     # índices y orden de landmarks Tasks API → posiciones del vector
└── fixtures/
    ├── raw/              # landmarks crudos de entrada
    ├── expected/         # vectores 201 esperados, congelados
    └── MANIFEST.json     # hashes, versión de contrato, procedencia
```

Los documentos de esta carpeta **explican y justifican** ese contrato; los archivos declarativos
**son** el contrato. Ante discrepancia, gana el archivo declarativo y se corrige el documento.

## Verificación

| Puerta | Qué verifica | Bloqueante |
|---|---|---|
| G1 | Equivalencia Python ↔ TypeScript sobre fixtures congelados, error absoluto <= 1e-6, más la prueba negativa (el runner debe fallar ante una alteración deliberada) | **Sí** |
| G2 | Política de confianza: umbral, bajo umbral, 3 intentos | **Sí** |
| G3 | No exfiltración: cero keypoints y cero frames en el tráfico saliente | **Sí** |
| N2 | Nivel 2 del contrato: mismo video por ambos caminos completos | No (informativo) |
