#!/usr/bin/env bash
set -euo pipefail
here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd -- "$here/../.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/bulk-operations.XXXXXXXX")
cleanup() {
    python3 - "$work" <<'PY'
import shutil
import sys
shutil.rmtree(sys.argv[1])
PY
}
trap cleanup EXIT
mkdir "$work/cases"
# Compile the real pure production implementation in the same Kotlin module.
timeout 60s kotlinc \
    "$repo/app/src/main/kotlin/org/fossify/gallery/extensions/BulkVisibilityFiles.kt" \
    "$here/BulkVisibilityFilesTest.kt" -include-runtime -d "$work/tests.jar"
timeout --kill-after=1s 5s java -jar "$work/tests.jar" "$work/cases"
