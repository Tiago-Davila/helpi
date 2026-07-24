<!--
SYNC IMPACT REPORT
==================
Version change: (plantilla sin ratificar) → 1.0.0
Bump rationale: MAJOR inicial. Primera ratificación: se define el conjunto completo de
principios de gobierno del proyecto a partir de la fase exploratoria (5 experimentos).

Modified principles:
  - [PRINCIPLE_1_NAME] → I. La Especificación Manda
  - [PRINCIPLE_2_NAME] → II. Arquitectura del Pipeline (NO NEGOCIABLE)
  - [PRINCIPLE_3_NAME] → III. Keypoints, Nunca Píxeles
  - [PRINCIPLE_4_NAME] → IV. Contrato de Datos de Keypoints (NO NEGOCIABLE)
  - [PRINCIPLE_5_NAME] → V. Metodología de Evaluación por Sujeto
  (nuevos, no presentes en la plantilla): VI–XIV

Added sections:
  - Core Principles: 14 principios (la plantilla traía 5 slots)
  - Restricciones Adicionales (ex [SECTION_2_NAME]): privacidad, licencia, latencia,
    deuda técnica heredada de la POC
  - Flujo de Trabajo y Puertas de Calidad (ex [SECTION_3_NAME]): fases Spec Kit,
    CI bloqueante, revisión de PR
  - Governance: procedimiento de enmienda, versionado, revisión de cumplimiento

Removed sections: ninguna (todos los placeholders de la plantilla fueron reemplazados)

Templates requiring updates:
  - ✅ .specify/templates/plan-template.md (Constitution Check con puertas concretas)
  - ✅ .specify/templates/spec-template.md (secciones obligatorias: privacidad,
       confianza, latencia, trazabilidad)
  - ✅ .specify/templates/tasks-template.md (tests del contrato de datos NO opcionales;
       categorías de tareas por principio)
  - ✅ .specify/templates/checklist-template.md (nota de cumplimiento constitucional)
  - ⚠ Readme.md (raíz del repo, fuera de helpi/): pendiente de añadir aviso de
       herramienta de ASISTENCIA + atribución LSA64 (Principios VIII y X)

Deferred TODOs: ninguno. RATIFICATION_DATE fijada al día de esta ratificación.
-->

# Helpi Constitution

Helpi es un traductor de Lengua de Señas Argentina (LSA) por video, con fines de
accesibilidad e investigación. Reconoce señas con la cámara y las traduce a texto/voz
con confianza explícita, en condiciones reales de uso (casa, calle, transporte).

Helpi es una herramienta de **ASISTENCIA** a la comunicación. **NO** es un reemplazo de
intérpretes humanos de LSA. Esta distinción MUST aparecer de forma visible en la interfaz
de usuario y en toda la documentación pública del proyecto.

## Core Principles

### I. La Especificación Manda

La especificación gobierna a la implementación. NO se escribe código de funcionalidad que
no trace a una historia de usuario o a un requisito funcional identificado (`FR-###` /
`US#`). Todo PR MUST declarar a qué requisito responde. El código sin trazabilidad se
rechaza en revisión, sin importar su calidad.

**Rationale**: el proyecto tiene alcance de investigación y tiende a expandirse por
curiosidad técnica. La trazabilidad es lo que mantiene el esfuerzo dirigido a las personas
usuarias y no a la exploración indefinida.

### II. Arquitectura del Pipeline (NO NEGOCIABLE)

El pipeline es, en este orden y sin atajos:

```text
video → keypoints (MediaPipe) → modelo de secuencias → glosa + confianza → texto/voz
```

Cada etapa consume únicamente la salida de la anterior. Si se usa un LLM, su ÚNICA función
es convertir glosa en frase natural en español. El LLM NEVER reconoce, clasifica, infiere
ni "ve" señas; no recibe video, imágenes ni keypoints crudos. Cualquier propuesta de saltar,
fusionar o reordenar etapas MUST justificarse en `research.md` y aprobarse como enmienda a
esta constitution.

