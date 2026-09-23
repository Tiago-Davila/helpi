# Plan de implementación: traductor LSA-T junto a Eva

## 1. Objetivo

Incorporar el modelo secuencial LSA-T como un segundo motor de reconocimiento
offline, sin reemplazar ni modificar el comportamiento del clasificador Eva de
64 señas.

La aplicación ofrecerá dos modos separados:

- **Seña individual (Eva):** reconocimiento automático de una seña aislada.
- **Frase experimental (LSA-T):** captura delimitada por el usuario y generación
  de texto mediante encoder y decoder.

El video crudo y los keypoints permanecerán en el dispositivo. Los dos motores
usarán CameraX y MediaPipe HolisticLandmarker, pero tendrán contratos de entrada,
buffers, inferencia y políticas de aceptación independientes.

## 2. Alcance y repositorios

### Repositorio de entrenamiento

Ruta actual: `/home/tiagoashe/entrenamiento-modelo`

Responsabilidades:

- auditoría y limpieza del dataset;
- definición del contrato de keypoints del modelo secuencial;
- entrenamiento y evaluación;
- exportación de los artefactos LiteRT;
- generación del manifiesto y los fixtures de referencia.

### Repositorio Android

Ruta actual: `/home/tiagoashe/helpi/helpi`

Responsabilidades:

- consumo de artefactos ya exportados;
- preprocesamiento equivalente en Kotlin;
- inferencia completamente offline con LiteRT;
- interfaz, estados, confirmación y comunicación de limitaciones;
- pruebas del modelo, del contrato y de la cadena completa.

El dataset, los checkpoints PyTorch, ONNX y scripts de entrenamiento no deben
incorporarse al APK ni versionarse dentro del repositorio Android.

## 3. Decisiones de arquitectura

1. Eva continúa siendo el modo predeterminado y estable.
2. LSA-T se incorpora inicialmente como modo experimental explícito.
3. No se mezclan logits, probabilidades ni textos de ambos modelos.
4. LSA-T no se usa como fallback automático de Eva.
5. La captura de una frase tendrá inicio y fin explícitos. El segmentador de
   señas aisladas no se reutiliza para cortar una frase.
6. El resultado experimental se muestra para confirmación antes de enviarlo o
   pronunciarlo.
7. Ambos modelos deben funcionar sin red.
8. Los artefactos LSA-T se almacenan en un directorio distinto a los de Eva.
9. LiteRT no puede requerir Flex, Select TF Ops ni operaciones personalizadas.
10. Un fallo del modelo experimental no debe deshabilitar a Eva.

## 4. Estado inicial verificado

- Dataset procesado después de la limpieza: 8.184 muestras activas.
  - `train`: 5.242.
  - `val`: 1.305.
  - `test`: 1.637.
- 266 muestras quedaron en cuarentena reproducible en
  `data/quarantine/phase1`; no se eliminaron de forma irreversible.
- La auditoría inicial encontró 9 muestras ausentes respecto de las 8.459
  anotaciones esperadas; el manifiesto de limpieza conserva esa trazabilidad.
- Vocabulario: 5.981 tokens.
- Entrada del modelo secuencial: 75 cuadros por 126 coordenadas de manos.
- Checkpoint: Transformer de aproximadamente 11,88 millones de parámetros.
- Mejor BLEU registrado en validación: aproximadamente 0,99.
- Antes de limpiar, 374 muestras tenían al menos 90 % de coordenadas en cero y
  265 al menos 99 %; las muestras rechazadas se conservaron en cuarentena.
- 28 etiquetas superan la capacidad actual de 62 palabras útiles.
- El encoder TFLite existente no es integrable: usa `float16`, contiene
  `ONNX_LAYERNORMALIZATION` y falla al preparar una operación de cuantización.
- El decoder TFLite existente sí puede inicializarse, pero no debe entregarse
  sin un encoder compatible del mismo paquete.
- Los scripts buscan `lsa/data` y `lsa/checkpoints`, mientras que el dataset y
  el checkpoint más recientes están en la raíz del repositorio de entrenamiento.

