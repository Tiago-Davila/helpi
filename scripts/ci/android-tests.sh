#!/usr/bin/env bash
set -euo pipefail
results=app/build/outputs/androidTest-results/connected
reports=app/build/reports/qa-stages
mkdir -p "$reports"
# Never publish a previous successful run as evidence for the current attempt.
rm -rf "$reports/model-produccion" "$reports/model-evaluacion" \
  "$reports/ui-produccion" "$reports/ui-evaluacion" "$reports/capture-status.txt"
rm -rf "$results"
# Corre Gradle y reintenta solo si adb no pudo instalar el APK: en ese caso no se
# ejecutó ningún test. Una prueba que falla nunca se reintenta.
connected() {
  local log attempt
  log="$(mktemp)"
  for attempt in 1 2 3; do
    if ./gradlew "$@" --stacktrace 2>&1 | tee "$log"; then
      return 0
    fi
    if ! grep -q 'Failed to install split APK' "$log" || ! grep -q 'Finished 0 tests' "$log"; then
      return 1
    fi
    echo "adb no pudo instalar el APK (intento $attempt de 3)" >&2
    rm -rf "$results"
    adb kill-server || true
    adb start-server
    adb wait-for-device
  done
  return 1
}
for flavor in Produccion Evaluacion; do
  lower_flavor="${flavor,,}"
  rm -rf "$results"
  connected ":app:connected${flavor}DebugAndroidTest" \
    '-Pandroid.testInstrumentationRunnerArguments.class=com.helpi.conversation.lsa.ModelReferenceTest'
  python3 scripts/ci/verify.py junit "$results" com.helpi.conversation.lsa.ModelReferenceTest
  cp -r "$results" "$reports/model-${lower_flavor}"
done
# MediaPipe must start on the device: a missing runtime class only surfaces
# there, as "No se pudo iniciar la cámara".
for flavor in Produccion Evaluacion; do
  lower_flavor="${flavor,,}"
  rm -rf "$results" "$reports/vision-${lower_flavor}"
  connected ":app:connected${flavor}DebugAndroidTest" \
    '-Pandroid.testInstrumentationRunnerArguments.class=com.helpi.conversation.vision.HolisticExtractorStartupTest' \
    --rerun-tasks
  python3 scripts/ci/verify.py junit "$results" com.helpi.conversation.vision.HolisticExtractorStartupTest
  cp -r "$results" "$reports/vision-${lower_flavor}"
done
for flavor in Produccion Evaluacion; do
  lower_flavor="${flavor,,}"
  rm -rf "$results"
  connected ":app:connected${flavor}DebugAndroidTest" \
    '-Pandroid.testInstrumentationRunnerArguments.package=com.helpi.conversation.ui' \
    --rerun-tasks
  python3 scripts/ci/verify.py junit "$results" com.helpi.conversation.ui.ConversationScreenTest
  cp -r "$results" "$reports/ui-${lower_flavor}"
done
rm -rf "$reports/sobrecosto"
mkdir -p "$reports/sobrecosto"
rm -rf "$results"
connected :app:connectedEvaluacionDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=com.helpi.conversation.evaluacion.RegistroSobrecostoTest' \
  --rerun-tasks
python3 scripts/ci/verify.py junit "$results" \
  com.helpi.conversation.evaluacion.RegistroSobrecostoTest
mapfile -t benchmark_logs < <(
  find "$results" -type f \
    -name 'logcat-com.helpi.conversation.evaluacion.RegistroSobrecostoTest-*.txt'
)
if [[ "${#benchmark_logs[@]}" -eq 0 ]]; then
  echo "No benchmark logcat found; the device report is unavailable." >&2
  exit 1
fi
for log_file in "${benchmark_logs[@]}"; do
  device_name="$(basename "$(dirname "$log_file")")"
  report_json="$(sed -n 's/.*SOBRECOSTO_REPORTE=//p' "$log_file" | tail -n 1)"
  if [[ -z "$report_json" ]]; then
    echo "Benchmark report marker missing from $log_file." >&2
    exit 1
  fi
  printf '%s\n' "$report_json" > "$reports/sobrecosto/${device_name// /_}.json"
done
# There are no provenance-checked capture reference clips in this repository.
# Do not equate UI or model fixture tests with a validated camera pipeline.
printf '%s\n' 'PENDING: extractor distribution and full camera chain require helpi-ml clips and reference metrics.' | tee "$reports/capture-status.txt"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$reports/capture-status.txt" >> "$GITHUB_STEP_SUMMARY"
fi
