# Andor's Trail project audit

- **Revision audited:** `0dfa1f6` (`master` of AndorsTrailRelease/andors-trail, identical to `master` of this fork), 29 September 2026.
- **Scope:** the Android app (`AndorsTrail/`, about 31,700 lines of Java in 214 files), the game content (JSON data, 1,296 TMX maps, world map), 53 translations, build and CI, and the auxiliary folders.
- **Method:**
  - Read the code paths named in each finding.
  - Ran a scripted cross-reference check of all content the engine loads ([`audit/check_content.py`](audit/check_content.py)).
  - Checked the format specifiers of every translated UI string ([`audit/check_translation_formats.py`](audit/check_translation_formats.py)).
  - Built and ran the unit tests on GitHub Actions.
  - Tested the world map page in headless Chromium with an Android phone viewport.
- **Not done:** running the app on a device or emulator. The audit environment had no access to the Android SDK. Findings marked *device check* are derived from the code and the platform documentation.

Verification labels used below:
- **Reproduced**: executed or measured during the audit.
- **Code**: confirmed by reading the whole code path.
- **Device check**: should be confirmed on a device before acting on it.

Paths below are relative to `AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/` unless they start with `AndorsTrail/`.

## Summary

| ID | Severity | Finding | Verification |
|----|----------|---------|--------------|
| H1 | High | System Back on Android 16 bypasses the game's Back handling (targetSdk 36) | Code, device check |
| H2 | High | Savegames are overwritten in place: a failed or interrupted write destroys the save | Code |
| H3 | High | The APK ships 224 MB of translation sources that are never read (about 44% of the APK) | Reproduced |
| H4 | High | World map opens slowly; the first PR attempt rendered it blank | Reproduced, fixed in [AndorsTrailRelease/andors-trail#139](https://github.com/AndorsTrailRelease/andors-trail/pull/139) |
| M1 | Medium | A reference to a missing dialogue phrase crashes the game (22 such references in content) | Code |
| M2 | Medium | Zip Slip in "Import world map" on Android 13 and older | Code |
| M3 | Medium | Spawn-area state is restored by ID but reset by index after loading | Code |
| M4 | Medium | Corrupted or imported savegames crash the app instead of failing to load | Code |
| M5 | Medium | Map transitions change the shared game model on a background thread | Code |
| M6 | Medium | Import/export failures are invisible and can leave the progress dialog open forever | Code |
| M7 | Medium | Every launch parses all 1,296 maps and makes about 12.7 million spawn-group comparisons | Code |
| M8 | Medium | First load of a save without world map cache renders every visited map synchronously | Code |
| M9 | Medium | Content: NPCs that never appear, unreachable dialogue branches | Reproduced (script) |
| L1–L16 | Low | Security hardening, localization, build/CI, tests, content hygiene, legacy tooling | See below |

## High

### H1. System Back on Android 16 bypasses the game's Back handling

- **Where:**
  - `AndorsTrail/app/build.gradle` sets `targetSdkVersion 36` and pins `androidx.activity:activity:1.0.0` "for API 17 support". `minSdkVersion` is already 21.
  - [`activity/MainActivity.java:248`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/activity/MainActivity.java#L248) overrides `onBackPressed()` to close the toolbox first.
  - [`activity/StartScreenActivity.java:44`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/activity/StartScreenActivity.java#L44) uses an `OnBackPressedCallback` to pop start-screen fragments and to ask "press Back again to exit".
  - The manifest does not set `android:enableOnBackInvokedCallback`.
- **What happens:** for apps targeting API 36, Android 16 enables predictive back by default. The system then no longer calls `onBackPressed()` and no longer dispatches `KEYCODE_BACK` for the system Back gesture or button. `androidx.activity` only routes system Back into `OnBackPressedDispatcher` from version 1.6 on.
- **Impact on Android 16 devices:**
  - Back in the game screen leaves `MainActivity` even when the toolbox is open.
  - Back on a start-screen sub-page (new game, and so on) closes the activity instead of returning to the menu.
  - The exit confirmation is skipped.

  Keyboard Escape and gamepad B keep working, because `ActivityKeyHandler` calls the handlers directly.
- **Fix:**
  - Short term: add `android:enableOnBackInvokedCallback="false"` to `<application>`.
  - Proper fix: update `androidx.activity`/`androidx.fragment` (API 21 allows current versions), make `MainActivity` a `ComponentActivity`, and register `OnBackPressedCallback`s that are enabled only while there is something to close.

### H2. Savegames are overwritten in place

- **Where:** [`savegames/Savegames.java:70-72`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/savegames/Savegames.java#L70-L72).
- **What happens:**
  - `getOutputFile()` opens `new FileOutputStream(slotFile)`, or `openFileOutput()` for the quicksave. Both truncate the existing save before the new bytes are written.
  - The in-memory buffer only protects against serialization errors (see the comment at line 63). If the write then fails (storage full, I/O error) or the process is killed, the old save is already gone and the new one is incomplete.
  - The stream is not closed when `write` throws.
  - The cheat-detection files are written the same way. A truncated one makes `triedToCheat` throw `EOFException`, so that save can no longer be loaded.
- **Impact:** permanent loss of the save. With limited saves, there is no second copy to go back to.
- **Fix:** write to a temporary file, call `fsync`, then rename. `android.util.AtomicFile` does exactly this and is available on all supported API levels.

### H3. The APK ships the translation sources

- **Where:** `copyTranslation` in `AndorsTrail/app/build.gradle` (lines 103-107) copies the whole `AndorsTrail/assets/translation/` folder into the assets. At runtime only `translation/<lang>.mo` is opened ([`resource/TranslationLoader.java:34`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/resource/TranslationLoader.java#L34)). The file name comes from `localize_resources_from_mo_filename`.
- **Measured:**

  | Files | Raw size | Deflated size |
  |-------|----------|---------------|
  | `.po` | 220.3 MB | 51.7 MB |
  | `english.pot` | 3.6 MB | — |
  | `.mo` (needed) | 53.2 MB | 18.6 MB |

  The debug APK built by CI is 118 MB, so the unused `.po` files are about 44% of it. `createMo.sh` is packaged too.
- **Fix:** add `include '*.mo'` to `copyTranslation`.

### H4. World map opens slowly; the first PR attempt rendered it blank

- **Where (master):**
  - The generated page references every visited map of the segment as an eagerly loaded `<img>`. `onPageFinished`, and with it the scroll to the player, waits until all of them have loaded. For `world1` that is up to 546 maps and about 18.5 megapixels.
  - On Android 10+ each image is a separate `content://` request through the `FileProvider`.
  - [`activity/DisplayWorldMapActivity.java:134-136`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/activity/DisplayWorldMapActivity.java#L134-L136) calls `loadUrl` before `setWebViewClient`.
  - `populateWorldMap` rebuilds the whole segment HTML after every map it renders ([`controller/WorldMapController.java:343-344`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/controller/WorldMapController.java#L343-L344)).
- **Measured in Chromium with a phone viewport (412×780):**
  - Master loads 546 images before `onPageFinished`.
  - `IntersectionObserver` and `loading="lazy"` still load about 440 of them. Because of `minimum-scale`, Chromium enlarges the layout viewport to about ten screens, and both observe that viewport.
  - The fix loads about 70 images around the player, and loads the rest when panning or zooming out.
- **Status:** fixed in [AndorsTrailRelease/andors-trail#139](https://github.com/AndorsTrailRelease/andors-trail/pull/139).
  - The PR also fixes an unescaped `"` in `worldmap_template.xml` that aapt2 silently removed. That quote made the page script a syntax error: no map images and the player marker in the top left corner.
  - It adds `WorldMapTemplateTest` to catch that class of error.
  - Its CI in the upstream repository waits for a maintainer to approve workflows from forks. It passes in this fork.

## Medium

### M1. A reference to a missing dialogue phrase crashes the game

- **Where:** [`resource/ConversationLoader.java:26`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/resource/ConversationLoader.java#L26) does `int resourceID = resourceIDsPerPhraseID.get(phraseID);`. For an unknown ID this unboxes `null` and throws `NullPointerException`. The debug fallback "phrase not implemented yet" in `ConversationController.setCurrentPhrase` is never reached.
- **Other content lookups that throw on a bad ID:**
  - `SkillID.valueOf` in `ConversationController.java:104`.
  - `findMapForScriptEffect` returning `null` (line 194) for a misspelled map name.
  - `getDropList(id).createRandomLoot` (line 326).
- **Content today:** 22 references to phrases that do not exist: 14 NPC `phraseID`s, 5 dialogue replies and 3 key areas. None of the ones traced is reachable in normal play:
  - The sirens on `mountainlake21` cannot be reached on foot (checked by flood fill over the walkable layer).
  - `ll2_circe_55` is shadowed by an earlier branch of Circe's dialogue.
  - The `dummykey` key areas require `andor:1`, which is set at the start of the game.

  So no crash was found in current content, but nothing protects future content.
- **Fix:** make `loadPhrase` return `null` for unknown IDs and end the conversation gracefully. Run `audit/check_content.py` (or an equivalent) in CI.

### M2. Zip Slip in "Import world map"

- **Where:** [`util/AndroidStorage.java:229-249`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/util/AndroidStorage.java#L229-L249) builds `new File(targetDirectory, entry.getName())` without checking the resulting path. It is reached from `LoadSaveActivity.importWorldmap`.
- **Impact:** a crafted ZIP, for example one shared as a "world map backup", with entries like `../savegame1` overwrites savegames or cheat-detection files. Before scoped storage it can also overwrite other files the app can write on external storage. Android 14+ rejects `..` entry names for apps targeting API 34+, so the exposure is devices on Android 13 and older.
- **Related:** `chosenZip.getName()` can be `null` (`LoadSaveActivity.importWorldmap`), which throws `NullPointerException`.
- **Fix:** compare canonical paths against `targetDirectory`'s canonical path plus the separator, and reject entry names that contain a path separator or `..`.

### M3. Spawn-area state is restored by ID but reset by index

- **Where:** [`model/map/PredefinedMap.java:279`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/model/map/PredefinedMap.java#L279) matches saved spawn areas by ID. Line [377](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/model/map/PredefinedMap.java#L377) then resets every area with index ≥ the saved count.
- **Impact:** suppose an update inserts a spawn area before existing ones in a map the player has visited. After loading, the last saved areas are reset:
  - Killed unique monsters respawn (`spawnAllInArea(..., true)`).
  - Areas switched off by quests become active again.
- **Related:** 303 spawn-area names are duplicated in 153 maps, although the content format reference says they must be unique. Because matching starts at index `i`, duplicates only restore correctly while the map layout stays the same.
- **Fix:** keep a `boolean[] loaded` and initialize only the areas that were not loaded.

### M4. Corrupted or imported savegames crash the app

- **Where:**
  - `Savegames.loadWorld(..., slot)` catches only `IOException` and `DigestException` (`Savegames.java:117`, `:140`).
  - The scene loader `AsyncTask` in `WorldSetup.java:111-118` catches nothing.
- **Examples:** parsing trusts counts read from the file. `Inventory.readFromParcel` writes `wear[i]` for a `numWornSlots` taken from the file (`ArrayIndexOutOfBoundsException` if it is larger than the array). Unknown item IDs become `null` item types that fail later.
- **Impact:** a damaged save, or one from a modified build, kills the process instead of showing "cannot load".
- **Fix:** catch `RuntimeException` around parsing and return `unknownError`. Validate counts before allocating.

### M5. Map transitions change the shared game model on a background thread

- **Where:** `MovementController.placePlayerAsyncAt` runs `placePlayerAt` in `AsyncTask.doInBackground` (`controller/MovementController.java:55`). That path:
  - reads the TMX file and loads tiles;
  - spawns monsters and runs map scripts;
  - replaces `world.model.currentMaps`;
  - starts another `AsyncTask` (`WorldMapController.updateWorldMap`) from the background thread.

  Meanwhile the UI thread keeps handling input and redrawing `MainView` from the same objects, without a lock.
- **Impact:** rare drawing glitches or `ConcurrentModificationException`. If the transition throws, only a debug log is written, and the player position may already point into the new map while `currentMaps` still holds the old one.
- **Fix:** load files and tiles in the background, then apply the state change on the UI thread. Restore the previous position on failure.

### M6. Import/export failures are invisible, and the progress dialog can hang

- **Where:**
  - `copyDocumentFilesFromToAsync` and `copyDocumentFilesToDirAsync` catch `NullPointerException` and do nothing unless the user cancelled ([`util/AndroidStorage.java:356`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/util/AndroidStorage.java#L356), [`:399`](AndorsTrail/app/src/main/java/com/gpl/rpg/AndorsTrail/util/AndroidStorage.java#L399)). Neither `onFailure` nor `onComplete` runs, so the progress dialog never closes.
  - `ContentResolver.openOutputStream()` may return `null`, which leads to exactly that `NullPointerException`.
  - `onFailure` (line 445) discards the exception.
  - All logging in `util/L.java` is disabled in release builds, and there is no crash reporting.
- **Related:**
  - `BackgroundWorker.run()` creates a new executor for each operation and never shuts it down (`util/BackgroundWorker.java:38`).
  - `cancelled` is not `volatile`.
  - The temporary ZIP from `createZipDocumentFileFromFilesAsync` (line 138) is never deleted.
- **Fix:**
  - Always report the result.
  - Log errors with `Log.w` in release builds too.
  - Use one shared executor.
  - Delete temporary files.

### M7. Every launch parses all maps; spawn-group lookups are quadratic

- **Where:**
  - `ResourceLoader.loadResourcesAsync` reads all 1,296 TMX maps at every launch.
  - For each of the 6,675 spawn areas, `TMXMapTranslator.java:142` calls `MonsterTypeCollection.getMonsterTypesFromSpawnGroup` (line 22). Each call scans all 1,907 monster types with `equalsIgnoreCase`, about 12.7 million comparisons per launch.
  - The first part of resource loading (tilesets, skills, item categories, actor conditions) runs synchronously on the UI thread (`activity/StartScreenActivity.java:130`).
- **Fix:** build a lowercase `spawnGroup → types` map once. Measure startup on a low-end device before deciding on lazy map parsing.

### M8. First load without world map cache renders every visited map during loading

- **Where:** `Savegames.loadWorld` calls `WorldMapController.populateWorldMap` (`controller/WorldMapController.java:320`). For each visited map without an image, it parses the TMX, loads the tiles, renders and compresses a PNG. On master it also rebuilds the segment HTML after every image.
- **When it happens:** after a reinstall, on a new device, after importing saves without `worldmap.zip`, and in the separate `.dev` debug app.
- **Status:** PR #139 batches the HTML rebuild. The images are still rendered synchronously while the savegame loads.
- **Fix:** render missing images in the background after the scene is ready.

### M9. Content: NPCs that never appear, unreachable branches

These come from [`audit/content-report.txt`](audit/content-report.txt), produced by `audit/check_content.py`.

- **Spawn areas that are inactive by default and never activated, because the script uses a different name:**

  | Map | Area in the map | Script | Name the script spawns |
  |-----|-----------------|--------|------------------------|
  | `laerothprison4` | `prisoner2i`, `prisoner3i`, `prisoner4i` | `lae_demon4` | `lae_prisoner2i/3i/4i` |
  | `mountainlake_circe` | `ll2_circe_crew` | `ll2_circe_46` | `ll2_crew` |
  | `graveyard1` | `graveyard_corpse_boss_patrol` | `graveyardwall_2` | `graveyard_corpse_boss_kill` |

  The monster `ll2_circe_crew` also has a missing phrase (`ll2_circe_crew`). Fixing only the spawn name would turn talking to the crew into a crash (M1).
- **Other script effects:** 116 spawn and object-group effects name areas or groups that do not exist in the target map. Most are harmless extras, for example `burhczyd15`–`22` in `crossglen_hall`.
- **Quest-flag requirements never set:** 143 non-negated requirements test quest stages that no reward ever sets (41 distinct stages in 24 quests), so those branches can never be taken.
  - Some are intentional: `not_yet_realized`, and `scores`, which is checked by the debug phrases `dbg_scores*`.
  - Others look like typos: `guynmart:190444`, `brv_employee:1`, `brv_school2:12`.
- **Timers never created:** 3 `timerElapsed` requirements use timers that no reward creates (`brightport_hide`, `Brightport_package`).
- **Items that do not exist:** 5 requirements name nonexistent items (`ratdom_compass_3/4/5`, `oegyth_crystal`, `ring_mikhail_DISABLED`). These are always false.
- **Missing layers:** 2 replace areas use layers that do not exist (`crossglen_cave`/`ratdom_ground`, `brightport_bakery`/`oven`).
- **Missing places:** 2 exits lead to places that do not exist (`stoutford_castle_barrack2`/`base_beamto_*`). The dialogue that would enable them is disabled.
- **Stages without log entries:** 10 quest stages are set by dialogue but have no quest-log entry, so there is no log text and no XP.

## Low

### L1. The FileProvider shares more than it needs

[`AndorsTrail/res/xml/fileprovider.xml`](AndorsTrail/res/xml/fileprovider.xml) exposes `root-path` and `external-path` with `path="."`. The provider is not exported and the app never grants URIs, so this is least-privilege hardening. Limit it to `external-files-path` `worldmap/`.

### L2. WebView hardening

`DisplayWorldMapActivity` enables JavaScript and file access (lines 50 and 74). Named-area names from translations are inserted into the page without HTML escaping (`controller/WorldMapController.java:254`). A translation containing `<` or `&` breaks the page. Escape the text.

### L3. Backups can exceed the Auto Backup quota

`android:allowBackup="true"` has no `dataExtractionRules` or `fullBackupContent`. Auto Backup includes `getExternalFilesDir()`, which holds the regenerable world map cache and, in debug builds, logcat files. When the app's data exceeds the 25 MB quota, Android skips the backup entirely. Exclude `worldmap/` and `log/`.

### L4. Storage permission for all API levels

`WRITE_EXTERNAL_STORAGE` has no `android:maxSdkVersion`, although the comment says it is only needed up to Android 12. `requestLegacyExternalStorage` is ignored for targetSdk ≥ 30.

### L5. Two Latin strings throw when formatted

Reproduced with `java.util.Formatter`:

| String | Latin text | Exception |
|--------|------------|-----------|
| `dialog_loot_pickedupitems` | `%1$D` | `UnknownFormatConversionException` |
| `iteminfo_effect_critical_multiplier` | `%1$,d.1f`, applied to a float | `IllegalFormatConversionException` |

Both are in `AndorsTrail/res/values-la/strings.xml`. Latin is not in the in-game language list, so this is only reachable with a Latin system locale. Separately, 13 translations drop an argument, which does not crash.

### L6. Chinese language code uses resource-qualifier syntax

The in-game language value `zh-rCN` (`AndorsTrail/res/values/arrays.xml:182`) becomes `Locale("zh", "RCN")`. It then only works through Android's fuzzy locale matching. Use `zh-CN`.

### L7. Locale handling

- `AndorsTrailApplication.setLocale` compares strings with `==` (line 126).
- It uses the deprecated `Configuration.locale` and `updateConfiguration` (lines 137-139).
- The `DefaultLocale` lint check is suppressed. `toLowerCase()` without a locale in `model/actor/Monster.java:112` breaks legacy monster IDs with Turkish or Azerbaijani system locales. That path only affects savegames from before version 20.

### L8. Debug builds never clean up their logcat files

On every start, debug builds run `logcat -c` and then `logcat -f <external>/log/logcatNNN.txt` (`AndorsTrailApplication.java:189-191`). A new file and process are created each time, and they are never cleaned up.

### L9. Game timer uses the wall clock

`util/TimedMessageTask.java` uses `System.currentTimeMillis()`, which can jump. Use `SystemClock.uptimeMillis()`.

### L10. Icons allocate a new bitmap on every bind

The `TileManager` icon helpers (lines 190-316) create a new scaled `Bitmap` every time a list row is bound. This produces allocation churn in long inventories.

### L11. Build script

- The keystore properties are loaded twice (`AndorsTrail/app/build.gradle:11-19`).
- `proguard-rules.txt` is referenced but missing.
- Release builds are neither minified nor resource-shrunk.
- `versionCode` and `versionName` live in `AndroidManifest.xml`. The savegame format version is `BuildConfig.VERSION_CODE` (`AndorsTrailApplication.java:47`). Set both in `defaultConfig` so the value is explicit.
- `afterEvaluate` wires about 25 AGP task names by hand. A renamed task in a future AGP version breaks the build.
- The androidx libraries are pinned to 2019 versions "for API 17" although minSdk is 21, plus the `legacy-support-v4` umbrella library.

### L12. CI

The workflow validates the Gradle wrapper and uses `contents: read`. Gaps:
- No `lint` run and no release build.
- The logs warn that `actions/upload-artifact@v5` runs on the deprecated Node 20.
- Actions are pinned to major tags, not commit SHAs.
- Pushes to PR branches in the same repository run twice (`push` and `pull_request`).
- Pull requests from forks need "Approve and run" by a maintainer.
- `.travis.yml` and `travis/` target an end-of-life service and Debian stretch.

### L13. Tests

- There are 11 unit tests: `GameRoundControllerTest` (7) and `LocalizedNumberFormatterTest` (4).
- `CombatController.java:541` asks to run `CombatControllerTest`, which does not exist.
- Content is only validated at runtime in debug builds (`DEVELOPMENT_VALIDATEDATA`), not in CI.

### L14. Content hygiene

- 101 exits lead to Ratdom maze maps that do not exist. 91 are walled off. The other 10 are on walkable tiles; stepping on them does nothing, and the engine logs "Cannot find map".
- 9 map objects extend outside their map.
- `lakecave1` is in two world map segments.
- `itemlist_pre0610_unused.json` is never loaded.
- `monsters_newb_2` and `monsters_newb_4` are declared with grids that do not match their images; both are unused.
- `items_japozero.png` and `map_items_japozero.png` are identical (1.1 MB).
- 152 phrases are never referenced.

### L15. Legacy tooling

`AndorsTrailEdit/` bundles AngularJS 1.0.3 (2012) and jQuery 1.8.3/1.9.0. Both are end of life, with known XSS issues. The editor is fine as a local tool but should not be hosted; it has been superseded by ATCS. `AndorsTrail/import-summary.txt` is a leftover from the Eclipse import.

### L16. Deprecated APIs

- `AsyncTask` (5 uses).
- `Resources.getDrawable(int)` (34).
- `startActivityForResult` (16).
- `android.preference` (3).
- `WebView.getScale()` (2).
- Implicit-looper `Handler()` (4).

None is urgent, but they add up once targetSdk moves on.

## Positive observations

- Savegames carry a checksum. I/O errors while loading become a "cannot load" result, not a crash.
- The MediaStore export uses `IS_PENDING` and removes partial entries on failure.
- Tiles are decoded with `inScaled = false` and cached. The pause-reason model of `GameRoundController` has unit tests.
- CI validates the Gradle wrapper and runs with read-only permissions.
- Debug and beta builds validate content references at runtime.

## Suggested order of work

1. Merge [AndorsTrailRelease/andors-trail#139](https://github.com/AndorsTrailRelease/andors-trail/pull/139) (H4).
2. `copyTranslation { include '*.mo' }` (H3). This is a one-line change that saves about 52 MB.
3. Opt out of predictive back, or migrate the Back handling (H1).
4. Write savegames with `AtomicFile` (H2).
5. Make `ConversationLoader.loadPhrase` null-safe, and run `audit/check_content.py` in CI (M1, M9). Then fix the three spawn-name typos in M9 together with the missing `ll2_circe_crew` phrase.
6. Add a canonical-path check to the ZIP import (M2).
7. Fix the spawn-area reset loop (M3). Catch `RuntimeException` while loading savegames (M4).
8. Index spawn groups once (M7). Move world map image rendering out of savegame loading (M8).
9. Always report import/export results, and keep error logging in release builds (M6).
10. The low-severity items, starting with L1, L3 and L11.

## Reproducing the scripted checks

```sh
python3 audit/check_content.py > audit/content-report.txt
python3 audit/check_translation_formats.py > audit/translation-format-report.txt
```

Both scripts use only the Python standard library and read `AndorsTrail/` next to the `audit/` folder. The reports in this folder were produced from revision `0dfa1f6`.
