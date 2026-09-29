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
