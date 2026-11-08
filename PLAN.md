# Workout Tracker — build plan

Personal Android app replacing the "Workout Tracker" Google Sheet.
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
| 3 | Live session + set sheet + rest timer service (screens 3–4) | Full workout logged with the screen locked between sets; killed app resumes the session |
| 4 | This week + exercise progress (screens 2, 5) | Week strip and totals match logged sessions; 8-week chart; past workout can be added on a chosen date; per-exercise chart, best set, PRs |
| 5 | Widget, kg/lb setting, signed release APK | Widget starts/resumes a workout; APK installs over USB |

Milestones 0–3 = usable replacement for the sheet.

### Progress
- **0 — done.** Gradle 8.11.1 wrapper (checksums verified), AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01, Room 2.6.1, minSdk 31 / target 35, dark theme, adaptive icon, no INTERNET permission, backup rules.
- **1 — done.** Room schema v1 (exported to `app/schemas/`), seeded routines, settings (DataStore), rotation / progression / 1RM / weekly logic, routine badges.
- **2 — done** (checked on the emulator 2026-11-08). First launch screen (rotation preview, kg/lb, Continue / Review routines), bottom navigation (Today, Routines), Today with Skip + Undo and an Edit link, Routines list with move up/down, routine editor (name, per-set min/max with open-ended "+", add/remove sets, rest ±15 s, RPT switch, alternative, reorder/remove exercises, add exercise with suggestions from existing exercises, delete routine with confirm, discard-changes prompt, inline validation). 43 unit tests.
  - Moved to milestone 3: "Pick another" (it only matters once Start exists) and the Start button itself.
  - History and Progress tabs join the bottom bar in milestone 4.

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

## Design changes (done 2026-11-08)
- Import screen removed.
- Today now shows the rotation (done / next / later) instead of a weekday strip, plus "Do a different routine today".
- New First launch screen (screen 0): the 5 pre-loaded routines in rotation order, kg/lb choice, "Start Upper".
- New widget designs (screen 6): 4×2 idle, 4×2 during a workout with rest countdown, 2×2 compact variants.
- Upper: second Lat Pulldown → Dumbbell Pullover (shown on First launch).
- Today: "Skip Push" + "Pick another" buttons; rotation strip shows a skipped routine.
- New "This week" screen (History tab): workout days, skips, weekly sets / volume / time, 8-week chart, log a past workout.
