# Learning ONE MORE YOU

ONE MORE YOU is a small rules engine with an Android shell around it. The phone watches which app is open. The engine says blocked or allowed. The shell draws a cover when the answer is blocked.

This note is for reading the code. Day-to-day behavior is in [HOW-IT-WORKS.md](HOW-IT-WORKS.md). The screens are in [CURRENT-UI.md](CURRENT-UI.md).

## The one decision

Everything interesting collapses into this check in `engine/src/main/kotlin/com/onemoreyou/blocking/BlockingEngine.kt`:

```kotlin
val covered = input.monitored || input.usageLimit?.enabled == true
val blocked = covered && (limitReached || (scheduleActive && !pass.active))
```

Read it as three questions:

1. Is this app one you chose? It is covered if it is on the schedule list, or if it has a daily limit turned on.
2. Is the daily time used up? If yes, block it. A learning pass does not override a used-up limit.
3. Is the clock inside the schedule, and is there no active pass? If yes, block it.

If none of those fire, the app stays open. Settings and ONE MORE YOU itself are never covered.

That function does not know about screens, notifications, or Android. It only receives facts and returns a `BlockDecision`. That is why it can be tested on a computer. `BlockingEngineTest`, `ScheduleRulesTest`, and `UsageCalculatorTest` feed it fake times and fake usage and check the answer.

## Two modules

| Module | What it is | What it must not do |
| --- | --- | --- |
| `engine` | Plain Kotlin. Models, schedule math, usage math, pass math, the block decision. | Draw a screen or call Android. |
| `app` | The phone. Screens, permissions, storage, the watcher, the cover. | Invent its own block rule. It asks the engine. |

The rules stay testable because they do not depend on a phone. The phone stays thin because it only gathers facts and follows the decision.

## What gets saved

`BlockerConfig` in `engine/src/main/kotlin/com/onemoreyou/domain/Models.kt` is the whole memory of the app:

- **monitored** — apps on the shared schedule.
- **sharedSchedule** — one set of days and hours for all of those apps. The default is weekdays, 9:00 to 19:00. Minutes are stored as minutes since midnight, so 9:00 is `9 * 60`.
- **limits** — a daily budget per app, either one number for every day or a different number per weekday.
- **allowances** — how many passes that app gets (0, 1, or 2) and how long each one is (5, 10, or 15 minutes).
- **passStates** — how many passes were used today, and the timestamp when the current pass ends.

The phone stores that object as JSON through DataStore (`SettingsRepository`). Closing the app does not erase it. The screens only edit this object. They do not block anything themselves.

An overnight window is just "start minute is later than end minute." Friday 21:00 to 07:00 means: after 21:00 on Friday, and also before 07:00 on Saturday, because Saturday morning is the spill from Friday. `ScheduleRules.activeHit` checks both today's window and yesterday's spill.

## What happens when you open a blocked app

1. `MainActivity` starts `BlockerWatchService`, a foreground service. Android keeps it alive after you leave ONE MORE YOU, and it must show a notification.
2. About once a second the watcher asks Android which app is in front (`UsageTracker`) and tells `BlockingCoordinator`.
3. The coordinator loads today's usage for that package, then calls `BlockingEngine.evaluatePackage`.
4. If the decision is blocked, `OverlayController` draws a screen on top of the app. **Go to home screen** leaves it. **Use a pass** calls `PassRules.grant`, which writes a new `LearningPassSnapshot`: one more pass used, and `activeUntil` set to now plus the pass length.
5. While a pass is running, the same loop runs every second. The engine sees `pass.active` and says not blocked. The cover hides and a notification counts down. When `activeUntil` is in the past, the next evaluation blocks again.

Usage from Android arrives a bit late, so `rememberLiveUsage` in `BlockingCoordinator` keeps a local stopwatch for the app that is open right now and uses whichever number is larger. A limit can hit while you are still inside the app, instead of only after you leave it.

A pass is only offered for a schedule block. If the daily limit is already used up, the pass button stays off.

## Why the permissions exist

Android does not let a normal app do these things without a special switch:

- **Usage access** reads which app is open and how long it has been open today.
- **Display over other apps** draws the cover on top of the blocked app.
- **Exact alarms** wake the phone at the schedule's start and end, even if the watcher was frozen. `WakeScheduler` plans the next edge.
- **Notifications** are required for the foreground watcher and the pass countdown.
- **Device admin** is the uninstall lock. While it is on, Android refuses a direct uninstall. Turning it off inside the app requires the typed prayer. The phone's own uninstall screen can still remove the admin and then the app.

The prayer is a friction gate, not a password. The text is on the screen. You type it one character at a time, so raising a limit or unlocking the schedule is slow. Paste does not count.

## A good order to read the code

1. `engine/src/main/kotlin/com/onemoreyou/domain/Models.kt` — the nouns.
2. `ScheduleRules.kt`, then `PassRules.kt`, then `BlockingEngine.kt` — the verbs. Read a test beside each one.
3. `BlockingCoordinator.evaluate` — where the phone obeys the decision.
4. `BlockerWatchService` — the loop that keeps asking.
5. `OneMoreYouRoot` and one screen, such as `HomeScreen` — how the saved config is shown.

Skip the theme and layout files until the decision path is clear. Those files only change colors and spacing.
