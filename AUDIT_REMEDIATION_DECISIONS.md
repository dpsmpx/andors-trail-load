# Audit remediation decisions

One entry per significant behavioral or architectural change. Finding ids refer to
[`AUDIT.md`](AUDIT.md).

## H2. Savegame persistence

**Finding:** H2, savegames overwritten in place.

**Problem:** a failed or interrupted write destroyed the previous save. A truncated
cheat-detection file made the save impossible to load.

**Root cause:** `new FileOutputStream(file)` and `Context.openFileOutput()` truncate the target
before the new bytes are written. The in-memory buffer in `Savegames.saveWorld` only protected
against serialization errors.

**Chosen solution:** `util/AtomicFileWriter` writes to `<target>.tmp` next to the target, flushes,
calls `FileDescriptor.sync()`, then renames the temporary file over the target. On any failure it
deletes the temporary file and leaves the target untouched. It is used for:
- savegames and the quicksave;
- both cheat-detection files and the cheat-detection backup;
- world map images, segment HTML and population markers.

**Alternatives considered:**
- `android.util.AtomicFile`. It works the same way, but cannot be tested on the JVM. It also keeps
  a `.bak` file that every reader has to know about.
- Writing a backup copy before overwriting. This needs recovery logic on the read side, and a
  crash between the steps still loses data.

**Why this solution:** `rename(2)` replaces the target atomically on the file systems Android uses
for app storage (ext4, f2fs, and FUSE over them). The helper is 30 lines of plain Java with no
Android dependency, so it can be unit tested.

**Trade-offs:**
- A save now needs free space for one extra copy while it is written. Savegames are a few hundred
  KB.
- If a rename ever fails, the save reports failure instead of falling back to a non-atomic path.
- A process killed mid-write can leave a `savegameN.tmp` file. It is not a slot, because
  `Savegames.getUsedSavegameSlots` matches `savegame(\d+)` exactly, and the next save of that slot
  overwrites it.

**Regression protection:** `AtomicFileWriterTest` (7 tests). They cover a new file, replacement
with shorter content, I/O and runtime failures while writing, an interrupted write, a failed rename
and a missing directory.

**Verification result:** the unit tests pass on the JVM and in CI. A real interrupted write on a
device is **DEVICE CHECK REQUIRED** (see `AUDIT_REMEDIATION_DEVICE_TESTS.md`).

## H1. Android Back handling

**Finding:** H1, Android 16 system Back bypasses the game's Back handling.

**Problem:** with `targetSdk 36` on Android 16, system Back skips `onBackPressed()`:
- in `MainActivity` it no longer closes the toolbox first;
- in `StartScreenActivity` it no longer pops the start-screen fragments or asks for exit
  confirmation.

**Root cause:** Android 16 enables predictive back for apps targeting API 36. The system then
dispatches Back only to `OnBackInvokedCallback`s. `androidx.activity` 1.0.0 predates the bridge from
`OnBackPressedDispatcher` to that mechanism, which arrived in 1.6.

**Chosen solution:** `activity/BackNavigation.register(activity, handler)` registers a platform
`OnBackInvokedCallback` on API 33+ that runs the activity's existing Back handling:
- `MainActivity` passes `onBackPressed`;
- `StartScreenActivity` forwards to its `OnBackPressedDispatcher`.

**Alternatives considered:**
- `android:enableOnBackInvokedCallback="false"` in the manifest. It is simpler, but Android
  documents it as a temporary opt-out that later versions may remove.
- Upgrading `androidx.activity`/`androidx.fragment` and moving `MainActivity` to
  `ComponentActivity`. This is the long-term direction, but a 2019→2025 upgrade of both libraries
  changes fragment lifecycle behavior and cannot be verified without devices.

**Why this solution:** it uses the platform API that Android 16 calls, needs no new dependency, and
keeps the existing Back logic unchanged. On Android 13–15 the app does not opt in to predictive
back, so the callback stays unused and `onBackPressed()` keeps working as before. On API < 33 the
helper does nothing.

**Trade-offs:** while the callback is registered, the activity gives up the system's predictive
back-to-home animation, since the app decides what Back does.

**Regression protection:** `BackNavigationTest` scans the sources and fails when a class overrides
`onBackPressed()` or creates an `OnBackPressedCallback` without calling `BackNavigation.register`.
A mutation check (removing the registration from `MainActivity`) makes it fail.

