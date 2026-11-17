# Hidden-only filter validation (historical first release)

> Historical evidence: package IDs, hashes, dates and references below identify
> prior releases, not BaizhuGallery 1.15.0. The independent identity does not
> recertify this matrix; see [release/migration context](independent-release.md).

This report describes `hidden-only-v1.14.2-1`, before explicit user-intent
tracking. For the new default source policy, see `user-hidden-items.md` and
`user-hidden-validation.md`. Historical results below are not reused as proof
of the new policy.

## Build and source checks

Base: upstream `0359a4598ec35ad7b50683f4365641eb379491bb` (v1.14.2).
Validation used OpenJDK 17, Gradle wrapper 9.5.1, Android SDK 36 / Build Tools
36.0.0, provisioned with a temporary traditional Nix expression outside the
repository. No dependencies, build configuration, manifest permissions, target
SDK or quality baselines were changed.

Fresh final-source command:

```sh
./gradlew --no-daemon --max-workers=4 --continue \
  -Pandroid.builder.sdkDownload=false \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:assembleFossDebug :app:testFossDebugUnitTest detekt lint \
  :app:assembleFossRelease
bash tests/hidden-media-filter/run.sh
```

Results:

- Debug APK build: **PASS**.
- Optimized/minified FOSS Release build: **PASS** (unsigned, build validation only).
- Android lint for configured variants: **PASS**, existing baseline unchanged.
- detekt: **PASS**, existing baseline unchanged. Initial new complexity/style
  findings were fixed in the implementation, not suppressed or baselined.
- Upstream unit-test task: **NO-SOURCE**, not a JUnit execution.
- Standalone runner: **28/28 PASS**, including 27 actual production-classifier
  checks and one explicitly harness-only type-intersection composition check.
- `git diff --check`: **PASS**.
- Independent read-only implementation review: no confirmed high-priority defect.

Final debug APK SHA-256:

```text
d3fc6ccbc6ac87578e71e076474b38ad519531a0bd4e2e43b9fd0cdcc480efb1
```

Package: `org.fossify.gallery.debug`; this is an unofficial, debug-signed build
that installs alongside the upstream release. It is not signed with Fossify's
release key. Source and license accompany the APK at the fork release tag.

## Device validation

Fresh dedicated synthetic-data emulator: Android 15 / API 35, x86_64,
1080 × 1920, serial `emulator-5582`. No real device, user media or previous
experiment's test results were used. Sixteen newly generated media fixtures
cover dot files, dot ancestors, exact `.nomedia` ancestors, near misses,
ordinary/hidden mixed folders, PNG, GIF and SVG.

Behavioral regression results (APK `06038e97…`, before the resource-only shorter
menu-label correction):

| Check | Result |
|---|---|
| Exact hidden set, including ancestor and near-miss cases | PASS: exactly 9/16 |
| Image/GIF/SVG/video-only and combined type intersection | PASS: 6 / 2 / 1 / 0 / 9 |
| Folder/media checkable menus | PASS: actual unchecked/checked widgets |
| Password cancellation and correct PIN | PASS: real UI; no preference-seeded authentication |
| Missing all-files access and cancellation/recovery | PASS |
| Cold-launch locked filter and explicit unlock | PASS |
| Search, viewer traversal of all hidden siblings | PASS |
| Unhide current image and return to ordinary results | PASS |
| Rebuilt groups, obsolete headings removed | PASS |
| Ordinary favorite preserved across filtering and cold relaunch | PASS |
| Package-specific GET_CONTENT media entry | PASS, limited to menu/subset behavior |

Initial font-scale 2.0 tests at density 420 covered Light, Dark, Black & White,
System default + OS light and System default + OS dark. They found that the long
English menu label was ellipsized. The new label was shortened to **Hidden only**;
the historical failure evidence is retained rather than described as a pass.

The final APK (`d3fc6ccb…`) was installed without resetting app data. Its exact
nine-item hidden subset passed again at **both font scales 1.0 and 2.0**; actual
menu off/on transitions passed. The shorter, fully visible **Hidden only** label
and its reachable checked row passed in **both folder and media menus** for:

| Final label-fit followup, font 2.0 / density 420 | Media menu | Folder menu |
|---|---|---|
| Light | PASS | PASS |
| Dark | PASS | PASS |
| Black & White | PASS | PASS |
| System default + OS light | PASS | PASS |
| System default + OS dark | PASS | PASS |

Parent review included original full-size screenshots as well as the ten-view
contact sheet. The resource-only correction did not change Kotlin behavior; the
broader behavior matrix above was not misrepresented as having been repeated in
full on the final binary. Original font scale 1.0 and density 420 were restored.
Chinese text is included, but its optional separate large-font locale test was
not run. This is scoped menu readability testing, not full WCAG certification.

Detailed logs, scripts, synthetic fixture manifest, original PNG/XML, historical
failures and final followup are included in the fork release's
`verification.tar.gz`: see `ui/REPORT.md` and `ui/REPORT-label-fix.md` there.

### Test setup and limits

All fixtures were synthetic. Media access was initially granted via system UI;
all-files access denial/recovery used disclosed emulator `appops` setup. Fixture
folders and filename display were seeded via allowlisted test-app preferences,
not hidden-filter/session/password/theme behavior preferences. Password, menu,
theme, search, unhide and favorite behavior used actual UI actions. No app-data
reset or database edits were used between cache/favorite checks.

The unmodified PIN keyboard clipped at default display density 420, so actual
PIN setup/authentication was verified at density 320/font scale 1.0. Density
420 was restored before the five-combination large-font matrix. This layout
path was not changed; no upstream baseline comparison was performed, and it
is not represented as universally accessible.

Not exhaustively tested: other Android APIs/real devices, real SAF/OTG storage,
positive video/RAW/portrait decoding, all excluded/protected-folder combinations,
recycle/copy/move/delete runtime permutations, concurrent permission revocation
during authentication, wrong-PIN/rotation matrices, external caller result
handling and external ACTION_VIEW isolation. Custom-cover filtering and aggregate
cache guards were source-reviewed, not exhaustively device-asserted. Group headers
have no numeric counts: exact members and headings, not invented numeric labels,
were checked. Favorite preservation is UI persistence coverage, not a full database
forensic audit. Existing filename clipping and themed checkbox contrast were not
formally certified.
