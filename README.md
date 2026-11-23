# Lite Phone

A minimal home screen (launcher) for a Samsung Galaxy S25+ that only lets these apps open:

**Phone · Messages · Camera · Spotify · Audible · Claude · ChatGPT · Outlook**

**Settings** is allowed too, but any Settings page that could switch Lite Phone off is locked. That includes Lite Phone's app info, Accessibility, the Home app choice, device admin, battery sleep lists and developer options/USB debugging.

Everything else gets closed straight away and you're sent back to the Lite Phone home screen. That covers every way of opening an app: notifications, links, Google Assistant or Bixby, edge panels and recent apps. Changing anything needs a passcode, and only the passcode holder knows it.

## What the passcode holder can do

Tap **Lite Phone 🔒** at the bottom of the home screen and enter the passcode to:

- **Unlock all apps for 15 minutes or 1 hour.** Apps lock again on their own when time runs out.
- **Choose allowed apps.** Add or remove apps, for example Gallery, Maps or Calendar.
- **Unlock all of Settings for 15 minutes**, including the locked pages.
- **Change the passcode.**
- **Turn off Lite Phone** to remove it completely.

After 5 wrong passcode tries, entry locks for 30 seconds. Each further wrong try doubles the wait, up to 1 hour.

These always keep working: calls (including emergency calls), emergency alerts, the keyboard, contacts, alarms, permission pop-ups, Google sign-in, photo and file pickers, and the notification shade and quick settings.

## Install on the S25+

1. **Turn off Auto Blocker for now.** Go to *Settings → Security and privacy → Auto Blocker → Off*. Samsung blocks app installs from outside the stores while it's on.
2. On the phone, open this repository's **Releases** page in a browser. You'll need to be signed in to GitHub because the repo is private. Download `LitePhone.apk` from the newest *Lite Phone build*.
3. Open the downloaded file. When asked, let your browser **install unknown apps**, then tap **Install**.
4. *(Optional)* Turn Auto Blocker back on.
5. Open **Lite Phone** and hand the phone to the passcode holder.

## Setup (done by the passcode holder)

The setup screen walks through each step and shows a ✓ when it's done:

1. **Create the passcode** (4–12 digits).
2. **Make Lite Phone the Home app.**
3. **Turn on the app blocker.** In *Accessibility → Installed apps → Lite Phone app blocker*, switch it on.
   Sideloaded apps are "restricted" at first, so the switch may be greyed out. If it is, tap **Allow restricted setting** in Lite Phone. On the App info screen, tap **⋮** (top right) → **Allow restricted settings**, then go back and switch the blocker on.
4. **Turn on uninstall protection.** Tap *Activate*.
5. Tap **Finish and lock**.

## Updating

Every push to this repo builds a new `LitePhone.apk` on the Releases page. To install an update, the passcode holder unlocks all apps for 15 minutes, turns Auto Blocker off if needed, then downloads and installs the new APK over the old one. Settings and the passcode are kept.

## Limits

Lite Phone is a strong speed bump, but it isn't a vault. A normal launcher can't stop these:

- **Safe mode.** Holding Power and then long-pressing *Power off* starts the phone without third-party apps. Lite Phone comes back after a normal restart.
- **Factory reset** from the recovery menu, which erases the phone.
- **A computer with USB debugging**, which needs Developer options turned on first. Those Settings pages are locked.

The only way to close those gaps is Android's "device owner" kiosk mode. That needs the phone wiped and set up from a computer.

## How it works (for the curious)

| Piece | Job |
| --- | --- |
| `LauncherActivity` | Home screen: clock, date and the allowed apps (icons shown in grey to keep them dull) |
| `GuardService` | Accessibility service that sees which app comes to the front and sends you home if it isn't allowed |
| `AdminReceiver` | Device admin, which stops Android from uninstalling the app |
| `Passcode` | Stores only a salted PBKDF2 hash of the passcode, with lockout after wrong tries |
| `Policy` | The allow list, plus the system parts that must never be blocked |

Building yourself: `./gradlew assembleRelease` with the Android SDK installed. GitHub Actions does this automatically (`.github/workflows/build.yml`). The signing key in `keystore/` is only there so each new build installs as an update over the last one. It isn't a Play Store key.
