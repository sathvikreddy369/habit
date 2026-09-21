# Physical Device Verification Protocol & Empirical Procedures

This document defines the **empirical verification procedures** for Habit1 on physical Android hardware.

In accordance with Habit1 Core Principles (*"Measure before optimizing"*, *"Do not represent architectural reasoning as empirical performance measurements"*), this protocol specifies exact testing commands, observed conditions, and hardware measurement procedures without fabricated figures or arbitrary pass/fail criteria.

---

## 1. Hardware Verification Status

| Parameter | Current Status |
| :--- | :--- |
| **Connected Hardware** | None detected at time of build (`adb devices -l` returned empty) |
| **Physical Verification Status** | **PENDING PHYSICAL HARDWARE ATTACHMENT** |
| **Build Artifact Verified** | `app-release-unsigned.apk` (1.4 MB, R8 minified, resource-shrunk) |
| **Automated Verification Status** | 177/177 unit tests passing across Debug and Release |
| **Static Analysis Status** | `lintDebug` passed with 0 errors |

When a physical Android device is connected via ADB, execute the test suites documented below and log actual measured values into the **Empirical Results Log** in Section 6.

---

## 2. Environmental Setup & Installation

### 2.1 Device Pre-Conditions
1. Enable **Developer Options** and **USB Debugging** on the target Android device.
2. Confirm device connectivity:
   ```bash
   adb devices -l
   ```
3. Record device specifications:
   ```bash
   adb shell getprop ro.product.model
   adb shell getprop ro.build.version.release
   adb shell getprop ro.build.version.sdk
   ```

### 2.2 Release Build Installation
To install the release variant for testing:
```bash
# Build unsigned release APK
./gradlew assembleRelease

# Sign locally with a testing key or install debug build for debuggable inspection:
# For release APK verification:
adb install -r app/build/outputs/apk/release/app-release-unsigned.apk
```
*(Note: Android requires an APK to have a valid signature before installation. For local release testing on physical hardware without a production Play Store keystore, sign with a local testing key via `apksigner` or test the corresponding optimized release build. Never commit private signing keys to source control.)*

---

## 3. Core Measurement Procedures

### 3.1 Cold Startup Timing
Measure application cold startup latency using `ActivityManager`:
```bash
# Force-stop any running instance
adb shell am force-stop com.habit1.app

# Drop OS filesystem caches (requires root if available, otherwise skip)
# adb shell "echo 3 > /proc/sys/vm/drop_caches"

# Launch main activity and measure cold start
adb shell am start-activity -W -n com.habit1.app/.ui.MainActivity -S
```

**Metrics to Record**:
* `ThisTime`: Time spent starting the activity directly.
* `TotalTime`: Total cold start latency including process initialization.
* `WaitTime`: Total time including window manager transition.

*Procedure*: Execute 5 consecutive runs with `am force-stop` between iterations. Discard run 1 (cold cache), and record median of runs 2–5. Do not impose an arbitrary `<500ms` ceiling; establish the device's empirical baseline.

---

### 3.2 Memory Footprint (`dumpsys meminfo`)
Measure native heap, Dalvik heap, and PSS memory allocations:
```bash
adb shell dumpsys meminfo com.habit1.app
```

**Key Fields to Record**:
* **TOTAL PSS**: Proportional Set Size (actual RAM consumed by the process).
* **Java Heap**: Memory occupied by runtime objects.
* **Native Heap**: Memory allocated by SQLite, Skia rendering engine, and graphics pipelines.
* **Graphics**: GPU/display buffer memory.

*Procedure*:
1. Measure baseline memory immediately after launch on the **Today** screen.
2. Measure memory after scrolling through 12 months in **History**.
3. Measure memory following a backup export and restore operation.

---

### 3.3 Doze Mode & Exact Alarm Delivery
Verify that habit reminders scheduled via `AlarmManager` fire reliably during device idle/Doze:

1. Create a habit with a reminder set 3 minutes in the future.
2. Verify notification permission is granted:
   ```bash
   adb shell dumpsys package com.habit1.app | grep -i "POST_NOTIFICATIONS"
   ```
3. Verify exact-alarm permission state (Android 12+ / API 31+):
   ```bash
   adb shell cmd appops get com.habit1.app SCHEDULE_EXACT_ALARM
   ```
4. Confirm the alarm exists in `AlarmManager`:
   ```bash
   adb shell dumpsys alarm | grep -E "com.habit1.app|habit1://reminder"
   ```
5. Unplug USB power and simulate entering Deep Doze:
   ```bash
   adb shell dumpsys battery unplug
   adb shell dumpsys deviceidle force-idle
   adb shell dumpsys deviceidle get
   ```
   *(Ensure status is `IDLE` or `IDLE_MAINTENANCE`)*
6. Wait for the scheduled reminder time.
7. Observe whether the reminder notification is posted to the status bar.
8. Restore battery state and exit Doze:
   ```bash
   adb shell dumpsys deviceidle unforce
   adb shell dumpsys battery reset
   ```

