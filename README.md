# Habit1 — Offline Habit & Daily Goal Tracker

Habit1 is a personal habit tracker and daily goal application designed exclusively for Android. It operates on a strict **local-first, privacy-first** paradigm, storing all data on-device without cloud synchronization, user accounts, telemetry, ads, or internet permissions.

---

## 1. Product Philosophy

* **Local-First**: All data is created, indexed, and stored directly on your physical Android device.
* **Privacy-First**: Zero tracking. Zero telemetry. Zero analytics. Zero advertising.
* **No Cloud / No Backend**: The application communicates with no remote servers. It requires no network permissions.
* **Simple & Focused**: Deliberately avoids social feeds, gamified scores, artificial intelligence, or feature creep.
* **History is Truth**: Historical user records are factual snapshots of what was logged on a given civil date; past data is never silently deleted, altered, or fabricated.
* **Battery Conscious**: Zero persistent background services or polling loops. The app remains completely dormant when closed.

---

## 2. Features

### Today View
* **Unified Dashboard**: Displays scheduled habits, daily outcomes, and daily progress for the current civil date.
* **Progress Tracking**: Clear progress rings and completion indicators updated in real time.
* **Midnight Date Rollover**: Gracefully updates to the next calendar date when left open across midnight without background polling.

### Habits & Measurement Types
Habits support four distinct measurement models:
* **BooleanChoice**: Binary done/not-done habits (e.g., Morning Meditation).
* **Count**: Integer-based repetition targets with increment (`+`) and decrement (`-`) controls (e.g., 8 glasses of water).
* **Duration**: Time-based habits tracked in minutes (e.g., 30 minutes reading).
* **Quantity**: Decimal/floating-point values with custom units (e.g., 2.5 kilometers running).

### Flexible Scheduling
* **Daily**: Habits scheduled for every day of the week.
* **Specific Days**: Habits scheduled on select weekdays (e.g., Mon, Wed, Fri).
* **Intervals**: Habits repeated every $N$ days anchored to a specific start date (e.g., every 3 days).

### Daily Goals & Subtasks
* **Outcome Planning**: Explicit goals for a target civil calendar date, independent of recurring habits.
* **Subtasks**: Granular subtasks under each goal.
* **Independent Completion**: Completing subtasks does not automatically complete the parent goal, nor does completing a goal force subtask states.
* **Reordering & Date Shifting**: Atomic drag/reorder and one-tap shifting to tomorrow.

### History, Calendar & Explainable Statistics
* **Calendar Heatmap**: Month-by-month grid showing completed, incomplete, and rest days.
* **Historical Fact vs Projection**: Explicitly distinguishes recorded facts from current-schedule projections.
* **Transparent Metrics**:
  * **Current Streak**: Consecutive scheduled days completed up to today.
  * **Longest Streak**: Maximum historical consecutive scheduled completions.
  * **Completion Rate**: Mathematically defined as `(Completed / Scheduled) * 100`.
  * **No Synthetic Scores**: No opaque algorithms, productivity ratings, or gamified health levels.

### Habit Reminders (Platform Notifications)
* **Battery-Conscious Alarms**: Scheduled using Android platform `AlarmManager` (`setExactAndAllowWhileIdle` with graceful fallbacks).
* **"Notifications are Reminders, Not Records"**: Dismissing, ignoring, or opening a notification never creates records, alters streaks, or affects statistics.
* **Reboot Recovery**: Automatically reschedules pending reminders via `BootReceiver` after device restart or timezone changes.

### Backup, Export & Restore
* **User-Controlled**: Explicitly triggered by the user; no background or automatic backups.
* **Storage Access Framework (SAF)**: Export and import using Android's native file picker (`CreateDocument` / `OpenDocument`).
* **Format & Integrity**: Transparent JSON envelope with deterministic SHA-256 integrity verification.
* **Restore Modes**:
  * **Merge**: Non-destructive restore preserving existing data and resolving conflicts deterministically.
  * **Replace All**: Complete, atomic database replacement with pre-wipe reminder cancellation and post-commit alarm synchronization.

