# Audit remediation scope

This is the remediation map for the findings in [`AUDIT.md`](AUDIT.md) (audited revision `0dfa1f6`).
It lists the action chosen for every finding. The authoritative final status, with evidence, is in
[`AUDIT_REMEDIATION.md`](AUDIT_REMEDIATION.md).

Branch: `audit/remediation`, created from `audit/project-audit` (`29461db` = `0dfa1f6` + audit files),
with `fix/world-map-loading` (`3ed9237`, upstream PR AndorsTrailRelease/andors-trail#139) merged unchanged.

Statuses: `FIXED`, `ALREADY FIXED`, `INTENTIONAL`, `NOT REPRODUCIBLE`, `DEVICE CHECK REQUIRED`,
`DEFERRED — MAINTAINER DECISION`. A finding with parts in different states lists each part.

| ID | Severity | Finding | Action taken | Final status |
|----|----------|---------|--------------|--------------|
| H1 | High | Android 16 system Back bypasses `onBackPressed()` | Platform `OnBackInvokedCallback` (API 33+) runs the existing Back logic in `MainActivity` and `StartScreenActivity`; static test that every Back override is registered | DEVICE CHECK REQUIRED: compiles and is tested statically; Android 16 behavior not observed |
| H2 | High | Savegames overwritten in place | Savegames, quicksave, cheat-detection files and world map files are written to a temporary file, synced, then renamed | FIXED |
| H3 | High | APK ships unused translation sources | Only `.mo` files are packaged; CI fails when other translation files reach the APK | FIXED: APK −51.7%, measured in CI |
| H4 | High | World map slow to open / blank page | Merged `fix/world-map-loading` (PR #139); browser regression tests in CI | FIXED in the browser tests; device timing NOT MEASURED |
| M1 | Medium | Missing phrase crashes the game | Unknown phrase ends the conversation; unknown skills, maps, droplists, items, filters and conditions in scripts are skipped and logged | FIXED |
| M2 | Medium | Zip Slip in world map import | Canonical-path check of every entry; `null` file name handled | FIXED |
| M3 | Medium | Spawn areas restored by id, reset by index | Restored areas are marked; exactly the others are initialized | FIXED |
| M4 | Medium | Malformed savegames crash the app | Parser runtime exceptions become load failures; maps reset after a failed parse; unknown current map rejected; checksum length checked | FIXED |
| M5 | Medium | Map transitions mutate the model off the UI thread | Map loaded in the background, transition applied on the UI thread | DEVICE CHECK REQUIRED |
| M6 | Medium | Import/export failures invisible, dialog can hang | Exactly one result per background task; failures reported and logged in release builds; shared executor; temporary ZIPs deleted | FIXED |
| M7 | Medium | Startup parses all maps; quadratic spawn-group lookup | Spawn-group index (123–135 ms → 2.8–2.9 ms on a desktop JVM). Lazy map parsing and moving the synchronous loading off the UI thread need device profiling | FIXED (lookup); DEFERRED — MAINTAINER DECISION (lazy parsing) |
| M8 | Medium | Save loading renders missing world map images synchronously | HTML rebuild already batched by PR #139; background rendering would read the live model (see M5) | ALREADY FIXED (HTML rebuild, PR #139); DEFERRED — MAINTAINER DECISION (image rendering) |
| M9 | Medium | Content: NPCs that never appear, dead branches | CI fails on content or translation problems that are not in the reviewed baseline; the content edits need content decisions | FIXED (CI guard); DEFERRED — MAINTAINER DECISION (content edits) |
| L1 | Low | FileProvider shares the whole file system | Only `andors-trail/worldmap/` in the external files directory | DEVICE CHECK REQUIRED |
| L2 | Low | WebView: unescaped names, file access | Area names HTML-escaped; file access only before Android 10 | FIXED (escaping); DEVICE CHECK REQUIRED (file access) |
| L3 | Low | Backups can exceed the 25 MB quota | Backup and data extraction rules exclude the world map cache and logs from cloud backups | DEVICE CHECK REQUIRED |
| L4 | Low | Storage permissions for all API levels | `maxSdkVersion` 29 (WRITE) and 32 (READ), matching the code paths | DEVICE CHECK REQUIRED |
| L5 | Low | Two Latin strings throw when formatted | Specifiers fixed; CI fails on new translations that would throw | FIXED |
| L6 | Low | `zh-rCN` language value | Resource-qualifier region parsed as a region | FIXED |
| L7 | Low | Locale handling | `equals` instead of `==`; `Locale.ROOT` for legacy monster ids; `updateConfiguration` kept | FIXED |
| L8 | Low | Debug logcat files never cleaned up | At most ten log files are kept | FIXED |
| L9 | Low | Game timer uses the wall clock | `SystemClock.uptimeMillis()` | FIXED |
| L10 | Low | Icons allocate a bitmap per bind | Needs allocation profiling on a device before trading memory for allocations | DEFERRED — MAINTAINER DECISION |
| L11 | Low | Build script issues | Duplicated keystore loading removed. Version in Gradle, task wiring, minification, ProGuard file and the AndroidX upgrade are release-process decisions | FIXED (keystore); DEFERRED — MAINTAINER DECISION (rest) |
| L12 | Low | CI gaps | Lint, release build and release unit tests, pinned actions, Node 24 artifact upload, content/translation/APK checks. Duplicate push/PR runs and the Travis files are maintainer decisions; fork PR approval is a GitHub security setting | FIXED (listed items); DEFERRED — MAINTAINER DECISION (duplicate runs, Travis); INTENTIONAL (fork approval) |
| L13 | Low | Few tests; missing `CombatControllerTest` | 11 → 57 unit tests (8 from PR #139, 38 from the remediation), including `CombatControllerTest`; run for debug and release | FIXED |
| L14 | Low | Content hygiene | Guarded by the content baseline; the edits are content decisions | DEFERRED — MAINTAINER DECISION |
| L15 | Low | Legacy tooling (`AndorsTrailEdit`, Eclipse leftover) | Whether to keep the old editor is a project decision | DEFERRED — MAINTAINER DECISION |
| L16 | Low | Deprecated APIs | No functional defect; not modernized (instructions: no automatic modernization) | DEFERRED — MAINTAINER DECISION |
