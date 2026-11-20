# ONE MORE YOU — current screens

This matches the app after the latest UI change. Behavior is unchanged. Some screens are still the older layout; those are marked below.

## Look

Five full themes. A theme sets the background, cards, lines, tab bar, block screen, and accent together. Default is **Midnight**. A saved Blue choice stays Midnight. Coral becomes Ember, Amber becomes Sand, Green becomes Forest, Violet becomes Dusk.

| Theme | Background | Card | Raised | Line | Tab bar | Strong border | Block screen | Accent |
|---|---|---|---|---|---|---|---|---|
| Midnight | `#12141C` | `#1C2030` | `#242A3D` | `#2C3348` | `#171A26` | `#4A5170` | `#0D0F15` | Blue `#5BA8FF` |
| Ember | `#17110F` | `#251A17` | `#30221E` | `#3B2B26` | `#1E1613` | `#5A4038` | `#100B0A` | Coral `#FF6B4A` |
| Sand | `#15130D` | `#231F15` | `#2E291C` | `#3A3322` | `#1B1810` | `#5A4E30` | `#0E0D08` | Amber `#FFC857` |
| Forest | `#0E1512` | `#16231C` | `#1E2E25` | `#27392F` | `#121B16` | `#3A5446` | `#09100C` | Green `#3DDC97` |
| Dusk | `#13111C` | `#1F1A2D` | `#292238` | `#322A44` | `#18142A` | `#4C3F66` | `#0D0B14` | Violet `#B388FF` |

Same in every theme: text `#F6F3EE`, muted `#C9C3B8`, faint `#9A958C`, good `#3DDC97`, time up `#FF6B4A`, warning `#FFC857`. Text on accent buttons is dark `#0B1220`.

Primary button: full width, 52dp, 14dp corners, accent fill. Secondary button: same size, 1.5dp strong border, no fill. Page padding 20dp. Titles 28sp extra bold.

Bottom tabs stay hidden until setup is done. Then: Home, Schedule, Limits, Settings. The tab bar uses the theme tab color. The active tab is the accent.

## 1. Setup

Shown first, and again if any permission is off. No tabs.

- Accent label **ONE MORE YOU**, and on the right `Step N of 5` (or of 4 when notifications are hidden).
- Title: **Let's get you set up**
- Line: "The app needs 5 permissions to close apps for you. Blocking starts when all 5 are on."
- Progress bar: finished steps divided by the total.
- One list. Each step has a number circle, a name, and a short reason.
  - Done: green check, "Done · reason". Closed.
  - Current (first one not done): accent border, open, with a How box and the full-width button.
  - Later: grey circle, "Next · reason". Closed.
- Steps:
  1. Show over other apps — "shows the block screen" — **Allow display over apps**
  2. See app usage — "counts your time in each app" — **Allow usage access**
  3. Alarms — "lets the schedule start and end on time" — How tells them to turn on Allow setting alarms — **Allow alarms**
  4. Watch the open app — "knows which app is open" — How: "In the list, tap ONE MORE YOU and turn it on." — **Turn on ONE MORE YOU**
  5. Notifications, Android 13 and newer only — "keeps the blocker running" — **Allow notifications**
- Footer: "Everything stays on your phone. If you turn one off later, this screen comes back."
- Coming back from phone settings refreshes the list and opens the next step.

## 2. You're all set

Shown once, after every permission is on, before the tabs.

- Big check in an accent circle. Title **You're all set**.
- Line: "All permissions are on. Now tell the app which apps to close, and when."
- Label **3 STEPS TO START**, three cards:
  1. **Make a schedule** — "Pick days, hours and apps. Those apps stay closed in that time."
  2. **Set daily limits** — "Choose how long an app can stay open each day."
  3. **Lock it** — "Once locked, changing it needs a typed prayer. That pause helps you stay strong."
- **Make my schedule** opens Schedule. **I'll do it later** opens Home. Either choice hides this screen for good.

## 3. Home

- Accent label **ONE MORE YOU**. If the schedule is locked, **Locked** sits on the right.
- Greeting by time: **Good morning** before 12, **Good afternoon** from 12 to 17, **Good evening** after 17.
- Line: "Friday · Here is your day".

When nothing is set (no scheduled apps and no limits), the rest of the page is two cards and a note:

- **Schedule** · "Not set" · the example about Instagram closed Mon–Fri, 9 AM to 7 PM.
- **Daily limits** · "None yet" · a daily time budget that closes the app until tomorrow.
- Note: "You can use both. A schedule closes apps by the clock. A limit closes them by time used."

When something is set:

- Focus card:
  - Inside the hours: **FOCUS TIME IS ON**, "Apps closed until 7:00 PM", time left and the start time, a progress bar, start and end under it, then "Instagram and YouTube are closed".
  - Before the hours: **STARTS LATER**, "Apps close at 9:00 AM".
  - After the hours: **DONE FOR TODAY**, "Apps opened at 7:00 PM".
  - No schedule today: **TODAY**, "No schedule today", "Your limits still count."
