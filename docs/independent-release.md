# Independent release: BaizhuGallery 1.15.0

## Identity and distribution

[baizhu945/BaizhuGallery](https://github.com/baizhu945/BaizhuGallery) is a new,
non-fork GitHub repository for independently maintained work derived from Fossify
Gallery. It is not affiliated with Fossify. The launcher name stays **Gallery**.
Application ID, namespace and source package root are
`org.fossifymigration.baizhu.gallery`. The stable release version is **1.15.0**.

Published APKs must be signed **Release** builds with exactly that application ID,
not `org.fossifymigration.baizhu.gallery.debug`. The new release signing identity
is separate from the prior local debug signing key and from Fossify's signing key.
Only [this repository's releases](https://github.com/baizhu945/BaizhuGallery/releases)
are the canonical independent download channel; this document does not assert a
store listing or that a release has already been uploaded.

The default launcher icon (Light_blue) and brief startup splash use `#0288D1`.
Green and other configurable icon alternatives remain available; Android themed
icons still follow the system/OEM launcher palette rather than a fixed blue.
Resource/static-pixel checks are not device-rendering certification. About's
source/issues gateway points to this independent project; upstream FAQ, licenses
and credit links remain explicitly labeled as upstream.

See [Commons compatibility](commons-compatibility.md) for the narrowly scoped
build-time adjustment. Existing lint/detekt baseline files receive only one-to-one
namespace/path substitutions for the identity rename, with no new suppressed
issues or relaxed checks. Historical statements that baselines were unchanged
refer to those earlier feature changes, not this identity rename.

## Side-by-side installation is not private-data migration

The new package can coexist with upstream `org.fossify.gallery` and earlier local
`org.fossify.gallery.debug` builds. Check package identity before opening or
uninstalling either app: both may show the name Gallery.

There is **no automatic migration** of private settings, favorites, hidden-origin
ownership records, recycle-bin backups, password configuration or Android SAF
folder grants. Reauthorize storage and configure the new app separately. Do not
copy private app directories/databases or use an ad-hoc data-copy migration.

**Before uninstalling the old app, restore its recycle-bin items and export any
needed settings/data through supported UI, then verify important media and
backups. Uninstalling discards the old app's private recycle backups and other
private data.** An exported settings file is not a backup of private recycled
media. Keep the old installation until recovery and verification are complete.
Any manual settings import must be checked for compatibility; it is not a promise
of complete cross-package migration. Exports may contain private filenames.

Shared media files are not duplicated automatically by installing the new app.
Both apps can modify the same accessible media: filesystem `.nomedia` markers and
dot-prefixed names/ancestors remain shared visibility state. The new app does not
import the old ownership registry automatically. Older hidden media may therefore
require explicit **Other hidden → Claim hidden** in the new app; permissions,
password authorization and independent hidden-child boundaries still apply.
See [user-hidden policy](user-hidden-items.md). Hiding is not encryption.

## Build and signing

Use the existing Gradle wrapper with JDK 17, Android SDK platform 36 and Build
Tools 36.0.0. On NixOS provision these with a traditional Nix expression / `nix-shell`
(no flakes); use the existing local SDK/Nix environment rather than introducing
another package manager. Set `JAVA_HOME`, `ANDROID_HOME` and `ANDROID_SDK_ROOT`
consistently for that environment. For example, inside the provisioned shell:

```sh
./gradlew --no-daemon --max-workers=4 \
  -Pandroid.builder.sdkDownload=false \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  :app:assembleFossRelease
```

The existing signing configuration accepts all four environment variables:
`SIGNING_STORE_FILE` (prefer an absolute keystore path), `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS`, and `SIGNING_KEY_PASSWORD`. Supply them securely; do not commit
keys/passwords or place literal secrets in documentation or shell history.
An existing root `keystore.properties` takes precedence over these variables;
ensure it does not unintentionally select a different key. Without signing
configuration Gradle can produce an **unsigned** Release build, not a publishable
signed APK. Debug build success is not release-signature verification.

Before publishing, independently inspect the built APK's exact package/version,
Release variant, signing certificate and SHA-256, and record them alongside its
source revision and verification scope in the release notes. Do not reuse a prior
APK hash or debug certificate as evidence for the new artifact. Future updates
must retain the independent release signing identity.

## Historical evidence and limitations

The hidden-only, user-hidden and performance validation reports, and the historical
benchmark section of `tests/performance/README.md`, describe prior releases and
candidates. Their old package IDs, dates, hashes, commit references and URLs are
intentional historical evidence, not stale values to globally replace. The earlier
changelog entries and upstream issue links are retained for the same reason.

Changing identity does **not** recertify those device/theme/storage/performance
matrices for 1.15.0. Each new test report must identify its own artifact and actual
checks. Historical emulator measurements include regressions and untested paths;
there is no universal speedup, provider-safety or full accessibility guarantee.
No new 1.15.0 device matrix is claimed by this documentation update.

GPL v3, original copyright notices and project history remain in place. Thanks to
[Fossify Gallery](https://github.com/FossifyOrg/Gallery),
[Fossify Commons](https://github.com/FossifyOrg/Commons), and the earlier
[Simple Mobile Tools](https://github.com/SimpleMobileTools) heritage.
