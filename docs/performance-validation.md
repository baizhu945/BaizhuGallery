# Performance and bulk-operation validation

> Historical evidence: package IDs, hashes, dates and references below identify
> prior releases, not BaizhuGallery 1.15.0. The independent identity does not
> recertify this matrix; see [release/migration context](independent-release.md).

Base: `79acc72c024cf57ca41540950f93588790146e85` (the user-hidden-policy release).

## Build / independent checks

OpenJDK 17, Gradle wrapper 9.5.1, SDK 36 / Build Tools 36.0.0 from traditional Nix
expressions outside the repository. No new dependency, SDK target, manifest
permission, Room schema or lint/detekt baseline change.

```sh
bash tests/hidden-media-filter/run.sh
bash tests/bulk-operations/run.sh
bash tests/performance/run.sh
./gradlew :app:assembleFossDebug :app:testFossDebugUnitTest detekt lint \
  :app:assembleFossRelease
```

The independent source suites pass 64 hidden-policy/helper checks and 25 actual
bulk-filesystem checks, including 1,000 mixed success/failure items. These are
production helper tests on desktop JVM/filesystem, not Android SAF simulations.
The existing Gradle JUnit task is `NO-SOURCE`, not an executed JUnit suite.

Debug/optimized Release builds, lint and detekt passed for the tested candidates.
Initial findings were fixed/refactored without suppressing or baselining them.

## Controlled workload (not universal FPS)

A dedicated API35 x86_64 emulator, 1080×1920, density420, font1, two virtual cores,
2GiB RAM and SwiftShader. The 2,280-image / 32-album dataset (one 1,000-image album)
was checked by complete path/SHA inventories before/after. Benchmark preference and
permission setup was explicitly seeded for repeatability; this is not permission
or hidden-feature behavioral evidence. The same volume/data were retained across
APK installs. OS/Glide/DB caches evolved naturally; no randomized crossover or
CPU-isolated physical-device study was performed.

Three warmed samples per flow, fixed swipe/input schedule, Android modern jank
and per-run histogram percentiles; table cells are medians of runs, not pooled
percentiles. Baseline APK `1987a34f…`; accepted optimized measurement APK `f98630be…`.
A rejected startup-crashing earlier prototype contributed no accepted measurements.

| Flow | Janky frames % before → after | p95 ms before → after |
|---|---:|---:|
| Folder overview | 2.38 → 1.71 | 32 → 30 |
| All photos | 1.09 → 0.82 | 17 → 17 |
| Large album | 1.10 → 0.55 | 17 → 16 |
| Rapid search | 5.31 → 5.14 | 26 → 27 |

Results are mixed, not an all-scenario speedup claim. Search p90 also regressed
19→22ms. Activity display (`am start -W`, NOT media-ready latency) medians regressed
917→951ms process-cold and 35→41ms warm-task. Small differences over three software-
rendered runs are not statistically established causal effects. A second emulator
and occasional parent build work shared the 20-core host; exact contention could
not be isolated. Original invalid large-album navigation samples were rejected and
retained, not silently counted as album results.

The hidden-subset/fallback-stat exercise was not established by this benchmark:
its OwnHidden UI search was empty despite staged files/registry. Normal-flow frame
numbers do not prove hidden-library speedups. Raw navigation validation, manifests,
prefs, timestamps and hashes are in `benchmark/REPORT.md` inside the evidence pack.

## Source-helper microbenchmark

Actual baseline/current source, standalone Kotlin/JVM, 3 warmups and 5 timed
samples; registry construction outside the timed region. At 20,000 media paths /
2,000 claimed roots, median longest-root matcher time changed **441.237→2.220ms**;
combined filter **457.478→4.035ms**. These are isolated algorithms, not an app-wide
speed multiplier. Probe counts are callbacks, not filesystem timing. The media-
only matcher separately avoids probing `.nomedia` beneath a filename. Raw source
hashes, CSV and regression checks are preserved with the runner.

## Functional/device coverage and failure history

Dedicated `5592` synthetic-data device, separate from benchmark `5590`.

- Initial prototype `863c…` crashed before activity context attachment. Lazy
  controller creation at Lifecycle CREATED fixed this; no data mutations occurred
  in the rejected launch attempts.
- Candidate `f986…` completed real 128-item Hide/Unhide with exact path/hash and
  registry round trips, but progress body contrast failed. That failure is retained.
