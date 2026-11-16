# User-intent hidden policy: validation

This followup is based on commit `7c1f65cc5849baa8ce8c83cb9e381d12f0965aa2`
(the first fork release). It changes all hidden-media browsing to an explicit
user-intent policy. Older physical-hidden-only results are historical, not proof
of this policy.

## Reproducible checks

```sh
bash tests/hidden-media-filter/run.sh
./gradlew --no-daemon --max-workers=4 --continue \
  -Pandroid.builder.sdkDownload=false \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:assembleFossDebug :app:testFossDebugUnitTest detekt lint \
  :app:assembleFossRelease
```

The standalone runner compiles the actual production helpers, not mock copies:
29 physical/origin classifier checks, 11 user file/folder scope checks, and 19
state invalidation checks (including all 16 policy/authorization combinations).
It requires only Kotlin/JVM and no added project dependency. The existing Gradle
unit-test task has no JUnit sources; `NO-SOURCE` is not counted as a test-suite pass.

No dependency, build configuration, target SDK, manifest permission, Room schema,
lint baseline or detekt baseline changes are made. Build tools are supplied by
traditional Nix expressions outside the repository, not installed globally with
another package manager.

## Build and independent checks

Fresh-source results using OpenJDK 17 / wrapper Gradle 9.5.1 / Android SDK 36
and Build Tools 36.0.0:

- FOSS Debug build: **PASS**.
- Optimized/minified FOSS Release build: **PASS**, unsigned build check only.
- lint and detekt: **PASS**, existing baselines unchanged. New style/complexity
  findings were fixed, not suppressed or baselined.
- Standalone production-helper runner: **59/59 PASS** (29 classifier, 11 registry,
  19 state checks).
- Gradle unit-test task: **NO-SOURCE**, not counted as JUnit coverage.
- Pre-runtime independent source review found no confirmed high-priority defect;
  actual device tests subsequently caught the collision defect described below.
- Final collision-fix source was rebuilt: Debug, optimized Release, lint, detekt
  and all 59 helper checks passed again.

Final delivered APK SHA-256:

```text
1987a34f60cd815a19e60f3a6bb596a59a932541d5a033e30c232b5bf7637da3
```

Package remains `org.fossify.gallery.debug`, signed with the same local debug key
for an in-place update of the first fork release. This is not an official Fossify
release or signature.

## Fresh device validation

Dedicated synthetic-only `emulator-5582`, Android 15/API 35, x86_64,
1080 × 1920. The first fork installation and settings were preserved: no app-data
reset, clear or uninstall. Before upgrade, new registry keys were absent; after
installation, the old preferences were semantically unchanged. Installed base APK
bytes were checked against the supplied SHA-256, not just the input filename.

The first new-policy candidate (`9af07d39…`) passed the following runtime flows:

| Area | Observed result |
|---|---|
| Empty legacy registry, normal/temporary/Hidden-only routes | Unknown old hidden items excluded |
| Explicit Other explanation cancellation and enable/disable | Correct checkbox and result transitions |
| File Claim / Forget, including confirmation cancellation | Correct inclusion/removal; file SHA inventories unchanged |
| Tiny (<1 KiB) and large (>100 KiB) actual app Hide / Unhide | Both sizes recorded/displayed/cleared correctly |
| Claimed folder, root marker, direct/deep ordinary descendants | Included by scope |
| Independently hidden dot file and descendant `.nomedia` | Excluded unless separately claimed |
| Explicit child claim followed by forgetting parent | Child retained; inherited-only item excluded |
| Actual new folder Hide / Unhide | Correct marker/view/removal; initial durable-add disk snapshot was not verifiable |
| Actual PIN cancellation/correct PIN and all-files refusal/recovery | No unauthorized enable/claim |
| Persistent Show hidden items with Other off | Unknown hidden content still excluded |
| Cached search and hidden favorite exclusion/recovery | Hidden favorite preserved rather than deleted |
| HOME and force-stop/relaunch | Existing claims and Other-off preference persisted |

These were audited as 62 passing evidence assertions, not 62 independent
end-to-end scenarios. Immediate SharedPreferences `apply()` disk reads sometimes
preceded the flush; later settled captures were used where available, and the
folder durable-add snapshot was explicitly left unverified rather than counted
as a failure or invented pass.

### Failure caught before publication: visibility-name collision

One synthetic test found a high-severity existing rename-path hazard: Hide could
replace an already existing hidden destination without confirmation. Different
before/after SHA-256 inventories proved loss of the synthetic target bytes. That
candidate was **not released**. A destination-exists guard was added for both
Hide and Unhide: reject, return the original path, and do not update claims/DB.
The original failing screenshots/inventories are retained in the evidence bundle.
**Resolved on final APK `1987a34f…`: four focused scenarios, 18 audited assertions,
zero failures.** Installed APK bytes were rechecked; the in-place installation
again preserved all preferences. Fresh distinct-content collision pairs proved:

- Hide collision preserves both original files and complete-root SHA inventories;
  it does not adopt the pre-existing hidden destination or change registry sets.
- Unhide collision preserves both originals and retains the existing hidden claim.
- Noncolliding tiny Hide/Unhide still works, including warm/cold claim persistence,
  correct PIN reauthorization and a complete hash-preserving round trip.
- Folder Hide now has a settled on-disk added record after HOME/relaunch; Unhide
  removes it and its marker. The earlier durable-add snapshot limitation is
  resolved on this final artifact.

Parent review included both final collision screenshots with the safety toast
and machine-readable hash/claim assertions. The full policy/appearance matrix
was not represented as rerun in full after this small safety-only change. Its
broader results refer to the earlier candidate; the final artifact followup is
separately identified. Font scale 1.0, density 420 and English were restored.

The release's `verification.tar.gz` retains `ui/REPORT.md` (including the original
failure), `ui/REPORT-collision-fix.md` (resolution), raw PNG/XML, settled preference
snapshots and before/after hash inventories. Read the final addendum alongside
the initial report; historical failures were not erased.

### Themes, largest font and Chinese

On the new-policy candidate, Light, Dark, Black & White, System default/day and
System default/night were actually selected and saved at font scale 2.0 / density
420. Main/media Other controls, file/folder Claim/Forget CAB labels, and all three
confirmation explanations/buttons were exercised. The four new English labels
and explanations were complete/reachable. Android App language UI was used to
select Simplified Chinese/China; corresponding labels/descriptions/Yes/No were
captured and used, then locale restored to System default/English. Parent review
included original Chinese PNGs and representative appearance sheets.

Existing themed dark-green dialog buttons have low apparent contrast in dark
appearances; no quantitative WCAG or full screen-reader compliance is claimed.
Unrelated long menu labels/filenames can still truncate. These limitations are
not represented as new-label fit passes for the entire application.

### Coverage limits

No exhaustive real-device/API/storage/SAF/OTG, custom-cover picker, positive video,
external caller, copy/move/batch-rename or concurrent external filesystem-change
matrix. A dot-directory fixture existed, but there was no isolated runtime
assertion for that child; the pure scope matcher covers that barrier. Separately
claiming a nested folder is unit-covered, not separately device-proved. Existing
PIN-layout limitations and asynchronous preference disk-write timing remain
explicitly disclosed. The uninstrumented Commons API cannot supply trustworthy
conflict-resolved per-item transfer mappings; no such mappings were guessed.
