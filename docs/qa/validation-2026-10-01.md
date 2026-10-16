# Validación local — 2026-10-01

Versión comprobada: `feature/tests`, base `1de3e2e` más los cambios de
estabilización incluidos con este registro. Esta evidencia corresponde al árbol
de trabajo del PR #187; no certifica un SHA integrado a develop/main.

Entorno local: OpenJDK 21, Gradle Wrapper 8.9, SDK 35, emuladores x86_64 API 26
y 35. CI mantiene JDK 17. Emulador 37.1.11, build 15917651, backend `swangle`,
Vulkan y animaciones desactivados. El host Compose reproduce la orientación
horizontal, los márgenes y las barras de sistema de producción.

| Control | Resultado |
| --- | --- |
| Tests JVM de app | 44 aprobados, cero omisiones |
| Tests JVM de domain | 50 aprobados, cero omisiones |
| Modelo sin cámara, API 26 | Un test aprobado con los 8 casos reales, tolerancia 1e-3 |
| Modelo sin cámara, API 35 | Un test aprobado con los 8 casos reales, tolerancia 1e-3 |
| Compose, API 26 | 11 aprobados, cero omisiones |
| Compose, API 35 | 11 aprobados, cero omisiones |
| Cobertura de domain | 466/504 líneas, 92,46 %, cumple la base |
| Cobertura de app | 673/3519 líneas, 19,12 %, cumple la base |
| ktlint, detekt, Android Lint, SpotBugs | Aprobados |
| Baselines | Sin crecimiento ni reducción del mínimo de cobertura |
| Verificadores de CI | 6 tests Python y 6 escenarios de ramas aprobados |
| actionlint | Aprobado con la configuración final de emuladores |
| Gitleaks | 52 commits analizados sin secretos; el token sintético fue rechazado |
| Dependency-Check 13 | 185 dependencias, cero vulnerabilidades sin excepción |
| APK debug | Sin INTERNET, backup deshabilitado, modelos sin compresión |

Dependency-Check comprobó la base NVD el 1 de octubre, con última modificación
del 30 de septiembre. Usa las excepciones exactas y con vencimiento versionadas
en `config/quality/dependency-suppressions.xml`. OSS Index no se ejecutó porque
requiere credenciales; el resultado de la tabla corresponde al análisis NVD.

## Pruebas negativas

Los verificadores rechazan reportes ausentes, cero tests, fallos y omisiones.
La nueva prueba de empaquetado elimina por separado modelo, catálogo y fixture,
y comprueba el rechazo de cero casos, tolerancia incorrecta y contrato distinto.
Los bytes sintéticos de esa prueba solo ejercitan el gate de empaquetado; la
inferencia se verifica con el modelo y las secuencias reales en instrumentación.

Los escenarios de política rechazan feature → main y fork:develop → main, y
aceptan develop del mismo repositorio. Un resultado SARIF sintético de severidad
alta bloquea el verificador aunque el análisis haya terminado correctamente.
Estas pruebas locales no sustituyen las pruebas de merge sobre rulesets activos.

## Incidencias corregidas y límites

- El tag de la guía decorativa se conserva dentro de `clearAndSetSemantics`.
  La prueba sigue exigiendo que se muestre; el estado accesible y las tres
  predicciones también se comprueban.
- La prueba de licencia desplaza el contenido antes de exigir visibilidad.
- El host genérico Compose usa la orientación de producción y recibe el foco
  inicial antes de componer los campos. El aviso de incertidumbre se activa
  mediante su acción accesible. Hubo fallos intermitentes de foco y toque durante
  el diagnóstico; las últimas suites completas de ambas APIs pasaron. CI deberá
  confirmar la estabilidad, sin reintentos automáticos ni omisiones.
- SwiftShader indirecto causó crashes nativos del emulador API 35. ANGLE permitió
  completar la suite; CI fija la misma versión y backend.
- El script limpia los reportes anteriores al empezar. Un fallo actual no puede
  conservar una etapa exitosa de una corrida anterior como evidencia vigente.

Etapas 2 y 3 de la cadena de cámara siguen pendientes de clips y referencias
de helpi-ml con licencia y procedencia. Tampoco se realizó QA en dispositivos
físicos. Los tests experimentales LSA-T no se cuentan como validación de Eva.

Actualización de GitHub: Dependency graph fue habilitado y la publicación de
snapshots aprobó. Se activaron rulesets iniciales en develop y main con checks
obligatorios, ambos jobs de CodeQL y sin bypass. El PR #187 pasó a estado
BLOCKED mientras los tests seguían ejecutándose, comprobado mediante la API.
La activación final de branch-policy sigue pendiente de integrar su workflow
confiable en las ramas de destino y aprobar CI en develop/main. Las pruebas
negativas reales de origen de ramas todavía no se realizaron.

Los reportes locales separados por API se guardan en
`app/build/reports/qa-validation-2026-10-01/`; CI conserva los reportes de cada
job durante 14 días. La ejecución reproducible está en [README.md](README.md).
