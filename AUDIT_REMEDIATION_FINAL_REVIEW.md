# Audit remediation: final review

A review of the finished remediation, done after all fixes. It covers the complete diff
`e03bd3d..audit/remediation` (the merge of PR #139 up to the last commit): production code, tests,
build and CI files, tools and documents. The questions were whether each change does what it
claims, whether anything else changed, and what remains unverified.

## Method

1. Re-read the complete production diff (28 files) with the audit finding next to it.
2. Checked every added line mechanically:
   - debug output, disabled tests, `TODO`s;
   - empty `catch` blocks;
   - non-English text;
   - whitespace errors;
   - untracked files.
3. Re-ran every new regression test against the unfixed code where that was possible (mutation runs;
   see `AUDIT_REMEDIATION_TESTING.md`).
4. Ran the unit tests locally in both variants (`jvm-tests-without-sdk.sh`, debug and
   `BUILD_TYPE=release`), and all jobs in CI.

## Results of the mechanical checks

| Check | Result |
|---|---|
| Files changed | 56 (28 production and build files, the rest tests, CI, tools and documents) |
| `System.out`, `printStackTrace`, `Thread.sleep` in added lines | Only where intended: the JVM-test fallback in `util/L.java`, a 100 ms wait for late results in `BackgroundWorkerTest`, and the output of `SpawnGroupBenchmark` (a command-line tool) |
| `@Ignore`, disabled or weakened tests | None. The two `GameRoundControllerTest` assertion tests were extended to check release builds as well, not relaxed. |
| Empty `catch` blocks added | None |
| Exceptions swallowed | None added. Two existing swallowed `NullPointerException`s now report the failure (M6). |
| Non-English text in added lines | None (checked for Cyrillic across the whole diff) |
| `git diff --check` | Clean |
| Unrelated formatting changes | None. The re-indentation in `AndroidStorage` is the `try` that now covers the temporary file (M6). |
| New dependencies | None in the app. CI installs Playwright 1.56.1 for the browser tests only. |

## Problems found during this review, and fixed

| Problem | Fix |
|---|---|
| Escaping a `null` area name would have thrown, where `append(null)` wrote "null" | `escapeHtmlText(null)` returns `""` (`8d6ef2f`) |
| `testReleaseUnitTest` did not exist under AGP 9, so release code paths were untested | `android.onlyEnableUnitTestForTheTestedBuildType=false` (`788d487`) |
| Once enabled, two upstream tests failed in the release variant (they assumed debug assertions) | Tests check both variants (`58a1848`) |
| `lintDebug` ran out of Gradle heap before reporting | 4 GB heap for the CI lint step only (`abaa5a4`) |
| `SavegameCorruptionTest` passed locally but failed in CI (`SparseIntArray` in the mockable `android.jar`) | Fixture rebuilt without `Player` serialization (`85bbc8e`); the harness documents the difference |
| Metrics document claimed "at most 11 comparisons" for a red-black tree | Corrected (`bcd3231`) |
| Test counts attributed PR #139's tests to the remediation | Corrected in the documents |

## Observations outside the audit (not changed)

- `MonsterMovementController` calls `Math.clamp` (Java 21, Android API 35) with minSdk 21. D8
  backports it: the CI dex check finds 0 direct calls. `apk-report.sh --check` fails if that ever
  changes.
- `ChecksumBuilder.add(double)` reserves 4 bytes for an 8-byte value. It is never called (the model
  has no `double` fields), so it cannot fail today.
- The `fileproviderPath` manifest placeholder in `app/build.gradle` is not used by the manifest.
- `requestLegacyExternalStorage` has no effect for targetSdk 30+ on Android 11+; it is harmless
  and still used on Android 10.

## What remains unverified

- **No test ran on an Android device or emulator.** The following are verified only by review,
  static tests and compilation:
  - H1 Back handling on Android 16;
  - M5 transition threading;
  - L1 FileProvider path;
  - L2 WebView file access;
  - L3 backup rules;
  - L4 permission levels;
  - the `ContentResolver` paths of M6.

  The device plan (`AUDIT_REMEDIATION_DEVICE_TESTS.md`) lists each test; all are NOT RUN.
- No timing on a device: world map opening, startup, map transitions (NOT MEASURED).
- The world map page was tested in desktop Chromium 141. Old Android WebViews (Chrome < 61, no
  `visualViewport`) were simulated only by removing `window.visualViewport`.

## Residual risks, by likelihood

1. **L1 FileProvider path.** If the `external-files-path` mapping behaves differently from the
   analysis on some device, the world map page does not load on Android 10+. Mitigation: device
   test 3.6 before release. The fix is one XML line to revert.
2. **M5 apply phase on the UI thread.** Spawning and scripts now run on the UI thread during a
   transition; on a slow device this could add a visible pause. Mitigation: device test 5.3.
3. **H1 on Android 16.** It follows the platform documentation, but was not observed.
4. **Error logging in release builds (M6).** `L.error` now reaches logcat in release builds.
   Messages contain file names and exception text, no savegame content.

## CI result

Pending: filled in from the final CI run.

## Proposed upstream pull requests

For `AndorsTrailRelease/andors-trail`, in this order. Each can be reviewed and merged on its own,
except where a dependency is named. The fork's audit tooling (`audit/`, the local JVM harness) is
not proposed upstream; the content check could move to the project's tools if the maintainers want
it.

| PR | Content | Findings | Commits | Before merging |
|---|---|---|---|---|
| 1 | World map loading (already open as #139) plus its browser tests | H4 | `c178095`, `9362ef0`, `3ed9237`, `9cfa38b` | Device test 3.x |
| 2 | Logging usable in JVM tests, errors logged in release | M6 (logging) | `0a76a77`, `cafd5d3` | none; later PRs' tests depend on it |
| 3 | Savegame robustness: atomic writes, damaged saves, spawn areas | H2, M4, M3 | `db01819`, `ab9607d`, `85bbc8e`, `df5bd57` | Device tests 2.x |
| 4 | Android 16 Back | H1 | `cd5dadd` | Device tests 1.x on Android 16 |
| 5 | APK size | H3 | `d1c9d7c` (+ `d3784c7`, `76c6dad` for the CI report) | none |
| 6 | Content robustness and Latin strings | M1, L5 | `c6def08`, `3a335d6` | none |
| 7 | Import/export: Zip Slip and reliable results | M2, M6 | `e231763`, `df62621` | Device tests 4.x |
| 8 | Spawn group index | M7 | `dc3360e` | none |
| 9 | Map transitions on the UI thread | M5 | `a5de03d` | Device tests 5.1–5.3 |
| 10 | Manifest and WebView hardening | L1, L2, L3, L4 | `fb2c6ae`, `37f11e6`, `8d6ef2f`, `d745e95`, `6e3a71b` | Device tests 3.6, 3.7, 6.x |
| 11 | Small fixes | L6, L7, L8, L9, L11 | `8d25d25`, `31daa5c`, `324544b`, `aa98a68` | none |
| 12 | Tests and CI | L12, L13 | `4465b88`, `58a1848`, `788d487`, `69d1d31`, `abaa5a4`, `f449951` (content check, if wanted) | none |
