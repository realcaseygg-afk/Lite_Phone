# Lite Phone

A minimal home screen (launcher) for a Samsung Galaxy S25+ that only lets these apps open:

**Phone · Messages · Visual Voicemail · Camera · Spotify · Audible · Claude · ChatGPT · Outlook · Gmail · Canvas · Duo Mobile · Kindle · My Apps: 毎日ノート (Japanese notebook) and The Scholar's Garden**

These are allowed too:

- **Files and documents:** Microsoft 365, Word, Excel, PowerPoint, OneNote, OneDrive, Google Drive, Docs, Sheets, Slides, Adobe Acrobat
- **Everyday:** Calendar, Samsung Notes, Calculator, Gallery, My Files, Clock, Maps, Google Wallet
- **Notes & Tasks:** Samsung Notes, OneNote, Google Keep, Notion, Evernote, Obsidian, Logseq, Simplenote, Standard Notes, Joplin, Squid, Nebo, Microsoft To Do, Todoist, TickTick and Samsung Reminder
- **Sign-in & Security:** password managers (Bitwarden, 1Password, LastPass, Dashlane, Proton Pass, Keeper, NordPass, Enpass and KeePassDX), Duo, Microsoft Authenticator, Google Authenticator, Authy, Okta Verify, LastPass Authenticator, Bitwarden Authenticator, 2FAS, Aegis, Yubico Authenticator, Symantec VIP Access and Entrust Identity
- **Banking and money:** Chase, Bank of America, Wells Fargo, Capital One, Citi, U.S. Bank, PNC, Truist, Discover, Amex, Ally, SoFi, Chime, Navy Federal, USAA, TD, Citizens, Fifth Third, Regions, KeyBank, Huntington, Venmo, PayPal, Cash App, Zelle, Credit Karma, Rocket Money, Fidelity, Schwab and Vanguard. Any other installed bank or credit union app is listed under **Suggested** in the passcode holder's app picker.
- **Food:** DoorDash, Uber Eats, Grubhub, Instacart, Starbucks, Chick-fil-A, McDonald's, Chipotle, Domino's, Pizza Hut, Papa John's, Taco Bell, Panera, Dunkin', Subway, Wendy's, Burger King, Kroger, Publix and Safeway
- **Shopping:** Amazon, Walmart, Target, Costco, Sam's Club, eBay, Etsy, Best Buy, Home Depot, Lowe's, IKEA, Kohl's, Nike, CVS, Walgreens and Shop


Apps on these lists that aren't installed yet show up as soon as they're installed.

**Settings** is allowed too, but any Settings page that could switch Lite Phone off is locked. That includes Lite Phone's app info, Accessibility, the Home app choice, device admin, battery sleep lists and developer options/USB debugging.

Everything else gets closed straight away and you're sent back to the Lite Phone home screen. That covers every way of opening an app: notifications, links, Google Assistant or Bixby, edge panels and recent apps. Changing anything needs a passcode, and only the passcode holder knows it.

## Home screen

The home screen has **two pages**. Swipe sideways between them; the dots at the bottom show where you are.

**Page 1 (apps):** the clock and weather, the search bar, **category tiles** and a **dock** of your four most-used apps, just above ⚙ Settings. The tiles are Phone & Messages, My Apps, School, Notes & Tasks, Sign-in & Security, AI, Music & Books, Camera & Photos, Banking & Money, Food, Shopping, Everyday and Other, each showing a few app icons. Tap a tile to see its apps. Only categories with apps in them are shown. The dock shows your **most used** apps (from **My use** over the last 14 days, updated hourly) or four **fixed** apps you pick: long-press the dock, or go to **⚙ Settings → Dock apps**.

