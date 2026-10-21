# One Line Journal

A private, local-first Android journal: one short line per date, an offline daily
writing cue, and every recorded year for the selected month/day on one screen.
There is no five-year limit, journal server, advertising, analytics, or external AI.

## Features

- Kotlin/Compose app for Android 8.0 and later.
- Evening Ritual interface: a quiet, wrapping **Write** editor with explicit saving,
  and a separate **Years** tab for every saved entry on the selected month/day.
  Both light and dark appearances follow the phone's system theme.
- Offline Room database with transactional saves and explicit pending-backup status.
- Date navigation, backfilling, edits, redacted deletion records, and a stacked
  same-day-across-years view. Leap day is separate from 28 February and 1 March.
- Stable offline prompts, with an optional different cue when writing a new entry.
- Configurable daily local notifications with the prompt in the notification.
  Reminders are inexact, rescheduled on reboot/time-zone changes, and need no
  special exact-alarm access. Permission/channel blocks are shown in settings.
- Native Google authorization with only `drive.file`, and visible Drive storage.
- Automatic, network-constrained uploads with retry and generation tracking.
  An edit made during an upload remains pending.
- Immutable cloud snapshots: no journal file is overwritten. The latest 30
  successful snapshots are retained; older snapshots are moved to Drive trash.
  Every snapshot contains the entire journal, not just 30 days.
- Restore previews, explicit conflict policies, optional full replacement, and
  five local safety copies made before restores/imports.
- Portable JSON and readable text exports through Android's document picker.
- Optional AES-256-GCM encrypted backups, PBKDF2-HMAC-SHA256 with 600,000
  iterations, and an independently chosen recovery passphrase.
- Optional biometric/device-credential app lock. Screenshots, screen recording,
  and Android recent-app previews are allowed; take care when sharing journal images.
- OS cloud backup and device-to-device database copying are excluded deliberately;
  use the explicit Drive or JSON restore path.

## Daily use

Write one thought, then tap **Save line** (or **Save changes** when editing).
The editor wraps visually but still stores one line; pasted paragraph breaks
become spaces. Saving confirms storage on the phone, not a completed Drive backup.
The summary below the **Write / Years** tabs opens the full Drive status in Settings.

**Years** shows every recorded year for the selected calendar day, including this
year. Tap an entry to open its original date in Write. Switching tabs or opening
Settings keeps the unsaved draft; changing to a different date asks before
discarding it. Android Back returns from Years to Write before leaving the journal.
Reminder notifications open their intended date in Write.

Use **Choose date**, the day arrows, or **Today** when browsing an older date.
Saved entries have an **Entry options > Delete line** action with confirmation.
Settings groups expand to reveal reminders, privacy controls, and export/recovery;
Drive details are open initially so backup issues are not concealed.

## Build on Windows

Requirements: JDK 17, Android SDK platform 36 and build tools, and internet access
for initial dependency downloads. Open this folder in Android Studio, or use the
included PowerShell scripts.

If the tools are not installed:

```powershell
# Downloads a portable Microsoft OpenJDK, Gradle, and Android SDK into .tools.
.\scripts\bootstrap.ps1
```

Review the Android SDK license when prompted. If you agree to its terms, rerun
with `-AcceptAndroidSdkLicense`. These tools and their caches are ignored by Git;
the scripts do not modify your global PATH.

```powershell
# Core tests, Android JVM tests, lint, and a debug APK
.\scripts\build.ps1

# A personally signed, optimized release APK. Creates a stable local signing key.
.\scripts\release.ps1
```

An optional on-device smoke test uses an isolated, wiped AVD, never a personal
phone. Install SDK packages `emulator` and
`system-images;android-35;google_apis;x86_64`, then run
`.\scripts\emulator-test.ps1`. It starts a headless emulator on port 5580, exercises
writing/reopening/settings through Compose instrumentation, and shuts it down.
The script refuses to run if another Android device is connected.

Outputs:

```text
app\build\outputs\apk\debug\app-debug.apk
app\build\outputs\apk\release\app-release.apk
```

The first release run creates `.tools\signing\one-line.p12` and a signing password
protected with Windows DPAPI in `.tools\signing\password.xml`. Subsequent runs reuse
the same key. Preserve the private key and recoverable password securely; the
DPAPI file is tied to the Windows account/machine and is not a portable password
backup. Losing the signing key prevents in-place updates. Never publish these
files. Android Studio's **Generate Signed Bundle / APK** is an alternative if you
prefer to manage your own release keystore and backup.

