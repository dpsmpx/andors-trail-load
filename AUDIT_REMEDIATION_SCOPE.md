# Audit remediation scope

This is the remediation map for the findings in [`AUDIT.md`](AUDIT.md) (audited revision `0dfa1f6`).
It lists the action chosen for every finding. The authoritative final status, with evidence, is in
[`AUDIT_REMEDIATION.md`](AUDIT_REMEDIATION.md).

Branch: `audit/remediation`, created from `audit/project-audit` (`29461db` = `0dfa1f6` + audit files),
with `fix/world-map-loading` (`3ed9237`, upstream PR AndorsTrailRelease/andors-trail#139) merged unchanged.

Statuses: `FIXED`, `ALREADY FIXED`, `INTENTIONAL`, `NOT REPRODUCIBLE`, `DEVICE CHECK REQUIRED`,
`DEFERRED — MAINTAINER DECISION`. `PLANNED` marks work not finished yet.

| ID | Severity | Finding | Action | Final status |
|----|----------|---------|--------|--------------|
| H1 | High | Android 16 system Back bypasses `onBackPressed()` | Register a platform `OnBackInvokedCallback` (API 33+) that runs the existing Back logic in `MainActivity` and `StartScreenActivity`; static test that every Back override is registered | PLANNED |
| H2 | High | Savegames overwritten in place | Write every savegame, quicksave, cheat-detection file and world map HTML to a temporary file, sync, then rename | PLANNED |
| H3 | High | APK ships unused translation sources | Copy only `.mo` files into the assets; CI fails when other translation files reach the APK | PLANNED |
| H4 | High | World map slow to open / blank page | Merged `fix/world-map-loading` (PR #139); add browser regression tests of the page to CI | PLANNED |
| M1 | Medium | Missing phrase crashes the game | Unknown phrase ends the conversation instead of throwing; invalid map, skill and droplist ids in script effects are skipped; content check in CI | PLANNED |
| M2 | Medium | Zip Slip in world map import | Reject entries that resolve outside the target directory or contain separators; handle a `null` file name | PLANNED |
| M3 | Medium | Spawn areas restored by id, reset by index | Track which areas were loaded and initialize only the others | PLANNED |
| M4 | Medium | Malformed savegames crash the app | Treat runtime exceptions while parsing as a failed load; validate counts read from the file | PLANNED |
| M5 | Medium | Map transitions mutate the model off the UI thread | Load map files and tiles in the background, apply the transition on the UI thread; keep the old state when loading fails | PLANNED |
| M6 | Medium | Import/export failures invisible, dialog can hang | `BackgroundWorker` guarantees exactly one result callback; errors are logged in release builds; shared executor; temporary files deleted | PLANNED |
| M7 | Medium | Startup parses all maps; quadratic spawn-group lookup | Index spawn groups once (measured); lazy map parsing needs device profiling | PLANNED |
| M8 | Medium | Save loading renders missing world map images synchronously | To be decided after reviewing tile-cache thread safety | PLANNED |
| M9 | Medium | Content: NPCs that never appear, dead branches | Fix references whose intent is unambiguous; everything else becomes a reviewed baseline, and CI fails on new problems | PLANNED |
| L1 | Low | FileProvider shares the whole file system | Limit it to the world map folder | PLANNED |
| L2 | Low | WebView: unescaped names, file access | HTML-escape named areas; enable file access only where `file://` is used (API < 29) | PLANNED |
| L3 | Low | Backups can exceed the 25 MB quota | Backup rules excluding the world map cache and debug logs | PLANNED |
| L4 | Low | Storage permissions for all API levels | Add `maxSdkVersion` matching the code paths that need them | PLANNED |
| L5 | Low | Two Latin strings throw when formatted | Fix the format specifiers; CI fails on translations that would throw | PLANNED |
| L6 | Low | `zh-rCN` language value | Parse resource-qualifier regions (`rCN`) as regions | PLANNED |
| L7 | Low | Locale handling (`==`, deprecated API, default-locale `toLowerCase`) | Fix the comparison and use `Locale.ROOT` for identifiers; keep `updateConfiguration` | PLANNED |
| L8 | Low | Debug logcat files never cleaned up | Keep only the most recent log files | PLANNED |
| L9 | Low | Game timer uses the wall clock | Use `SystemClock.uptimeMillis()` | PLANNED |
| L10 | Low | Icons allocate a bitmap per bind | Needs profiling on a device before trading memory for allocations | PLANNED |
| L11 | Low | Build script issues | Remove duplicated keystore loading, add the referenced ProGuard file, move version to Gradle, simplify task wiring; minification is a release-process decision | PLANNED |
| L12 | Low | CI gaps | Add lint, release build, content/translation/APK checks, pinned actions, no duplicate runs | PLANNED |
| L13 | Low | Few tests; missing `CombatControllerTest` | Tests for every fix; add `CombatControllerTest` | PLANNED |
| L14 | Low | Content hygiene | Covered by the content baseline; map edits are content decisions | PLANNED |
| L15 | Low | Legacy tooling (`AndorsTrailEdit`, Eclipse leftover) | Project decision whether to keep the old editor | PLANNED |
| L16 | Low | Deprecated APIs | No functional defect; changed only where a fix touches the code | PLANNED |