- Label **TODAY'S LIMITS**. One card per app: icon, name, "41 min of 59 min", and on the right "18 min left" in green or "Time's up" in coral. The bar is green while time is left and coral when it is used up.
- Tomorrow card: "Tomorrow · Saturday" and either the hours or "No schedule. Your limits still count."

## 4. Schedule

- Title **Schedule**. Line: "Pick when apps close. Then lock it." When locked: "Locked. Stay with it."
- Summary card on a soft accent background, label **YOUR SCHEDULE**, one sentence such as "Instagram and YouTube close on Mon, Tue, Wed, Thu, Fri, 9:00 AM – 7:00 PM." If nothing is added: "Your schedule is empty. Follow steps 1, 2 and 3 below."
- Under that sentence: "Ready to lock", or "Add an app to lock this", "Turn on a day to lock this", or "Start and end can't be the same".
- **1 Which days?** Seven chips: Mon Tue Wed Thu Fri Sat Sun. On uses the accent. Off uses the card color.
- Quick buttons, only while unlocked: **Weekdays**, **Every day**, **Weekend**.
- **2 Which hours?** Two buttons: **Starts** and **Ends**. Tap opens the phone time picker. Default is 9:00 AM and 7:00 PM.
- **3 Which apps?** with **Add app** on the right.
  - Each row: icon, name, pass line (`Pass off` or `2 × 10 min`), **Pass**, red trash.
  - Empty: "No apps yet. Tap Add app."
- **Lock schedule** or **Unlock schedule** at the bottom. Disabled until there is at least one app, one day on, and start is not the same as end.
- Under the button: "Locking is quick. To unlock later you will type a prayer." When locked: "Unlocking asks you to type a prayer."

Add app is still a dialog, not a bottom sheet. Title **Add an app**, search, list of installed apps, tap to add, **Close**.

Pass is still a dialog titled **Learning pass**. Chips Off / 1 / 2 and 5m / 10m / 15m. Saving a change opens the prayer dialog.

Lock confirm: "Days, times, and apps stay as they are." Buttons **Lock** and **Cancel**.

## 5. Limits

This screen is still the older layout.

- Title **Limits**. Line: "How long each app can stay open."
- **+ Add app**, which becomes **Close** while search is open.
- Each saved limit: icon, name, a short summary, red trash. Tap opens the editor. Trash opens the prayer titled **Remove this limit**.
- Editor: **Back**, app name, a large time, **One limit** / **Per day**, hours and minutes fields, **Save**. A lower limit saves at once. A higher limit, or clearing a day that had a limit, opens the prayer titled **Raise this limit**. Zero is rejected: "A limit cannot be 0. Set at least 1 minute."

## 6. Settings

This screen is still the older layout, with the new theme names.

- Title **Settings**. Palette icon and **Theme**. Line: "Color inside the app".
- Five circles in a row: Midnight, Ember, Sand, Forest, Dusk. The selected circle has a check. Tapping one applies it right away. The home-screen icon does not change.
- Text tabs **Permissions** and **Setup**.
- Permissions: Display over apps, Usage access, Exact alarms, each with an Allow button only when off. Uninstall lock with "Stops uninstall". Schedule lock with "Stops changes to the schedule", or "Add an app and a day on Schedule first" when it cannot lock. **App info**.
- Setup is four short lines: turn on permissions, set the schedule, set limits, lock the schedule.

## 7. Prayer dialog

Still the previous dialog, used to unlock the schedule, turn off the uninstall lock, raise or delete a limit, and change a pass.

- Title, for example **Unlock the schedule**.
- Hint: "Type this prayer below. Unlock opens when it matches."
- The prayer in a box. It must be typed one letter at a time. Paste does not count.
- A counter that becomes **Matched**.
- Field **Type it here**.
- Confirm button stays disabled until the text matches. **Cancel**.

Locking, and turning the uninstall lock on, are a short confirm. They do not use this dialog.

## 8. Block screen

Covers an app that should be closed. Background is the theme block color.

- Accent label **ONE MORE YOU**.
- Title: "Instagram is closed".
- The reason and the time it opens.
- Pass line: how many passes are left, or that the pass is off or today's time is used up.
- If a pass can be used: **Use a 10 min pass**.
- **Go to home screen**.

## Behavior that stays

- Setup blocks the app until every permission is on.
- A schedule cannot be locked with no app, no day, or start equal to end.
- Locking and turning the uninstall lock on are a short confirm.
- Unlocking, turning the uninstall lock off, raising or removing a limit, and changing a pass need the typed prayer.
- A pass is per app: Off, 1, or 2 passes of 5, 10, or 15 minutes.