To build directly with an existing SDK/JDK:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
$env:ANDROID_HOME = 'C:\path\to\Android\Sdk'
.\gradlew.bat :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

## Install on your phone

Enable Android developer options and USB debugging, connect and authorize your
phone, then select its serial explicitly if more than one device is connected:

```powershell
adb devices -l
adb -s YOUR_DEVICE_SERIAL install -r .\app\build\outputs\apk\release\app-release.apk
```

Start the app once. It works without Google setup. Enable reminders in Settings
and grant notification permission. Use **Test reminder** and check notification
channel preferences on the actual phone. System battery policies, Do Not Disturb,
force-stop, and locked Android Private Space can delay or prevent notifications.
No claim is made that an inexact reminder arrives at the exact selected minute.

### Update your phone after code changes

Connect and unlock your phone with USB debugging enabled, then run:

```powershell
.\scripts\update-phone.ps1
```

This rebuilds the signed release, installs it in place with `adb install -r`
(preserving journal data and settings), and reopens the app. It ignores emulators.
If multiple phones are connected, choose one explicitly:

```powershell
.\scripts\update-phone.ps1 -Serial YOUR_PHONE_SERIAL
```

The script requires the existing `.tools\signing` key and protected password.
Keep using the same key so Android permits updates and the registered Google
OAuth certificate stays valid. If Android reports a signature mismatch, do not
uninstall to work around it: export/back up your journal and resolve the signing
identity first. Save any unsaved draft before running the update.

### Emulator icon appears, but its window is off-screen

With the emulator still running, run this command from the project folder:

```powershell
.\scripts\show-emulator.ps1
```

This restores and moves only the window named
`Android Emulator - OneLineJournalTest:5580` to the top-left of the desktop. It
does not restart the virtual phone, resize its display, delete data, or move
unrelated windows. It refuses to act on a window not owned by the Android
emulator. An alternate emulator title or position can be supplied explicitly:

```powershell
.\scripts\show-emulator.ps1 -WindowTitle 'Android Emulator - OneLineJournalTest:5580' -X 20 -Y 20
```

The modern Android emulator ignores the old `-scale` flag; `-window-size` is not
an Android window-position control. Do not use `-wipe-data` for this issue.

## One-time Google Drive setup

The code uses the current Google Identity Services `AuthorizationClient`; there
is no API key, refresh token, service-account key, or web client secret to paste
into the app.

You must configure your own Google Cloud project and grant account consent:

1. Create/select a Google Cloud project and enable **Google Drive API**.
2. Configure the Google Auth Platform audience/branding and declare only:
   `https://www.googleapis.com/auth/drive.file`.
3. Create an **Android OAuth client** with package name:
   `com.lucabol.onelinejournal`.
4. Register the SHA-1 certificate of the APK you install. The release script
   prints it. For Android Studio/debug builds, run:

   ```powershell
   .\scripts\build.ps1 -Tasks @(':app:signingReport')
   ```

   Debug and personal release builds have different certificates. Register both
   if using both, or consistently use the personal release.

5. During development, add your own account as a test user. Before relying on
   long-lived backups, move the OAuth audience to **In production**. Google's
   testing-mode grants that include Drive expire after seven days.
6. A personal app with fewer than 100 users can qualify for the personal-use
   verification exemption. Branding/configuration requirements are still separate.
   A consumer Gmail project is not a Workspace "Internal" app.
7. On the phone, choose **Settings > Connect Google Drive**, select your account,
   and grant access. The selected account is displayed in settings. Its stable
   Drive account ID is checked before uploads.

No Google configuration or live account authorization was performed on your
behalf. OAuth errors explicitly explain the package/signature/API checks instead
of pretending backups succeeded.

Official references:

