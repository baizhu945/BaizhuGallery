# Performance pipeline and bulk-operation feedback

BaizhuGallery's pipeline originated as a followup to the user-hidden policy work,
based on `79acc72c024cf57ca41540950f93588790146e85`. This is now an independent
project, not an official Fossify release; no upstream pull request is proposed.
See [independent release](independent-release.md) for current identity and migration limits.

## What changed

- Filesystem hidden checks, signature preparation, filtering, sorting and grouping
  run on workers. Main-thread publication validates request/policy identity and
  applies a prepared presentation, rather than repeating library-wide work.
- Search is debounced and latest-only; obsolete workers are interrupted and cannot
  overwrite newer queries. Pager refreshes also have request identity and avoid
  equivalent replacements.
- Scoped-storage MediaStore data and metadata maps are shared per load instead of
  querying/copying the entire library for every album. Discovery is cancellation-
  aware and avoids overlapping/cyclic root traversal.
- Directory projections are coalesced, cached rows are read in batches, and DB
  writes use bounded executors rather than a new thread for each album. Cache-first
  loading no longer performs the complete fresh-storage discovery first.
- Immutable row comparisons issue targeted range/metadata updates. Selection
  lookup is indexed with collision-free adapter-local keys, not path hashes.
  Stable RecyclerView IDs are not falsely claimed for mutable path hashes.
- Thumbnail signatures no longer stat files during binding. Equivalent image
  requests are reused, locked thumbnails are explicitly cleared, and recycled
  Glide/Picasso requests cannot install late images in the wrong row.
- MediaStore mutation bursts are coalesced and deferred during owned batches.
  Explicit mutations, provider changes and folder-protection changes invalidate
  presentation reuse. Pull-to-refresh remains available for external changes a
  provider does not report.

Hidden origins, permissions, protected/excluded folders and recycle original-path
rules are not replaced by performance caches. Filtered results do not become proof
of deletion and cannot overwrite complete directory aggregates.

## Progress semantics

Bulk Hide/Unhide uses serial owned work, real settled-item counters and one
metadata/scan completion phase instead of launching many asynchronous renames.
Successful file-origin changes are committed together. Existing targets are not
silently overwritten.

Delete, copy and move have explicit preparation, writing/backup, permission and
library-update phases. Known item/byte work shows actual counts; a current-file
byte bar is labeled as such. Native system authorization is kept batched rather
than requesting permission once for every selected image. Unknown-duration work
uses an indeterminate indicator, never a percentage inferred from elapsed time.
"Processed" includes failed attempts; failures and incomplete batches are not
reported as successful completion.

The progress window appears only when an operation lasts long enough to warrant
it and throttles redraws. **Hide progress / 收起进度** only hides the window: it
is not cancellation. Another destructive operation cannot silently overlap the
current owner. For legacy activity-bound storage callbacks, the current screen
orientation is temporarily held and the original orientation policy is restored
when the operation ends. Home/backgrounding is distinct from cancelling.

For progress when the gallery is minimized, enable **Settings → File operations
→ Batch progress notifications** in Android's notification settings. A silent
notification shows the current phase and confirmed item/byte counts, without file
names or lock-screen content. It does not depend on the window staying visible.
Returning to the operation restores its dialog if it was not explicitly hidden
and no confirmation is open. Dismissing the dialog does not stop the notification
or the batch. When notifications are blocked, progress is available again on
returning to the operation; the app does not override Android's permission.

Foreground ownership does not promise an operating-system background service or
unconditional continuation after process death. Owned loops stop at safe boundaries
on host destruction; completed results are retained. A durable pending-operation
journal records backups and native deletion requests before destructive steps,
so replacement hosts can reconcile observed outcomes without treating destruction
as a permission denial or inventing success.

## Data protection

- Copy destinations are newly created, not silently truncated existing files.
  Conflicts offer keep-both, skip or stop-remaining behavior.
- Same-filesystem moves use verified rename mappings rather than copying and
  hashing whole images unnecessarily. Fallback transfers verify retained bytes
  before deleting sources.
- A recycle backup cannot overwrite an older backup of the same original path.
- Restoration metadata is independently inserted, not dependent on a replaceable
  source cache row already existing. Restore waits for provider stream closure and
  scanning, then reapplies and verifies dates before clearing the recycle entry.
  A successfully restored, hash-verified private backup is cleaned up; legacy
  unreferenced identical backups can be reused safely for a later recycle cycle.
  Differing backups or active recycle entries are never silently replaced.
- Native deletion/cancellation and host recreation retain uncertain backup/journal
  state until positive reconciliation. No timeout establishes successful deletion.
- Document destinations must have a verified identity before hidden bytes are
  written; unknown mappings are not guessed. Providers without adequate access or
  verifiable outcomes fail conservatively and preserve originals.
- Lists are refreshed from observed outcomes, not optimistically emptied before a
  native permission request or filesystem deletion has finished.

For safety and backend limitations, not every storage provider supports every
optimized path. Verify important media backups before large destructive actions.
No new dependency, SDK level, manifest permission, Room schema or quality baseline
is required by these changes.

## Verification

```sh
bash tests/hidden-media-filter/run.sh
bash tests/bulk-operations/run.sh
bash tests/performance/run.sh
./gradlew :app:assembleFossDebug :app:testFossDebugUnitTest detekt lint \
  :app:assembleFossRelease
```

The independent Kotlin tests compile actual production helpers. Desktop filesystem
checks are not Android SAF/native-permission tests. The existing Gradle JUnit task
has no sources; `NO-SOURCE` is not counted as executed unit tests.

Measured device results, workload, before/after scope and remaining limitations
are recorded historically in `docs/performance-validation.md`, not rerun for
1.15.0 by this documentation update. No universal FPS/throughput or
"zero jank on all devices" claim is implied by static optimizations.
