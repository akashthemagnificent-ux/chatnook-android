# Installing ChatNook

ChatNook is a private chat app for our group. There is no Play Store listing; you
install the APK directly.

## First install

1. On your phone, open https://github.com/akashthemagnificent-ux/chatnook-android/releases/latest
2. Download **app-release.apk**.
3. Tap the downloaded file. Android will warn that the app is from an unknown source —
   allow it for your browser or file manager, then install.
4. Open ChatNook and sign in. Either create an account with an email address and a
   password, send yourself a magic link, or tap **Continue without an account**.
   Forgotten passwords can be reset from the login screen — the emailed link brings
   you back into the app to choose a new one.

> **Pick email if you might change phones.** An anonymous account lives only on the
> device that created it. If you uninstall the app or lose the phone, that account is
> gone and cannot be recovered.

## Updating

Install the new APK over the old one — do not uninstall first. Every release is signed
with the same key, so Android accepts it as an update and you stay signed in.

The app also tells you when a newer release exists and links you to it.

## Notifications

Android asks for notification permission on first launch; say yes. If you missed it, go
to Settings → Apps → ChatNook → Notifications and enable them.

- Long-press a channel to choose **All messages**, **Only @mentions** or **Nothing**.
- Server settings has **Mute this server** for everything at once.
- A muted server shows a crossed-out bell next to its name.

If notifications stop arriving, check that the app is not battery-restricted: Settings →
Apps → ChatNook → Battery → **Unrestricted**.

## What is stored, and who can see it

Everything lives in a Supabase project (Postgres + storage) run for this group.

- **Your profile**: username, display name, avatar, banner, custom status, badges and
  theme. Anyone in a server with you can see it.
- **Messages and attachments**: readable by members of that server, or by the two people
  in a direct message. Nothing is public.
- **Push token**: the device token Firebase issues, so notifications can reach that phone.
- **Read state and notification settings**: only you.

Notifications are delivered through Firebase Cloud Messaging, which means the message
preview passes through Google's servers. There are no analytics, advertising or tracking
SDKs in the app.

## Rollback

If a release breaks something, install the previous APK from the releases page. Android
refuses to install an older version over a newer one, so uninstall first — which also
deletes the local session. Sign in again with the same email account and everything comes
back from the server. Anonymous accounts cannot come back; see the warning above.

## Troubleshooting

| Symptom | What to do |
|---|---|
| "App not installed" | An older build with a different signature is installed. Uninstall it first. |
| Play Protect warns about the app | Expected for any APK installed outside the Play Store. Tap "Install anyway". |
| Notifications arrive late | Battery optimisation. Set the app to Unrestricted. |
| "No channels yet" for a while | The first sync is slow on a poor connection; it resolves itself. |