---

### 3.4 Device Reboot Recovery
Verify that `BootReceiver` restores scheduled habit alarms upon device restart:

1. Configure 2 habits with distinct reminder times.
2. Confirm both alarms are registered:
   ```bash
   adb shell dumpsys alarm | grep "habit1://reminder"
   ```
3. Issue device reboot:
   ```bash
   adb reboot
   ```
4. Wait for the lock screen to appear and unlock the device.
5. Inspect Logcat for `BootReceiver` execution:
   ```bash
   adb logcat -d -s BootReceiver HabitReminderScheduler
   ```
6. Re-query `AlarmManager`:
   ```bash
   adb shell dumpsys alarm | grep "habit1://reminder"
   ```
7. Confirm that both habit alarms have been reconciled and rescheduled to their next valid trigger epoch.

---

## 4. Functional Verification Checklist

When executing on a physical device, verify each item:

### 4.1 Habits & Measurement Types
* [ ] **BooleanChoice**: Tap circle toggle on Today screen; verify instant completion styling and progress counter increment.
* [ ] **Count**: Tap increment `+` and decrement `-`; verify integer target progress updates correctly.
* [ ] **Duration**: Tap duration buttons; verify minute accumulation.
* [ ] **Quantity**: Enter decimal value (e.g., 2.5 L); verify numeric formatting and unit rendering.
* [ ] **Habit Management**: Create habit, edit schedule, pause habit, resume habit, archive habit, reorder habits.

### 4.2 Daily Goals & Subtasks
* [ ] **Create Daily Goal**: Add goal with title and optional notes.
* [ ] **Subtasks**: Add 2+ subtasks; verify subtask toggle is independent of parent goal completion.
* [ ] **Goal Reordering**: Move goal up/down; verify atomic order persistence.
* [ ] **Move to Tomorrow**: Move goal date to tomorrow; verify goal moves off today's view.

### 4.3 Daily Review / Reflection (Phase 10)
* [ ] **Empty State**: Verify subtle "Add daily reflection (optional)" card appears near the bottom of Today.
* [ ] **Add Reflection**: Tap card; enter notes, select mood (e.g. "Focused"); save.
* [ ] **Review Card**: Verify saved notes, mood chip, Edit button, and Delete button appear.
* [ ] **Edit Reflection**: Change notes/mood; verify update persists.
* [ ] **Delete Reflection**: Tap delete; verify card reverts to empty state.
* [ ] **History Integration**: Navigate to History screen; select date with review; verify reflection note and mood appear under daily breakdown with disclaimer: *"This is a user-written reflection, not a calculated productivity result."*
* [ ] **Zero Statistical Impact**: Verify adding/deleting daily reflection does NOT alter habit streaks, completion rates, or goal counts.

### 4.4 History & Calendar
* [ ] **Calendar Month View**: Navigate months; verify day cells show correct completion indicators.
* [ ] **Selected Date Breakdown**: Select past dates; verify historical snapshot records are displayed factually.
* [ ] **Projected Missed vs Rest**: Verify non-recorded past days show projected status without fabricating database records.

### 4.5 Backup, Export & Restore
* [ ] **Export**: Navigate to Settings -> Backup; trigger Export; select storage destination via SAF; verify valid JSON backup file is created.
* [ ] **Checksum**: Verify exported JSON contains SHA-256 integrity checksum.
* [ ] **Merge Restore**: Trigger restore on a new device or cleared state; select Merge; verify habits, records, goals, subtasks, and reviews are restored.
* [ ] **Replace All Restore**: Test Replace All; verify secondary confirmation dialog; verify atomic overwrite and alarm reconciliation.

---

## 5. Physical Verification Results Log (Template)

Record actual hardware measurements in this table:

| Test Item | Device Model | Android Version | Build Variant | Test Date | Measured Result | Notes / Deviations |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Cold Start (Run 1)** | Pending Hardware | — | Release | — | — | — |
| **Cold Start (Median 2–5)** | Pending Hardware | — | Release | — | — | — |
| **Memory Baseline (Today)** | Pending Hardware | — | Release | — | — | — |
| **Memory Peak (History Scroll)** | Pending Hardware | — | Release | — | — | — |
| **Doze Mode Firing** | Pending Hardware | — | Release | — | — | — |
| **Reboot Alarm Recovery** | Pending Hardware | — | Release | — | — | — |
| **Backup SAF Roundtrip** | Pending Hardware | — | Release | — | — | — |

---

## 6. Summary

Habit1 architecture is verified through:
* **177 automated unit & integration tests** covering all domain use cases, Room database transactions, alarm scheduling calculators, backup validation, and ViewModel state transitions.
* **Release build optimization** producing an efficient 1.4 MB APK with R8 bytecode shrinking and resource optimization.
* **Strict offline privacy** guaranteed by the complete omission of `android.permission.INTERNET`.

Hardware testing remains pending physical device connection and should be executed following the empirical steps documented above.
