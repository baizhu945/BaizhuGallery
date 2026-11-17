# Contributing to BaizhuGallery

## Reports and proposals

Use [baizhu945/BaizhuGallery issues](https://github.com/baizhu945/BaizhuGallery/issues)
for this independent application, not Fossify's support channels. Search existing
reports first. Include app version, exact package/build variant, Android version,
storage provider, reproduction steps and expected/observed behavior. Redact private
paths, photos, credentials and settings exports. Prefer synthetic fixtures.

## Development

Keep changes focused and follow existing Kotlin/XML style and architecture.
Retain upstream copyright/license notices and attribution. The inherited
[Fossify developer guidance](https://github.com/FossifyOrg/General-Discussion#contribution-rules-for-developers)
is useful style background, not a promise of upstream acceptance or affiliation.
Build/signing instructions and identity boundaries are in
[Independent release](docs/independent-release.md).

- Preserve hidden-origin, authorization, filtered-cache and data-integrity semantics.
  Do not guess transfer destinations or turn unknown outcomes into success.
- Run relevant standalone tests, Gradle builds, lint and detekt; report exact scope
  and failures. `NO-SOURCE` is not executed test coverage.
- Fix new findings rather than adding suppressions or expanding lint/detekt baselines.
- Keep controls and dialogs readable/reachable with large fonts, light/dark themes
  and translated text. Consider screen-reader labels and contrast; do not claim
  accessibility certification from screenshots or a limited theme matrix.
- Describe performance workloads and regressions as well as improvements. Historical
  APK evidence does not certify a new package, signature or release.

## Documentation and translations

Documentation, translation and accessibility improvements are welcome as pull
requests here. Preserve nuanced safety limitations and historical hashes/identities;
never publish private user data or signing material as test evidence.
