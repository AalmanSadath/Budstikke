# Budstikke requirements, first version

Each requirement is one feature from the first-version list. Acceptance criteria are written so
they can be checked by hand in the running app on a phone. Behaviour is taken from what
an1ndra/Messages visibly does (its README, store description, screenshots and release notes),
not from its source. The look is not taken from it; screens follow BifroSSH's styling.

The first version handled SMS only. MMS came after it, in R25 to R31; R21 and R24 changed
with it. Where a criterion in R1 to R24 says "message", it means an SMS unless it says
otherwise.

Status: `[ ]` open, `[x]` met and checked on a device.

---

## R1. Default SMS app

The app can take the SMS role and does everything Android expects of the holder.

- [ ] On first launch, when the app is not the default SMS app, a screen explains why it needs
      the role and offers one button that opens Android's "Set as default SMS app" dialog.
- [ ] After the role is granted, the conversation list opens without restarting the app.
- [ ] If the role is later given to another app, opening Budstikke shows the explainer again.
- [ ] Opening an `sms:` or `smsto:` link from another app (a dialer, a browser, a contact card)
      opens Budstikke's compose screen with the number filled in.
- [ ] "Reply with message" on an incoming call is offered by Budstikke and sends the chosen
      reply as an SMS.

## R2. Receive SMS

- [ ] An SMS sent from another phone appears in the conversation list within a few seconds,
      at the top, marked unread, without reopening the app.
- [ ] A long SMS sent as several parts appears as one message with the full text.
- [ ] The same SMS is visible in another SMS app after the role is switched back, so it is
      stored in the system message store and not only inside Budstikke.
- [ ] A notification is shown for each incoming SMS, unless that conversation is open on
      screen.

## R3. Send SMS, several SIMs

- [ ] A message typed in a conversation and sent arrives on the other phone.
- [ ] A message longer than one SMS arrives whole.
- [ ] Each sent message shows its state: sending, sent, delivered (when the carrier reports
      it), or failed. A failed message can be retried with one tap.
- [ ] On a phone with two active SIMs, the compose field shows which SIM will send, and tapping
      it switches SIM for that message.
- [ ] A default SIM for sending can be picked in settings; if that SIM is removed, sending falls
      back to the system default SIM instead of failing or showing "Unknown SIM".
- [ ] Each received message shows which SIM it arrived on when more than one SIM is active.

## R4. Conversation list

- [x] Every SMS conversation already on the phone is listed, newest first, with name or number,
      the last message's text (prefixed "You:" when sent), and a relative time ("7 min",
      "Yesterday", "Oct 31").
- [x] Unread conversations are visibly distinct (bold name and text, unread count).
- [x] Search finds conversations by contact name, number, and message text.
- [x] A button starts a new conversation: pick a contact, or type a number and choose
      "Send to <number>".

## R5. Conversation avatars and contact photos

- [ ] A saved contact with a photo shows that photo in the list, the conversation header and
      the contact details screen.
- [ ] A saved contact without a photo shows its initials (one or two letters) on a coloured
      circle; the colour stays the same for that contact across launches.
- [ ] An unsaved number or a sender name (such as a bank) shows a generic person icon on a
      coloured circle.
- [ ] Adding or changing a contact photo outside the app shows up after returning to the app.

## R6. Animated message bubbles

- [x] Sent messages sit on the right and received ones on the left, in visibly different
      bubble colours.
- [x] Consecutive messages from the same side group together, with a time label between groups
      that are far apart in time.
- [x] A newly sent or received message animates into place rather than appearing at once.
- [x] With reduced motion on (R20), the same message appears without animation.

## R7. Backup and restore, PIN-encrypted

- [x] Settings offers "Backup messages". It asks for a PIN, then opens the system file picker
      to choose where to save; an SD card and a cloud provider's folder can be chosen if the
      phone offers them.
- [x] The saved backup file cannot be read as text, and restoring it with the wrong PIN fails
      with a clear message and changes nothing.
- [x] Restoring with the right PIN, on the same or another phone with Budstikke as default
      app, brings back every SMS conversation and message with original dates, and the app's
      own data (pins, archive, blocks, drafts, scheduled messages, reactions, locks).
- [x] Restoring twice does not duplicate messages.
- [x] Progress is shown during backup and restore.

## R8. Pin conversations

- [ ] A conversation can be pinned from the list (long-press or swipe) and from inside it.
- [ ] Pinned conversations stay above all others, with a pin mark, regardless of date.
- [ ] Unpinning returns the conversation to its date order.

## R9. Archive conversations

- [ ] A conversation can be archived from the list; it leaves the main list and appears in an
      Archived screen opened from the list's top bar.
