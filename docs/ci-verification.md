# CI environment and release guardrails

## SDK setup failure (run 44020571986)

The original hosted build failed **before Gradle**: `setup-android@v3` defaults to
`tools platform-tools`, but the old `tools` SDK package is no longer published.
This was a workflow provisioning error, not evidence of an application runtime
failure. Both build and manual-release workflows now explicitly install only
`platform-tools platforms;android-36 build-tools;36.0.0`. License acceptance stays
explicit in the action, without printing entire license texts.

## Preventing nearby failures

- Pin Ubuntu 24.04, JDK 17 and the current SDK components; bound Gradle/Kotlin memory
  and workers instead of applying the local 8 GiB heap to a smaller hosted runner.
  Disable unplanned SDK downloads, use exact verification build-tools rather than
  whichever version happens to be highest on the runner, and retain diagnostics.
- Run the **actual** hidden-policy and bulk-filesystem helpers. The CI-only Kotlin
  compiler matches the version catalog and is checked against the committed SHA-256
  of JetBrains' official archive. The system `kotlinc` version is not trusted.
  The inherited Gradle JUnit task is still NO-SOURCE, not JUnit test coverage.
- Run the pure-JDK Commons constant-pool guard tests and pure-Python CI/release
  regression tests. Historical performance comparisons remain local/separate:
  they require old Git history and are not fresh whole-app performance measurements.
- Resolve an actual release tag to its immutable commit; reject existing releases
  before signing. Never overwrite the published 1.15.0 tag or assets. A manual
  release can build older source, so its verification stays workflow-owned/inline.
- Check every APK against the established release certificate, exact package,
  versionCode/ABI mapping, native contents, and non-debuggable status. Generate
  SHA256SUMS. Metadata validation derives its five required codes from VERSION_CODE;
  after codes 45–49, a future base must exceed 49 to preserve update ordering.
- Keep PR workflows read-only and unsigned; no signing secrets in PR builds, no
  `pull_request_target` execution of contributor code. Signing secrets remain
  optional administrator configuration; the manual release job is not run merely
  to test this SDK fix.

Local checks:

```sh
python3 -B -m unittest discover -s tests/ci -p '*Test.py' -v
python3 -B -m unittest discover -s tests/ci-release-validation -v
bash .github/scripts/setup-kotlin.sh --validate-only
# NixOS, traditional tooling (no flakes):
nix-shell -p actionlint --run 'actionlint -oneline'
```

These checks validate workflow contracts, syntax and verifier rejection cases;
mock APK/signature tests are not Android installation or signing certification.
Actual hosted execution must also pass on the pushed commit. No emulator is needed
or restarted for these CI changes. Application code, dependencies, SDK/API levels,
Room schema, baselines and the already published APK are unchanged.