### Daily Review / Reflection (Optional)
* **Personal Reflection**: Add an optional reflection note and mood tag (Calm, Energized, Focused, Tired, Grateful) to any day.
* **Completely Independent**: Never affects habit completion, streaks, consistency stats, or daily goals.
* **History Inspection**: View past reflections under the selected date breakdown in History with clear indication that it is a subjective reflection, not a productivity score.

---

## 3. Architecture

Habit1 follows a clean unidirectional architecture using modern Android Jetpack components:

```text
Compose UI (Declarative Material 3 UI)
    ↓ Events
ViewModels (StateFlow / UDF)
    ↓
Pure Domain Use Cases & Repositories
    ↓
Local Data Layer (Room WAL / DataStore Preferences)
    ↓
SQLite Database (On-Device Flash Storage)
```

### Architectural Highlights
* **Pure Kotlin Domain**: Domain models (`Habit`, `HabitRecord`, `DailyGoal`, `DailyReview`) and use cases contain no Android dependencies.
* **Manual Dependency Injection**: Implemented via an explicit, reflection-free `AppContainer` owned by `HabitApplication` (no Dagger/Hilt build overhead).
* **Kotlin Coroutines & Flow**: Unidirectional state observation using reactive `StateFlow` and structured concurrency.
* **Room with WAL**: SQLite database running in Write-Ahead Logging (WAL) mode for fast, concurrent reads and transactions.
* **AlarmManager & BroadcastReceivers**: Platform exact alarms with non-exported receivers (`AlarmReceiver`) and filtered boot receivers (`BootReceiver`).
* **Zero Network Surface**: `android.permission.INTERNET` is **completely omitted** from `AndroidManifest.xml`. The Android OS physically rejects any network socket creation.

---

## 4. Privacy & Data Security

1. **Local Storage Only**: All data is stored exclusively in private SQLite database files on your device (`/data/data/com.habit1.app/databases/`).
2. **No Backend or Cloud Service**: There are no servers, APIs, cloud accounts, or third-party SDKs.
3. **No Telemetry / Crash Reporting**: Zero analytical tracking (no Firebase, no Mixpanel, no Crashlytics).
4. **User-Controlled Backups**: Backup files are saved only to locations explicitly selected by the user via SAF.
5. **Unencrypted JSON with SHA-256 Checksum**: Exported backup files are transparent plain-text JSON. SHA-256 is used exclusively for payload integrity validation, not encryption. Keep your exported backups in secure storage.

---

## 5. Development & Testing

### Prerequisites
* JDK 21
* Android SDK 35 (`compileSdk = 35`, `minSdk = 26`)

### Build Commands
```bash
# Run unit tests (Robolectric & JVM)
./gradlew testDebugUnitTest

# Run release unit tests
./gradlew testReleaseUnitTest

# Run Android Lint static analysis
./gradlew lintDebug

# Build debug APK
./gradlew assembleDebug

# Build optimized, R8-minified release APK
./gradlew assembleRelease
```

### Automated Test Suite
The test suite consists of **177 automated unit and integration tests** verifying:
* Calculation of streaks across leap years, year boundaries, and alternate schedules.
* Consistency statistics and historical snapshot evaluations.
* Daily Goal independent completion and cascading subtask deletions.
* Backup validation, schema versioning, canonical SHA-256 checksums, and transactional rollback.
* Exact alarm reminder calculations and timezone reconciliation.
* Daily Review CRUD operations and isolation from streak/habit metrics.

---

## 6. Physical Device Verification

Physical hardware testing procedures (Cold Start, Memory Profiling, Doze Mode, and Reboot Recovery) are documented in:

* [docs/PHYSICAL_DEVICE_VERIFICATION.md](docs/PHYSICAL_DEVICE_VERIFICATION.md)

---

## 7. License

Local-first, privacy-focused open source software.