- [Android Google authorization](https://developer.android.com/identity/authorization)
- [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth)
- [OAuth audiences and seven-day testing grants](https://support.google.com/cloud/answer/15549945)
- [Personal-use verification exemption](https://support.google.com/cloud/answer/13464323)
- [Current private-distribution and ADB guidance](https://developer.android.com/developer-verification/guides)

## Backup behavior and recovery

The app creates a normal **OneLineJournal** folder with immutable
`journal-DATE-UUID.json` files and identifying app properties. This is intentionally
not Drive's hidden `appDataFolder` or Android Auto Backup. A content SHA-256 detects
accidental modification. Encrypted files can still be seen/downloaded in Drive.
Account tokens are obtained through Google Play services, not persisted by this app.

Saves work offline. Backup work requires a connected network and is also retried
by a 12-hour recovery job. The screen distinguishes local save, pending backup,
last successful backup, reconnect needs, and errors. Retention is limited to
app-tagged snapshot files within the selected folder; folders and unrelated files
are not deleted.

**One active writing phone:** this is recoverable backup, not concurrent two-way
sync. On a new installation, existing Drive history must be restored before any
upload. A changed cloud head or competing branches blocks automatic uploading
without overwriting either copy. A crash after a successful upload but before
local acknowledgement can recover its own upload.

To move phones:

1. Stop writing on the old phone.
2. Install the app signed with the same registered identity and connect the same
   Google account.
3. Choose **Restore from Drive** and select a snapshot.
4. Supply the original recovery passphrase if encrypted.
5. Review date range and conflicts; choose merge or explicit full replacement.
6. Confirm **Restore and use this phone**. Future backups use this phone's writer
   identity. The old phone must not continue writing.

If you intentionally select an older snapshot, the current cloud head is still
recorded as the acknowledged baseline, so rolling back is an explicit action.
The chosen old entries are never silently merged using timestamp guesses.

For document import, select a JSON export, preview it, and explicitly choose which
side wins on conflicting dates. Blank/invalid dates, unsupported formats,
duplicate dates, malformed encryption, and files larger than 32 MB are rejected.
If local entries change during preview, the preview must be repeated.

Before an import modifies existing data, a local safety snapshot is flushed to
the app's private, non-OS-backed-up recovery directory. **Local recovery copies**
can preview and restore one. These are not protection against losing the phone.

## Privacy boundaries

- This repository contains application source, not personal journal entries.
  Never commit journals, exports, backups, Google credentials, or signing keys.
  Google Drive journal files remain private and are not part of this repository.
- The local Room database is **not application-encrypted**. It relies on Android
  sandbox/device storage security. App lock prevents casual UI access, not a
  rooted-device or unlocked-backup forensic attack.
- Backup encryption is optional and initially off. Without it, Google Drive
  files and JSON exports contain readable journal text.
- When enabled, the recovery passphrase is wrapped on this phone by a
  non-exportable Android Keystore key. A backup is decryptable on another phone
  with the original passphrase; it does not depend on recovering that Keystore key.
- Do not lose your passphrase. Keep a recovery copy outside the phone.
- Enabling encryption does not retroactively erase old plaintext Drive snapshots,
  local safety copies, readable exports, or Drive trash.
- Changing the passphrase protects new snapshots. Older ones keep their original
  passphrase. Disabling encryption affects new snapshots only.
- Deleting an entry redacts its current local record, but old snapshots/exports
  may retain it until you remove them or retention expires.
- Readable text exports are always plaintext and require an explicit confirmation.
- Disconnecting stops this app's uploads but does not delete cloud files or
  globally revoke account permissions. You can revoke access in your Google
  account's third-party connections page.
- Android backup exclusion rules are present for both cloud backup and
  device transfer. Explicit Drive/file restore also creates a fresh writer
  identity, avoiding accidental duplicate writers.

## Project structure and validation

`core` contains platform-independent date, prompt, import, backup-conflict,
portable-format, and encryption logic. `app` contains the Compose UI, Room,
notification scheduling, native authorization, WorkManager, and Drive REST code.
The Gradle wrapper is included; no Android Studio-specific project files are needed.

Tests cover multi-year/leap-day behavior, stable prompts, daylight-saving
scheduling, JSON validation, encrypted recovery/tampering, conflict decisions,
Room transactions and stale edits, concurrent save/upload generation tracking,
Drive pagination/status/hash checks, immutable upload construction, and reminder
notification payloads with Robolectric.

Automated JVM/Robolectric tests are not a substitute for the final real-device
acceptance pass: verify notification permissions and power management, live Google
authorization, offline edits followed by backup, and a restore on a second clean
installation before trusting the app with irreplaceable entries. This environment
has no authorized physical Android phone or personal Google OAuth configuration.
An isolated Android 15 emulator exercised writing, reopening, and settings, and
successfully launched the optimized, personally signed release.
