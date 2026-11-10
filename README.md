# Budstikke

[![CI](https://github.com/AalmanSadath/Budstikke/actions/workflows/ci.yml/badge.svg)](https://github.com/AalmanSadath/Budstikke/actions/workflows/ci.yml)

An SMS app for Android that keeps your messages on your phone.

Budstikke replaces your default SMS app. It has no internet permission, so Android blocks it
from reaching the network: your messages never leave the device through the app.

## Status

Early development. What works now:

- Sending and receiving SMS, with a choice of SIM on phones with more than one.
- Notifications with quick reply.
- Contact photos and avatars.
- Pinning, archiving and deleting conversations, with a trash that empties itself after 30
  days.
- Blocking numbers.
- Drafts saved as you type.
- Scheduled messages: long-press send to pick a date and time.
- A send delay, so a message can be cancelled in the seconds before it goes out.
- Themes: colours from your wallpaper or Budstikke's own dark, light and AMOLED palettes, with
  an accent colour of your choice.

Not built yet:
- Backup and restore.
- Message lock.
- Highlighted one-time codes.
- Search.
- Work profile contacts.
- Reactions.
- Message import.
- Accessibility mode.
- MMS.

Expect rough edges before you rely on it as your only SMS app.

## Install

Budstikke needs Android 10 or newer.

### With Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) installs apps from their GitHub releases
and tells you when a new one is out.

1. In Obtainium, tap **Add app**.
2. Paste `https://github.com/AalmanSadath/Budstikke` as the source URL and tap **Add**.
3. Tap **Install**.

### By hand

Download `Budstikke-X.Y.Z.apk` from the
[latest release](https://github.com/AalmanSadath/Budstikke/releases/latest), open it on your
phone and allow the install when Android asks.

### Check the download

Every release is signed with the same certificate. Its SHA-256 fingerprint is:

```
1B:99:20:19:C4:46:14:9B:E2:3A:89:69:2A:A4:C2:22:8A:76:77:38:3D:E1:84:ED:87:BB:E9:A0:A6:90:AE:CE
```

Apps such as [AppVerifier](https://github.com/soupslurpr/AppVerifier) compare an installed app
against it. If Android ever refuses an update because the signature differs, do not uninstall to get
around it: that APK was not signed by Budstikke.

Each release lists the APK's SHA-256 next to it, in `Budstikke-X.Y.Z.apk.sha256`. The release
workflow also signs a record of which commit and build produced the APK. With the GitHub CLI:

```
gh attestation verify Budstikke-X.Y.Z.apk --repo AalmanSadath/Budstikke
```

## License

GPL-3.0. See [LICENSE](LICENSE).
