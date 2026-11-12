# PumpLog — build plan

PumpLog: personal Android app replacing the "Workout Tracker" Google Sheet. (App ID stays `com.workouttracker` so installs update in place.)
Designs: https://claude.ai/artifact/LaAwjhActFsfFBiwbLRA9p — 0 First launch · 1 Today · 2 This week · 3 Live session · 4 Drop set & note · 5 Exercise progress · 6 Routine editor · 7 Home-screen widget.

## Decisions

| Topic | Decision |
|---|---|
| Data from the sheet | None. Start fresh: no import, no export. Routines are pre-loaded (below), history starts empty. |
| Units | Stored in kg; kg/lb toggle in settings converts for display and entry. |
| Dumbbell weights | Per dumbbell (30 = one 30 kg dumbbell in each hand). |
| Bodyweight moves | Weight field = added weight only (0 = bodyweight, 20 = +20 kg). |
| Schedule | Fixed rotation Upper → Lower → Push → Pull → Legs, independent of weekday. Next = the routine after the most recent *finished or skipped* session. Days you don't open the app don't advance it. Today screen has "Skip <routine>" and "Pick another". |
| Skipping | "Skip" records a SKIPPED session for that routine on today's date and moves the rotation to the next routine. Undo-able from This week. Skips show in the week strip and count as "skipped", never as a workout. |
| Workout days & weekly work | Every session carries its date. "This week" screen (Mon–Sun): day strip (done / skipped / rest / today), totals for workouts, sets, volume, time with last week's numbers alongside, an 8-week bar chart (Sets / Volume / Days), and the week's sessions. "+" logs a past workout on a chosen date. |
| Rest timer | Per-exercise rest time, defaulted from the rep range (top ≤ 6 → 3:00, ≤ 10 → 2:00, else 1:30), editable. +15s / Skip while running. |
| Extras in v1 | Home-screen widget (next routine + Start, or Resume if a session is open). |
| Phone | Android 12+ → `minSdk 31`. |
| Tooling | Android Studio for editing/running. Command-line builds use Homebrew `openjdk@17`: Android Studio's bundled Java 25 is too new for Gradle 8.11. |
| Backup | Android Auto Backup left on, so data survives a phone change. No in-app export. |

## Stack

- Kotlin, Jetpack Compose, Material 3 (fixed dark theme from the designs: bg `#111214`, surface `#1B1D20`, accent `#CBF35C`; Inter + Space Grotesk).
- Room for storage, DataStore for settings, Navigation Compose.
- Manual dependency container (single module, no Hilt).
- Vico for charts.
- Foreground service + notification for the rest timer (keeps running with screen locked).
- Jetpack Glance for the widget.
- Single `app` module.

## Data model

```
Exercise(id, name, kind[BARBELL|DUMBBELL|CABLE|MACHINE|BODYWEIGHT])
Routine(id, name, rotationOrder)
RoutineExercise(id, routineId, exerciseId, order, isRpt, restSec, alternativeExerciseId?)
PlannedSet(routineExerciseId, setIndex, minReps, maxReps?, note?)   // maxReps null = "10+"
Session(id, routineId, status[IN_PROGRESS|DONE|SKIPPED],
        date, startedAt?, finishedAt?)                               // SKIPPED has no sets or times
LoggedSet(id, sessionId, plannedExerciseId, performedExerciseId,
          setIndex, type[NORMAL|DROP|WARMUP|FAILURE], note?, completedAt)
SetSegment(loggedSetId, order, reps, weightGrams)                    // drop set = 2+ segments
```

- Weights as integer grams (no float drift); lb conversion only at the UI edge.
- Swap = `performedExerciseId != plannedExerciseId`; history stays attached to the planned exercise.
- Each set is written the moment it's ticked; an unfinished session is resumed on next launch.

## Logic rules

- **Progression hint**: if every working set last time reached the top of its rep range, suggest +increment on each set. Default increment 2.5 kg (barbell/cable/machine), 2 kg (dumbbell, per dumbbell); editable.
- **Est. 1RM**: Epley `w × (1 + reps/30)`, best segment per session; bodyweight moves use added weight only.
- **Volume**: Σ reps × weight over working sets (warm-ups excluded).
- **PR**: new best est. 1RM or best set weight for that exercise.
- **Rotation**: next = routine after the latest DONE or SKIPPED session by `date` (ties: the one recorded last), so a backdated "past workout" only moves the rotation if it's the latest entry.
- **Weekly totals** (week = Mon–Sun, device time zone): workouts = DONE sessions; skipped = SKIPPED sessions; sets = completed working sets; volume as above; time = Σ (finishedAt − startedAt). Past workouts logged without times count toward sets/volume but not time.

