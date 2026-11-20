# How ONE MORE YOU works

ONE MORE YOU is an Android app that helps you stay off selected apps. It works on the phone only. It does not send your app list to a server.

The project has two parts:

- **engine** decides the rules. It does not draw screens and it does not talk to the phone.
- **app** is what you see and what runs on the phone. It asks the engine what to do, then blocks the app or lets it open.

## What you can do

| What | What it is for |
| --- | --- |
| Shared schedule | Close every selected app during the hours you choose. One schedule is used for all of those apps. Weekends can stay off. The hours can cross midnight, such as 9 PM to 7 AM. |
| Daily limit | Give one app a time budget for the day. You can use the same hours and minutes every day, or a different amount for each weekday. |
| Lower a limit | Save a smaller limit right away. Making the limit smaller does not need a task. |
| Raise or delete a limit | Stop a quick change that gives you more time. The save or delete button opens only after you type a paragraph exactly. A limit of 0 is not allowed. |
| Learning pass | Open one blocked app for a short time. Other blocked apps stay blocked. A countdown shows in a notification. |
| Block screen | Cover a blocked app so you cannot use it. Leave sends you back to the home screen. |
| Home | Show today's schedule and how much of each limit you have used. |
| Settings | Turn on the permissions the app needs, and turn the uninstall lock on or off. |
| Uninstall lock | Ask Android to refuse a direct uninstall while the lock is on. Turning the lock off inside the app needs the same paragraph. The phone's own uninstall screen can still turn the lock off and remove the app. |

## The engine

The engine is plain Kotlin. It can be tested on a computer without a phone. That keeps the rules easy to check.

| Piece | What it does | Why it is separate |
| --- | --- | --- |
| Models | Holds the schedule, the limits, the pass, and the saved names of apps. | One place for the facts the rest of the app uses. |
| Schedule rules | Checks whether the current time is inside today's window, including overnight hours. | The clock rule should not depend on a screen. |
| Usage calculator | Turns today's usage into a simple amount of time. | The limit rule needs a clear number. |
| Pass rules | Decides if a learning pass is still running and how many passes are left today. | A pass must expire even if the screen changes. |
| Blocking engine | Looks at the open app, the schedule, the limit, and the pass, then says blocked or allowed. | This is the main decision. The phone only follows it. |
| Config codec | Turns those saved facts into text and back again. | The phone can store one small file and read it later. |

## The app

| Piece | What it does | Why it is there |
| --- | --- | --- |
| Main screen | Opens the app and starts the watcher. | The phone needs a place to launch. |
| Navigation | Shows Home, Apps, Limits, and Settings at the bottom. | You move between those jobs without leaving the app. |
| Home | Shows today's window and a bar for each limited app. | You can see the plan and the time already used. |
| Apps | Lets you pick apps and set the shared days and hours. | The schedule is for the whole group, not one app at a time. |
| Limits | Lets you add an app, set hours and minutes, and delete a limit. | A time budget is separate from the schedule. |
| Settings | Shows permissions, setup steps, and the uninstall lock. | Setup stays out of the home screen after it is done. |
| Saved settings | Stores the schedule, limits, and passes on the phone. | Your choices stay after you close the app. |
| Installed apps | Reads the apps on the phone so you can pick one. | You should only choose apps that are actually installed. |
| Usage tracker | Asks Android which app is open and how long it has been used today. | The limit needs real usage, not a guess. |
| Watcher | Stays running and checks the open app about once a second. | Blocking has to happen when you open the app, not only when ONE MORE YOU is on screen. |
| Coordinator | Asks the engine, then shows the block screen, hides it, or starts the pass countdown. | One place connects the rules to the screen. |
| Block overlay | Draws the pause screen on top of the blocked app. | The other app stays installed, but you cannot use it while the cover is up. |
| Pass notification | Shows the remaining pass time in the notification shade. | You can see the countdown without a popup over the app. |
| Permissions | Opens the phone screens for display over other apps, usage access, and alarms. | Android hides those switches from normal apps. |
| Boot receiver | Starts the watcher again after the phone restarts. | A reboot should not silently turn blocking off. |
| Uninstall lock | Registers the app as a device admin while the lock is on. | Android then refuses a direct uninstall. |

## Tools

| Tool | What it is | Why this project uses it |
| --- | --- | --- |
| Kotlin | The programming language. | It is the usual language for modern Android apps, and the same language can run the rules on a computer. |
| Android Gradle Plugin | Builds the installable app file. | It packs the phone app into an APK. |
| Jetpack Compose | Draws the screens from Kotlin. | The home, apps, limits, and settings screens are built with it instead of older XML layouts. |
| Material 3 | Android's standard buttons, text, and colors. | The screens follow a familiar Android look. |
| Navigation Compose | Switches the four tabs. | The bottom bar needs a simple way to open each screen. |
| DataStore | A small store on the phone. | The schedule and limits should survive closing the app. |
| Kotlin serialization | Turns the saved settings into JSON text. | DataStore stores text. Serialization keeps that text in a clear shape. |
| Usage access | An Android permission. | The app must see which app is in front and how long it has been used. |
| Display over other apps | An Android permission. | The block screen has to sit on top of Instagram, YouTube, or any other chosen app. |
| Foreground service | A background job Android keeps alive. | The watcher must keep running after you leave ONE MORE YOU. A notification is required for that job. |
| Device admin | An Android switch for the uninstall lock. | It is the normal way for an app to ask the phone to block a direct uninstall. |
| JUnit | A test runner. | The engine tests check the schedule, the usage math, and the block decision without a phone. |
| GitHub Actions | A build that runs on GitHub when you start it. | You can make a release APK without building it on your own PC. It runs only when you click **Run workflow**. |

## How a block happens

1. The watcher asks Android which app is open.
2. The coordinator sends that app, the current time, and today's usage to the engine.
3. The engine allows the app if a learning pass is still running for that app.
4. Otherwise the engine blocks it when the shared schedule is active, or when today's limit is used up.
5. The overlay covers the app. If you start a pass, the overlay goes away and the notification counts down. When the time ends, the app is blocked again.

## Later

The release APK build is manual, and only the engine rules have unit tests. Add the rest later:

- Run `:engine:test` inside the GitHub build, and run that build on every push, not only when someone clicks **Run workflow**.
- Add automated tests for the phone screens: Schedule, the schedule lock, the uninstall lock, Limits, and Settings.
- Keep those screen tests in the same build so a push checks the rules and the screens before an APK is published.
