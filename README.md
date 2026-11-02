# Mail2Telegram

Native Android app (Kotlin, Jetpack Compose, min SDK 26) that forwards new e-mails from many IMAP
accounts to a Telegram chat. **Everything runs on the phone** using its own network — no backend,
cloud server or VPS.

## Features

- **Accounts** – add / edit / delete, presets for Gmail, Yahoo, Outlook/Hotmail and inbox.eu, plus
  custom IMAP (host, port, SSL/STARTTLS). Bulk import (paste `email,app-password` lines) for
  hundreds of accounts. Each account shows its status (OK / Auth error / Connection error) and when
  it was last checked.
- **Auth** – Gmail, Yahoo and inbox.eu use app passwords. Outlook/Hotmail uses OAuth2 (XOAUTH2) via
  Microsoft sign-in because Microsoft disabled basic auth.
- **Security** – passwords, refresh tokens and the bot token are encrypted with an AES-256-GCM key
  held in the Android Keystore.
- **Engine** – foreground service with a persistent notification, restarted on boot, prompts for
  battery-optimization exemption. Every account is checked once per minute using a bounded pool
  of 25 concurrent IMAP connections (Angus/Jakarta Mail). Designed for 500+ accounts.
- **No duplicates, no old mail** – last seen UID and UIDVALIDITY are stored per account in Room.
  When an account is added, the current newest UID becomes the baseline, so existing mail is never
  sent. The UID is advanced in the same database transaction that queues the Telegram message.
- **Resilience** – exponential back-off on connection failures (gentler for auth failures to avoid
  lock-outs); polling pauses cleanly while the phone is offline and resumes when it's back.
- **Filters** – global keywords plus optional per-account keywords. Each keyword can target any mix
  of subject, body, sender name and sender address. Case-insensitive substring matching
  (`office` matches `Office`, `OFFICE`, `office1`, `Office 2`), spaces trimmed. No keywords → everything
  is forwarded.
- **Telegram** – bot token + chat ID with a *Send test message* button. Messages contain sender,
  account, subject and a ~1500 character plain-text preview (HTML converted to text). A persistent
  queue respects Telegram rate limits and waits the `retry_after` delay on HTTP 429.
- **Log** – recent forwards, errors and info events.

## Download the APK (GitHub Actions)

Every push triggers the **Build signed APK** workflow.

1. Open the repository on GitHub and click the **Actions** tab.
2. Click the latest successful (green ✔) **Build signed APK** run.
3. Scroll to **Artifacts** at the bottom and download `Mail2Telegram-apk-<number>`.
4. The download is a `.zip` – extract it to get `Mail2Telegram-<number>.apk`.
5. Copy it to your phone and open it. Allow *Install unknown apps* for your browser / file manager
   when Android asks.

> You must be signed in to GitHub to download Actions artifacts. Artifacts are kept for 90 days.

### Signing key

The APK is always signed with a release key:

- **Recommended:** add your own keystore as repository secrets (*Settings → Secrets and variables →
  Actions*) so the key never changes:
  - `KEYSTORE_BASE64` – `base64 -w0 release.jks`
  - `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

  Create a keystore with:
  `keytool -genkeypair -v -keystore release.jks -alias mail2telegram -keyalg RSA -keysize 2048 -validity 10000`
- **Without secrets** the workflow generates a keystore and keeps it in the Actions cache, so later
  builds reuse it. GitHub evicts caches not used for 7 days; if that happens a new key is
  generated and you must uninstall the old app before installing the new APK (Android refuses
  updates signed with a different key).

## First-time setup in the app

1. **Telegram tab** – paste your bot token (from [@BotFather](https://t.me/BotFather)) and chat ID,
   tap **Save**, then **Send test message**. To find your chat ID: send any message to your bot, then
   open `https://api.telegram.org/bot<TOKEN>/getUpdates` and read `chat.id` (group IDs start with
   `-100`; add the bot to the group first).