## Pre-loaded routines (from the sheet)

**Upper** — Bench Press 4 × 4–6 · Lat Pulldown 3 × 6–8 · Standing Overhead Press 3 × 8–10 · Dumbbell Pullover 3 × 10–12 · High to Low Cable Flies 3 × 12–15 · Lying Face Pulls 3 × 10–12

**Lower** — Front Squat 3 × 10–12 · Deadlift 4 × 6–8 · Barbell Hip Thrust 4 × 8–12 · Single Leg Weighted Calf Raise (per side) 3 × 6–8 · Leg Press Calf Raise 3 × 8–12

**Push** — Incline Dumbbell Press 3 × 8–10 · Flat Dumbbell Press (RPT) 6–8 / 8–10 / 10–12 · Lateral Raises (Cable & Dumbbell) 4 × 8–12 · Banded Push-Ups 2 × 10+ · Overhead Rope Extensions 3 × 12–15 · Bar Triceps Pushdowns 3 × 8–12

**Pull** — Weighted Pull-Ups (RPT) 4–6 / 6–8 / 8–10 · Seated Row 3 × 8–10 (alt: Dumbbell Rows) · Smith Machine Shrugs 3 × 10–12 · Kneeling Face Pulls 4 × 10–15 · Incline Dumbbell Curls 3 × 8–10 · Hammer Curls 3 × 8–10 · Scapular Pull-Ups 2 × 5–10+

**Legs** — Back Squat 3 × 6–8 + set 4: 8–10 slow eccentric @ 60% · Bulgarian Split Squat (per side) 4 × 8–10 · Glute Ham Raise 4 × 10–12 · Smith Machine Calf Raises 3 × 10–15 · Seated Weighted Calf Raise 3 × 10–15

## Milestones

| # | Scope | Done when |
|---|---|---|
| 0 | Install Android Studio; create project (Compose, M3 theme, Room, minSdk 31) | Empty themed app runs on the phone |
| 1 | Data layer, seed routines, unit tests for rotation / progression / 1RM | Tests green; fresh install shows 5 routines |
| 2 | First launch + Today (screens 0–1) + routine editor (screen 6) | Rotation picks the right next routine; Skip moves it on and can be undone; routines editable and reorderable |
| 3 | Live session + set sheet + rest timer service (screens 3–4) + Start / Resume / Pick another | Full workout logged with the screen locked between sets; killed app resumes the session |
| 4 | This week + exercise progress (screens 2, 5) | Week strip and totals match logged sessions; 8-week chart; past workout can be added on a chosen date; per-exercise chart, best set, PRs |
| 5 | Widget, kg/lb setting, signed release APK | Widget starts/resumes a workout; APK installs over USB |

Milestones 0–3 = usable replacement for the sheet.

### Progress
- **0 — done.** Gradle 8.11.1 wrapper (checksums verified), AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, Room 2.6.1, minSdk 31 / target 35, dark theme, adaptive icon, no INTERNET permission, backup rules.
- **1 — done.** Room schema v1 (exported to `app/schemas/`), seeded routines, settings (DataStore), rotation / progression / 1RM / weekly logic, routine badges.
- **2 — done** (checked on the emulator 2026-11-08). First launch screen (rotation preview, kg/lb, Continue / Review routines), bottom navigation (Today, Routines), Today with Skip + Undo and an Edit link, Routines list with move up/down, routine editor (name, per-set min/max with open-ended "+", add/remove sets, rest ±15 s, RPT switch, alternative, reorder/remove exercises, add exercise with suggestions from existing exercises, delete routine with confirm, discard-changes prompt, inline validation). 43 unit tests.
  - History and Progress tabs join the bottom bar in milestone 4.