Estos puntos son la línea base del proyecto y no criterios de aceptación.

---

## Fase 0: congelar la línea base y definir propiedad

### Tareas

- [x] Registrar los hashes SHA-256 del checkpoint, vocabulario y exportaciones
  existentes.
- [x] Identificar de forma inequívoca qué `phase_b_best.pt` es el checkpoint
  oficial; actualmente hay copias diferentes en la raíz y en `lsa/checkpoints`.
- [x] Elegir un identificador de artefacto, por ejemplo
  `lsa-t-seq2seq-v1-experimental`.
- [x] Crear una rama de trabajo en cada repositorio cuando comience la
  implementación.
- [x] Mantener separados los cambios de entrenamiento y Android.
- [x] Documentar las versiones de Python, PyTorch, ONNX, onnxruntime, onnx2tf,
  TensorFlow/LiteRT y MediaPipe usadas para producir el artefacto.
- [x] Preservar los artefactos actuales como evidencia; no sobrescribirlos
  durante los experimentos.

### Entregables

- Inventario de archivos y hashes.
- Identificador único de la versión experimental.
- Checkpoint oficial seleccionado.

### Criterio de salida

Existe una sola fuente identificada para cada insumo y una exportación nueva no
puede confundirse con los artefactos anteriores.

## Fase 1: alinear rutas y auditar el dataset

### Tareas

- [x] Modificar la configuración del repositorio de entrenamiento para que
  apunte explícitamente a `data`, `checkpoints` y `exports` de la raíz.
- [x] Evitar copiar los 813 MB del dataset para resolver el problema de rutas.
- [x] Hacer que las rutas puedan configurarse sin modificar el código, por
  argumentos o variables específicas del proyecto.
- [ ] Registrar los nueve IDs ausentes y la causa de cada ausencia.
- [x] Validar todas las muestras:
  - forma `[T, 42, 3]`;
  - tipo `float32`;
  - valores finitos;
  - etiqueta no vacía;
  - cantidad de cuadros;
  - proporción de ceros por mano y por cuadro;
  - porcentaje de cuadros con al menos una mano detectada.
- [ ] Generar histogramas por split para longitud, cobertura de manos y longitud
  del texto.
- [x] Definir el criterio de exclusión de muestras sin suficiente información.
  El umbral debe decidirse observando validación, no por conveniencia.
- [x] Revisar manualmente una muestra de cada rango de calidad.
- [ ] Confirmar que no exista filtración entre `train`, `val` y `test` por video,
  fuente o persona, si esos metadatos están disponibles.
- [x] Generar un manifiesto del dataset limpio con conteos, exclusiones y motivo
  de cada exclusión.

### Entregables

- Reporte de calidad del dataset.
- Lista de muestras aceptadas y rechazadas.
- Manifiesto reproducible de los tres splits.

### Criterio de salida

Todas las muestras utilizadas son legibles, tienen trazabilidad y cumplen el
criterio de calidad acordado. Las exclusiones no cambian silenciosamente los
splits.

## Fase 2: fijar el contrato secuencial de keypoints

### Tareas

- [x] Crear una especificación independiente del contrato de Eva.
- [x] Fijar el orden exacto por cuadro:
  - mano izquierda: 21 landmarks por `(x, y, z)`;
  - mano derecha: 21 landmarks por `(x, y, z)`;
  - total: 126 coordenadas.
- [x] Definir que una mano no detectada se representa con 63 ceros.
- [x] Resolver y documentar la normalización:
  - centro `x/y` por cuadro;
  - tratamiento de landmarks en cero;
  - escala global `x/y` de la secuencia;
  - `z` sin escalar o con la transformación que finalmente se entrene.
- [x] Reemplazar `numpy.linspace(..., dtype=int)` por un muestreo entero exacto
  y compartido entre Python y Kotlin.
- [ ] Evaluar si 75 cuadros preservan suficiente información para las longitudes
  reales del dataset; comparar alternativas antes de fijar el contrato.
- [ ] Definir la duración máxima admitida en la aplicación y qué sucede al
  superarla.
