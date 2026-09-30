# Audit remediation

Remediation of the findings in [`AUDIT.md`](AUDIT.md), on branch `audit/remediation` of
`dpsmpx/andors-trail-load`. The branch was not merged into `master`, and no upstream pull request
was created or changed.

Companion documents:
- [`AUDIT_REMEDIATION_SCOPE.md`](AUDIT_REMEDIATION_SCOPE.md): the action taken for each finding.
- [`AUDIT_REMEDIATION_DECISIONS.md`](AUDIT_REMEDIATION_DECISIONS.md): why each significant change
  was made this way.
- [`AUDIT_REMEDIATION_TESTING.md`](AUDIT_REMEDIATION_TESTING.md): what was tested and how.
- [`AUDIT_REMEDIATION_METRICS.md`](AUDIT_REMEDIATION_METRICS.md): measured before/after values.
- [`AUDIT_REMEDIATION_DEVICE_TESTS.md`](AUDIT_REMEDIATION_DEVICE_TESTS.md): the device test plan.
  None of it has been run.
- [`AUDIT_REMEDIATION_FINAL_REVIEW.md`](AUDIT_REMEDIATION_FINAL_REVIEW.md): independent review of
  the result.

## Status summary

| Status | Findings |
|---|---|
| FIXED | H2, H3, H4, M1, M2, M3, M4, M6, L5, L6, L7, L8, L9, L13; parts of M7, M9, L2, L11, L12 |
| DEVICE CHECK REQUIRED | H1, M5, L1, L3, L4; the file-access part of L2 |
| ALREADY FIXED | the HTML-rebuild part of M8 (PR #139) |
| INTENTIONAL | the fork pull request approval of L12 (a GitHub security setting) |
| DEFERRED — MAINTAINER DECISION | M8 (image rendering), L10, L14, L15, L16; parts of M7, M9, L11, L12 |
| NOT REPRODUCIBLE | none |

"Before" and "After" are measured values, or the observed behavior in a test. "NOT MEASURED"
means no measurement was possible without an Android device.

## Final matrix

| ID | Severity | Status | Root cause | Fix | Regression test | Before | After | Remaining risk |
|---|---|---|---|---|---|---|---|---|
| H1 | High | DEVICE CHECK REQUIRED | targetSdk 36 opts in to predictive back on Android 16; `androidx.activity` 1.0.0 does not forward it | `BackNavigation` registers a platform `OnBackInvokedCallback` (API 33+) that runs the existing Back logic (`cd5dadd`) | `BackNavigationTest` (static) | Back skips the toolbox and start-screen handling on Android 16 (per platform documentation) | Not observed on a device | Behavior on Android 16 unverified; no predictive back animation while the callback is registered |
| H2 | High | FIXED | `FileOutputStream`/`openFileOutput` truncate before writing | `AtomicFileWriter`: temporary file, `fsync`, rename; used for savegames, quicksave, cheat detection, world map files (`db01819`) | `AtomicFileWriterTest` (7) | Failed write destroys the previous save | Previous file intact on any failure (tests) | Rename atomicity is a file-system property; a real interrupted save on a device is device test 2.2 |
| H3 | High | FIXED | `copyTranslation` copied `.po`/`.pot` sources | `include '*.mo'`, `Sync` task (`d1c9d7c`) | CI `apk-report.sh --check` | APK 120,663,105 bytes; 57 unused translation files | 58,228,837 bytes (−51.7%); 0 unused files; all 54 `.mo` files present | None known |
| H4 | High | FIXED | Eager loading of every map image; first PR attempt had a script error | PR #139 merged unchanged (`e03bd3d`) | `WorldMapTemplateTest`, `WorldMapControllerTest`, browser tests in CI | 546 images requested before the page finished loading; 702 ms to load (browser bench) | 72 images; 112 ms; no visible image left unloaded in any scenario | Device timing NOT MEASURED; WebView versions older than the tested Chromium |
| M1 | Medium | FIXED | Content lookups unbox or dereference `null` for unknown ids | Unknown phrase returns `null` and ends the conversation; unknown ids in scripts and requirements are skipped and logged (`c6def08`) | `ConversationControllerContentErrorsTest` (4) | `NullPointerException` / `IllegalArgumentException` (tests fail on the old code) | No exception; conversation ends (release) or placeholder (debug) | Broken content is now silent except in the log |
| M2 | Medium | FIXED | Entry names used as paths without a check | Canonical-path containment check; `null` file name handled (`e231763`) | `AndroidStorageUnzipTest` (3) | `../savegame1` written outside the folder (test fails on the old code) | Import fails, nothing written outside | An archive with one bad entry is rejected as a whole, after extracting the entries before it |
| M3 | Medium | FIXED | Restore by id, reset by index | Restored areas are marked; the others are initialized (`df5bd57`) | `PredefinedMapSpawnAreaTest` (2) | Last restored area reset, inserted area not initialized (tests fail on the old code) | Saved state kept, new areas initialized | None known |
| M4 | Medium | FIXED | Parsers trust the file; only `IOException` caught | `RuntimeException` during parsing becomes `IOException`; maps reset after a failed parse; unknown current map rejected; checksum length checked (`ab9607d`) | `SavegameCorruptionTest` (4) | `ArrayIndexOutOfBoundsException`, `OutOfMemoryError`, leftover map state (tests fail on the old code) | `IOException` → "cannot load"; maps reset | Unknown-current-map check covered by review only; failures after parsing (e.g. in scripts) still crash |
| M5 | Medium | DEVICE CHECK REQUIRED | Whole map transition ran in `doInBackground` | Load in the background, apply on the UI thread (`a5de03d`) | None automated (needs Android) | Model changed concurrently with drawing | Model changed on the UI thread only | UI-thread time of the apply phase NOT MEASURED; unverified on a device |
| M6 | Medium | FIXED | Paths that report no result; errors not logged in release | `BackgroundWorker` guarantees one result; exceptions reported and logged in release; shared executor; temporary ZIPs deleted (`cafd5d3`, `df62621`) | `BackgroundWorkerTest` (4) | Thrown exception or missing result left the dialog open (tests fail on the old code) | Exactly one result in every case | `ContentResolver` paths verified by review only |
| M7 | Medium | FIXED (lookup); DEFERRED — MAINTAINER DECISION (lazy parsing, UI-thread loading) | Linear scan of all monster types per spawn area | Case-insensitive spawn-group index (`dc3360e`) | `MonsterTypeCollectionTest` (3), `SpawnGroupBenchmark` (6,675 real lookups identical) | 12,729,225 comparisons; 123–135 ms per launch (desktop JVM) | 2.8–2.9 ms including building the index | Device startup time NOT MEASURED; the larger startup costs remain |
| M8 | Medium | ALREADY FIXED (HTML rebuild); DEFERRED — MAINTAINER DECISION (image rendering) | Missing world map images rendered while loading | PR #139 batches the HTML rebuild | PR #139 tests | Not measured | Not measured | One-time delay after reinstall or import; options in the decisions document |
| M9 | Medium | FIXED (CI guard); DEFERRED — MAINTAINER DECISION (content edits) | Hand-edited content without validation | `check-content.sh` with reviewed baselines in CI (`f449951`) | CI job `content` | No content check | 721 known problems baselined; new ones fail CI (verified with a deliberate regression) | The three spawn-name mismatches and 143 unreachable requirements remain until content authors decide |
| L1 | Low | DEVICE CHECK REQUIRED | Provider configured with broad roots | Only `andors-trail/worldmap/` (`fb2c6ae`) | `FileProviderPathsTest` | Whole file system, external storage and app files shareable | World map folder only | If the path mapping were wrong, the world map would not load on Android 10+ (device test 3.6) |
| L2 | Low | FIXED (escaping); DEVICE CHECK REQUIRED (file access) | Names inserted as HTML; file access on all versions | `escapeHtmlText`; file access only before Android 10 (`37f11e6`) | `WorldMapControllerTest.areaNamesAreEscaped` | `<`/`&` in a translation broke the page | Escaped | File access change unverified on devices (tests 3.6, 3.7) |
| L3 | Low | DEVICE CHECK REQUIRED | No backup rules | Backup and data extraction rules exclude the world map cache and logs from cloud backup (`d745e95`) | `BackupRulesTest` | Cache counted against the 25 MB quota | Excluded (by configuration) | Unverified with `bmgr` (test 6.4) |
| L4 | Low | DEVICE CHECK REQUIRED | Permissions declared for all API levels | `maxSdkVersion` 29/32 (`6e3a71b`) | None (manifest) | Declared on all levels | Declared where the code uses them | Permission flows on Android 10–12 unverified (tests 6.1–6.3) |
| L5 | Low | FIXED | Wrong format specifiers in Latin | Specifiers fixed (`3a335d6`); CI guard (`f449951`) | CI `content` job; `String.format` check | `UnknownFormatConversionException`, `IllegalFormatConversionException` | Formats | None known |
| L6 | Low | FIXED | Resource-qualifier syntax used as a language tag | Region prefix `r` dropped when parsing (`8d25d25`) | `AndorsTrailApplicationTest` | `Locale("zh", "RCN")` | `zh_CN` | None known |
| L7 | Low | FIXED | `==` on strings; default-locale lowercasing of ids | `Objects.equals`; `Locale.ROOT` (`8d25d25`) | Review | Cache miss on every call; Turkish "ı" in legacy ids | Equal strings match; ids lowercased in ROOT | Deprecated `updateConfiguration` kept deliberately |
| L8 | Low | FIXED | No cleanup of debug log files | Keep the newest ten (`31daa5c`) | `AndorsTrailApplicationTest.onlyTheNewestLogFilesAreKept` | Unbounded | At most ten | Debug builds only |
| L9 | Low | FIXED | Wall clock used for intervals | `SystemClock.uptimeMillis()` (`324544b`) | Review | Ticks could be skipped or doubled after a clock change | Same clock as the `Handler` | None known |
| L10 | Low | DEFERRED — MAINTAINER DECISION | Scaled icon bitmap per list bind | Not changed; needs allocation profiling on a device | — | NOT MEASURED | NOT MEASURED | Allocation churn in long lists |
| L11 | Low | FIXED (keystore); DEFERRED — MAINTAINER DECISION (rest) | Build script drift | Duplicate keystore loading removed (`aa98a68`) | CI build | Loaded twice | Loaded once | Version in manifest, hand-wired tasks, no minification, 2019 AndroidX pins remain |
| L12 | Low | FIXED (lint, release build/tests, pinned actions, Node 24 upload, content/APK checks); DEFERRED — MAINTAINER DECISION (duplicate runs, Travis); INTENTIONAL (fork approval) | CI covered debug build and tests only | `69d1d31`, `788d487`, `f449951`, `d3784c7` | The workflow itself | Debug tests and APK | See "CI result" below | Double runs for same-repository PR branches remain |
| L13 | Low | FIXED | Few tests | 38 new unit tests from the remediation (8 more came with PR #139); both variants in CI; `CombatControllerTest` added (`4465b88`) | — | 11 tests | 57 tests | Android-only behavior covered by the device plan only |
| L14 | Low | DEFERRED — MAINTAINER DECISION | Content hygiene | Covered by the content baseline | CI `content` job | — | — | Edits are content decisions |
| L15 | Low | DEFERRED — MAINTAINER DECISION | Old editor with end-of-life libraries | Not changed | — | — | — | Only if the editor is hosted |
| L16 | Low | DEFERRED — MAINTAINER DECISION | Deprecated APIs | Not changed (no functional defect) | — | — | — | Future targetSdk changes |

## CI result

Final verification run: GitHub Actions run 36664371541 on `90a4f1d` (the last code change; later
commits change documents only). All three jobs passed:

| Job / step | Result |
|---|---|
| `build`: `testDebugUnitTest` (57 tests) and `testReleaseUnitTest` (57 tests) | passed |
| `build`: `assembleDebug`, `assembleRelease` (including `lintVitalRelease`) | passed |
| `build`: `lintDebug` against `app/lint-baseline.xml` | passed; no issue outside the 393 baselined ones |
| `build`: APK report `--check` | passed; debug APK 58,233,321 bytes, 2,432 entries, 0 unused translation files, 0 direct `Math.clamp` calls |
| `worldmap-page`: browser regression tests | passed |
| `content`: content and translation baselines | passed; 0 problems outside the baseline |

The local JVM harness also passes the 57 tests in both variants.
