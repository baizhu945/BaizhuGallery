#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
helper_dir="$script_dir/../../app/src/main/kotlin/org/fossifymigration/baizhu/gallery/helpers"
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/gallery-hidden-refresh.XXXXXXXX")
cleanup() {
    if command -v remove-without-permission >/dev/null 2>&1; then
        remove-without-permission -rf -- "$work_dir"
    else
        python3 - "$work_dir" <<'PY'
import shutil
import sys
shutil.rmtree(sys.argv[1])
PY
    fi
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

kotlinc "$helper_dir/HiddenMediaState.kt" "$helper_dir/HiddenMediaFilter.kt" \
    "$script_dir/HiddenRefreshTest.kt" -include-runtime -d "$work_dir/tests.jar"
java -jar "$work_dir/tests.jar"
