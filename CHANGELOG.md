# Changelog

All notable changes to the Habit1 project are documented in this file.
This changelog is derived directly from the verified Git repository commit history and phase completions.

The project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [2.0.0] - 2026-11-26

### Major Redesign, AMOLED Theme, App Lock & Performance Release

#### Added & Improved
* **Home Screen & Habit Interaction Redesign**:
  * Modern Material 3 structural card redesign with refined spacing, elevation, and distinct visual container hierarchy.
  * Mutually exclusive, interactive checkmark (Done) and crossmark (Missed) actions with clear inline status text tags.
  * Contextual Habit Action Sheet for logging skips, notes, pausing, archiving, and editing without cluttering the main screen.
  * Compact daily completion ring indicator and live remaining habits counter.
* **RGB Color Wheel & Habit Form Enhancements**:
  * Dual-mode color selection: standard curated preset palette and interactive sweep-gradient HSV color wheel.
  * Real-time HSV gesture picker with brightness slider, individual RGB numeric channel sliders (0–255), and hex code synchronization.
  * Quantity preset example chips (e.g. *2.5 L Water*, *5 km Run*, *10,000 Steps*, *500 kcal*) for immediate measurement clarity.
  * Restructured habit form placing time-of-day reminder above tracking start date with clear *"Get notified"* status indicators.
* **Habits Screen Sleek Search & Unified Filters**:
  * Streamlined, thin search bar with placeholder *"Search habits"*.
  * Unified status filter dropdown menu (*All*, *Active*, *Paused*, *Archived*) eliminating visual chip clutter.
* **Habit History & Analytics Reorganization**:
  * Restructured Habit History screen into a clear, natural analytical flow:
    1. **Completion Trend**: Daily score trend and distribution graphs.
    2. **Calendar (Activity Heatmap)**: Range presets with 5-state accessible status grammar.
    3. **Quantitative Performance**: Daily bars vs cumulative running total, total volume, active day average, and target distribution.
    4. **Patterns & Consistency**: Best streaks timeline, weekday adherence, and consistency breakdown.
  * Strict calendar boundary enforcement: Week view strictly constrained to Monday–Sunday; Month view strictly constrained to 1st through end-of-month (zero inter-month bleed).
* **Settings, Privacy, & Storage Transparency**:
  * True AMOLED Black theme option (`#000000`) for maximum power efficiency on OLED screens.
  * App Lock security subsystem supporting Biometric authentication (fingerprint/face) and device credentials with customizable timeout intervals.
  * Accurate, physical on-device storage audit reporting exact sizes for Room database (`habit_app.db`, WAL, SHM), DataStore preferences, and temporary cache.
  * Standalone CSV and JSON data export with date filtering.
  * Robust Storage Access Framework (SAF) backup and restore with cached streams, SHA-256 verification, and dual restore modes (*Merge* / *Replace All*).
* **Packaging & Performance Optimization**:
  * R8 minification and resource shrinking reducing installed app size to **2.58 MB** (over 90% footprint reduction).
  * Maintained total device footprint under **3.5 MB** including active historical records.
  * Extended test suite to **717 passing automated unit tests** across 57 test suites.

---

## [1.0.0] - 2026-09-24

### Initial Production Release

#### Added
* **Phase 1: Project Initialization & Core Infrastructure**
  * Android Gradle Plugin 8.6.1 with Kotlin 2.0.20 and Compose BOM 2024.09.02.
  * Explicit manual Dependency Injection (`AppContainer`, `DefaultAppContainer`) owned by `HabitApplication`.
  * Multi-module packaging structure (`core`, `data`, `di`, `domain`, `platform`, `ui`).
* **Phase 2: Local Database & Persistence**
  * Authoritative SQLite database via AndroidX Room 2.6.1 running in Write-Ahead Logging (WAL) mode.
  * Foreign key enforcement enabled via `PRAGMA foreign_keys = ON;`.
  * Reactive Flow DAO queries for habits, habit records, daily goals, subtasks, and daily reviews.
* **Phase 3: Pure Kotlin Domain Engine**
  * Pure Kotlin domain models (`Habit`, `HabitRecord`, `DailyGoal`, `DailyReview`) with zero Android dependencies.
  * Streak calculation engine (`CalculateStreaksUseCase`) supporting pending-today forgiveness and schedule-specific streak rules.
  * Schedule evaluation use case (`EvaluateScheduleUseCase`) supporting Daily, Specific Days, and Interval schedules.
  * Progress recording use case (`RecordHabitProgressUseCase`) enforcing immutable historical record snapshotting.
* **Phase 4: Design System & Today Dashboard**
  * Material 3 dark/light theming with accessible contrast tokens and dynamic color switching.
  * Action-first Today dashboard with circular check controls and progress tracking.
  * Lightweight, stack-based navigation using sealed interface `Screen` and AndroidX `BackHandler`.
* **Phase 5: Habit Management & Measurement Engine**
  * Four measurement types: `BOOLEAN` (binary), `COUNT` (integer repetitions), `DURATION` (minutes), and `QUANTITY` (custom units).
  * Color customization, pause, archive, delete, and manual reordering.
  * Habit templates library (`HabitTemplatesScreen`) for rapid setup.
* **Phase 6: Daily Goals & Subtasks**
  * Daily goal planning tied to specific civil calendar dates.
  * Independent subtask completion (subtask completion does not force goal completion, and vice-versa).
  * Goal shifting to tomorrow with instant snackbar undo and History recovery to today.
* **Phase 7: History, Heatmaps & Meaningful Analytics**
  * Month view calendar heatmap and Year view matrix.
  * Daily drill-down inspection for past calendar dates.
  * Quantitative performance analytics, completion trend graphs, and frequency distribution cards.
  * Historical aggregate retention (`DailyGoalHistoryAggregateEntity`) preserving completion ratios during goal cleanups.
* **Phase 8: Platform Reminders & Exact Alarms**
  * Exact alarm scheduling via platform `AlarmManager` (`setExactAndAllowWhileIdle`) with battery-conscious event execution.
  * Automatic reminder recovery after device reboot or clock/timezone changes via `BootReceiver`.
  * Notification channels with non-destructive, reminder-only semantics.
  * Optional reminders for daily goals (Schema Version 4).
* **Phase 9: Local Backup, Export & Restore**
  * Storage Access Framework (SAF) integration for user-controlled file picking (`CreateDocument`, `OpenDocument`).
  * Canonical UTF-8 JSON backup envelope format (`BackupEnvelopeDto`).
  * Cryptographic SHA-256 payload checksum verification using constant-time byte comparisons (`MessageDigest.isEqual`).
  * Dual restore modes: Merge (non-destructive conflict resolution) and Replace All (single atomic Room transaction with rollback safety).
* **Phase 10: Daily Reflection, Release Hardening & Verification**
  * Optional subjective daily reflection note and mood tags (Calm, Energized, Focused, Tired, Grateful) strictly isolated from streak calculations.
  * R8 minification and resource shrinking reducing release APK size to 1.71 MB.
  * Complete 325-test automated unit test suite across 49 test suites (100% passing).
  * Physical device verification protocol on Android 12/15 hardware.
