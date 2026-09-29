#!/usr/bin/env bash
set -euo pipefail
version=8.30.1
archive="gitleaks_${version}_linux_x64.tar.gz"
directory="$(mktemp -d)"
trap 'rm -rf "$directory"' EXIT
curl --fail --silent --show-error --location "https://github.com/gitleaks/gitleaks/releases/download/v${version}/${archive}" -o "$directory/$archive"
echo "551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb  $directory/$archive" | sha256sum --check
# The checksum is pinned in source, not downloaded alongside the executable.
tar -xzf "$directory/$archive" -C "$directory" gitleaks
# Exercise the detector with a synthetic, non-functional token outside the repo.
mkdir -p "$directory/probe"
python3 - "$directory/probe/token.txt" <<'CHECK'
import random, string, sys
from pathlib import Path
rng = random.Random(67219)
Path(sys.argv[1]).write_text('token = "' + 'ghp_' + ''.join(rng.choices(string.ascii_letters + string.digits, k=36)) + '"')
CHECK
set +e
"$directory/gitleaks" dir "$directory/probe" --redact --no-banner > "$directory/probe-result.txt" 2>&1
probe_status=$?
set -e
if [[ "$probe_status" != 1 ]]; then
  echo "Secret detector did not reject the synthetic acceptance fixture" >&2
  exit 1
fi
mkdir -p build/reports/security
"$directory/gitleaks" git --redact --log-opts=--all --report-format json --report-path build/reports/security/gitleaks.json .