- [x] Definir el padding temporal y su máscara.
- [x] Generar valores de referencia para secuencias cortas, exactas y largas.
- [x] Versionar el contrato junto con el modelo.

### Entregables

- Documento del contrato `75×126` o de la forma finalmente elegida.
- Implementación Python de referencia.
- Fixture con entradas crudas, índices temporales y tensor normalizado esperado.

### Criterio de salida

Python y una implementación independiente producen exactamente el mismo tensor
para todos los casos del fixture.

## Fase 3: corregir el entrenamiento y entrenar una nueva versión

### Tareas

- [ ] Entrenar utilizando solamente el dataset aprobado en la Fase 1.
- [ ] Aplicar el contrato temporal y espacial fijado en la Fase 2.
- [ ] Resolver las etiquetas de más de 62 palabras:
  - segmentarlas de manera trazable; o
  - aumentar el límite si el costo móvil lo permite; o
  - excluirlas con justificación.
- [ ] Decidir el destino de la cabeza CTC:
  - agregar una pérdida CTC real y evaluarla; o
  - eliminarla del modelo y de la exportación.
- [ ] No utilizar logits CTC como confianza mientras esa cabeza no esté
  entrenada.
- [ ] Mantener un registro de hiperparámetros y semillas.
- [ ] Guardar `best` y `last` sin confundirlos.
- [ ] Evaluar sobre el conjunto completo de validación durante el desarrollo.
- [ ] Ejecutar una sola evaluación final sobre `test` para la versión candidata.
- [ ] Calcular como mínimo BLEU-4, WER y ROUGE-L.
- [ ] Revisar ejemplos cualitativos, incluyendo muestras con una mano, dos
  manos, oclusiones y frases fuera del vocabulario.
- [ ] Definir antes de la promoción los umbrales mínimos de calidad. No bajar
  esos umbrales después de observar el resultado para aceptar el modelo.
- [ ] Diseñar y calibrar una política de rechazo para evitar que el decoder
  siempre presente una frase aunque no haya entendido.

### Entregables

- Nuevo checkpoint versionado.
- Historial de entrenamiento.
- Reporte de evaluación completo.
- Política de aceptación o rechazo calibrada.

### Criterio de salida

La versión supera los umbrales acordados y los ejemplos revisados no muestran un
patrón de traducciones plausibles pero desconectadas de la seña. Si no los supera,
solo puede continuar como prototipo interno.

## Fase 4: producir un paquete LiteRT válido

### Tareas

- [ ] Corregir la importación faltante de `onnxruntime` usada por el calibrador
  del decoder.
- [ ] Cargar el checkpoint oficial y el vocabulario correspondiente.
- [ ] Exportar encoder y decoder desde el mismo checkpoint.
- [ ] Expresar LayerNorm mediante operaciones elementales compatibles.
- [ ] Eliminar la salida CTC si se decidió no entrenarla.
- [ ] Calibrar el encoder con muestras reales de validación.
- [ ] Calibrar el decoder con embeddings y prefijos reales, no con memoria en
  cero.
- [ ] Seleccionar la variante TFLite por interfaz real y no por orden de nombre.
- [ ] Verificar que encoder y decoder usen tipos compatibles entre sí.
- [ ] Inspeccionar todas las operaciones del FlatBuffer.
- [ ] Rechazar el artefacto si contiene:
  - `CUSTOM`;
  - `Flex*`;
  - `ONNX_LAYERNORMALIZATION`;
  - Select TF Ops.
- [ ] Inicializar e invocar ambos modelos con el intérprete de referencia.
- [ ] Comparar PyTorch, ONNX y TFLite con tolerancias definidas.
- [ ] Generar tres o más fixtures a partir de muestras reales no utilizadas para
  calibración.
- [ ] Generar un manifiesto con hashes, formas, tipos, layouts, vocabulario,
  tokens especiales, normalización y política de decodificación.

### Paquete indivisible esperado

```text
lsa_t/
├── encoder.tflite
├── decoder.tflite
├── vocab.json
├── lsa-t-manifest.json
└── fixture_android.json
```