**Verification result:** compiles and the static test passes in CI. Behavior on Android 16:
**DEVICE CHECK REQUIRED**.

Android lint, added to CI later (L12), independently reports the original problem as
`GestureBackNavigation` errors: "onBackPressed is no longer called for back gestures". Lint does not
recognize a platform `OnBackInvokedCallback` registration as the fix, so these reports stay in the
lint baseline.

## H3. Translation sources in the APK

**Finding:** H3, the APK ships translation sources that are never read.

**Root cause:** `copyTranslation` copied the whole `assets/translation` folder. `TranslationLoader`
reads only `translation/<lang>.mo`.

**Chosen solution:** `include '*.mo'`. The task type changed from `Copy` to `Sync`, so files left in
`build/gen-assets` by older builds are removed.

**Alternatives considered:**
- Moving the `.po` files out of `assets/`. That changes the layout that Weblate and the translation
  tools use.
- `aaptOptions.ignoreAssetsPattern`. It works too, but is less direct than not copying the files.

**Regression protection:** `apk-report.sh --check` in CI fails when any other file type reaches
`assets/translation`.

**Verification result:** the debug APK went from 120,663,105 to 58,228,837 bytes. All 54 `.mo`
files are still packaged (see `AUDIT_REMEDIATION_METRICS.md`).

## H4. World map architecture

**Finding:** H4, the world map opens slowly (and the first attempt of the fix rendered a blank
page).

