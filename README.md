# Reminder App — Android

A lightweight, **offline-first** reminder app with natural language input and voice support. No cloud, no LLM APIs, minimal UI — just fast, reliable reminders.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Clean Architecture |
| DI | Hilt |
| Database | Room (SQLite) |
| Scheduling | AlarmManager + WorkManager |
| Voice Input | Android SpeechRecognizer API |
| Async | Kotlin Coroutines + Flow |
| Preferences | DataStore |
| Serialization | Gson |

---

## Project Structure

```
app/src/main/java/com/reminderapp/
│
├── ReminderApplication.kt          # Hilt app class, WorkManager init
├── MainActivity.kt                  # Entry point, edge-to-edge, nav
│
├── data/
│   ├── local/
│   │   ├── Converters.kt           # Room TypeConverters (LocalDateTime, enums)
│   │   ├── ReminderDatabase.kt     # Room database definition
│   │   ├── dao/
│   │   │   └── ReminderDao.kt      # All DB queries (Flow-based)
│   │   └── entity/
│   │       └── ReminderEntity.kt   # DB entity + domain mappers
│   └── repository/
│       ├── ReminderRepository.kt   # Interface
│       ├── ReminderRepositoryImpl.kt
│       └── SettingsRepository.kt   # DataStore preferences
│
├── domain/
│   ├── model/
│   │   └── Reminder.kt             # Domain model, enums, ReminderParseResult
│   └── parser/
│       └── NaturalLanguageParser.kt  # Rule-based NLP (no AI/ML)
│
├── di/
│   ├── DatabaseModule.kt           # Room + Repository bindings
│   └── SchedulerModule.kt          # AlarmManager provision
│
├── scheduler/
│   ├── ReminderScheduler.kt        # Orchestrates AlarmManager + WorkManager
│   ├── AlarmReceiver.kt            # Handles alarm broadcasts
│   └── BootReceiver.kt             # Reschedules after reboot
│
├── notification/
│   ├── NotificationHelper.kt       # Channels, notification builder
│   ├── NotificationActionReceiver.kt  # Complete/Snooze actions
│   ├── ReminderNotificationWorker.kt  # WorkManager worker
│   └── AlarmActivity.kt            # Full-screen alarm UI
│
├── speech/
│   └── SpeechRecognitionManager.kt # Android SpeechRecognizer wrapper
│
├── util/
│   └── BackupRestoreManager.kt     # JSON export/import
│
└── ui/
    ├── NavGraph.kt                  # Navigation routes
    ├── theme/
    │   ├── Theme.kt                 # Material 3 color schemes (dynamic + static)
    │   └── Typography.kt
    ├── home/
    │   ├── HomeScreen.kt            # Main screen: upcoming/completed/missed
    │   └── HomeViewModel.kt
    ├── addreminder/
    │   ├── AddReminderScreen.kt     # Form: title, date/time, type, mode
    │   └── AddReminderViewModel.kt  # Also handles NLP parse + voice
    ├── reminderdetail/
    │   └── ReminderDetailScreen.kt  # Detail view + ViewModel
    ├── settings/
    │   └── SettingsScreen.kt        # Settings + ViewModel
    └── components/
        ├── ReminderCard.kt          # Reusable reminder card
        ├── SectionHeader.kt         # Collapsible section header
        └── AssistantInputBar.kt     # Bot FAB pill + confirmation sheet
```

---

## Features

### Home Screen
- **Upcoming** — always expanded, shows top 3 reminders with "See All"
- **Completed** — collapsed by default, expandable with count badge
- **Missed** — collapsed by default, expandable with count badge
- Two FABs: `+` (form) and `🤖` (assistant input)
- Overdue active reminders automatically marked MISSED on app open

### Add Reminder (Form)
- Title, description, date picker, time picker
- Reminder type: One-time / Daily / Weekly / Monthly / Yearly
- Notification mode: Notification / Alarm

### Assistant Input (Natural Language)
- Expandable pill from bottom-right bot FAB
- Text field + mic button + send button
- Rule-based parser — **no AI, no internet required**
- Confirmation bottom sheet with Edit / Save / Cancel
- Supports:
  - `"Pay electricity bill tomorrow 9am"` → one-time
  - `"Doctor appointment next Sunday 4pm"` → weekly
  - `"Every month on 5th pay credit card"` → monthly recurring
  - `"Every year on june 10 anniversary"` → yearly recurring
  - `"Every day morning standup 9am"` → daily recurring

### Notifications
- **Notification mode**: WorkManager schedules `ReminderNotificationWorker`, shows notification with Complete + Snooze actions
- **Alarm mode**: AlarmManager fires `AlarmReceiver`, launches full-screen `AlarmActivity` with sound + vibration, snooze options (5/10/30/60 min)

### Settings
- Default notification mode (Notification / Alarm)
- Default snooze duration (5 / 10 / 30 / 60 min)
- Export backup (JSON to device storage)
- Import backup (JSON restore)

### Persistence
- Room with indexes on `reminderDateTime` and `status`
- Survives process kill, reboots (`BootReceiver` reschedules all active alarms)
- DataStore for preferences

---

## Setup

### Requirements
- Android Studio Ladybug or newer
- Android SDK 26+ (minSdk 26)
- JDK 17

### Steps
```bash
git clone <repo>
cd ReminderApp
# Open in Android Studio → Sync Gradle → Run
```

### Permissions requested at runtime
- `POST_NOTIFICATIONS` (Android 13+) — for notifications
- `RECORD_AUDIO` — for voice input (requested only when mic button tapped)
- `SCHEDULE_EXACT_ALARM` — for alarm mode (direct to settings on Android 12+)

---

## Architecture Decisions

### Why AlarmManager for alarms?
WorkManager is inexact by design — it batches work for battery efficiency. For alarm-type reminders that need to fire at an exact time (with sound/vibration), `setExactAndAllowWhileIdle` is required.

### Why WorkManager for notifications?
Standard notifications that can fire within a minute window use WorkManager — it handles Doze mode, battery optimization, and process death more gracefully than raw handlers.

### Why rule-based NLP?
The spec explicitly forbids LLM APIs and requires offline-first. The rule-based parser covers all spec patterns (today/tomorrow/next weekday/specific dates, 12h times, recurring patterns) with zero dependencies and zero latency.

### MVVM with Hilt
Each screen has its own ViewModel. The ViewModel talks to the Repository via Kotlin Flow. UI observes StateFlow with `collectAsStateWithLifecycle()` for lifecycle safety.

---

## Reminder Status Flow

```
ACTIVE ──(time passes, not completed)──► MISSED
ACTIVE ──(user marks complete)──────────► COMPLETED
MISSED ──(user marks complete)──────────► COMPLETED
```

Recurring reminders: on completion or expiry, `ReminderScheduler.rescheduleRecurring()` creates the next occurrence automatically.

---

## Not Implemented (v1 exclusions per spec)
- Search reminders
- Reminder categories
- Widgets
- WearOS support
- Calendar view
- Productivity dashboard
- Chat history in assistant