### Criterio de salida

Los cinco archivos se validan como una unidad, los hashes coinciden y ambos
modelos se ejecutan sin operaciones externas a LiteRT.

## Fase 5: crear la base del segundo motor en Android

### Tareas

- [ ] Copiar solamente el paquete aprobado a `app/src/main/assets/lsa_t/`.
- [ ] Mantener intactos `app/src/main/assets/lsa/` y el `ModelBundle` de Eva.
- [x] Crear `SequenceModelBundle` para:
  - leer el manifiesto;
  - verificar hashes;
  - comprobar formas y tipos;
  - cargar encoder, decoder y vocabulario como una unidad;
  - fallar de forma aislada sin deshabilitar a Eva.
- [x] Crear `SequenceKeypointContract` como único propietario del contrato
  `75×126`.
- [x] Crear `SequenceTranslator` para:
  - ejecutar una vez el encoder;
  - ejecutar el decoder autoregresivamente;
  - comenzar con `<BOS>`;
  - detenerse con `<EOS>` o el máximo permitido;
  - convertir IDs mediante el vocabulario del mismo paquete.
- [x] Crear una política de aceptación independiente de
  `SignAcceptancePolicy`.
- [x] Manejar layouts, transposiciones y buffers sin asumir el orden
  accidental de tensores.
- [x] Ejecutar la inferencia en un executor dedicado y serial.
- [x] Cancelar resultados obsoletos al cambiar de modo, cerrar la sesión o
  desactivar la cámara.
- [x] Limitar la cola a una traducción activa.
- [x] Cerrar los intérpretes de LiteRT durante el ciclo de vida correspondiente.

> Estado de esta preparación: el bundle, el runner, el buffer y la compuerta
> de aceptación tienen tests JVM. La interfaz y la cámara ya conocen el modo
> experimental, pero el selector queda deshabilitado hasta que exista un
> paquete LiteRT LSA-T validado y su fixture instrumentado.

### Entregables

- Cargador validado del paquete LSA-T.
- Contrato Kotlin independiente.
- Runner encoder-decoder sin cámara.

### Criterio de salida

Android puede cargar, validar, ejecutar y cerrar el nuevo motor usando datos de
fixture, mientras Eva continúa pasando sus pruebas existentes.

## Fase 6: pruebas del modelo y del contrato

La prueba instrumentada del contrato ya está preparada en
`SequenceContractFixtureTest`; queda pendiente copiar
`sequence_contract_fixture.json` a `app/src/androidTest/assets/` junto con el
paquete generado en la máquina de entrenamiento.

El orden de esta fase es obligatorio.

### Etapa 1: modelo sin cámara

- [ ] Crear un test instrumentado que cargue el paquete desde assets.
- [ ] Ejecutar las entradas de `fixture_android.json`.
- [ ] Comparar embeddings del encoder dentro de la tolerancia acordada.
- [ ] Comparar los primeros logits del decoder.
- [ ] Comparar la secuencia completa de IDs generados.
- [ ] Probar rechazo por hash incorrecto, vocabulario incorrecto y formas
  incompatibles.

### Etapa 2: keypoints sin modelo

- [ ] Probar mano izquierda, derecha, ambas manos y ausencia de manos.
- [ ] Probar normalización, escala, `z`, padding y muestreo temporal.
- [ ] Comparar bit a bit o con la tolerancia definida contra el fixture Python.
- [ ] Comparar distribuciones de captura real con las del dataset:
  - proporción de ceros;
  - rangos por eje;
  - movimiento entre cuadros;
  - cobertura por mano.

### Etapa 3: cadena completa

- [ ] Capturar una frase delimitada.
- [ ] Construir el tensor.
- [ ] Ejecutar encoder y decoder.
- [ ] Mostrar el texto candidato.
- [ ] Confirmar, rechazar y cancelar el resultado.
- [ ] Verificar que un fallo experimental no detenga Eva.

### Criterio de salida

