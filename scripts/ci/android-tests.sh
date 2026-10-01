#!/usr/bin/env bash
set -euo pipefail
results=app/build/outputs/androidTest-results/connected
reports=app/build/reports/qa-stages
mkdir -p "$reports"
# Never publish a previous successful run as evidence for the current attempt.
rm -rf "$reports/model" "$reports/ui" "$reports/capture-status.txt"
rm -rf "$results"
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.helpi.conversation.lsa.ModelReferenceTest' --stacktrace
python3 scripts/ci/verify.py junit "$results" com.helpi.conversation.lsa.ModelReferenceTest
cp -r "$results" "$reports/model"
rm -rf "$results"
./gradlew :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.package=com.helpi.conversation.ui' --rerun-tasks --stacktrace
python3 scripts/ci/verify.py junit "$results" com.helpi.conversation.ui.ConversationScreenTest
cp -r "$results" "$reports/ui"
# There are no provenance-checked capture reference clips in this repository.
# Do not equate UI or model fixture tests with a validated camera pipeline.
printf '%s\n' 'PENDING: extractor distribution and full camera chain require helpi-ml clips and reference metrics.' | tee "$reports/capture-status.txt"
if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
  cat "$reports/capture-status.txt" >> "$GITHUB_STEP_SUMMARY"
fi
