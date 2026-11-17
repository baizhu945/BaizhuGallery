# Bulk visibility: standalone JVM filesystem tests

Run from any directory:

```sh
bash /path/to/FossifyGallery/tests/bulk-operations/run.sh
```

Requires `kotlinc`, Java, Python 3, GNU `timeout`, and an unprivileged Linux
user on a writable POSIX filesystem with symlinks and millisecond timestamps.
No Gradle, Android SDK, downloaded dependencies, or mocks are used. The runner
compiles the actual `extensions/BulkVisibilityFiles.kt`, not a copied helper.
Compilation is bounded to 60 seconds; the entire test JVM to five seconds.
All fixtures and compiled output live in a fresh temporary directory, removed
with Python `shutil` on exit. Permissions change only on one owned temporary
fixture and are restored in `finally`. Root is deliberately unsupported for
the permission test: bypassing permission checks would invalidate that test.

Coverage includes both collision directions with distinct SHA-256 hashes,
hide/unhide content, leading dots, preserved/refreshed modification times,
invalid/unknown paths, missing paths, directories, source and destination
symlinks (including dangling destinations), unsupported permissions, settled
versus failed counts, and a 1000-item mixed batch with exact failure counts
and disk-state assertions. Missing ordinary-name unhide intentionally verifies
a stale-claim no-op; invalid paths are unverified, not claimed as successes.

These are **desktop filesystem** checks, not SAF, Android filesystem/device,
UI, provider callback, permission-dialog, or lifecycle validation. Eligible SAF
backend callback tests belong to separate device testing. Android-dependent
`Owned*` helpers are not compiled here; no Android mocks or device claims are
made. This suite does not access any device (including UI device 5592).
