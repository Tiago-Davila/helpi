# Calidad y entrega Android

El flujo es rama de trabajo → develop (squash) → main (merge commit).
Después de promover, abrir un PR main → develop con merge commit para sincronizar
la historia. develop permite squash y merge: squash es la convención para features;
merge es necesario para incorporar la ascendencia de main y satisfacer el check
de rama actualizada. Exigir squash en esa sincronización impediría conservarla.
No se exige aprobación humana; sí conversaciones resueltas, origen válido y CI.
No hay publicación en tiendas, firma de producción ni Sentry.

## Ejecución local

Requisitos: JDK 17, SDK Android 35, Python 3, Git y acceso a Maven/Google.

```sh
./gradlew ktlintCheck :app:detekt :app:lintDebug :domain:spotbugsMain
./gradlew :domain:jacocoTestReport :app:jacocoDebugReport
python3 scripts/ci/verify.py coverage
python3 scripts/ci/verify.py bundle
python3 -m unittest discover -s scripts/ci -p 'test_*.py'
./gradlew :app:assembleDebug :app:assembleRelease
bash scripts/ci/gitleaks.sh
./gradlew dependencyCheckAggregate
# Con un emulador x86_64 iniciado (API 26 y luego 35):
bash scripts/ci/android-tests.sh
```

La clave NVD_API_KEY es opcional para ejecución local. El análisis usa el
mirror público diario de OWASP para evitar los límites de la API de NVD. Un error
al actualizar la base hace fallar el control; no se trata como ausencia de CVEs.
CI no expone secretos a compilaciones de PR. No ejecutar código de PR mediante
pull_request_target ni workflow_run.

## Checks y artefactos

- `branch-policy`: main solo acepta develop del mismo repositorio; un PR inválido
  puede abrirse, pero no integrarse. Usa únicamente metadatos del evento.
- `quality`: ktlint, detekt, Android Lint, SpotBugs y baseline sin crecimiento.
- `unit-contract`: tests JVM, contrato, paquete Eva y cobertura por módulo.
- `android-tests`: modelo real y UI en API 26/35; falla ante tests omitidos.
- `build`: debug y release, sin Internet, sin backup y modelos sin comprimir.
- `security`: secretos, excepciones y dependencias con CVSS >= 7 bloqueante.
- `codeql`: Java/Kotlin y Actions; los rulesets también exigen resultados del
  análisis, porque un job correcto puede haber encontrado vulnerabilidades.
- `ci-gate`: exige éxito de todos los jobs; errores, cancelaciones y omisiones
  inesperadas no son éxito.

Los reportes se conservan 14 días, incluidos los fallos. Solo después de un push
integrado y CI verde se generan artefactos `qa-<rama>-<SHA>`, con APK debug
instalable, release sin firma de producción, SHA de origen y hashes de archivos.
La entrega recompila el mismo SHA y vuelve a verificar el APK. Un fallo posterior
al merge bloquea la entrega; corregir o revertir mediante PR.

Dependency graph se calcula en un runner sin permisos de escritura. Un job separado publica los snapshots del repositorio; para forks, el workflow
Submit dependency graph procesa únicamente los datos generados y los publica;
no ejecuta ni descarga código del PR. Dependency Review espera los snapshots.
Los workflows de workflow_run deben estar en la rama por defecto para funcionar.

## Baselines

`config/quality/coverage.json` registra las líneas cubiertas/totales de la primera
medición. Se compara como fracción exacta, no como porcentaje redondeado. No se
excluyen clases de aplicación para mejorar artificialmente la cobertura.

Las baselines de ktlint/detekt/lint representan deuda inicial; el ratchet impide
agregar entradas aun cuando se quite otra. Al corregir deuda, reducir la baseline
correspondiente. No regenerarlas en CI. SpotBugs analiza dominio Java.

Las excepciones de dependencias requieren CVE y package URL exactos, notas con
`owner:` y `reason:`, y fecha `until`. No se aceptan regex ni excepciones vencidas.
No versionar secretos ni datos de conversaciones en reportes.

## Estado de validación LSA

Resultados y límites de la validación local: [registro del 2026-10-01](validation-2026-10-01.md).

1. Modelo sin cámara: fixture real, paquete consistente y tolerancia 1e-3.
2. Extracción y distribución: **pendiente de referencias de helpi-ml**.
3. Cámara → extracción → modelo → publicación: **pendiente de etapa 2**.

No confundir los tests Compose con la etapa 3. Para desbloquear 2 se necesitan
clips con procedencia/licencia documentadas y métricas de referencia: proporción
de ceros por bloque, rangos y movimiento, más tolerancias acordadas con el
productor. No generar el dataset ni entrenar en este repositorio.

Los tests experimentales del paquete LSA-T no forman parte del gate de Eva: sus
artefactos todavía pueden faltar. Se conservan separados y no se cuentan como
una validación aprobada del modelo experimental.

La fuente técnica vigente es el contrato v3 con fixtures v3: además del centrado
XY, normaliza a píxeles y por ancho de hombros. Esta integración de CI no cambia
ese contrato, los pesos del modelo ni las referencias compartidas.

## Activación y verificación de GitHub

La protección inicial de develop y main ya está activa: PR obligatorio, sin
bypass, conversaciones resueltas, rama actualizada y checks obligatorios de
calidad, tests, build, seguridad, ci-gate y ambos jobs de CodeQL. Esto bloquea
el primer PR de instalación mientras los controles estén pendientes o fallen.
Todavía no se exige branch-policy: su workflow pull_request_target necesita
estar integrado en las ramas de destino para poder emitir el check.

Para completar la protección, integrar workflows en develop y main y obtener
runs correctos. Solo entonces ejecutar `python3 scripts/ci/configure_rulesets.py --apply`.
El script comprueba la presencia de workflows y el éxito del último CI sobre el
HEAD de ambas ramas. Sin `--apply` solo muestra los payloads previstos.

Verificar en PR de prueba: test roto, omisión de fixture, secret sintético y
alerta controlada bloquean; feature → main y fork:develop → main no se integran;
develop → main y posterior sincronización son válidos. No probar force-push o
borrado sobre ramas reales: comprobar los rulesets y sus reglas efectivas.

Un administrador conserva capacidad de editar rulesets, aunque no tenga bypass.
Como no hay revisión humana obligatoria, mantener permisos de escritura limitados
a colaboradores de confianza; un workflow editable no constituye un límite de
seguridad contra un mantenedor malicioso.
