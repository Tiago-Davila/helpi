# Quickstart: sesión de validación de punta a punta

**Feature**: `001-calidad-reconocimiento` | **Plan**: [plan.md](plan.md)

Guía para comprobar que el build de evaluación funciona completo: separación de builds,
sobrecosto, protocolo, persistencia ante interrupciones, exportación y envío. No describe la
implementación. Los nombres de tareas Gradle y de tests son los que define el plan.

---

## 0. Requisitos

| Qué | Detalle |
|---|---|
| Dispositivo de referencia | Teléfono físico Android ≥ 10 con almacenamiento cifrado (research R-03), el mismo para todas las mediciones de sobrecosto. Se registra modelo y versión en el reporte |
| Propiedades locales | `helpi.evaluacion.urlReceptor` y `helpi.evaluacion.claveEnvio` en `local.properties` (nunca en el repositorio). Sin ellas el build funciona, pero solo exporta a archivo |
| Assets de evaluación | Video del aviso en LSA (`avisoVersion` vigente) y paquete de videos de referencia de LSA64 de `helpi-ml`, con hashes que coincidan con sus manifiestos |
| Receptor | Un entorno de `helpi-firebase` que pase sus propias pruebas de contrato ([contrato](contracts/envio-sesion-evaluacion.md#sincronización-con-helpi-firebase)) |
| Participante | Código asignado por el equipo (`P-NNN`); persona informada de que el consentimiento es por dispositivo |

---

## 1. Verificar la separación de builds (sin dispositivo)

```bash
./gradlew :app:assembleProduccionRelease :app:verificarProduccionSinEvaluacion
python3 scripts/ci/verify.py apk app/build/outputs/apk/produccion/release/app-produccion-release-unsigned.apk "$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer"
```

Esperado:

- La tarea Gradle termina sin dependencias prohibidas en el classpath.
- `verify.py` informa que el APK no tiene `INTERNET`, ni paquetes `com.helpi.evaluacion`,
  `androidx.room` o `androidx.work`, ni los assets de evaluación.

Canario (debe **fallar**, y ese fallo es el resultado esperado):

```bash
./gradlew :app:assembleProduccionRelease :app:verificarProduccionSinEvaluacion -PcanarioEvaluacion=true
```

Si este comando termina bien, la verificación está rota: **no distribuir ningún build**.

## 2. Medir el sobrecosto del registro en el dispositivo de referencia

```bash
./gradlew :app:connectedEvaluacionDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.helpi.conversation.evaluacion.RegistroSobrecostoTest
```

El reporte (`app/build/reports/qa-stages/sobrecosto/`) informa:

- n pares (≥ 200);
- p95 y p50 de las diferencias dᵢ;
- p95 A/A, que es el piso de ruido;
- p95 de cada rama, solo como contexto.

| Resultado | Condición | Acción |
|---|---|---|
| **Aprobado** | p95(d) ≤ 5 ms y p95 A/A ≤ 5 ms | Seguir |
| **No concluyente** | p95 A/A > 5 ms | Repetir con el teléfono frío, en modo avión y con la batería por encima del 50 %. No se distribuye con este resultado |
| **Rechazado** | p95(d) > 5 ms con A/A ≤ 5 ms | No distribuir; abrir un defecto |

Antes de la medición conviene confirmar que la etapa 1 del modelo pasa (CLAUDE.md §6):
`ModelReferenceTest` en el sabor `evaluacion`.

## 3. Instalar el build de evaluación

```bash
./gradlew :app:installEvaluacionRelease
```

Comprobar:

- **"Helpi Evaluación"** convive con la app de producción, porque usa otro
  `applicationId`.
- El **distintivo de evaluación** se ve en todas las pantallas (FR-005).
- La app de producción instalada no tiene ninguna entrada de "Validación" (US2-3).

## 4. Consentimiento

1. Abrir "Validación". Sin consentimiento vigente aparece la pantalla del aviso, con el texto
   llano y el video en LSA uno al lado del otro.
2. Comprobar que no se puede pasar sin "Acepto que se registre", y que ese botón no exige ver
   el video.
3. Aceptar.

## 5. Crear la sesión

1. Cargar el código del participante (`P-007`).
2. Completar las condiciones: entorno, tipo, iluminación, contraluz, distancia, mano
   dominante, guantes, soporte y perfil. El botón "Empezar" queda inactivo mientras falte
   algún campo (US1-2).

## 6. Recorrer el bloque 1 y forzar cada caso

| Paso | Qué hacer | Qué debe pasar |
|---|---|---|
| a | Hacer la seña pedida | Se ve "Intento 1 de 192 · Bloque 1 de 4"; sobre el umbral, la glosa queda pendiente 3 s y después se registra |
| b | Hacer otra seña y tocar "Descartar" en la ventana | Se registra `descartado = true`; no se vocaliza nada |
| c | Hacer la seña mal a propósito y tocar "Lo hice mal" | `loHiceMal = true` |
| d | Salir de cuadro (hombros fuera) | "Sin resultado" con la causa; aparece "Repetir intento" |
| e | Tocar "Repetir intento", después "Ver seña de referencia", y hacer la seña | El intento anterior queda `reemplazado`; el nuevo, con `vioVideo = true` |
| f | No hacer nada durante 15 s | `SIN_RESULTADO / TIEMPO_AGOTADO` |

En ningún momento se muestra una exactitud acumulada.

## 7. Interrupción y retoma

1. A mitad del bloque, **forzar el cierre**:
   `adb shell am force-stop com.helpi.conversation.evaluacion`.
2. Reabrir la app y entrar en "Validación". La sesión aparece `INTERRUMPIDA` (causa
   `PROCESO_TERMINADO`) con todos los intentos ya resueltos conservados.
3. Retomar. El bloque sigue desde el **primer intento sin respuesta**, con la misma semilla,
   el mismo participante y las mismas condiciones.
4. Repetir con la app **en segundo plano** (botón de inicio). La causa debe ser `SEGUNDO_PLANO`.

## 8. Exportar y enviar

1. En la lista de sesiones, sobre la sesión interrumpida:
   - **"Exportar archivo"**: abre el diálogo de compartir del sistema con un `.json`. Validar
     el archivo contra el [esquema](contracts/envio-sesion-evaluacion.schema.json).
   - **"Enviar al equipo"**: la sesión pasa a "envío pendiente".
2. **Sin red** (modo avión), el envío queda pendiente y la app sigue funcionando completa
   (US6-1).
3. Con red, el envío se confirma. En el receptor aparece el documento con el mismo `sha256`
   que el cliente, y en la app la sesión muestra la fecha de la última exportación.
4. Opcional, con un receptor de prueba que devuelva 503: el envío queda pendiente y se
   reintenta con backoff (ver `adb shell dumpsys jobscheduler`).

## 9. Revocar y borrar

1. Revocar el consentimiento en Ajustes de evaluación:
   - no se puede iniciar ni retomar una sesión;
   - los envíos pendientes desaparecen;
   - las sesiones siguen listadas (US7-1).
2. "Borrar sesiones" → elegir `P-007`. La pantalla muestra cuántas sesiones, intentos y
   envíos se borran, y lista los `sesionId` ya enviados con la indicación de cómo pedir su
   eliminación al equipo (US7-3, US7-4).
3. Confirmar. Las sesiones de otros participantes siguen.

---

## Suite automatizada

| Test | Dónde | Cubre |
|---|---|---|
| Verificación de producción y canario | CI (`quality`) | FR-004, SC-003 |
| Generador del protocolo (192 intentos, 3 por seña, sin repeticiones adyacentes, la semilla de ejemplo reproduce `secuencia`) | `:evaluacion-dominio` (JVM) | FR-040a, FR-041 |
| Reglas de intento e invariantes, máquina de estados de la sesión | `:evaluacion-dominio` (JVM) | FR-007, FR-044, Edge Cases |
| Contrato: serialización = ejemplo; esquema y reglas S-xx; negativos rechazados | `:evaluacion` (JVM, Robolectric) | FR-039, R-11 |
| Cliente HTTP contra `MockWebServer` con la tabla de respuestas | `:evaluacion` (JVM) | FR-030, R-05 |
| Cola: sin conexión, reintento, confirmación, `sha256` distinto, vencimiento a 30 días, revocación | `:evaluacion` (`work-testing` + Room en memoria) | FR-027 a FR-031, R-05 |
| Sesión interrumpida: proceso terminado entre intentos, retoma en el primer intento sin respuesta | `:evaluacion` (instrumentado) | Edge Cases, FR-040a |
| Retención de sesiones sin intentos, retomadas y saltos de reloj | `:evaluacion-dominio` (JVM) | NFR-011, R-13 |
| Sobrecosto pareado | `androidTestEvaluacion` (dispositivo de referencia) | NFR-005, SC-007 |
