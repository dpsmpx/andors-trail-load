# Audit remediation: testing

What was tested, where, and with which result. Device tests are in
`AUDIT_REMEDIATION_DEVICE_TESTS.md`; none of them has been run.

## Environments

| Environment | Used for |
|---|---|
| **CI**: GitHub Actions `ubuntu-latest`, Temurin JDK 17, Gradle 9.6.1, AGP 9.3.1, workflow `.github/workflows/android.yml` | The authoritative build: unit tests (debug and release), debug and release APKs, lint, APK report, world map browser tests, content checks |
| **Local JVM harness**: `audit/remediation/scripts/jvm-tests-without-sdk.sh`, OpenJDK 21, Robolectric `android-all` 14 instead of the SDK, no Gradle | Fast local runs of the same unit tests, debug (default) and release (`BUILD_TYPE=release`) variants; mutation checks |
| **Browser**: Chromium 141 through Playwright 1.56.1, phone viewport | World map page tests (`audit/remediation/worldmap/`) |

The harness differs from Gradle in one way that matters (documented in the script): `android-all`
has working framework classes such as `android.util.SparseIntArray`, while every method of AGP's
mockable `android.jar` throws. A test that passes locally can therefore still fail in CI; this
happened once (`SavegameCorruptionTest`, fixed in `85bbc8e`). CI is the reference.

## Automated checks in CI

| Job / step | What it checks | Fails when |
|---|---|---|
| `build` → Build and test | `testDebugUnitTest testReleaseUnitTest assembleDebug assembleRelease` | A unit test fails in either variant, or a variant does not build |
| `build` → Lint | `lintDebug` with `app/lint.xml` and `app/lint-baseline.xml` (393 existing issues: 112 errors, 281 warnings) | Lint reports an issue that is not in the baseline |
| `build` → APK report | `apk-report.sh --check` on the debug APK | A translation file other than `.mo` is packaged, or dex calls `Math.clamp` directly (API 35) |
| `worldmap-page` | `build_pages.py` + `scenarios.js` in Chromium | The world map page loads too many images early, leaves visible images unloaded, has script errors, or its fallback fails |
| `content` | `check-content.sh` | Content or translation problems appear that are not in the reviewed baseline |

## Unit tests

57 tests in 16 classes. At the audited revision there were 11 in 2 classes; PR #139 added 8 and the remediation 38.

| Test class | Tests | Finding | What it proves | Without the fix |
|---|---:|---|---|---|
| `AtomicFileWriterTest` | 7 | H2 | Replacement is all-or-nothing on I/O errors, runtime errors, interrupted writes, failed renames | new class; the old code wrote in place |
| `BackNavigationTest` | 1 | H1 | Every `onBackPressed()` override and `OnBackPressedCallback` is registered with `BackNavigation` | fails (mutation run: registration removed from `MainActivity`) |
| `WorldMapTemplateTest`, `WorldMapControllerTest` | 2 + 7 | H4, L2 | Template script, cache versioning (PR #139); area names are escaped | escaping: new helper |
| `ConversationControllerContentErrorsTest` | 4 | M1 | Unknown phrase and 12 kinds of unknown ids do not throw; debug and release behavior | all 4 fail (mutation run: pre-fix code restored) |
| `AndroidStorageUnzipTest` | 3 | M2 | Real ZIP files: valid export extracted, `..` entries rejected, nothing written outside | fails (mutation run: pre-fix code restored) |
| `PredefinedMapSpawnAreaTest` | 2 | M3 | Inserted and duplicate spawn areas are restored and initialized correctly | both fail (mutation run: pre-fix code restored) |
| `SavegameCorruptionTest` | 4 | M4 | Truncated file, too many spawn areas, huge checksum length fail as `IOException`; maps are reset | 3 of 4 fail (mutation run); the fourth guards the fixture |
| `BackgroundWorkerTest` | 4 | M6 | Exactly one result: thrown exception, missing result, late results | 3 of 4 fail (mutation run); the fourth is the normal case |
| `MonsterTypeCollectionTest` | 3 | M7 | Spawn group index returns what the linear scan returned, in the same order | 3 of 3 fail with a case-sensitive index (mutation run) |
| `FileProviderPathsTest` | 1 | L1 | The provider shares exactly the world map folder the code writes to | fails by construction: the old file had four roots |
| `BackupRulesTest` | 1 | L3 | Backup rules exclude exactly the world map cache and log folders | new files |
| `AndorsTrailApplicationTest` | 3 | L6, L8 | Language values, `zh-rCN`; old log files are deleted | `zh-rCN` fails by construction (old result `Locale("zh", "RCN")`); log cleanup is new |
| `CombatControllerTest` | 4 | L13 | Average damage per hit: range, resistance, criticals, critical immunity | pins existing behavior; the test did not exist |
| `GameRoundControllerTest` | 7 | (upstream) | Pause handling; the two assertion tests now also check release behavior | |
| `LocalizedNumberFormatterTest` | 4 | (upstream) | unchanged | |

"Mutation run" means that the fix was reverted or broken locally and the harness was run. The named
tests failed, and then the fix was restored. "By construction" means the failure follows from the
old code but was not run.

## Other checks

| Check | Command | Result |
|---|---|---|
| World map browser tests | `build_pages.py` + `scenarios.js` | Pass on the current template. Fail on the template of the first PR attempt (`c178095`). |
| Content and translation baselines | `audit/remediation/scripts/check-content.sh` | 721 content problems and 2 translation problems (arguments dropped, no crash), all in the baseline. Restoring the two broken Latin strings makes the check fail with three new problems. |
| Latin format strings | `String.format` with the old and new strings | Old: `UnknownFormatConversionException`, `IllegalFormatConversionException`. New: formatted. |
| Spawn group benchmark | `audit/remediation/scripts/spawn-group-benchmark.sh` | Same result for all 6,675 real lookups; timings in `AUDIT_REMEDIATION_METRICS.md` |
| APK contents | `apk-report.sh` in CI | See `AUDIT_REMEDIATION_METRICS.md` |

## Not covered by automated tests

| Change | Why not | Covered by |
|---|---|---|
| H1 behavior on Android 16 | Needs the platform back dispatcher | Device tests 1.x |
| M4 unknown current map | Needs a serialized `Player` (`SparseIntArray`) | Review; device test 2.6 |
| M5 transition split | Needs `AsyncTask`, `Resources` and TMX parsing | Review; device tests 5.1–5.3 |
| M6 `ContentResolver` null streams, temp ZIP deletion | Needs a `ContentResolver` | Review; device tests 4.x |
| L2 WebView file access, L1 content:// resolution | Needs a WebView and a FileProvider | Device tests 3.6, 3.7 |
| L3 backup rules, L4 permission levels | Need the backup service and the package manager | Static tests of the paths; device tests 6.x |
| L7 `Locale.ROOT` for pre-version-20 monster ids, L9 uptime clock | One-line changes; `SystemClock` is not available in JVM tests | Review |

## Results

Final CI run 36664371541 on `90a4f1d`: every job and step passed (details in
`AUDIT_REMEDIATION.md`). Locally: `jvm-tests-without-sdk.sh`, 57 tests passed in the debug and the
release variant.