- [ ] A new incoming message in an archived conversation is still delivered and notified;
      the conversation stays archived. (To confirm on a device how an1ndra behaves here.)
- [ ] An archived conversation can be unarchived.

## R10. Delete conversations and the trash

- [ ] Deleting a conversation or a single message moves it to Trash, and an "Undo" action is
      offered right after. Undo of several deletions restores them last first.
- [ ] Trash lists deleted items with the date they were deleted; each can be restored or
      deleted for good.
- [ ] Items in Trash for more than 30 days are removed automatically, without the app being
      opened that day.
- [ ] A restored conversation returns with all its messages and original dates.

## R11. Block numbers

- [x] A number can be blocked from the conversation's contact details screen ("Block number")
      and from the list.
- [x] Messages from a blocked number produce no notification and do not appear in the main
      list; they are kept in a "Spam & blocked" folder.
- [x] Blocked numbers can be listed and unblocked in settings.
- [x] Messages from blocked numbers do not count towards the launcher badge.

## R12. Drafts

- [ ] Text typed in a conversation and left unsent is still there after leaving and reopening
      the conversation, and after the app is closed from recents.
- [ ] A conversation with a draft shows "Draft:" and the draft text in the list.
- [ ] Sending the message, or clearing the field, removes the draft.

## R13. Scheduled messages

- [ ] Long-pressing the send button opens a date and time picker.
- [ ] A scheduled message shows in the conversation as scheduled, with its time, and can be
      edited, sent now, or cancelled.
- [ ] At the chosen time it is sent, even if the app is closed and the phone has restarted
      since.
- [ ] Settings has a "Scheduled messages" screen listing every pending scheduled message.

## R14. Quick reply from the notification

- [ ] An incoming SMS notification has a "Reply" action with a text field; a reply typed there
      is sent and appears in the conversation.
- [ ] The notification also offers "Mark as read".
- [ ] Notifications are grouped per conversation.
- [ ] Opening a conversation dismisses its notification.

## R15. Message lock behind biometrics

- [x] A message, or a whole conversation, can be locked from its long-press menu.
- [x] A locked message's text is hidden in the conversation, the list and notifications until
      fingerprint, face or device PIN is confirmed.
- [x] After unlocking, the text stays visible until the app goes to the background.
- [x] "Select all" does not select locked messages; a locked message cannot be deleted without
      unlocking first.

## R16. One-time codes highlighted

- [x] In a message containing a one-time code ("Your code is 482913"), the code is highlighted
      and underlined like a link.
- [x] Tapping the code copies it to the clipboard and confirms with a short message.
- [x] Plain numbers that are not codes (amounts, dates, phone numbers) are not highlighted.

## R17. Delayed sending

- [ ] Settings has a send delay: off, or a few seconds (exact choices to be decided).
- [ ] With a delay set, a sent message shows a countdown and a cancel control; cancelling puts
      the text back in the compose field and nothing is sent.
- [ ] When the countdown ends, the message is sent as normal.

## R18. Work profile contacts

- [x] On a phone with a work profile, searching in "New conversation" and in the list finds
      both personal and work contacts.
- [x] Work contacts carry a briefcase badge on their avatar.
- [x] Without a work profile, nothing changes and no badge appears.

## R19. Reactions

- [x] Long-pressing a message selects it and shows a bar of eight emoji (👍 😂 ❤️ 🔥 😢 😮 🙏 🎉)
      above it, with copy, forward, star and delete actions in the top bar.
- [x] Tapping an emoji attaches it to the message, shown on the bubble's edge; tapping the same
      emoji again removes it.
- [x] A reaction is kept on this phone and also sent to the other person as an SMS, in the
      wording Google Messages uses (`Reacted 👍 to "…"`, `Removed 👍 from "…"`), but only to a
      full phone number. Reactions arriving as such texts, or as iPhone tapbacks, show on the
      message they quote.

## R20. Accessibility mode

A single "Accessibility mode" switch in settings, with its parts adjustable.

- [x] With TalkBack on, every button, avatar, bubble and status icon is read with a useful
      description ("Message from Dad, 4:45 PM, It is on the kitchen table").
- [x] High-contrast theme: text and controls meet at least 7:1 contrast against their
      background.
- [x] Larger text: message and list text scale beyond the system font size setting, and
      nothing is cut off.
- [x] Larger touch targets: every tappable control is at least 56 dp.
- [x] Reduced motion: no bubble, list or screen-change animation plays.

## R21. Message import

- [x] Settings has "Import messages". It offers three sources: a Budstikke backup (R7), an
      "SMS Import / Export" backup (`.zip` or `.json`), and a QKSMS backup.
