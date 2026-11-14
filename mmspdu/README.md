# mmspdu

Encodes and decodes MMS PDUs (OMA MMS encapsulation over WAP binary headers): the
notification a phone gets when an MMS is waiting, the message it downloads, and the message
it sends. The rest of MMS (downloading, sending, storing, showing) lives in the app.

## Where it comes from

Copied from the Android Open Source Project, Apache License 2.0:

- Repository: `platform/frameworks/base`
- Path: `telephony/common/com/google/android/mms/` (the `pdu/` directory and the three files
  beside it)
- Commit: `1cdfff555f4a21f71ccc978290e2e212e2f8b168` (main, 2026-11-14)

This is the copy Android itself uses, so it receives the security fixes listed in the Android
Security Bulletins. The copies in the retired AOSP Messaging app and in klinker's
android-smsmms lack at least one of its length checks.

`PduPersister.java`, `package.html` and `util/` (caches and DRM) are not copied: the app
writes messages to the system store itself.

## Changes from upstream

Two kinds, so a diff against upstream shows exactly these and nothing else.

Mechanical, made by the command below:

- The package `com.google.android.mms` becomes `dev.aalman.budstikke.mms`. Android's boot
  classpath already holds a hidden copy under the original name, and a class loaded under
  that name would be the framework's, not this one.
- `@UnsupportedAppUsage` annotations and the imports they need are removed. They are
  framework-internal and do not compile outside it.

```
sed -i -E \
  -e 's/com\.google\.android\.mms/dev.aalman.budstikke.mms/g' \
  -e '/^import android\.compat\.annotation\.UnsupportedAppUsage;$/d' \
  -e '/^import android\.os\.Build;$/d' \
  -e '/^[[:space:]]*@UnsupportedAppUsage(\(.*\))?[[:space:]]*$/d' \
  $(find mmspdu -name '*.java')
```

Security fixes, in `bounds.patch`, each marked `// Budstikke:` in the code. Upstream
`PduParser` reads a part's length, or the length of a value to skip, from the message and
allocates that many bytes before checking how much data is left. A message of a few dozen
bytes claiming a 2 GB part runs the app out of memory. The patch refuses any length larger
than the data left. Drop a hunk once upstream checks the same thing.

```
git apply mmspdu/bounds.patch
```

Make no other edits to the Java files. Hardening that belongs to this app (size limits,
catching everything the parser throws) is in `sms/MmsPdu.kt` in the app.

## Updating

After each Android Security Bulletin, or when upstream changes:

1. Download the same files at the new commit from
   `https://android.googlesource.com/platform/frameworks/base/+/<commit>/telephony/common/com/google/android/mms/`
   (append `?format=TEXT` to a file's URL for its base64 text).
2. Replace the files here, run the command above, apply `bounds.patch`, and update the commit
   in this file. If a hunk no longer applies, read what upstream changed there before
   writing it again.
3. Review `git diff`, then run the unit tests (`./gradlew testDebugUnitTest`), which include
   the parser fuzz test and a test of the part-length check in the patch.
