#!/usr/bin/env bash
# CI-only, ephemeral compiler: no system package manager or mutable action installs.
# Checksum is the digest of JetBrains' official v2.3.10 compiler release asset.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.."
version=$(python3 - <<'PY'
import tomllib
from pathlib import Path
print(tomllib.loads(Path('gradle/libs.versions.toml').read_text())['versions']['kotlin'])
PY
)
read -r checksum archive extra < .github/kotlin-compiler.sha256
[[ "$checksum" =~ ^[0-9a-f]{64}$ && "$archive" == "kotlin-compiler-$version.zip" && -z "${extra:-}" ]] || {
  echo 'Kotlin version/checksum mismatch; review the official compiler asset before updating CI.' >&2
  exit 1
}
if [[ ${1:-} == --validate-only ]]; then
  printf 'Pinned compiler metadata valid: Kotlin %s\n' "$version"
  exit 0
fi
: "${RUNNER_TEMP:?This installer is for an ephemeral GitHub runner}"
: "${GITHUB_PATH:?GitHub PATH output is required}"
work=$(mktemp -d "$RUNNER_TEMP/gallery-kotlin.XXXXXXXX")
curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \
  "https://github.com/JetBrains/kotlin/releases/download/v$version/$archive" -o "$work/$archive"
(cd "$work"; printf '%s  %s\n' "$checksum" "$archive" | sha256sum --check --strict)
unzip -q "$work/$archive" -d "$work"
"$work/kotlinc/bin/kotlinc" -version
printf '%s\n' "$work/kotlinc/bin" >> "$GITHUB_PATH"