Las tres etapas pasan en orden. No se acepta una prueba visual de cámara como
sustituto del fixture del modelo.

## Fase 7: compartir la cámara sin mezclar los contratos

### Tareas

- [ ] Reutilizar `FrontCameraSource`, `HolisticExtractor` y `LandmarkFrame`.
- [x] Mantener el overlay de landmarks para ambos modos.
- [x] En modo Eva, usar el contrato v2 `40×168` y conservar su segmentador.
- [x] En modo frase, construir por cuadro el vector de 126 coordenadas desde
  `leftHand` y `rightHand`.
- [x] Mantener un buffer secuencial separado del buffer de Eva.
- [x] Iniciar la captura de frase solamente mediante una acción explícita.
- [x] Finalizarla mediante otra acción explícita o por un límite comunicado.
- [x] Mostrar estado de manos detectadas durante la captura.
- [x] Rechazar localmente una captura con cobertura insuficiente antes de
  ejecutar el modelo.
- [x] No almacenar video crudo ni keypoints después de completar o cancelar la
  traducción.

### Criterio de salida

Cambiar de modo no contamina buffers ni estados. La misma detección de MediaPipe
alimenta el contrato correspondiente sin duplicar la cámara.

## Fase 8: interfaz y experiencia de uso

### Tareas

- [x] Agregar un selector claro entre `Seña individual` y `Frase experimental`.
- [x] Explicar brevemente la diferencia antes del primer uso.
- [x] En modo frase, mostrar:
  - [x] preparado;
  - [x] grabando;
  - [x] manos no visibles;
  - [x] procesando;
  - [x] texto candidato;
  - [x] no entendido;
  - [x] error del modelo.
- [x] Agregar controles accesibles de iniciar, terminar y cancelar.
- [x] Mantener visibles los puntos reconocidos por MediaPipe.
- [x] Permitir editar, confirmar o descartar el texto candidato.
- [x] No enviar a voz un resultado experimental sin confirmación.
- [ ] Evitar mostrar una frase cuando la política indique baja confianza.
- [ ] Comunicar que es una ayuda experimental y no reemplaza a una persona
  intérprete.
- [ ] Comunicar las limitaciones del dataset y del modelo.
- [ ] Mantener la atribución y licencia correspondiente al dataset/modelo.
- [ ] Verificar TalkBack, tamaño de objetivos táctiles, contraste y orientación.

### Criterio de salida

La persona sabe qué motor está usando, cuándo se está capturando y si el
resultado necesita confirmación. Nunca se presenta una conjetura como certeza.

## Fase 9: rendimiento y estabilidad en dispositivo

### Tareas

- [ ] Medir por separado:
  - carga del encoder;
  - inferencia del encoder;
  - cada iteración del decoder;
  - decodificación completa;
  - memoria máxima;
  - aumento del APK.
- [ ] Probar frases cortas y el peor caso de 64 iteraciones.
- [ ] Verificar que la preview y el overlay no se congelen durante inferencia.
- [ ] Medir temperatura y consumo en sesiones repetidas.
- [ ] Probar al menos un dispositivo de gama media además del emulador.
- [ ] Comprobar comportamiento con poca memoria y recreación de Activity.
- [ ] Verificar funcionamiento completo sin conectividad.
- [ ] Definir presupuestos de latencia, memoria y tamaño antes de promover el
  modo experimental.
- [ ] Si no cumple, optimizar el artefacto o reducir la estrategia de decoder;
  no bloquear el hilo de interfaz para ocultar el costo.

### Criterio de salida

El modo frase funciona dentro de los presupuestos acordados y Eva no presenta
regresiones de latencia, memoria o estabilidad.

## Fase 10: despliegue gradual y promoción

### Tareas

- [ ] Proteger inicialmente el modo frase con una bandera experimental local.
- [ ] Mantener Eva como modo predeterminado.
- [ ] Realizar una prueba interna con personas que conozcan LSA.
- [ ] Registrar feedback explícito de `correcto` o `incorrecto` sin guardar video
  ni subir keypoints.
