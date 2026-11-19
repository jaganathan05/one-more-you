# ONE MORE YOU

Offline Android app that blocks selected apps on a shared schedule and with daily time limits.

It runs on Android 8 and newer. The phone does not need an account, and the app does not send your app list anywhere.

## What it does

- One schedule covers every app you add on the Apps tab. Weekdays can be on and weekends off. A window can run overnight, such as 9:00 PM to 7:00 AM.
- Any installed app can have its own daily limit. Use one limit for every day, or a different limit for each weekday. Enter hours and minutes.
- Lowering a limit saves immediately. Raising a limit, or removing an app from Limits, stays closed until you type the paragraph exactly.
- A limit of 0 is rejected. Use at least 1 minute.
- A learning pass opens only the app you request. The countdown stays in a notification.
- While a blocked app is open, a screen covers it. Leave returns to the home screen.
- The uninstall lock in Settings uses Android device admin. A direct uninstall is refused while it is on. Turning the lock off in the app requires the paragraph. The phone’s own uninstall screen can still turn the admin off and remove the app.

## Build an installable APK

The APK is not built on every push. On GitHub, open **Actions**, choose **Build APK**, and click **Run workflow**. When the run finishes, the release APK is attached to a GitHub release.

You can also build it locally:

```bash
./gradlew :app:assembleRelease
```

The release APK is signed with the Android debug key so it can be installed by hand. It is at `app/build/outputs/apk/release/app-release.apk`.

## Install

Uninstall an older copy first if the phone already has ONE MORE YOU. If uninstall is refused, open the app, go to **Settings**, and turn off the uninstall lock. Download the release APK, open it, and allow install from that source.

On first launch, allow display over other apps, usage access, and notifications. On phones that freeze background apps, allow autostart and unrestricted battery use, then open the app once after a reboot.

## License

[MIT](LICENSE)
