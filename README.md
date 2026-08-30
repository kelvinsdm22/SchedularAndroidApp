# Scheduler

A native Android task scheduler. You list a task and its total duration (e.g.
*Outlier application, 8 hrs*); the app breaks it into fixed **4‑hour time slots**,
puts them on a calendar, and fires notifications when each slot is due so you can
**Confirm** attendance or **Postpone** it.

- **minSdk 21** (Android 5.0 Lollipop) · **targetSdk 35**
- Kotlin · Room · AlarmManager · no network, no accounts, all data on‑device

## Download

**[⬇ Download the latest APK](https://github.com/kelvinsdm22/SchedularAndroidApp/releases/latest/download/scheduler-app.apk)**

Enable *Install unknown apps* for your browser/file manager, open the APK, install.
Works on Android 5.0 and newer. All releases: <https://github.com/kelvinsdm22/SchedularAndroidApp/releases>

## Features

| Requirement | How it works |
|---|---|
| List tasks with a duration | Task = name + total hours + preferred start hour |
| Fixed 4‑hour slots | Every slot is 4 hrs (`Slot.SLOT_HOURS`) |
| Auto‑generate slots | Creating an 8 hr task makes two 4 hr slots, one per day from tomorrow at the preferred hour; top‑up slots are added if you raise the duration |
| Add / remove slots | "Add time slot" button + per‑slot **Remove**; date & time pickers |
| Confirm / Postpone | Buttons in the app and **action buttons on every notification**. Confirm marks the slot done and counts 4 hrs toward the task. Postpone opens that slot in the date/time picker |
| Active notifications | Per slot: one reminder **15 min before** and one **at start**, both high‑priority with Confirm / Postpone. Plus a low‑priority **"Next up"** notification showing the nearest slot — **swipe it away** to dismiss (it stays gone until a different slot becomes next), or turn it off entirely from the overflow menu (**⋮ → Show "Next up" notification**) |
| Survives reboot | `BootReceiver` re‑arms every future alarm after restart / app update |

## Project layout

```
app/src/main/java/com/jengadirect/scheduler/
  SchedulerApp.kt              Application – creates notification channels
  Prefs.kt                     SharedPreferences: "Next up" on/off + dismissed marker
  data/
    Entities.kt                Task, Slot, SlotStatus, Room TypeConverter
    Daos.kt                    TaskDao, SlotDao
    AppDatabase.kt             Room database (singleton)
    Repository.kt              business logic + slot auto‑generation + progress rollup
  alarm/
    AlarmScheduler.kt          arms/cancels the two exact alarms per slot
    SlotAlarmReceiver.kt       fires the slot notification
    NotificationActionReceiver.kt   handles "Confirm" + "Next up" swipe‑to‑dismiss
    BootReceiver.kt            re‑arms alarms after reboot
    Notifications.kt           channels + notification builders
  ui/
    MainActivity.kt            task list + FAB, permission prompts
    TaskEditActivity.kt        add / edit / delete a task
    TaskDetailActivity.kt      a task's slots; add / confirm / reschedule / remove
    TaskListAdapter.kt / SlotListAdapter.kt / Format.kt
```

## Build

Requires JDK 17+ and the Android SDK (platform 35, build‑tools 35.0.0).
`local.properties` points `sdk.dir` at the SDK.

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

### CI

- **`.github/workflows/build.yml`** — builds the debug APK and runs lint on every
  push / PR to `main`. Grab the APK from the run's **Artifacts → scheduler-debug-apk**.
- **`.github/workflows/release.yml`** — on a pushed `v*` tag, builds a **signed**
  release APK and publishes a GitHub Release with `scheduler-app.apk` attached, so
  `releases/latest/download/scheduler-app.apk` always points at the newest build.

### Cutting a release

One‑time: add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 scheduler-release.jks` |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | `scheduler` |
| `KEY_PASSWORD` | key password |

Then:

```bash
git tag v1.0.0
git push origin v1.0.0
```

Local signed build (needs `keystore.properties` in the project root, git‑ignored):

```bash
./gradlew assembleRelease   # -> app/build/outputs/apk/release/app-release.apk
```

Install on a device / emulator:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Runtime permissions & OEM notes

- **Android 13+**: the app asks for the *Notifications* permission on first launch.
- **Android 12+**: exact alarms need the *Alarms & reminders* special access. The
  app shows a prompt linking straight to that settings screen; until it is
  granted, alerts fall back to an inexact ~10‑minute window.
- Some vendors (Xiaomi, Huawei, Oppo, Samsung…) kill background apps aggressively.
  For reliable alerts, disable battery optim/ enable "Auto‑start" for Scheduler.

## Not included (v1)

- Editing the preferred start hour does not move slots that already exist.
- No recurring tasks, no cloud sync, no widget.
- Notification content‑tap opens the task directly rather than building a full
  back stack.