- [ ] Revisar falsos positivos, frases inventadas y fallos sistemáticos.
- [ ] No habilitar fallback automático entre modelos durante esta etapa.
- [ ] Definir criterios de rollback del paquete LSA-T.
- [ ] Permitir deshabilitar el motor experimental sin retirar Eva.
- [ ] Promoverlo fuera del modo experimental solamente si supera calidad,
  accesibilidad, rendimiento y revisión de riesgo.

### Criterio de salida

Existe evidencia de uso controlado, rollback independiente y aprobación de los
criterios definidos. Si la calidad sigue siendo insuficiente, el modo permanece
interno aunque la integración técnica funcione.

## Fase 11: documentación y entrega

### Tareas

- [ ] Documentar cómo reproducir el dataset limpio y el entrenamiento.
- [ ] Documentar cómo producir el paquete LiteRT.
- [ ] Documentar el contrato secuencial para Python y Kotlin.
- [ ] Documentar cómo actualizar el paquete sin tocar Eva.
- [ ] Registrar versiones, hashes, métricas y licencia en las notas de entrega.
- [ ] Actualizar la documentación de arquitectura Android.
- [ ] Agregar una lista de verificación para futuras versiones del modelo.
- [ ] Confirmar que el repositorio Android no contiene dataset, checkpoints ni
  herramientas de entrenamiento.

### Criterio de salida

Otra persona puede reproducir el artefacto, verificarlo, actualizarlo y volver a
la versión anterior sin depender de conocimiento oral.

---

## 5. Dependencias entre fases

```text
Fase 0
  ↓
Fase 1 → Fase 2 → Fase 3 → Fase 4
                                ↓
                              Fase 5 → Fase 6 → Fase 7 → Fase 8
                                                               ↓
                                                             Fase 9
                                                               ↓
                                                            Fase 10
                                                               ↓
                                                            Fase 11
```

La interfaz puede diseñarse antes, pero no debe conectarse a una exportación que
no haya superado la Fase 4. La cámara no debe conectarse al modelo antes de que
pase la primera etapa de la Fase 6.

## 6. Riesgos principales y mitigaciones

| Riesgo | Consecuencia | Mitigación |
|---|---|---|
| Muchas muestras sin manos | El modelo aprende correlaciones falsas | Auditoría, filtrado y reentrenamiento |
| BLEU muy bajo | Frases plausibles pero incorrectas | Umbral de calidad, confirmación y modo experimental |
| Preprocesamiento diferente | Traducciones incorrectas sin excepción | Contrato único y fixture compartido |
| Operaciones TFLite no soportadas | Fallo al cargar en Android | Inspección de ops y prueba instrumentada |
| Encoder y decoder incompatibles | Formas o tipos que no conectan | Manifiesto, hashes y validación como paquete |
| Decoder autoregresivo lento | Interfaz congelada o mala experiencia | Executor dedicado y presupuesto de latencia |
| Confianza no calibrada | Se muestra una invención como traducción | Política de rechazo y confirmación humana |
| Regresión de Eva | Se pierde el modo estable | Assets, clases, buffers y pruebas independientes |
| Aumento grande del APK | Instalación o memoria problemáticas | Medición, cuantización válida y presupuesto |
| Mezcla de versiones | IDs de vocabulario incorrectos | Bundle indivisible con hashes |

## 7. Definición global de terminado

La integración se considera terminada cuando:

- Eva conserva su comportamiento y pasan todas sus pruebas.
- LSA-T usa un paquete LiteRT sin Flex ni operaciones personalizadas.
- Python y Kotlin producen el mismo tensor de entrada.
- El test instrumentado reproduce los resultados del fixture.
- El resultado de baja calidad se rechaza en lugar de presentarse como certeza.
- El usuario elige explícitamente el modo frase y confirma el texto antes de
  usar voz.
- La inferencia funciona completamente offline.
- Los presupuestos de rendimiento se cumplen en un dispositivo real.
- Modelo, vocabulario, manifiesto y fixture están versionados como una unidad.
- Las limitaciones, la atribución y la licencia son visibles y están documentadas.