2. **Accounts tab** – tap **+** (or **Import** for many accounts) and enter the e-mail address and
   app password. Use **Test** to verify the login.
3. Turn on the **Forwarding service** switch. Allow notifications and disable battery optimization
   when prompted — otherwise Android will put the app to sleep.
4. Optional: **Filters tab** for keywords.

Bulk import format (one per line):

```
someone@gmail.com,abcdefghijklmnop
someone@yahoo.com,abcdefghijklmnop
someone@inbox.eu,MyAppPassword
login@example.org,password,imap.example.org,993
```

## Creating app passwords

### Gmail
1. Turn on **2-Step Verification**: <https://myaccount.google.com/security>.
2. Open <https://myaccount.google.com/apppasswords>.
3. Enter a name (e.g. *Mail2Telegram*) and click **Create**.
4. Copy the 16-character password (spaces don't matter) into the app.
5. IMAP is always enabled for Gmail personal accounts. For Google Workspace, the admin must allow IMAP
   and app passwords.

### Yahoo Mail
1. Sign in at <https://login.yahoo.com/account/security>.
2. Make sure **2-step verification** is on (required for app passwords on most accounts).
3. Click **Generate app password** (or *Manage app passwords*), name it *Mail2Telegram*, click
   **Generate**.
4. Copy the 16-character password into the app.

### inbox.eu
1. Sign in to the inbox.eu web mail.
2. Open **Settings → Security / Password** (look for *App passwords* / *Application passwords*,
   and enable IMAP access in **Settings → Mail → IMAP/POP3** if it is switched off).
3. Create a new app password for *Mail2Telegram* and copy it into the app.
   Server used: `mail.inbox.eu:993` (SSL).

### Outlook / Hotmail / Live (OAuth2)
Microsoft no longer accepts passwords for IMAP, so the app signs in with your Microsoft account and
stores an encrypted refresh token. Because there's no server, you register your own (free) Azure app
once:

1. Go to <https://portal.azure.com> → **Microsoft Entra ID** → **App registrations** → **New
   registration** (any Microsoft account works; you may need to create a free Azure directory).
2. Name: *Mail2Telegram*. Supported account types: **Accounts in any organizational directory and
   personal Microsoft accounts**.
3. Redirect URI: platform **Public client/native (mobile & desktop)**, value
   `mail2telegram://auth`. Click **Register**.
4. Copy the **Application (client) ID**.
5. **API permissions** → *Add a permission* → *APIs my organization uses* → search
   **Office 365 Exchange Online** → *Delegated* → `IMAP.AccessAsUser.All` (Outlook.com personal
   accounts work with the scope automatically; adding it avoids consent issues for work accounts).
6. **Authentication** → set *Allow public client flows* to **Yes** → Save.
7. In the app: **Telegram tab → Microsoft sign-in**, paste the client ID (tenant `common`), Save.
8. Add an account with the **Outlook / Hotmail** preset and tap **Sign in with Microsoft**. After
   signing in, the browser returns to the app — tap **Save**.

For work/school accounts, IMAP must be enabled for the mailbox by the Exchange admin.

### Custom IMAP
Choose **Custom IMAP** and enter host, port and SSL. With SSL off, STARTTLS is used if the server
offers it (usually port 143).

## Reliability tips

- Keep the phone on Wi-Fi/mobile data and charging if you monitor hundreds of accounts.
- Some manufacturers (Xiaomi, Huawei, Samsung, OnePlus…) have extra "auto-start" / "background
  activity" settings — allow them for Mail2Telegram. See <https://dontkillmyapp.com>.
- Each poll opens a short IMAP session; 500 accounts × 1/minute is well within provider limits, but
  you can raise the interval in **Telegram tab → Engine**.

## Building locally

```
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (API 35). Without signing environment variables the release
build is signed with the debug key. Signing variables: `SIGNING_KEYSTORE_FILE`,
`SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`.