**Rationale**: esta arquitectura fue validada empíricamente en la fase exploratoria. Delegar
reconocimiento a un LLM produce alucinaciones no medibles y rompe el principio VIII.

### III. Keypoints, Nunca Píxeles

El modelo de reconocimiento opera exclusivamente sobre keypoints. NEVER sobre píxeles.
Los problemas de iluminación, fondo, color de piel, vestimenta o encuadre se resuelven en
la etapa de detección de keypoints (configuración de MediaPipe, guía de encuadre al usuario,
calidad de captura). Está PROHIBIDO introducir filtros de imagen, normalizaciones de color,
recortes o aumentos de contraste en la ruta que alimenta al clasificador.

**Rationale**: los keypoints son la abstracción que da invarianza a condiciones reales y es
lo que hace viable el principio VII (solo viajan coordenadas). Parchear con filtros de imagen
mueve el problema al lugar equivocado y contamina el contrato de datos.

### IV. Contrato de Datos de Keypoints (NO NEGOCIABLE)

El vector de entrada MUST ser exactamente:

- mano izquierda: 63 valores (21 landmarks × 3)
- mano derecha: 63 valores (21 landmarks × 3)
- pose, landmarks 0–24: 75 valores (25 landmarks × 3)
- **Total: 201 coordenadas por frame**

Reglas de normalización, idénticas para todos los productores:

- Centrado en el punto medio de los hombros (pose landmarks 11 y 12).
- La componente `z` NO se centra.
- Secuencias de largo fijo, muestreadas con `linspace` sobre los frames disponibles.

TODO productor de keypoints — preprocesamiento en Python, cliente web, cliente móvil,
cualquier futuro cliente — MUST cumplir este contrato de forma idéntica, bit a bit dentro de
la tolerancia numérica declarada. MUST existir un test automatizado que verifique cada
productor contra secuencias de referencia versionadas. Ese test es BLOQUEANTE de CI
(ver Principio XIII).

**Rationale**: la fase exploratoria demostró que las desalineaciones de este contrato son
silenciosas y catastróficas: el sistema no falla, simplemente predice mal, y el error es
indistinguible de un problema de modelo. El test de contrato es la única defensa.

### V. Metodología de Evaluación por Sujeto

Toda métrica reportable MUST medirse con split POR SUJETO: las personas del conjunto de test
NEVER aparecen en entrenamiento. El split aleatorio queda PROHIBIDO para cualquier número que
se reporte en documentación, papers, README o PRs; se demostró que infla el resultado en
~9 puntos.

- Baseline de referencia vigente: **0.85 de accuracy sobre 64 señas** (LSA64, sujeto 10
  held-out).
- Ninguna regresión por debajo de ese número se mergea sin justificación escrita en el PR,
  con la métrica medida, el motivo y el plan de recuperación.
- Todo cambio que toque modelo, contrato de datos o preprocesamiento MUST reportar esta
  métrica en el PR.

**Rationale**: la métrica que no generaliza a personas nuevas es una métrica falsa para un
producto que se usa con personas nuevas.

### VI. Reproducibilidad de ML

Cada experimento MUST registrar: configuración completa (hiperparámetros, arquitectura,
largo de secuencia), seed, dataset y versión exacta del mismo, y versiones de dependencias.
Los artefactos de cada experimento — modelo entrenado, métricas, matriz de confusión, logs —
se conservan en ubicaciones separadas por experimento y NEVER se sobrescriben. Un experimento
sin configuración registrada no existe: sus números no son citables.

**Rationale**: cinco experimentos exploratorios ya demostraron que los resultados se vuelven
imposibles de interpretar cuando los artefactos se pisan entre corridas.

### VII. Privacidad por Diseño

El video crudo NEVER sale del dispositivo del usuario. Solo viajan al servidor keypoints:
coordenadas numéricas anónimas. No se transmiten, almacenan ni registran frames, imágenes ni
derivados visuales identificables. Esto es un requisito legal-ético, NO una optimización de
ancho de banda, y no puede negociarse por rendimiento.

