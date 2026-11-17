#!/usr/bin/env bash
set -euo pipefail

script_dir=$(CDPATH= cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
project_dir=$(CDPATH= cd -- "$script_dir/../.." && pwd)
helper_dir="$project_dir/app/src/main/kotlin/org/fossifymigration/baizhu/gallery/helpers"
helpers=("$helper_dir/HiddenMediaFilter.kt" "$helper_dir/UserHiddenPaths.kt" "$helper_dir/HiddenMediaState.kt")

for helper in "${helpers[@]}"; do
    if [[ ! -f "$helper" ]]; then
        printf 'Pending: production helper does not exist: %s\n' "$helper" >&2
        exit 2
    fi
done

command -v kotlinc >/dev/null
command -v java >/dev/null
# Resolve cleanup before creating temporary output; never rely on plain rm.
if command -v remove-without-permission >/dev/null 2>&1; then
    cleanup_method=wrapper
else
    command -v python3 >/dev/null
    cleanup_method=python
fi
work_dir=$(mktemp -d "${TMPDIR:-/tmp}/gallery-hidden-filter-run.XXXXXXXX")
cleanup() {
    if [[ "$cleanup_method" == wrapper ]]; then
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

kotlinc "${helpers[@]}" "$script_dir/HiddenMediaFilterTest.kt" "$script_dir/UserHiddenPathsTest.kt" \
    "$script_dir/HiddenMediaStateTest.kt" -include-runtime -d "$work_dir/tests.jar"
java -Djava.io.tmpdir="$work_dir" -jar "$work_dir/tests.jar"