**Chosen solution:** `fix/world-map-loading` (upstream PR #139) merged unchanged. Its design is
documented in the PR and in `AUDIT.md`:
- images are loaded from the visual viewport position;
- the area around the player is requested during parsing;
- the activity's fallback script loads every image if the page script fails;
- the cached HTML carries a version marker that includes a hash of the template.

**Why merged unchanged:** the PR is under upstream review. Keeping its commits identical avoids
divergence when it is merged upstream.

**Regression protection:**
- `WorldMapTemplateTest` and `WorldMapControllerTest` (from the PR).
- `audit/remediation/worldmap/` browser tests in a separate CI job. They fail on the template of
  the broken first commit and pass on the current one.

## M1. Content validation: missing phrases and unknown ids

**Finding:** M1, a reference to a missing dialogue phrase crashes the game.

**Problem:** `ConversationLoader.loadPhrase` unboxed `null` for an unknown phrase and threw
`NullPointerException`. Script effects threw on unknown skills (`SkillID.valueOf`), maps, droplists,
items, item filters and actor conditions.

**Root cause:** content lookups assumed that every id exists. The content is edited by hand and
only partly validated, and the audit found 22 references to phrases that do not exist.

**Chosen solution:**
- `loadPhrase` returns `null` for an unknown phrase.
- The conversation ends in release builds. Debug builds keep the existing "not implemented yet"
  placeholder so that content authors see the problem.
- A script effect with an unknown id is skipped, and a requirement on an unknown skill is not
  fulfilled.
- Each case is logged as a content error with `L.error`, which logs in release builds too (M6).

**Alternatives considered:**
- Validating all content at startup and refusing to start. A content bug would then block every
  player instead of one dialogue branch.
- Throwing a checked exception. Every caller would need to handle it, for the same result.

**Why this solution:** the smallest change that keeps the game running. The CI content check (M9)
keeps new broken references from being merged.

**Trade-offs:** a broken reference is now silent in release builds, except in the log. The player
sees a conversation end early instead of a crash.

**Regression protection:** `ConversationControllerContentErrorsTest`:
- an unknown phrase is not loaded;
- a conversation that reaches an unknown phrase ends (release) or shows the placeholder (debug);
- 12 script effects with unknown ids change nothing;
- requirements on unknown skills are not fulfilled.

The CI content job fails on new broken references (M9).

**Verification result:** the tests pass on the JVM and in CI.

## M2. ZIP security in "Import world map"

**Finding:** M2, Zip Slip.

**Root cause:** `unzipStreamToDirectory` wrote each entry to `new File(targetDirectory,
entry.getName())` without checking where the path ends up.

**Chosen solution:** before writing, the canonical path of each entry must start with the canonical
path of the target directory plus a separator. Otherwise the import fails with an `IOException`,
which the import already reports as a failure. `DocumentFile.getName()` returning `null` is handled.

**Alternatives considered:**
- Rejecting every name that contains a separator, as `AUDIT.md` also suggests. Exported world maps
  are flat, so this would work too. The canonical-path check alone is sufficient, is the check
  Android's own guidance recommends, and also covers symbolic links.
- Skipping bad entries instead of failing. A crafted archive is not a valid export, and failing
  tells the user.

**Trade-offs:** an archive with one bad entry fails as a whole. Entries before the bad one have
already been extracted inside the world map folder, which is harmless.

**Regression protection:** `AndroidStorageUnzipTest` extracts real ZIP files:
- a valid export is extracted;
- existing files are kept when not overwriting;
- `../savegame1`, `maps/../../savegame2` and `../worldmap-other/x.png` are rejected, and nothing
  is written outside the folder.

**Verification result:** the tests pass on the JVM and in CI, and fail without the fix.

## M3 and M4. Savegame loading

**Findings:** M3, spawn areas restored by id but reset by index; M4, damaged savegames crash the app.

**Root causes:**
- M3: `PredefinedMap.readFromParcel` reset areas by index (`i >= number of saved areas`), although
  it matched them by id.
- M4: the parsers trust counts and names read from the file, and the load path caught only
  `IOException` and `DigestException`.

**Chosen solution:**
- M3: a `boolean[] loaded` marks restored areas. Exactly the other areas are initialized. The id
  search skips areas that were already restored, so duplicate ids map to successive areas.
- M4:
  - `Savegames.loadWorld` turns `RuntimeException`s from parsing into an `IOException`, which is
    reported as `unknownError`.
  - A failed parse resets the maps, so a following game does not inherit half-loaded state.
  - An unknown current map fails the load, as an unknown saved map already did.
  - The checksum length is checked before allocating.

**Alternatives considered:**
- Catching `RuntimeException` around the whole scene loader. It would also hide programming errors
  after a successful parse, which then no longer reach the Play Console crash reports.
- Validating every count read from the file. Each parser would need a limit. With the
  `RuntimeException` conversion, only the allocation (checksum) needs an explicit check.

**Trade-offs:** a damaged savegame reports "cannot load" instead of crashing. The file is kept.

**Regression protection:**
- `PredefinedMapSpawnAreaTest`: an area inserted before saved areas, and duplicate ids.
- `SavegameCorruptionTest`: a file that ends early (maps are reset), a pre-43 file with more areas
  than the map, and a huge checksum length.
- All of them fail without the fix.
- The unknown-current-map check needs a serialized player, and `Player.writeToParcel` uses
  `android.util.SparseIntArray`, which JVM unit tests do not provide. It is covered by review only.

**Verification result:** the tests pass on the JVM and in CI.

## M5. Concurrency: map transitions

**Finding:** M5, map transitions change the shared game model on a background thread.

**Root cause:** `placePlayerAsyncAt` ran the whole transition in `AsyncTask.doInBackground`. That
included moving the player, replacing `currentMaps`, spawning monsters, running scripts and starting
other `AsyncTask`s. Meanwhile the UI thread drew and handled input from the same objects.

**Chosen solution:** the transition is split into two phases:
- `loadPlacement`, in the background: reads the TMX map and loads its tiles. It changes no game
  state.
- `applyPlacement`, in `onPostExecute` on the UI thread: moves the player, replaces
  `currentMaps`, and does everything else that changes state.

`placePlayerAt` and `prepareMapAsCurrentMap` run both phases synchronously, as before.

**Alternatives considered:**
- Locking the model. Every reader in drawing and input handling would need the lock, and a lock
  in `onDraw` risks stalls.
- Copying the model. It is too large and too interconnected.

**Why this solution:** the expensive part (file parsing, bitmap decoding) stays in the background.
The state change happens on the thread that owns the state. A failure while loading now leaves the
player where they were.

**Trade-offs:** spawning, scripts and replacements now run on the UI thread. They are in-memory
operations, but their duration on a low-end device is **NOT MEASURED**. A failure in the apply
phase can still leave partial state; it is logged.

**Regression protection:** the phases are separate methods with documented contracts. There is no
JVM test, because `AsyncTask`, `Resources` and TMX parsing need Android. Behavior:
**DEVICE CHECK REQUIRED** (map transitions, combat next to exits, rapid transitions).

**Verification result:** compiles, and the unit tests pass in CI. Not run on a device.

## M6. Concurrency and error reporting: import and export

**Finding:** M6, import/export failures are invisible, and the progress dialog can hang.

**Root cause:** the progress dialog closes only when the task reports a result. Some paths
reported nothing:
- `NullPointerException` was swallowed unless the user had cancelled;
- the unzip task caught only `IOException`.

**Chosen solution:**
- `BackgroundWorker` guarantees exactly one result:
  - a thrown exception or a task that returns without a result becomes a failure;
  - later results are ignored.
- The copy tasks report their exceptions, and null streams from `ContentResolver` become
  `IOException`s.
- Failures are logged with `L.error`, which now logs in release builds. The other log levels stay
  debug-only.
- One shared cached thread pool. The `cancelled` flag is volatile. Temporary ZIP files are deleted
  on every path.
- A missing quicksave no longer throws inside `quickload`, so the start screen of a new
  installation logs nothing.

**Alternatives considered:**
- Fixing each task separately without a guarantee in `BackgroundWorker`. The next task written
  could hang the dialog again.
- Replacing `BackgroundWorker` with `java.util.concurrent` futures. That is a larger change of the
  same idea.

**Trade-offs:** errors are now written to the system log in release builds. They contain file names
and exception messages, but no savegame content.

**Regression protection:** `BackgroundWorkerTest` covers a result, a thrown exception, a task
without a result, and results after the first one. Three of the four fail without the fix. The
`ContentResolver` paths need Android: **DEVICE CHECK REQUIRED** (export or import to a provider that
fails, and cancelling).

**Verification result:** the tests pass on the JVM and in CI.

## M7. Startup: spawn group lookup

**Finding:** M7, every launch parses all maps, and spawn group lookups are quadratic.

**Chosen solution:** `MonsterTypeCollection` builds a spawn group index on first use and drops it
when types are added. The index is a `TreeMap` with `String.CASE_INSENSITIVE_ORDER`, which matches
like `equalsIgnoreCase`, and keeps each group in the iteration order of the id map. Results are
therefore identical to the linear scan, including their order, and the random choice of spawned
monsters does not change.

**Alternatives considered:**
- A `HashMap` keyed by `toLowerCase(Locale.ROOT)`. For a few non-ASCII characters it does not match
  exactly like `equalsIgnoreCase`.
- Lazy map parsing and moving the synchronous resource loading off the UI thread. These are the
  larger startup items in `AUDIT.md`, but whether they are worth their complexity needs a startup
  profile on a low-end device. **DEFERRED — MAINTAINER DECISION**, **NOT MEASURED**.

**Regression protection:**
- `MonsterTypeCollectionTest` compares the index with the former linear scan, including case
  variants, the id fallback and unknown groups.
- `SpawnGroupBenchmark` checks the same on the real content: all 6,675 lookups over 1,907 monster
  types give the same result.

**Verification result:** 123–135 ms → 2.8–2.9 ms per pass of all lookups on a desktop JVM (see
`AUDIT_REMEDIATION_METRICS.md`). Device timing: **NOT MEASURED**.

## M8. World map: first load without a cache

**Finding:** M8, loading a savegame renders every visited map without a world map image.

**Decision:** **DEFERRED — MAINTAINER DECISION.** PR #139 (merged here) already removed the part
whose cost grew fastest, the segment HTML rebuild after every image. The images are still rendered
while the savegame loads.

**Why not changed:** rendering them in the background after the scene is ready means reading the
live game model (visited flags, color filters, spawn areas, tile caches) while the game runs. That
is the class of problem fixed in M5. Doing it safely needs either a snapshot of the map state or
rendering on demand when the world map is opened. The cost appears once per savegame and
installation (reinstall, new device, import without `worldmap.zip`) and was **NOT MEASURED** on a
device.

**Options for the maintainers:**
1. Keep rendering during the load, but show progress.
2. Snapshot the visited maps' state, then render in the background.
3. Render missing images when the world map is opened.

## M9. Content validation in CI

**Finding:** M9, NPCs that never appear and unreachable dialogue branches.

**Chosen solution:** `audit/remediation/scripts/check-content.sh` runs the audit's content and
translation checks in a new CI job. A problem that is not in the reviewed baseline
(`audit/remediation/baselines/`) fails the job.

**Why the content itself is not changed:** each case in `AUDIT.md` needs a content decision:
- `lae_demon4` names spawn groups (`lae_prisoner2i`) where the engine expects area ids, and each of
  those groups is used by several areas of `laerothprison4`.
- `graveyard_corpse_boss_kill` may mean `graveyard_corpse_boss_patrol` or an area that was never
  added.
- Spawning `ll2_circe_crew` needs a dialogue phrase that does not exist.

Renaming map areas would also change the ids stored in savegames. **DEFERRED — MAINTAINER
DECISION** for the content, with the details in `AUDIT.md`.

**Regression protection:** the CI content job. Checked by restoring the two broken Latin strings:
the job reports three new problems and fails.

## L1 to L4. Storage, WebView and backup hardening

**Findings:** L1 (FileProvider roots), L2 (WebView), L3 (backup quota), L4 (storage permissions).

**Root cause:** configuration that grants more than the code uses.
- The provider exposed `root-path`, external storage and both app files directories, and serves
  only the world map.
- The WebView allowed `file://` access on every Android version, but uses it only before Android 10.
- Area names were inserted into the page as HTML.
- Auto Backup had no rules.
- The storage permissions were declared for every API level.

**Chosen solution:**
- **FileProvider:** a single `external-files-path` for `andors-trail/worldmap/`.
- **WebView:** file access enabled only before Android 10. Area names are HTML-escaped, and the
  page format version is increased so cached pages are rebuilt once.
- **Backup:** the world map cache and logs are excluded from cloud backups, but kept for
  device-to-device transfer, which has no quota.
- **Permissions:** `maxSdkVersion` 29 for WRITE and 32 for READ. These are the levels at which
  the code requests them (up to Android 10) and uses READ to migrate old savegames (Android 11
  and 12). On Android 10, `LoadSaveActivity` finishes when a requested permission is denied, so
  WRITE must stay declared through API 29.

**Alternatives considered:**
- Removing the permissions entirely. Android 10 still requests them.
- A lower `maxSdkVersion` for WRITE (28). On Android 10 the request would then be denied
  automatically and close the load/save screen.

**Trade-offs:** none known in behavior. The provider and WebView changes break the world map on
Android 10+ if the path mapping is wrong, which is why they are marked DEVICE CHECK REQUIRED.

**Regression protection:**
- `FileProviderPathsTest` and `BackupRulesTest` tie the XML paths to the directory constants.
- `WorldMapControllerTest.areaNamesAreEscaped`.
- Device tests 3.6, 3.7 and 6.1–6.4.

**Verification result:** the static tests pass and the release build's `lintVitalRelease` passes.
Device behavior was not observed.

## L12. CI

**Chosen solution:**
- Actions pinned to commit SHAs; `upload-artifact` moved to v6.0.0 (Node 24). Each SHA was
  resolved with `git ls-remote`, and each `action.yml` runtime was read at that SHA.
- Release unit tests and release build: `testReleaseUnitTest` needed
  `android.onlyEnableUnitTestForTheTestedBuildType=false` under AGP 9.
- Lint with a 4 GB heap for that step only, gated on `app/lint-baseline.xml`. The baseline holds
  the 393 issues in the existing code. It was generated by `updateLintBaseline` in CI, because the
  SDK is not available locally, and committed. The largest groups:
  - `RtlHardcoded` (107);
  - `StringFormatMatches` (106). Checked: `%s` given an `int`, and translations that drop an
    argument. None throws.
  - `PluralsCandidate` (34).

  The two `NewApi` errors are an XML theme attribute that older versions ignore.
- The content job (M9) and the world map job (H4).

**Not changed:**
- Duplicate `push`/`pull_request` runs for branches in the same repository. The fork relies on
  push runs; limiting them is the maintainers' choice. **DEFERRED — MAINTAINER DECISION.**
- `.travis.yml` and `travis/`. Removing another project's CI configuration is the maintainers'
  choice. **DEFERRED — MAINTAINER DECISION.**
- Fork PR approval. A GitHub security setting that protects secrets. **INTENTIONAL.**
