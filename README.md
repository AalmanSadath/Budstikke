# Budstikke

[![CI](https://github.com/AalmanSadath/Budstikke/actions/workflows/ci.yml/badge.svg)](https://github.com/AalmanSadath/Budstikke/actions/workflows/ci.yml)

An SMS and MMS app for Android that keeps your messages on your phone.

Budstikke replaces your default SMS app. It has no internet permission, so Android blocks it
from reaching the network: your messages never leave the device through the app, and nothing
goes through Google. Picture and group messages travel through Android's own MMS service,
the same service the phone's built-in messaging app uses.

<p>
  <a href="docs/screenshots/list.png"><img src="docs/screenshots/list.png" width="200" alt="Conversation list"></a>
  <a href="docs/screenshots/conversation.png"><img src="docs/screenshots/conversation.png" width="200" alt="A conversation with reactions"></a>
  <a href="docs/screenshots/search.png"><img src="docs/screenshots/search.png" width="200" alt="Search results"></a>
  <a href="docs/screenshots/settings.png"><img src="docs/screenshots/settings.png" width="200" alt="Colour settings"></a>
</p>

## Features

### Messaging
- Send and receive SMS, with a choice of SIM on phones with more than one. An optional badge
  shows which SIM each conversation uses.
- Send and receive pictures. Photos are shrunk to fit your carrier's limit, and their
  location and other details are stripped before they go. Videos, voice notes, contact
  cards and files that arrive open in the app that handles them.
- Group conversations: everyone's messages in one thread, with each sender's name and photo.
  Start one by adding several people in New message.
- Picture messages download as they arrive, or wait for a tap when you turn that off, and
  they wait while roaming unless you allow it.
- Long texts can go as one MMS instead of a string of SMS, so they arrive whole and in order.
  Your carrier's setting is used until you change it.
- Reply straight from the notification, to one person or a group. Pictures show in the
  notification, scaled down.
- Drafts are saved as you type and come back when you open the conversation.
- Schedule a message: long-press send to pick a date and time.
- Set a send delay, so a message can be cancelled in the seconds before it goes out.
- React to messages with an emoji. Reactions from Google Messages and iPhones show on the
  message they react to instead of as separate texts.
- Star messages to find them later, and copy or forward them.
- Web addresses, email addresses and phone numbers in messages can be tapped. A web link
  shows its full address, with the site it really goes to in bold, before it opens.

### Privacy
- No internet permission, so the app cannot send your messages anywhere. MMS are sent and
  downloaded by Android's MMS service over your mobile data, not by the app.
- Lock a conversation or single messages behind your screen lock. Locked text stays hidden in
  the list, in notifications and in search, and their pictures stay out of notifications.
- One-time codes are underlined, and a tap copies them. A notification with a code offers a
  Copy code button.
- Block numbers.

### Organising
- Pin, archive and delete conversations. Deleted ones wait in a trash that empties itself
  after 30 days.
- Search conversations and messages.
- Contacts from your work profile show up in search and New message, marked with a briefcase.

### Backup and import
- Back up all your messages to a file anywhere the file picker reaches, including SD cards
  and cloud folders. The file is encrypted with a PIN you choose. Pictures and attachments
  go in too, unless you untick them for a smaller file.
- Restore a backup on the same phone or a new one.
- Import messages from SMS Import / Export, picture messages included, and from QKSMS
  backups.

### Look and accessibility
- Colours from your wallpaper, or Budstikke's own dark, light and true black themes, with an
  accent colour of your choice.
- Accessibility mode: high contrast, larger text, larger touch targets and reduced motion,
  each of which can be turned off on its own, and descriptions for TalkBack.
- Choose whether the name under the icon is Budstikke or Messages.

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

The code that reads and writes MMS messages comes from the Android Open Source Project and
is under the Apache License 2.0. See [NOTICE](NOTICE).
