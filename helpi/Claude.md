# Claude.md — Traductor LSA (Lengua de Señas Argentina)

Contexto estable del proyecto para agentes de IA (Claude Code / Codex). Notas evolutivas (experimentos, decisiones con fecha, glosario): Obsidian del autor. Especificación formal: specs/ (GitHub Spec Kit — constitution, spec, plan, tasks).

---

## Visión (caso de uso norte)

Una persona sorda viaja en el subte y necesita comunicarse. Abre la app, el asistente graba sus señas y las va traduciendo por texto/voz a su interlocutor, en tiempo real y con confianza suficiente.

La versión actual NO es eso. Es el norte que ordena las decisiones. Herramienta de **asistencia**, no reemplazo de intérpretes humanos.

## Alcance del proyecto (4–6 meses)

1. Reconocimiento de señas aisladas de LSA (validado en fase exploratoria).
2. Traducción a texto con umbral de confianza (mostrar solo cuando está seguro, comunicar incertidumbre cuando no).
3. Robustez de entorno: casa, calle, transporte — distintas cámaras, luz, fondo.

Fuera de alcance: traducción continua de frases (LSA-T, fase futura), señas inventadas fuera de LSA64, voz→seña (dirección inversa).

---

## Fase exploratoria COMPLETADA (jul 2026) — hechos validados

- **Pipeline validado**: video → keypoints (MediaPipe) → LSTM → seña.
- **Exp 3**: 64 señas, split aleatorio → test 0.94.
- **Exp 4 (el número honesto)**: split POR SUJETO (train 1-8, val 9, test 10) → **test 0.85**. El modelo generaliza a personas no vistas.
- **Exp 5**: transferencia laboratorio→webcam CONFIRMADA. Demo en vivo (FastAPI + WS + MediaPipe Tasks JS) reconoce señas reales del autor.
- Overfitting moderado detectado con split por sujeto (train 0.98 vs val 0.80).
- Hiperparámetros estables: lr=3e-4, Adam, early stopping (paciencia 15), FRAMES_FIJOS=40, hidden=128, 2 capas LSTM, dropout 0.3.

## Decisiones duras (restricciones — respetar siempre)

- **El LLM NO reconoce señas.** Solo pule glosa→frase al final del pipeline.
- **Keypoints, no pixels.** El modelo de reconocimiento trabaja sobre coordenadas MediaPipe. La iluminación/color afecta a MediaPipe, nunca al clasificador. No proponer filtros de imagen para "mejorar el modelo".
- Secuencia temporal completa (no keyframes sueltos).
- Vector de entrada: mano_izq(63) + mano_der(63) + pose 0-24(75) = 201 coords, centradas en punto medio de hombros (landmarks 11 y 12), z sin centrar. Este formato es un CONTRATO entre preprocesamiento, entrenamiento y clientes.
- **Métricas reportables: SOLO con split por sujeto.** Split aleatorio se permite únicamente como comparación interna (está inflado ~9 puntos).
- **Privacidad: nunca enviar video crudo al servidor.** Solo keypoints.
- Dataset: LSA64 versión cut. **Licencia NO comercial**, citar sitio/paper, derivados con la misma licencia. Contactar autores (LIDI-UNLP) para otros usos.

## Deuda técnica conocida (a resolver en el proyecto formal)

- MediaPipe Holistic REMOVIDO de Python en versiones nuevas. POC fijada en `mediapipe==0.10.21` (+ numpy<2, opencv<4.10). Migrar a Tasks API (HandLandmarker + PoseLandmarker) — el cliente web YA usa Tasks.
- **Segmentación temporal SIN RESOLVER** (el problema abierto más importante): la ventana deslizante de 40 frames crudos no coincide con el entrenamiento (seña completa muestreada a 40 con linspace). Causa confianza inestable.
- Cross-validation por sujeto pendiente (rotar sujeto de test, promediar).
- Data augmentation pendiente (contra el overfitting del split por sujeto).
- RTX 5050 = Blackwell sm_120 → PyTorch build cu128+ (cu121 no funciona).
- FastAPI + WebSocket requiere `uvicorn[standard]`.
- `detectForVideo` de MediaPipe JS falla con timestamps repetidos (bug ya sufrido: nunca llamarlo dos veces con el mismo ts).

## Stack