- **3 — done** (branch `milestone-3-logging`, checked on the emulator 2026-11-08).
  - Today: Start <next>, Pick another, and an In progress card with Resume. Only one workout can be open at a time.
  - Live workout screen: one card per exercise (the next unfinished one opens automatically), per-set rows with last time's numbers, weight pre-filled from the suggestion or last time, reps placeholder = target range, tick to log / untick to undo, extra sets, progression hint when every set hit the top of its range last time.
  - Set sheet: Normal / Drop set / Warm-up / Failure, up to 5 drop blocks, note, remove set.
  - Swap for today: alternative, search existing exercises, or a new name; logged sets follow the swap; history stays with the planned exercise.
  - Finish (counts logged vs planned) or Discard; finishing advances the rotation.
  - Rest timer: in-app bar (+15 s / Skip) plus a foreground service (`specialUse`) whose notification counts down; "Rest's up" alert with vibration when it ends, also with the screen locked. Notification permission asked when the first workout opens.
  - Database v2: unique (session, exercise, set) index on `logged_set`, added by an automatic migration from v1 (tested by upgrading the emulator's v1 install).
  - Known nits: the rest bar keeps the "Next:" name it started with if you swap mid-rest; "Last time" shows numbers without a unit.
- **4 — done** (branch `milestone-4-history`, checked on the emulator 2026-11-08).
  - Rename exercise (routine editor): renames it everywhere, history included; refuses a name another exercise already has (case-insensitive).
  - Reopen: Today shows "✓ <routine> finished at <time> · Reopen" for the last workout finished today; reopening puts it back in progress and the rotation steps back until it is finished again.
  - History tab ("This week"): Mon–Sun strip (routine badge / skip / rest / today), workouts · sets · volume · time with last week's numbers, 8-week bar chart (Sets / Volume / Days), previous/next week, the week's workouts and skips (Undo on skips).
  - Edit past workouts: tap a workout in History → same workout screen in edit mode (no rest timer or suggestion, "Done", "Delete workout"). "Last time" there means the workout before it.
  - Log a past workout: "+" → pick a day (past year, no future) → pick a routine → fill in its sets. Logged without times, so it adds sets and volume but not time; it only moves the rotation if it is the most recent entry.
  - Progress tab: exercises with history, most recent first → per-exercise screen with Est. 1RM / Top set / Volume chart (last 20 workouts), change since the first, best set, best volume, PR date, and every workout's sets.
  - Bottom bar now has four tabs: Today · History · Progress · Routines.
  - Known nits: a long drop-set chip in Progress history gets cut off; the first week label on the History chart can be cut to just the day number. Swapped sets are filed under the planned exercise (e.g. Seated Row done in place of Lat Pulldown shows under Lat Pulldown in Progress) — by design, but the name can surprise.

### Build commands
```bash
export JAVA_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
./gradlew testDebugUnitTest     # unit tests
./gradlew assembleDebug         # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug          # onto a connected phone / running emulator
```

Emulator (disk is tight, so shrink its storage; needs ~7.4 GB free):
```bash
~/Library/Android/sdk/emulator/emulator -avd Medium_Phone_API_37.0 -partition-size 2048
```

### Rest-alert fix (branch `fix-rest-alert`, 2026-11-12)
- Problem on the real phone (OnePlus CPH2585, Android 16): `OplusHansManager` froze the app ~5 s after it left the
  screen, so the in-app countdown never fired "Rest's up". Notification permission and channels were fine.
- The alert now comes from an Android alarm-clock alarm (`AlarmManager.setAlarmClock`, permission `USE_EXACT_ALARM`)
  delivered to a non-exported receiver, which wakes the app even if it was frozen or killed. The countdown
  notification stays (its time is drawn by the system). The service no longer posts the alert itself, and is
  stopped with `stopService` so the alarm never has to start it from the background.
- Settings screen (gear on Today): kg/lb, rest-alert status (notifications, background activity, exact alarms) with
  shortcuts to the right system pages, and "Test rest alert" (fires in 10 s).
- Workout screen: warning if notifications are off; one-time tip to allow background activity; a rest that has
  already ended is no longer shown as a 0:00 bar.
- Emulator check: test alert with the app killed and the screen locked was delivered ~1 s after it was due.
- Phone check (2026-11-12): "Test rest alert" with the phone locked arrived, with background activity still restricted.
  Still worth watching during a real workout, where rests are 1.5–3 min rather than 10 s.

## Design changes (done 2026-11-08)
- Import screen removed.
- Today now shows the rotation (done / next / later) instead of a weekday strip, plus "Do a different routine today".
- New First launch screen (screen 0): the 5 pre-loaded routines in rotation order, kg/lb choice, "Start Upper".
- New widget designs (screen 6): 4×2 idle, 4×2 during a workout with rest countdown, 2×2 compact variants.
- Upper: second Lat Pulldown → Dumbbell Pullover (shown on First launch).
- Today: "Skip Push" + "Pick another" buttons; rotation strip shows a skipped routine.
- New "This week" screen (History tab): workout days, skips, weekly sets / volume / time, 8-week chart, log a past workout.
