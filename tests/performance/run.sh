#!/usr/bin/env bash
set -euo pipefail
here=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo=$(cd "$here/../.." && pwd)
out=${GALLERY_BENCH_OUT:-$HOME/.cache/gallery-performance/benchmark/pure}
mkdir -p "$out"
# All extracted/compiled files are isolated in owned benchmark directory; no app edits.
for version in before current; do
  mkdir -p "$out/$version"
  for helper in UserHiddenPaths HiddenMediaFilter; do
    rel="app/src/main/kotlin/org/fossify/gallery/helpers/$helper.kt"
    if [[ $version == before ]]; then
      git -C "$repo" show "79acc:$rel" > "$out/$version/$helper.kt"
    else
      cp "$repo/$rel" "$out/$version/$helper.kt"
    fi
  done
  python3 - "$here/HelperBenchmark.kt" "$out/$version/HelperBenchmark.kt" "$version" <<'PY'
import pathlib,sys
s=pathlib.Path(sys.argv[1]).read_text()
if sys.argv[3]=='current':
 s=s.replace('/*MEDIA_CALL*/ registry.matches(path, probe)','if (mediaOnly) registry.matchesMedia(path, probe) else registry.matches(path, probe)')
 s=s.replace('/*KINDS*/ listOf("registry-general","filter")','listOf("registry-general","registry-media","filter")')
pathlib.Path(sys.argv[2]).write_text(s)
PY
  sha256sum "$out/$version/"*.kt > "$out/$version/source-sha256.txt"
  kotlinc "$out/$version/"*.kt -include-runtime -d "$out/$version/helpers.jar"
  java -Xms256m -Xmx768m -jar "$out/$version/helpers.jar" "$version" > "$out/$version/results.csv"
done
# Existing suite needs a non-dot temporary ancestor: .cache is physically hidden.
# Its own runner creates/removes the isolated /tmp directory.
TMPDIR=/tmp bash "$repo/tests/hidden-media-filter/run.sh" > "$out/existing-tests.txt"
# Extract only the exact Android-independent class; do NOT stub RecyclerView.
python3 - "$repo/app/src/main/kotlin/org/fossify/gallery/adapters/AdapterRowIndex.kt" "$out/AdapterRowIndex.kt" <<'PY'
import pathlib,sys
s=pathlib.Path(sys.argv[1]).read_text()
start=s.index('internal class AdapterRowIndex {')
end=s.index('/** Linear prefix/suffix reconciliation.')
pathlib.Path(sys.argv[2]).write_text('package org.fossify.gallery.adapters\n\n'+s[start:end])
PY
kotlinc "$out/AdapterRowIndex.kt" "$here/IndexTest.kt" -include-runtime -d "$out/index-tests.jar"
java -jar "$out/index-tests.jar" > "$out/index-tests.txt"
python3 "$here/summarize.py" "$out"
