# 1.15.0 independent release verification

## Artifact identity

- Application ID / namespace / source root: `org.fossifymigration.baizhu.gallery`.
- Version: **1.15.0**, universal FOSS Release, versionCode **45**; desktop **Gallery**.
- APK SHA-256: `378755d638a4bd0c3df113b4d6a5f150cb16c45ac6744f5e76d625f25b6e5cc0`.
- New RSA release certificate SHA-256:
  `03723266d53edf378d9f44f5bea3fb1027bc646eb69bbdd85d40f62babe55371`.
- Release APK signature verified; not debuggable, not signed with the former Android
  Debug certificate. Includes arm64-v8a, armeabi-v7a, x86 and x86_64 native libraries.
- Repository: [baizhu945/BaizhuGallery](https://github.com/baizhu945/BaizhuGallery),
  independently created, **not a GitHub fork**. Full upstream/fork history and GPL
  are retained. Earlier releases in baizhu945/Gallery are unchanged.

## Checks actually performed

1. JDK 17 / existing SDK 36: `assembleFossDebug`, `testFossDebugUnitTest`, `detekt`,
   `lint`, and independently signed `assembleFossRelease`: **PASS**. The inherited
   Gradle JUnit task remains **NO-SOURCE**, not executed JUnit coverage. Existing
   lint warnings/baselined findings remain; there is no claim of zero warnings.
2. Actual standalone production helpers: **30 classifier + 15 registry + 19 state
   tests**, **25 bulk filesystem tests** (including a 1,000-item mixed batch), plus
   the production selection-index runner: **PASS**. Historical helper comparison
   still extracts the exact old source under its old namespace; these are not new
   whole-app FPS measurements.
3. Source and compiled APK identity: 140 Kotlin source files under the new root;
   no defined old `org.fossify.gallery` app classes. Compiled application/provider,
   widgets and custom-view references follow the new package. Exactly one launcher
   alias is enabled by default: **Light_blue**. Default label is Gallery. The 13
   installed permission declarations match the prior APK; SDK levels, dependency
   versions and Room schema were not upgraded.
4. Default launcher/round icon background and supplied source previews use
   **#0288D1**, with the existing white glyph. Lossless raster RGB/alpha checks,
   adaptive resources, default icon preference and legacy/Android 12+ splash paths
   were inspected. The optional original Green icon remains green and selectable;
   Android themed icons intentionally remain system-colored.
5. Commons compatibility: pure-JDK constant-pool self-test and rejection cases
   **PASS**. Independently comparing 494 AAR entries and 693 classes.jar entries
   proved only `BaseSimpleActivity.class` content changed, by exactly one UTF8
   constant substitution. Pool count remains 2,149; every other class byte and
   resource entry is unchanged. See [scope and rationale](commons-compatibility.md).
6. Baselines are exactly the prior files with namespace/path substitutions only:
   no issue entries added/removed, no new suppressions. GPL file is byte-identical.
   Markdown links/whitespace and `git diff --check` passed. Signing keys/passwords,
   emulator disks and prior APK copies are excluded from repository/release assets.

## Device-testing boundary

The user requested that both prior emulator background tasks stop. Both exited
normally with their data preserved and **were not restarted for 1.15.0**. Therefore
this release has build, source, compiled-artifact, helper and icon-resource proof,
**not a newly run device/theme/SAF/camera-date/performance matrix**. In particular,
actual OEM/system splash rendering, fresh install/upgrade, every selectable icon
and the new About dialog were not device-tested here. Prior functional and frame
reports remain valid only for the exact artifacts they name; they are not relabelled
as 1.15.0 results. Android permission grants still require user consent.

Read [migration and uninstall warnings](independent-release.md) before removing an
older installation: changing the package creates a separate private sandbox and
**does not migrate recycle backups, hidden ownership, favorites or SAF grants**.
The release signing key is a new long-lived local identity; future updates must
use it. GitHub's optional manual signing workflow needs separately configured
repository secrets and was not used to sign this local release.

The release evidence archive contains build/signature/compiled-manifest receipts,
source identity assertions, actual helper outputs, narrow-compatibility comparison,
icon previews and selected test scripts. Published assets are checked against
GitHub server SHA-256 digests; checksums are published alongside the APK.
