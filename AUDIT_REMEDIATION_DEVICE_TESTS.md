# Audit remediation: device tests

No Android device or emulator was available during the remediation. **None of the tests in this
document has been run.** Each one is marked **NOT RUN**. A result may only be filled in after it
was actually run on the stated device.

What is already verified without a device is listed in `AUDIT_REMEDIATION_TESTING.md`. This
document covers what only a device can show.

## Test matrix

Run every test marked "all" on each of these:

| Device | Why |
|---|---|
| Android 16 (API 36), gesture navigation | Predictive back is active for targetSdk 36 (H1) |
| Android 16, 3-button navigation | Back button path of H1 |
| Android 15 (API 35) | Last version where `onBackPressed()` still receives Back |
| Android 14 (API 34) | Zip entries with `..` are rejected by the platform (M2) |
| Android 13 (API 33) | First version with `OnBackInvokedDispatcher`; still affected by Zip Slip; READ_EXTERNAL_STORAGE not granted (L4) |
| Android 10 (API 29) *(optional)* | Last version that requests the storage permissions (L4), first with content:// world map (L1) |
| Android 9 or older *(optional)* | file:// world map (L2 file access) |

Use a debug build (`.dev`, all DEVELOPMENT_* checks on) and a release build (signed with any key)
where noted. Use `adb logcat -s AndorsTrail` to see log lines. After this remediation, release
builds log errors too (M6).

## Build under test

- Branch `audit/remediation`, commit: ______
- Build type: debug / release
- Device, Android version, navigation mode: ______

## 1. Android Back (H1)

| # | Steps | Expected | Devices | Result |
|---|---|---|---|---|
| 1.1 | In game, open the toolbox (bottom-left button), press Back | Toolbox closes, game stays open | all | NOT RUN |
| 1.2 | In game with the toolbox closed, press Back | The game screen closes, as on Android 15 (`MainActivity.onBackPressed` → `super`) | all | NOT RUN |
| 1.3 | Start screen → "New game", press Back | Returns to the main menu, app stays open | all | NOT RUN |
| 1.4 | Start screen main menu, press Back once, then again within 2 s | First Back shows "press Back again to exit" and the app stays open; the second one exits | all | NOT RUN |
| 1.5 | Android 16 gesture navigation: start a back swipe and cancel it | Nothing happens; no crash | Android 16 | NOT RUN |
| 1.6 | Rotate the device on the start screen sub-page, then Back | Same as 1.3 (callback re-registered after recreation) | all | NOT RUN |

## 2. Savegames (H2, M3, M4)

| # | Steps | Expected | Devices | Result |
|---|---|---|---|---|
| 2.1 | Save to slot 1, load slot 1 | Game continues where it was saved | all | NOT RUN |
| 2.2 | Save to slot 1 and kill the app (`adb shell am kill com.gpl.rpg.AndorsTrail.dev`) while "Saving" is shown; repeat 10× | Slot 1 always loads, either the old or the new state; never "cannot load"; no `savegame1.tmp` left after the next successful save | all | NOT RUN |
| 2.3 | Fill the storage until less space than one savegame is free, then save | Save fails with a message; the previous save still loads | one device | NOT RUN |
| 2.4 | Quicksave, then kill the app during the next quicksave | Quicksave loads | one device | NOT RUN |
| 2.5 | Replace `savegame1` with a truncated copy (`head -c 5000`) and load it | "Cannot load" dialog, app keeps running, a new game starts normally afterwards | all | NOT RUN |
| 2.6 | Replace `savegame1` with random bytes after the header and load it | Same as 2.5 | one device | NOT RUN |
| 2.7 | Load a save from the current release (0.8.18) with visited maps and killed unique monsters | Killed unique monsters stay dead; quest-disabled spawn areas stay disabled | one device | NOT RUN |

## 3. World map (H4, L1, L2)