Cualquier feature que requiera enviar video MUST detenerse y escalarse como enmienda
constitucional, no como decisión de diseño.

**Rationale**: las personas usuarias son una comunidad vulnerable filmándose en su casa y en
la vía pública. La confianza se pierde una sola vez.

### VIII. Confianza Explícita (Preferimos el Silencio al Error)

El sistema NEVER presenta una traducción como certeza cuando no la tiene.

- MUST existir un umbral de confianza configurable.
- Por debajo del umbral, el sistema MUST comunicar que no entendió, en lugar de adivinar.
- La confianza MUST ser visible o audible para la persona usuaria, no solo interna.
- La UI MUST reforzar que Helpi es asistencia, no interpretación profesional.

**Rationale**: una traducción errada presentada con seguridad puede dañar la comunicación de
una persona sorda más que la ausencia de traducción. El costo de los dos tipos de error no es
simétrico y el diseño MUST reflejarlo.

### IX. Tiempo Real como Restricción

La latencia percibida entre la seña y su traducción MUST permitir una conversación.
Objetivo inicial: **< 2 s** extremo a extremo. Toda decisión de arquitectura que comprometa
ese objetivo MUST justificarse en `research.md`, con la medición que la sustenta y la
alternativa descartada.

**Rationale**: un traductor que llega tarde no es un traductor conversacional; es un
subtitulador diferido, que resuelve otro problema.

### X. Licencia y Atribución

El dataset LSA64 es de uso **NO comercial**. Las obras derivadas heredan esa restricción y
MUST citar el paper y el sitio oficial de LSA64. El proyecto completo se mantiene compatible
con esa restricción — licencias, distribución, cualquier eventual monetización — hasta obtener
otra licencia por escrito de sus autores (LIDI, UNLP). La atribución MUST figurar en el README
y en la documentación del modelo.

**Rationale**: el proyecto existe gracias a un dataset cedido bajo condiciones. Respetarlas es
condición de continuidad, además de una obligación legal.

### XI. Separación de Responsabilidades

Captura, extracción de keypoints, clasificación, post-procesamiento (glosa → texto) y
presentación son módulos independientes con contratos explícitos entre sí. Ningún cliente
conoce detalles internos del modelo (arquitectura, pesos, clases indexadas). Ningún modelo ni
servicio de inferencia conoce detalles de la UI. El acoplamiento entre módulos se corrige
antes de mergear, no se documenta como deuda.

**Rationale**: la fase exploratoria mezcló etapas y eso hizo imposible cambiar el modelo sin
tocar el cliente. El contrato del Principio IV solo es verificable si las fronteras existen.

### XII. Deuda Técnica Heredada de la POC

La siguiente deuda es conocida, se asume explícitamente, y MUST trazarse como tareas
priorizadas en el backlog — no puede quedar implícita:

1. **Migración de MediaPipe Holistic → Tasks API** (Holistic fue removido del paquete Python).
2. **Segmentación temporal**: la ventana deslizante actual es provisoria y produce confianza
   inestable; MUST reemplazarse por segmentación explícita de seña.
3. **Cross-validation por sujeto**: hoy se evalúa con un único sujeto held-out (sujeto 10).
4. **Data augmentation** contra el overfitting medido (train 0.98 vs val 0.80).

Ningún PR nuevo puede agravar estos cuatro puntos. Cerrarlos tiene prioridad sobre features
que dependan de ellos.

### XIII. Tests de Reglas de Negocio y de Pipeline

Toda regla de negocio o de pipeline importante MUST tener tests automatizados. En particular:

- Los tests del contrato de datos (Principio IV) son **BLOQUEANTES de CI**: si fallan, el PR
  no se mergea, sin excepción ni override.
- El umbral de confianza y el comportamiento por debajo del umbral (Principio VIII) MUST tener
  tests.
- La ausencia de video en el tráfico saliente (Principio VII) MUST verificarse con un test.

