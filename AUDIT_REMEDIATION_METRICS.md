# Audit remediation metrics

Measured values only. Where a value could not be measured in the available environment it is
marked **NOT MEASURED**, with the environment it would need.

Environments used:

- **CI:** GitHub Actions `ubuntu-latest`, JDK 17, Gradle 9.6.1, AGP 9.3.1 (workflow `.github/workflows/android.yml`).
- **Browser bench:** Chromium 141.0.7390.37 (Playwright 1.56.1), headless, phone viewport 412×780 at
  2.625 device pixels per CSS pixel, mobile viewport rules, pages loaded from `file://` on a
  desktop-class CPU. Absolute timings are not device timings; image counts are exact.
- **JVM bench:** OpenJDK 21 on the audit container (4 cores). Timings are desktop timings.

No measurement was taken on an Android device or emulator: none was available.

## APK size (H3)

Debug APK built by CI, reported by `audit/remediation/scripts/apk-report.sh`.

| | Before (`db01819`) | After (`d1c9d7c`) | Change |
|---|---:|---:|---:|
| APK file size | 120,663,105 bytes | 58,228,837 bytes | −62,434,268 bytes (−51.7%) |
| Entries | 2,487 | 2,430 | −57 |
| `assets/translation` stored bytes | 84,527,778 | 22,104,490 | −62,423,288 |
| Translation files not read at runtime (`.po`, `.pot`, `.sh`) | 57 | 0 | −57 |
| `.po` stored bytes | 61,508,576 | 0 | |
| `english.pot` stored bytes | 914,412 | 0 | |
| `.mo` files (read at runtime) | 54 (22,104,490 bytes) | 54 (22,104,490 bytes) | unchanged |
| Direct calls to `java.lang.Math.clamp` in dex | 0 | 0 | |

The CI job now fails when a translation file other than `.mo` reaches the APK.

## World map page (H4)

Browser bench, `audit/remediation/worldmap/scenarios.js`, segment `world1` with all 546 maps
visited (the late-game worst case), player in Fallhaven. "Before" uses the template of `0dfa1f6`
with eager `<img src>` markup (`build_pages.py --template <0dfa1f6 template> --markup eager`,
`scenarios.js --allow-eager`).

| | Before (`0dfa1f6`) | After |
|---|---:|---:|
| Images requested before `onPageFinished` | 546 | 72 |
| Time to the load event (`onPageFinished`), browser bench | 702 ms | 112 ms |
| Images loaded after centering on the player | 546 | 72 |
| Images loaded after panning to Crossglen | 546 | 98 |
| Images loaded when zoomed out to the minimum scale (all visible) | 546 | 546 |
| Visible images not loaded, in every scenario | 0 | 0 |

For comparison, the IntersectionObserver version of the first PR commit, with its syntax error
fixed, loaded 440 of 546 images (measured during the audit with the same bench).

On a device, each image of the "before" page is additionally a `content://` request through the
`FileProvider` (Android 10+). Device timings: **NOT MEASURED** (needs an Android 10+ device with a
late-game save; see `AUDIT_REMEDIATION_DEVICE_TESTS.md`).