| # | Steps | Expected | Devices | Result |
|---|---|---|---|---|
| 3.1 | New game in Crossglen, open the world map | Map image and player marker visible (the first PR attempt showed a blank page here) | all | NOT RUN |
| 3.2 | Late-game save (many visited maps), open the world map | Opens quickly; the area around the player is visible without waiting for the whole map | all | NOT RUN |
| 3.3 | Pan, zoom in, zoom out to the minimum | Every visible map image appears; no permanently empty squares | all | NOT RUN |
| 3.4 | Close and reopen the world map | Same as 3.2 | all | NOT RUN |
| 3.5 | Measure: time from tapping "World map" to the area around the player being drawn, late-game save, before (0.8.18) and after | Record both numbers in `AUDIT_REMEDIATION_METRICS.md` | Android 10+ | NOT RUN |
| 3.6 | Android 10+: open the world map after the FileProvider change | Page and images load (content:// URLs resolve through the `worldmap` path only) | Android 10+ | NOT RUN |
| 3.7 | Android 9 or older: open the world map | Page and images load (file:// access still enabled there) | Android ≤ 9 | NOT RUN |
| 3.8 | Switch the game language to one whose area names contain `&` or `<` (or edit a translation), open the world map | Names are shown literally, the page is intact | one device | NOT RUN |
| 3.9 | Uninstall/reinstall, import saves without `worldmap.zip`, load a late-game save | Load finishes; time noted (M8, deferred) | one device | NOT RUN |

## 4. Import and export (M2, M6)

| # | Steps | Expected | Devices | Result |
|---|---|---|---|---|
| 4.1 | Export savegames (Storage Access Framework), import them on a clean install | Savegames appear and load | all | NOT RUN |
| 4.2 | Export the world map, import it | Import succeeds; world map shows without regenerating | all | NOT RUN |
| 4.3 | Import a crafted `worldmap.zip` containing `../savegame1` (see `AndroidStorageUnzipTest` for how to build one) | Import fails with a message; `savegame1` unchanged | Android 13 (and older) | NOT RUN |
| 4.4 | Start an export, cancel the progress dialog | Dialog closes; no crash | all | NOT RUN |
| 4.5 | Export to a folder on a removed SD card or a cloud provider that fails | Dialog closes with a failure message; an `AndorsTrail` error line in logcat (release build too) | one device | NOT RUN |
| 4.6 | Export to Downloads (MediaStore), then check the app's cache directory | No `temp_worldmap*.zip` left | Android 10+ | NOT RUN |

## 5. Map transitions and gameplay (M1, M5, L5, L9)

| # | Steps | Expected | Devices | Result |
|---|---|---|---|---|
| 5.1 | Walk through 20+ map transitions quickly, including while monsters move | No crash, no flicker of the old map, player always placed at the destination | all | NOT RUN |
| 5.2 | Enter a map transition while a monster is adjacent | Transition completes; combat state consistent | one device | NOT RUN |
| 5.3 | Measure the transition time between two large maps, before and after | Record in `AUDIT_REMEDIATION_METRICS.md` (M5 moved in-memory work to the UI thread) | low-end device | NOT RUN |
| 5.4 | Latin system locale: pick up several items; open the info of an item with a critical multiplier | Text shown, no crash (L5) | one device | NOT RUN |
| 5.5 | Change the system time by ±1 hour while playing | Game rounds continue normally (L9) | one device | NOT RUN |
| 5.6 | Settings: select the Chinese entry of the in-game language list (value `zh-rCN`) | Simplified Chinese resources shown (L6) | one device | NOT RUN |

## 6. Permissions, backup, startup (L3, L4, M7)

| # | Steps | Expected | Devices | Result |
|---|---|---|---|---|
| 6.1 | Android 10: fresh install, open load/save | Storage permission dialog as before | Android 10 | NOT RUN |
| 6.2 | Android 11 or 12 with savegames in `/sdcard/andors-trail` from an old version | Migration dialog and savegames migrated (READ still declared up to API 32) | Android 11/12 | NOT RUN |
| 6.3 | Android 13+: fresh install, open load/save | No storage permission dialog; everything works | Android 13+ | NOT RUN |
| 6.4 | `adb shell bmgr backupnow com.gpl.rpg.AndorsTrail` with a large world map cache | Backup succeeds; `worldmap/` and `log/` are not in the backup | Android 12+ | NOT RUN |
| 6.5 | Cold start to the main menu, 5 runs, before and after (M7) | Record the median in `AUDIT_REMEDIATION_METRICS.md` | low-end device | NOT RUN |
| 6.6 | Debug build: start the app 12 times | At most 10 `logcat*.txt` files in `andors-trail/log` (L8) | one device | NOT RUN |

## 7. Stability

| # | Steps | Expected | Devices | Result |
|---|---|---|---|---|
| 7.1 | 30 minutes of normal play with saving, loading, world map, conversations | No crash, no ANR | all | NOT RUN |
| 7.2 | `adb shell monkey -p com.gpl.rpg.AndorsTrail.dev --throttle 100 -v 20000` | No crash; check logcat for `AndorsTrail` errors | one device | NOT RUN |
| 7.3 | Play with "Don't keep activities" enabled in developer options | No crash when returning to the game | one device | NOT RUN |
