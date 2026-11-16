# Hidden by you: default browsing policy

This fork now distinguishes **explicit user intent** from merely being hidden on
the filesystem. It does not claim that Android provides a reliable "hidden by the
system" attribute, or that small images are system images. File size is never used
as evidence of who hid a file.

## Default behavior

All hidden-media browsing routes, including **Hidden only**, **Temporarily show
hidden**, the persistent **Show hidden items** setting, cached media, search and
gallery viewer navigation, show only hidden items you have:

- successfully hidden using this app after this change; or
- explicitly added using **Claim hidden** (Chinese: **纳入我的隐藏**).

Ordinary visible media remain visible in normal browsing. **Hidden only** still
restricts the result to hidden media, and media-type filters still intersect.
An intentionally hidden tiny image is included; an unrelated large hidden image
is excluded. Authorization, storage permissions, excluded folders and locked
folders remain separate requirements, not replaced by the intent registry.

## Existing hidden photos: claim, do not guess

Older versions only renamed files or created `.nomedia`; they did not record
who performed the hiding. Existing media therefore are **not automatically
adopted**. They are not deleted, unhidden, moved or modified by this upgrade.

1. Enable **Other hidden** (Chinese: **包含其它隐藏**) in the overflow menu.
   Read the explanation and complete the existing hidden-item authorization.
2. Select your old photos, open their action menu, and choose **Claim hidden**.
   You can select several photos at once.
3. For a hidden folder, select it in the folder overview and use **Claim hidden**;
   confirm the folder scope described below.
4. Turn **Other hidden** off. Your claimed content remains; unknown system/app
   assets disappear again.

**Other hidden** is off by default. It is an explicit, remembered opt-in, not a
new source-detection heuristic. Cancelling the explanation, password or storage
prompt does not enable it. An empty My-hidden view provides the same recovery
entry. Imported older settings do not invent ownership of old hidden files.

**Forget claim** (Chinese: **取消认领**) removes an explicit record without
unhiding or deleting the underlying file. If the item is still covered by a
claimed parent folder it remains included; forget that folder's claim to remove
that inherited scope. No per-child denylist is implied.

## Folder scope avoids importing hidden cache children

A folder you hide or claim covers ordinary descendants hidden by that folder's
own dot-prefixed path or `.nomedia` marker. It does **not** silently adopt
independently hidden descendants:

- `.thumbnail.png` inside that folder is not adopted automatically;
- a `.cache/` child directory is not adopted automatically;
- a descendant directory with its own `.nomedia` is not adopted automatically.

Claim those particular files or deeper folders separately if they are yours.
An explicit deeper claim overrides that independent hidden boundary. Prefix
siblings (such as `Album` and `Album-extra`) never share a folder claim.

## Persistence and lifecycle

The registry is independent of replaceable Room media caches. Its file and folder
sets are updated together under a shared lock with a revision counter; browsing
requests reject stale policy/registry snapshots. Successful app hide/unhide,
verified single-item rename, folder rename, recycle/restore and verified deletion
maintain records. Failed or cancelled filesystem operations do not create claims.
Hide/unhide refuses a pre-existing destination name rather than silently replacing
that file; resolve the name conflict first. The unchanged filesystem API does not
provide a general transaction against concurrent changes by other applications.

Recycle classification uses the original path. Restore only propagates the
successfully restored item's effective user intent; conflict restoration must not
steal a still-live original's record or adopt every sibling from a claimed folder.

The unchanged Commons API does **not** expose actual per-item, conflict-resolved
paths for batch rename or copy/move. The app does not guess destination names or
attribute unrelated files by size/timestamps. Transferred/batch-renamed hidden
items may need **Other hidden → Claim hidden** again; a transfer notice explains
this where applicable. External moves, aliases and external replacements are not
reliably followed. This is an intent registry, not a persistent filesystem identity
or encryption system; externally replacing a recorded path cannot establish a new
file's origin reliably. Reclaim/revoke records when reorganizing outside the app.

Settings export/import preserves the two sets as JSON, including commas and
Unicode in paths. Clearing app data or uninstalling can lose the registry, but
not the hidden files on disk; restore exported settings or claim them again.
Exported paths can reveal private filenames, so protect the settings backup.

## Cache and privacy boundaries

Default mixed-mode results are also a filtered subset. Their emptiness is not
evidence of deletion, and their aggregates must not overwrite complete directory
caches. Counts/covers/search/grouping/pager results use the same policy. Claiming
or forgetting invalidates in-flight presentation results before refreshing.

An explicit external Android view request is treated separately from gallery
browsing; opening a requested URI must not authorize unrelated hidden siblings.
The legacy hidden-folder management screen is a filesystem maintenance tool,
not proof that every listed `.nomedia` folder belongs to you.

## Verification

```sh
bash tests/hidden-media-filter/run.sh
./gradlew :app:assembleFossDebug :app:testFossDebugUnitTest detekt lint \
  :app:assembleFossRelease
```

No dependency, build configuration, target SDK, Room schema or quality baseline
change is required. See `docs/user-hidden-validation.md` for fresh results,
artifact identity, device setup and limitations. The earlier
`docs/hidden-only-validation.md` describes the previous physical-hidden-only
policy and is retained as historical evidence, not new-policy verification.