- [x] After choosing a source and a file through the file picker, imported messages appear in
      their conversations with original dates and sent or received direction.
- [x] Progress is shown, and importing the same file twice does not duplicate messages.
- [x] MMS in an SMS Import / Export `.zip` are imported with their pictures and files (R31).
      MMS in other files are skipped, and the result says how many were skipped.

## R22. Colours and themes

- [x] Theme choices: Dynamic (Material 3 colours from the wallpaper, Android 12 and later),
      BifroSSH dark, BifroSSH light, BifroSSH AMOLED (true black).
- [x] Each choice can follow the system's light or dark mode or be fixed by hand.
- [x] An accent colour can be picked in either Dynamic or BifroSSH themes, replacing the
      accent everywhere it shows.
- [x] Text on accent and danger colours stays readable in every theme (at least 4.5:1).
- [x] Changing theme applies at once, without restarting.

## R23. Launcher name

- [x] By default the name under the app icon is "Budstikke".
- [x] A setting changes it to "Messages"; after the change, the app drawer shows "Messages".
- [x] Switching asks first, saying that some launchers remove an icon already placed on the
      home screen when the name changes, and that it can be added again from the app drawer.

## R24. No network access

- [ ] The installed app lists no "full network access" permission in Android's app info.
- [ ] The release APK's manifest contains no `android.permission.INTERNET`.
- [ ] Every feature above works with mobile data and Wi-Fi turned off, except sending and
      receiving SMS, which need only the mobile network, and sending and downloading MMS,
      which Android's own MMS service does over mobile data.

## R25. Receive and show MMS

- [x] A picture message from another phone appears in its conversation with the picture in the
      bubble; a tap opens it full screen.
- [x] Videos, voice notes, contact cards and other files show as a chip that opens them in
      the app that handles them.
- [x] MMS already on the phone show in their conversations, in date order with the SMS, and
      the list, search, notifications, stars, locks and trash cover them as they do SMS.
- [x] With "Download MMS automatically" off, or while roaming without "Also while roaming", a
      message waits with a Download button and its size; a failed download can be tried again.
- [x] An MMS from a blocked number is not downloaded and gives no notification.
- [x] A picture claiming huge dimensions is decoded at a bounded size, so it cannot run the
      app out of memory.

## R26. Send pictures

- [x] The compose bar, in a conversation and in New message, has a button that opens the
      system photo picker; up to five pictures show as removable thumbnails.
- [x] Each picture is shrunk to fit the carrier's size and dimension limits and re-encoded,
      dropping its location and other metadata.
- [x] Sending, the send delay, scheduling, retrying a failed message and editing a pending one
      work with pictures as they do with text.
- [x] Reacting to a picture sends `Reacted 👍 to an image`, as Google Messages words it.

## R27. Group conversations

- [x] A received MMS sent to several people opens one group conversation with all of them
      except this phone.
- [x] A group shows its members' first names and two overlapping avatars; each received
      message names its sender over the first bubble of their run, with their avatar beside it.
- [x] New message with several numbers starts one group conversation; anything sent to a group
      goes as one MMS.
- [x] Details list a group's members, each opening their contact. A group is never called or
      blocked as a whole; a blocked member's messages are still dropped.
- [x] A group's notification names the group and each sender, and a reply from it goes to
      the whole group.

## R28. Send long texts as MMS

- [x] Settings has "Send long texts as MMS". Until changed, it follows the carrier: on when the
      carrier names a threshold, off when it does not.
- [x] When on, a text longer than the carrier's threshold, or than three SMS when it names
      none, goes as one MMS; shorter texts stay SMS.

## R29. Back up MMS

- [x] A backup holds MMS with their pictures and files, unless "Include pictures and
      attachments" is unticked, when they keep their text and show placeholders.
- [x] A large backup is written and read in pieces, without holding it in memory whole.
- [x] Restoring brings MMS back with their stars, locks and reactions, and does not duplicate
      those already on the phone; a wrong PIN or damaged file still changes nothing.
- [x] Backups from before MMS still restore.

## R30. Pictures in notifications

- [x] A received picture shows in its notification, scaled down, with its text under it.
- [x] A locked conversation's or message's notification shows no picture.
- [ ] On a lock screen that hides private content, the notification shows text only.
- [x] The scaled copies are deleted once the notification is gone.

## R31. Import MMS

- [ ] An SMS Import / Export `.zip` exported from a real phone imports its MMS with their
      pictures, in the right conversations and with the right dates.
- [x] Only plain file names from the `.zip` are used, so nothing is written outside the app's
      own scratch folder, and that folder is removed afterwards.