**Rationale**: los principios sin verificación automática se erosionan en la práctica. Los
fallos de este proyecto son silenciosos por naturaleza (ver Principio IV), así que el test es
el único detector.

### XIV. Fases de Documentación Sin Código

Durante las fases de **especificación, aclaración, checklist, planificación y generación de
tareas** NO se implementa código de producción. Esas fases producen o actualizan únicamente
sus documentos correspondientes (`spec.md`, `plan.md`, `research.md`, `data-model.md`,
`contracts/`, `checklists/`, `tasks.md`). La implementación comienza solo en la fase de
implementación.

**Rationale**: escribir código durante la planificación convierte la especificación en una
justificación retroactiva de lo ya construido, e invierte el Principio I.

## Restricciones Adicionales

**Dominio y alcance**: LSA (Lengua de Señas Argentina). Condiciones reales de uso: casa,
calle, transporte — iluminación variable, fondos no controlados, cámara de teléfono en mano.

**Privacidad operativa**: la frontera dispositivo/servidor es la frontera de datos. Todo lo
que cruza esa frontera MUST ser keypoints o metadatos no identificables.

**Latencia**: objetivo < 2 s percibidos; toda excepción documentada en `research.md`.

**Licencia**: NO comercial mientras LSA64 sea el dataset base (Principio X).

**Dataset base actual**: LSA64, 64 señas, split por sujeto con sujeto 10 held-out.

**Deuda técnica activa**: los 4 ítems del Principio XII, trazados como tareas.

## Flujo de Trabajo y Puertas de Calidad

**Fases Spec Kit**: `specify` → `clarify` → `plan` → `tasks` → `checklist` → `implement`.
Las cinco primeras son documentales (Principio XIV).

**Constitution Check**: `plan.md` MUST incluir una verificación explícita contra estos
principios, antes de la fase de investigación y de nuevo tras el diseño. Las violaciones se
registran en Complexity Tracking con la alternativa más simple descartada y por qué.

**Puertas de CI (bloqueantes)**:

1. Tests del contrato de keypoints (Principio IV) para TODOS los productores.
2. Tests de umbral de confianza y comportamiento bajo umbral (Principio VIII).
3. Test de no-exfiltración de video (Principio VII).

**Revisión de PR**: cada PR MUST declarar (a) el requisito o historia que implementa
(Principio I), (b) si toca modelo/datos, la métrica por sujeto medida frente al baseline 0.85
(Principio V), y (c) si introduce una violación constitucional, la justificación escrita.

**Experimentos de ML**: cada corrida registra configuración, seed, dataset y dependencias, y
guarda artefactos en carpeta propia (Principio VI).

## Governance

Esta constitution supersede cualquier otra práctica, convención o preferencia del equipo. Ante
conflicto entre esta constitution y un documento de diseño, código existente o costumbre,
prevalece la constitution.

**Procedimiento de enmienda**:

1. Propuesta escrita: principio afectado, texto nuevo, motivo y evidencia.
2. Para principios marcados NO NEGOCIABLE (II, IV) y para el Principio VII (privacidad), la
   enmienda requiere además evidencia empírica que contradiga la razón original documentada.
3. Aprobación explícita del responsable del proyecto.
4. Plan de migración para el código y los documentos afectados.
5. Actualización de la versión y de los templates dependientes en el mismo cambio.

**Política de versionado** (semver sobre el documento):

- **MAJOR**: eliminación o redefinición incompatible de un principio o del gobierno.
- **MINOR**: nuevo principio o sección, o ampliación material de guía existente.
- **PATCH**: aclaraciones, redacción, correcciones sin cambio semántico.

**Revisión de cumplimiento**: toda revisión de PR verifica cumplimiento constitucional. Las
puertas de CI del apartado anterior son la verificación automática mínima. Las violaciones no
justificadas por escrito bloquean el merge. La complejidad añadida MUST justificarse; ante
duda, gana la opción más simple.

**Version**: 1.0.0 | **Ratified**: 2026-07-23 | **Last Amended**: 2026-07-23
