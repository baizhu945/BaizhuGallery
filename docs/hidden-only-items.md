# Hidden-only media browsing

Independent BaizhuGallery feature, originally developed against Fossify Gallery
`0359a4598ec35ad7b50683f4365641eb379491bb` (v1.14.2), inspired by
[upstream issue #274](https://github.com/FossifyOrg/Gallery/issues/274).
No upstream pull request is proposed. Use [this project's contribution guide](../CONTRIBUTING.md)
and read [independent identity and installation boundaries](independent-release.md).

## Current default: hidden by you

Hidden-media browsing now defaults to items you explicitly hid in this app or
manually claimed. Unknown system/app/legacy hidden assets are excluded unless
**Other hidden** is explicitly enabled. See
[Hidden by you: policy and migration](user-hidden-items.md) for claiming older
photos/folders without modifying them. No size-based origin guessing is used.

## Usage

Open the overflow menu in the folder overview or media list and select
**Hidden only** (Simplified Chinese: **仅显示隐藏的项目**).
The checkmark indicates the active filter; selecting it again removes the
filter. The option also exists when Gallery is used as a media picker.

The filter intersects with **Filter media** (images, videos, GIFs, RAW, SVG,
portraits). It changes the displayed subset, not the files themselves. Physical
hiddenness means a dot-prefixed name/ancestor, or an exact `.nomedia` marker in
the containing directory or an ancestor. `.nomedia.bak` is not a marker. Matching
also requires user intent (or explicit **Other hidden** opt-in) and the existing
hidden-media authorization. Explicitly including a directory affects discovery,
not hidden ownership.

Existing excluded-directory and protected-folder rules remain in force.
Discovery uses the existing remembered/indexed/included folders; this is not
an unrestricted whole-storage scanner. Add otherwise undiscovered folders
through **Settings → Manage included folders**.

## Permission and session behavior

On Android 11+, all-files access is required. Activation uses the existing
hidden-item password protection and temporary hidden session. Cancelling either
prompt does not enable the filter. Storage access is checked again after
asynchronous authentication.

The filter preference persists like the existing media-type filter, but it is
**not authorization**. A cold launch or expired temporary session can leave a
checked, empty hidden-only view. Use **Temporarily show hidden** to unlock it
again, or uncheck **Hidden only** to return to normal browsing.
**Stop showing hidden** also clears the hidden-only filter. Turning off just
the filter does not end an already authorized hidden session.

This feature does not encrypt or secure the underlying files. Explicit external
Android `ACTION_VIEW` requests can still display their requested target; while
hidden-only browsing is active, that external viewer does not load siblings.

## Implementation map

The app has one Android `app` module with `foss`/`gplay` flavors. Kotlin activities
coordinate directory/media/search/viewer screens, adapters present their models,
Room DAOs cache directories/media/favorites, and `MediaFetcher` combines filesystem,
MediaStore and OTG discovery. Hiding an individual file renames it with a dot;
hiding a folder creates `.nomedia` through the existing activity extensions.

Relevant change points:

- `helpers/HiddenMediaFilter.kt`: operation-scoped physical classifier and ancestor
  probe cache, independent of Android for direct regression testing.
- `extensions/HiddenMedia.kt`: shared menu activation and authorization transition.
- `helpers/MediaFetcher.kt`, `extensions/Context.kt`: live/cached intersections,
  directory summary/cover construction and maintenance bypass.
- `MainActivity`, `MediaActivity`, `SearchActivity`, `ViewPagerActivity`: presentation,
  reload/session handling and filtered navigation.
- `adapters/DirectoryAdapter.kt`: safe subset-based bulk operations.
- `res/menu/menu_{main,main_intent,media}.xml`: checkable overflow entries.

## Data integrity

- Live and cached media are filtered before grouping. Filtered groups are rebuilt
  rather than keeping obsolete headings or counts.
- Directory counts, sizes, types and covers derive from matching items; an
  ordinary custom cover cannot leak into the hidden-only overview.
- Filtered emptiness is not evidence of deletion. Full directory aggregates,
  cached ordinary media and favorites are not reconciled against this subset.
- Background maintenance can bypass this browsing filter without introducing a
  new permission or password bypass.
- Directory copy/move/delete enumeration respects the displayed filter. Generic
  Favorites/Recycle Bin directory deletion is unavailable in hidden-only mode
  because it would clear invisible items. Explicit **Empty recycle bin** and
  **Empty and disable recycle bin** retain their existing whole-bin meaning.
- Recycle media are classified by their original path, not the private recycle
  directory. Ancestor `.nomedia` status is evaluated now, not at deletion time.

## Reproducible verification

No Gradle/dependency/SDK configuration or quality baseline is changed.

```sh
./gradlew :app:assembleFossDebug :app:testFossDebugUnitTest detekt lint
bash tests/hidden-media-filter/run.sh
```

The standalone runner compiles the actual production classifier and user-intent
matcher with `kotlinc` and needs no additional test dependency. The upstream
Gradle unit-test task currently has no test sources; a `NO-SOURCE` result is not
a JUnit pass.

Historical policy verification is recorded in `docs/user-hidden-validation.md`;
it does not certify the independent 1.15.0 artifact.
`docs/hidden-only-validation.md` preserves the earlier physical-hidden-only
version's verification, not current origin-policy test results.