- Candidate `58cc…` passed default readable counts/byte feedback, 128-item visibility,
  8-file copy, different-content move conflicts (Stop/Skip/Keep both), 8-file
  recycle/restore, 3-file permanent deletion, Claim/Favorites, Hide-progress/Home
  waiting-prompt behavior and orientation policy restoration.
- Complete final 58cc inventory was audited: 167 files = initial162 + copies8 -
  permanent deletions3; keep-both moves changed paths, not count. Unrelated bytes
  remained unchanged.
- Explicit Light/White body contrast and White/Black & White filled-bar contrast
  remained unacceptable; a palette matched to the effective dialog surface was
  added. Waiting prompts now suspend the progress window instead of being covered.
- Native-denial/journal device tests were initially blocked by the old broad-access
  adapter gate. R+ deletion now reaches the owned engine/native batch request
  without requiring all-files access first; ordinary read/password/confirmation
  safeguards remain.

### Palette/prompt/native followup (`bbbc60fd…`)

Actual 136-item work at font2 verified readable body, counts, note, button and
advancing filled bars under Light, White, Black & White, Dark and System, plus
Chinese. The previously rejected contrast cases passed. Conflict Skip/Stop was
immediately accessible without manually hiding progress; complete 167-file
inventories stayed unchanged in those decisions.

A genuine Android native deletion request was reached with all-files access off:
Deny preserved all 167 paths/hashes, semantic source rows and visible items. Allow
with recycle produced three durable journal entries before approval, three bin
rows and backups, then cleared the journal after reconciliation. SAF restore
returned all 167 exact paths/hashes, but the old restore chain replaced original
modified/taken times and required a manual retry after the first grant. This is
metadata-fidelity failure, not byte loss. It is retained as a rejected followup
result; stream/provider-close ordering and authorization continuation are being
corrected before final delivery.

Subsequent targeted restore verification established exact filesystem modification
times, corrected derived MediaStore modification seconds, all 167 path/hash pairs,
and (on `2660130a…`) the complete live Room/DateTakens values and successful backup/
bin cleanup. Original synthetic PNG provider DATE_TAKEN was already NULL before
deletion: a positive Gallery fallback is not proof of an original provider capture
date. An over-strict update-count check was removed in favor of actual read-back
postconditions. The repeated-cycle followup also exposed seconds/milliseconds
precision differences in that fallback; its compatibility correction still keeps
actual filesystem time exact and positive provider capture dates strict.

### Final repeated-cycle followup (`e5f2d670…`): PASS

Delivered APK SHA-256:

```text
e5f2d670478d27398e0eb45e1e8b2cbb0603d4657a56473ba9ec8a92a6e20962
```

The installed base APK was independently hashed. Two actual scoped restore cycles
completed: all 167 original paths/SHA-256 values, exact filesystem milliseconds,
provider modification seconds, exact positive Room/DateTakens values, and removal
of all three bin rows/private backups were independently verified. A second normal
recycle/restore cycle did not recur into stale-backup or precision rejection.

These PNGs originally had provider DATE_TAKEN NULL. The Gallery fallback could be
second-rounded while file time retained fractional milliseconds. Read-back checks
respect that field precision; non-null provider capture dates remain strict. This
is not proof of a populated camera-EXIF/provider date matrix.

Parent review included original live-progress full frames for Light, Black & White
and Chinese, plus final per-cycle hash/metadata assertions. Earlier failures remain
in the archive, not rewritten as passes. The first newly granted SAF callback was
not separately exercised in the final run; persisted-grant continuation was. Native
forced-host-recreation replay also remains separately scoped: normal journal
persistence/clearing is not misrepresented as that fault injection.

The full frame matrix was measured on `f98630be…`, before final progress/restore
fixes; it was not falsely reported as rerun on the delivered artifact. Final bulk
UI/restore addenda identify their own exact APKs. See `ui/REPORT-e5f2d670.md` and
`benchmark/REPORT.md` in the evidence archive for raw receipts and boundaries.

## Scope and limits

No exhaustive physical-device/API/storage-provider matrix. Standalone filesystem
checks do not certify SAF behavior. Native request denial/recreation, recovery with
an absent cache row, selection hash-collision UI and every partial-progress terminal
render need separately identified device assertions; missing cases are not promoted
to passes. No claim of exactly one runtime refresh is inferred from a final image.
All operation counts mean settled/processed, including failures, not all-success.

The evidence archive retains rejected candidates' reports, original videos/PNG/XML,
before/after inventories and final addenda. No real user media, signing keystore or
adb private keys are included. This is an unofficial debug build and is not an
encryption/privacy vault or a guaranteed background operation service.