**Page 2 (Agenda):** tap **+ Widgets** to put app widgets at the top, such as Canvas's own widget or a calendar widget. Only allowed apps' widgets are offered. Android asks once to let Lite Phone show widgets. Widgets can be made taller, made shorter or removed from the same button. Below the widgets is the next 7 days of **calendar events** (from the phone's calendars, after you allow access once) and **Canvas assignments** that are due and not yet submitted, in one timeline. Tap an item to open it in Calendar or Canvas. To connect Canvas, go to **⚙ Settings → Connect Canvas to the Agenda** and enter your school's Canvas address and an access token you make on the **Canvas website** (not the app; use a 🌐 Web pass) under *Account → Settings → Approved Integrations → + New Access Token*. The token stays on the phone and is only sent to your school's Canvas.

- **Weather by the clock:** the current temperature and conditions, today's high and low, and your town. Tap it once to allow approximate location. After that, tapping refreshes the weather and opens your weather app (Samsung Weather, The Weather Channel or AccuWeather, all allowed under Everyday), and long-pressing opens Customize. Data comes from [Open-Meteo](https://open-meteo.com), which is free and needs no account. Only a location rounded to about 1 km is sent. In **Customize** you can pick a city instead of using your location, choose °F or °C, or hide the weather.
- **🌐 Web pass:** in ⚙ Settings, start a pass to open browsers (Chrome, Samsung Internet and so on) for 30 minutes, twice a day, without the passcode. Links from other apps open during a pass too. Browsers close on their own when it ends, and the count resets at midnight. The home screen shows a banner while a pass is running, and you can end it early from Settings.
- **Command bar:** the search box also takes commands. The top suggestion runs when you press **Go**, or tap any suggestion:
  - `call mom` and `text sarah running late`, which need contacts access once. A number works too, like `call 555 1234`.
  - `timer 10` (also `90s` or `1h`) and `alarm 7:30` (or `7am`).
  - `due` or `agenda` opens the Agenda, and `weather` refreshes it.
  - `?your question` or `ask …` sends a question to the Claude app.
- **Theme** (Customize): **Dark**, **Paper** (warm cream with a serif font) or **Auto**, which is Paper from sunrise to sunset at your location and Dark at night.
- **Japanese mode** (Customize): the date shows as 11月25日（水）, categories get Japanese names (学校, 音楽・本…), and the Agenda and labels switch to Japanese.
- **Word of the day:** a word from your 毎日ノート vocabulary under the date, such as 経験（けいけん）· experience. It's read from the Japanese repo with the Updates token, with a built-in list until then. Tap it to open 毎日ノート.
- **Garden:** one plant for each of the last 7 days. 🌷 means you stayed under your screen-time goal, 🥀 means you went over, and 🌱 is today. It also shows your streak. The goal (2–5 h, default 3 h) is set in Customize, and the garden needs usage tracking.
- **Morning and evening cards:**
  - **Morning (5 AM to noon):** weather, your first event and what's due today.
  - **Evening (6 PM to midnight):** screen time and pickups, plus tomorrow's first event and anything due tomorrow.
  - Tap a card for the Agenda, or **✕** to hide it until the next one.
- **Search:** type in the search bar to find any allowed app. Matches replace the category list as you type, and **Go** on the keyboard opens the top match.
- **Quiet notifications:** notifications from **food apps are always cleared**, even during an unlock. Notifications from blocked apps are cleared automatically too, so the notification shade only shows allowed apps. System notices like updates, battery and USB still show. Clearing blocked apps is paused during an unlock. Both use the same notification access as the count bubbles.
- **Count bubbles** show only missed calls, voicemails and unread texts. They appear on the Phone and Messages apps and on their category. Turn this on once in the protection checklist.
- **Arrange** (⚙ Settings → Arrange, or long-press a category or app): drag the **≡** handle to reorder categories. Tap a category to drag its apps into order, and use **Move** to put an app in another category, or in a **new category** you name. The list scrolls by itself when you drag near the edge. This doesn't need the passcode, because it can't change which apps are allowed.
- **⚙ Settings** (bottom of the home screen) has My use, Customize look, Arrange, Updates and, behind the passcode, **Add or remove apps** and the **passcode menu**.
- **Customize:** long-press the clock, or use ⚙ Settings, to change the look:
  - **Background:** black, the phone's wallpaper, or a photo, with an option to darken it so text stays readable
  - **Accent colour**
  - **Icons:** grey, colour or none
  - **Text size**, **left or centred layout**, and **font**
  - **Clock size**, and whether to show the date
- **My use** shows screen time per app for today and the last 7 days, pickups, and which blocked apps were tried. It's only on this phone, and usage tracking is turned on once in the protection checklist.

## What the passcode holder can do

Open **⚙ Settings → 🔒 Passcode menu** and enter the passcode to:

- **Unlock all apps for 15 minutes or 1 hour.** Apps lock again on their own when time runs out.
- **Choose allowed apps.** Add or remove any app. A **Suggested** section lists school apps (Teams, Zoom, Office, Google Drive, authenticators and more) and everyday ones (Calendar, Notes, Gallery, Google Wallet and more) that are already installed. There's also a one-tap **Allow all school apps** button.
- **Unlock all of Settings for 15 minutes**, including the locked pages.
- **Web pass:** turn it on or off, and set its length (15, 30 or 60 minutes) and how many are allowed a day (1–3).
- **Change the passcode.**
- **Turn off Lite Phone** to remove it completely.

After 5 wrong passcode tries, entry locks for 30 seconds. Each further wrong try doubles the wait, up to 1 hour.

These always keep working: Samsung's **Modes and Routines**, for turning Work, Sleep or Driving mode on and off. Your chosen **autofill and passkey provider** (Samsung Pass, Google or a password manager), Android's app installer (a newly installed app stays blocked until the passcode holder allows it), calls (including emergency calls), emergency alerts, the keyboard, contacts, alarms, permission pop-ups, Google sign-in, photo and file pickers, and the notification shade and quick settings.

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

The optional steps can be done later from **⚙ Settings → 🔒 Passcode menu → Protection checklist**.

## Updating

Every push to this repo builds a new `LitePhone.apk` on the Releases page.

**⚙ Settings → Updates → Check for updates** checks Lite Phone and **your own apps** together, then shows an **Update** or **Install** button for each one that's newer. No passcode is needed. The passcode holder has to tick *Allow Lite Phone to update itself* in the protection checklist once.

- **Lite Phone** comes from this repo's latest public release.
- **Your other apps** are listed in [`my-apps.json`](my-apps.json). To add an app, add an entry with its `name`, `package` (applicationId) and `repo`, plus either `path` (an APK committed to the repo's default branch) or `asset` (the APK's file name on the repo's latest release). Lite Phone reads the list fresh each time, so no new build is needed.
- **Private repos** need a read-only GitHub token, saved on the Updates screen. Create it on GitHub under *Settings → Developer settings → Fine-grained tokens*, give it access to just your app repos with **Contents: Read-only**, and set nothing else. The token is only ever sent to `api.github.com`.
- **Install from a file** takes an APK from Drive, OneDrive, email or Downloads.

Every update must be the same app, signed with the same key as the installed version, and newer. Lite Phone updates itself without a confirmation screen. Other apps may show Android's **Update** confirmation. Being listed in `my-apps.json` only covers updates. Whether an app may open is still set in **Add or remove apps**.

Settings and the passcode are kept. The passcode menu shows the installed Lite Phone version and its date.

## Limits

Lite Phone is a strong speed bump, but it isn't a vault. A normal launcher can't stop these:

- **Safe mode.** Holding Power and then long-pressing *Power off* starts the phone without third-party apps. Lite Phone comes back after a normal restart.
- **Factory reset** from the recovery menu, which erases the phone.
- **A computer with USB debugging**, which needs Developer options turned on first. Those Settings pages are locked.
- **Self-updates.** Whoever controls this GitHub repo could publish a build with the lock removed, and ⚙ Settings → Updates would install it. The passcode menu shows the installed version and date, so a change is visible.

The only way to close those gaps is Android's "device owner" kiosk mode. That needs the phone wiped and set up from a computer.

## How it works (for the curious)

| Piece | Job |
| --- | --- |
| `LauncherActivity` | Home screen: clock, date and the allowed apps (icons shown in grey to keep them dull) |
| `GuardService` | Accessibility service that sees which app comes to the front and sends you home if it isn't allowed |
| `AdminReceiver` | Device admin, which stops Android from uninstalling the app |
| `Passcode` | Stores only a salted PBKDF2 hash of the passcode, with lockout after wrong tries |
| `Policy` | The allow list, plus the system parts that must never be blocked |
| `BadgeService` | Notification listener that counts notifications per app for the badges and clears notifications from blocked apps |
| `Updater`, `UpdateActivity` | Updates Lite Phone and the apps in `my-apps.json`: downloads or opens an APK, checks the package, signing key and version, then installs it |
| `WebPass` | Time-limited browser access: tracks the daily count and which apps are browsers |
| `ReorderList`, `ArrangeActivity` | Drag-and-drop ordering of categories and apps, moving apps between categories, and custom categories |
| `Weather` | Home screen weather from Open-Meteo, cached for 30 minutes, using a rounded location or a chosen city |
| `Pager`, `Dock`, `Agenda`, `CanvasSetupActivity` | Two-page home: swipe pager, most-used dock, and the calendar + Canvas agenda |
| `Theme`, `Commands`, `Vocab`, `Garden`, `DayCard` | Day/night paper theme, command bar, word of the day, screen-time garden, morning/evening cards |
| `Look`, `CustomizeActivity` | Home screen appearance settings and the wallpaper photo, which is stored only inside the app |
| `UsageReport`, `BlockLog` | Screen time from Android's usage events, plus a 30-day local log of blocked attempts |

Building yourself: `./gradlew assembleRelease` with the Android SDK installed. GitHub Actions does this automatically (`.github/workflows/build.yml`).

**Signing key:** release builds are signed with Lite Phone's private key, which is stored only in two GitHub Actions secrets, `LITEPHONE_KEYSTORE` (the keystore, base64-encoded) and `LITEPHONE_KEYSTORE_PASSWORD`. It is never stored in the repo. The phone only accepts updates signed with that key, so keep a private backup of it. Without the secrets, the build stops with an error instead of publishing an APK the phone would reject.
