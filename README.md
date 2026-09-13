# Walkaway Lock

Automatically locks your car when you walk away from it, by calling Hyundai
Bluelink's remote lock command the moment your phone loses its connection to
the car (Wi-Fi hotspot, Bluetooth, or CarPlay disconnect).

**This is an unofficial, personal project and is not affiliated with,
endorsed by, or supported by Hyundai Motor Company or Hyundai Motor
America.** It talks to Bluelink through a reverse-engineered, undocumented
API, using your own Bluelink account credentials on your own vehicle. There
is no guarantee this will keep working if Hyundai changes their API, and no
guarantee it will lock your car reliably — **use at your own risk**, and
verify independently (mirror check, physical door handle) rather than
trusting it blindly, especially at first.

## How it works

- **Fully on-device. No backend server, ever.** The app never polls
  Bluelink in a loop — it only sends a lock command when triggered, because
  Bluelink enforces daily rate limits and polling risks getting your account
  locked out.
- Your Bluelink credentials are stored locally (iOS Keychain / Android
  Keystore) and never leave your device or go to any third party.
- A short debounce/cooldown prevents a flaky Wi-Fi blip from spamming lock
  commands.
- A **Dry Run** mode is available (and defaults on for new setups) so you
  can confirm the trigger fires correctly before it starts sending real lock
  commands.

## Platform status

| Platform | Status |
|---|---|
| iOS | Functional. Tested on a Hyundai Ioniq 5 with a US Bluelink account. |
| Android | Early scaffold, **not yet functional**. The notification-listener trigger (`BluelinkUnlockMatcher`) is intentionally wired up empty until real Bluelink notification text is captured — see the comments in that file. Contributions welcome. |

## Setup (iOS)

Walkaway Lock can't create the automation for you — Apple requires it to be
set up manually in the Shortcuts app. This only takes a minute, and the app
itself repeats these steps under Settings → Set Up Automation.

1. **Connect your phone to your car's Wi-Fi at least once** (if using Wi-Fi
   as the trigger). In iPhone Settings → Wi-Fi, join your car's network.
   Being connected to CarPlay is not the same thing — your phone won't join
   the car's Wi-Fi automatically just because CarPlay is connected. The
   network name is usually something like `Hyundai_XXXX` (check your car's
   infotainment Wi-Fi Hotspot settings if you're not sure). Skip this step
   if you're using Bluetooth or CarPlay disconnect instead.
2. **Open the Shortcuts app** (built-in on iOS; search Spotlight for
   "Shortcuts" if you don't see it).
3. **Create a "Wait & Lock" shortcut first.** Go to the *Shortcuts* tab (not
   *Automation*) and tap **+**. Add a **Wait** action (under Scripting) and
   set it to however many seconds you want — this gives you time to get out
   of the car if your trigger fires the instant it's turned off. Then add
   the **Lock Car** action after it, under Walkaway Lock. Rename the
   shortcut to something like "Wait & Lock" so you can find it in the next
   step.
4. **Go to the Automation tab.** Tap **Automation** at the bottom, then the
   **+** button and choose **Create Personal Automation**.
5. **Choose a trigger for "leaving the car."** Wi-Fi disconnect is just one
   example — pick whatever signal is most reliable for your car. Other
   options include Bluetooth disconnect (if your car pairs as a Bluetooth
   device) or CarPlay disconnect.
6. **Configure the trigger to fire on disconnect.** For Wi-Fi: tap
   **Choose**, pick your car's Wi-Fi network, then set the trigger to
   **Disconnect** (not Connect). For Bluetooth/CarPlay: choose your car from
   the device list and set it to **Disconnect** the same way. Tap **Next**.
7. **Add "Run Shortcut" and pick "Wait & Lock."** Personal Automations only
   host a single action here, so instead of adding Wait and Lock Car
   directly, tap **Add Action**, search for **Run Shortcut**, add it, then
   choose the "Wait & Lock" shortcut from step 3. Tap **Next**.
8. **Turn off "Ask Before Running."** This is the important part — if left
   on, iOS will ask you to confirm every time instead of running silently in
   the background. Turn off **Ask Before Running** (and **Notify When Run**
   if shown), then tap **Done**.
9. **Test it with Dry Run still on.** Trigger your automation (e.g. walk out
   of your car's Wi-Fi range), then check **View Logs** in the app for a
   `dry_run_lock` entry to confirm the automation fired.
10. **Turn off Dry Run when you're confident.** Once the automation has
    triggered reliably over a few real park-and-leave cycles, turn off Dry
    Run in Settings so it sends real lock commands.

**Troubleshooting:** if Shortcuts shows a notification that "Lock Car" could
not run because of an unknown error, try shortening or removing the Wait
action. Very long silent automations can run into the same iOS background
execution time limit the Wait step is meant to work around — see the code
comments in `LockCarIntent` for details.

## Building it yourself

Neither platform is distributed on an app store (see [Why isn't this on the
App Store?](#why-isnt-this-on-the-app-store) below), so you'll need to build
and sideload it yourself.

**iOS:**
1. Install [XcodeGen](https://github.com/yonaskolb/XcodeGen).
2. `cd ios && xcodegen generate`
3. Open `Autolock.xcodeproj` in Xcode.
4. Change `DEVELOPMENT_TEAM` in `project.yml` to your own Apple Developer
   Team ID (find it at developer.apple.com), then re-run `xcodegen generate`.
5. Build and run on your own device (requires a free or paid Apple Developer
   account).

**Android:**
1. Open the `android/` folder in Android Studio.
2. Build and run on your own device.

## Why isn't this on the App Store?

It was submitted and rejected under Apple's Guideline 5.2.1 (Intellectual
Property), which requires documentary evidence of authorization to control
vehicles via a manufacturer's API — Hyundai has no public developer program
for Bluelink, so no such authorization exists. That's why this exists as a
sideloaded, open-source project instead.

## License

[MIT](LICENSE) — do whatever you want with it, no warranty.