- Modelo: Python, PyTorch, MediaPipe, NumPy. Datos intermedios: .npy.
- Servidor: FastAPI + WebSocket (recibe keypoints, devuelve predicciones top-k con confianza).
- Cliente actual: HTML/JS + MediaPipe Tasks (webcam). Cliente futuro: Flutter.
- TTS: flutter_tts (móvil) / Web Speech API (web).
- LLM (fase posterior, opcional): Ollama local 7-8B q4 o API externa.

## Convenciones

- Conventional commits en español (feat, fix, test, chore). Feature branches. Correcciones mínimas, no rewrites.
- Para los commits no agregar coautoria. 
- SDD con GitHub Spec Kit: la especificación manda sobre la implementación.
- No implementar código en fases de especificación/planificación.
- Cada experimento de ML se registra: config completa + seed + resultado.

## Recursos

- LSA64: https://facundoq.github.io/datasets/lsa64/
- Guía datasets señas: http://facundoq.github.io/guides/sign_language_datasets/slr
- LSA-T (continuo, futuro). Grupo LIDI (UNLP): contacto potencial.


<!-- SPEC-KIT:BEGIN - generado por update_agent_context.py, no editar a mano -->

## Estado de la especificacion (generado automaticamente)

Feature activa: `specs/001-lsa-sign-translator`  
Ultima actualizacion: 2026-07-30 01:02 UTC

### Artefactos

| Archivo | Rol | Estado |
|---|---|---|
| `spec.md` | Especificacion | presente (2026-07-29) |
| `plan.md` | Plan tecnico | presente (2026-07-29) |
| `research.md` | Decisiones y trade-offs | presente (2026-07-29) |
| `data-model.md` | Modelo de datos | presente (2026-07-29) |
| `quickstart.md` | Validacion E2E | presente (2026-07-29) |
| `tasks.md` | Plan de ejecucion | presente (2026-07-29) |
| `contracts/` | Contratos | 5 archivo(s) |

**Inventario de identificadores:** 5 DD, 40 FR, 27 NFR, 18 R, 26 SC, 182 T, 8 US

**Progreso de tareas:** 0/183 (0%)

### Decisiones de diseño (DD)

- DD-001 — Quién inicia la grabación (SUPERSEDIDA por DD-002)
- DD-002 — Grabación continua con segmentación automática de señas
- DD-003 — Voz agrupada por pausa y pulido glosa→frase con LLM
- DD-004 — El LLM solo puede agregar palabras funcionales
- DD-005 — La frontera de privacidad se redefine: salen glosas, no keypoints ni video

### Research (R)

- R-001 — Migración de MediaPipe Holistic a Tasks API
- R-002 — Aporte real de la componente z (201 vs 134 coordenadas)
- R-003 — Runtime de inferencia en el dispositivo
- R-004 — Segmentación temporal: detectar inicio y fin de seña
- R-005 — Optional stopping: cuantificar la inflación y compensar el umbral
- R-006 — Tiempo muerto inicial y transiciones ausentes en LSA64 cut
- R-007 — Umbral de confianza: global o calibrado por clase
- R-008 — Presupuesto de latencia y dispositivo de referencia
- R-009 — Criterio exacto de normalización temporal
- R-010 — TTS con Web Speech API: selección de voz, cola y degradación
- R-011 — Servicio de pulido: modelo, despliegue y validación
- R-012 — Valores provisionales: criterio y momento de revisión
- R-013 — Pares de señas confundidos y suficiencia de la configuración de mano
- R-014 — Equivalencia numérica Python ↔ TypeScript
- R-015 — Antecedentes de viabilidad de inferencia local

### Abierto / no resuelto

- El runtime y el modelo del servicio de pulido (R-011). Depende de infraestructura disponible.
- La cota empírica del Nivel 2 del contrato (NFR-014). Se mide en la Fase C, por definición.
- El rango de duración de seña con el que el reconocimiento se mantiene sobre el umbral. Se mide
- Los valores finales de los umbrales de confianza. Salen de NFR-019, no de este documento.

### Reglas de trabajo

- La especificacion manda sobre la implementacion.
- Una tarea, un diff, un commit. No avanzar a la siguiente sin cerrar.
- Fuente de verdad: los archivos en `specs/001-lsa-sign-translator/`, no este resumen.
- Este bloque es generado: editarlo a mano no tiene efecto.

<!-- SPEC-KIT:END -->
