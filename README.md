# Lite Phone

A minimal home screen (launcher) for a Samsung Galaxy S25+ that only lets these apps open:

**Phone · Messages · Camera · Spotify · Audible · Claude · ChatGPT · Outlook · Canvas · Duo Mobile**

These are allowed too, under **More apps** (one tap from the home screen):

- **Files and documents:** Microsoft 365, Word, Excel, PowerPoint, OneNote, OneDrive, Google Drive, Docs, Sheets, Slides, Adobe Acrobat
- **Everyday:** Calendar, Samsung Notes, Calculator, Gallery, My Files, Clock, Maps, Samsung Wallet, Google Wallet

Apps on these lists that aren't installed yet show up as soon as they're installed.

**Settings** is allowed too, but any Settings page that could switch Lite Phone off is locked. That includes Lite Phone's app info, Accessibility, the Home app choice, device admin, battery sleep lists and developer options/USB debugging.

Everything else gets closed straight away and you're sent back to the Lite Phone home screen. That covers every way of opening an app: notifications, links, Google Assistant or Bixby, edge panels and recent apps. Changing anything needs a passcode, and only the passcode holder knows it.

## Home screen

- **Count badges** next to apps show missed calls, unread texts and other notifications. Turn this on once in the protection checklist.
- **Arrange:** long-press any app to reorder the home screen or move apps between Home and More apps. This doesn't need the passcode, because it can't change which apps are allowed.
- **Ask to unlock** texts the passcode holder a request. If they agree, they text back a one-time code. Entering it unlocks all apps for the time they chose: 15 minutes, 30 minutes or 1 hour.
- **My use** shows screen time per app for today and the last 7 days, pickups, and which blocked apps were tried. It's only on this phone, and usage tracking is turned on once in the protection checklist.

## What the passcode holder can do

Tap **🔒 Passcode** at the bottom of the home screen and enter the passcode to:

- **Unlock all apps for 15 minutes or 1 hour.** Apps lock again on their own when time runs out.
- **Choose allowed apps.** Add or remove any app. A **Suggested** section lists school apps (Teams, Zoom, Office, Google Drive, authenticators and more) and everyday ones (Calendar, Notes, Gallery, Wallet and more) that are already installed. There's also a one-tap **Allow all school apps** button.
- **Unlock all of Settings for 15 minutes**, including the locked pages.
- **Texting unlock codes:** set the name and number that "Ask to unlock" texts, choose how long each code unlocks for, and make a set of 10 one-time codes. Codes are shown once, so write them down or photograph them with another phone. Only hashes are stored.
- **Change the passcode.**
- **Turn off Lite Phone** to remove it completely.

After 5 wrong tries of the passcode or a texted code, entry locks for 30 seconds. Each further wrong try doubles the wait, up to 1 hour.

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
5. *(Optional)* **Show missed calls and unread texts:** allow Lite Phone notification access.
6. *(Optional)* **Turn on usage tracking:** allow Lite Phone usage data access, for My use.
7. Tap **Finish and lock**.

The optional steps can be done later from **Passcode → Protection checklist**.

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
| `BadgeService` | Notification listener that only counts notifications per app for the home screen badges |
| `UnlockCodes` | One-time texted codes, stored as salted SHA-256 hashes |
| `UsageReport`, `BlockLog` | Screen time from Android's usage events, plus a 30-day local log of blocked attempts |

Building yourself: `./gradlew assembleRelease` with the Android SDK installed. GitHub Actions does this automatically (`.github/workflows/build.yml`). The signing key in `keystore/` is only there so each new build installs as an update over the last one. It isn't a Play Store key.
