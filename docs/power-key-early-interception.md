# Android 15 power-key early interception

## Purpose

Fix the path where configuring a double press of POWER to launch a wallet still
runs the native single-press sleep action. Volume-button remapping can work at the
same time because power is handled specially by system policy.

Upstream baseline: `95dfdb4642590731e8183e90275847e032b9ddbd`.
Working branch: `fix/power-key-before-queueing`.

## Implementation

- Hook `InputManagerService.interceptKeyBeforeQueueing(KeyEvent, int)` **before**
  its body, which calls `mWindowManagerCallbacks.interceptKeyBeforeQueueing`.
  This is earlier than `PhoneWindowManager` power handling, including the native
  gesture detector. Return the integer policy result `0` only for owned events.
- Keep volume remapping in the existing filter/dispatch route. Observe physical
  volume events early only to preserve native power/volume chords: a volume key
  already held prevents a new power sequence; a volume DOWN during a held power
  press hands the saved power DOWN to native policy before the volume event.
- Exclude POWER from the late KeyManager route to avoid feeding a single event
  into two detectors.
- Use an Android-free power detector with explicit press, waiting-double,
  consumed and native-down states. Snapshot configuration per sequence; finish
  its release even if game mode/configuration changes or SCREEN_OFF occurs.
- If only double press is configured, hold the first DOWN/UP until the double
  window expires. Replay both for normal single-press sleep/wake if no second
  press arrives. A mapped double executes once without giving native policy
  either press. An unmapped long hold hands DOWN to the native system while
  held and forwards subsequent repeats and the balancing UP.
- Match injected events using key/action/downTime/eventTime/device/repeat, keep
  matches valid across all hooks, and bound/expire them. Account for Android 15
  InputDispatcher replacing injected device ids with `VIRTUAL_KEYBOARD_ID=-1`.
  A timestamp-only, destructive marker can cause recursive power interception.

## Expected timing and remaining device checks

Single-press fallback waits for a power-only 200 ms double-tap window after
release, followed by any native policy delay. Volume keys retain the platform
double-tap timeout. An unmapped native long hold starts
native processing after EdgeX's hold threshold, so native long-press actions may
arrive later than with the module disabled. The unit tests establish event
ownership, ordering and balanced replay; they do not establish Sony firmware's
power menu timing, Wallet launch/unlock behavior or emergency/multi-press behavior.

No Android device was attached during this change. Treat Xperia 1 V / Android 15
compatibility as pending device validation, not as an observed fix on that phone.

## Build and host checks

From the repository root, with JDK 21 and an Android SDK containing platform 36:

```sh
JAVA_HOME=$(/usr/libexec/java_home -v 21) \
ANDROID_HOME=/Users/nickji/Library/Android/sdk \
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --console=plain
python3 tests/test_power_key_routing.py
git diff --check
```

Observed host results: baseline 45/45 JVM tests; patched 77/77 JVM tests; 6/6
routing checks. Debug and release APKs build successfully. Full debug lint reports
63 errors, 404 warnings and 9 hints in both baseline and patch; normalized issue
comparison shows zero added/removed issues. These existing lint failures were
not suppressed.

The JVM tests cover single/double/long press, native replay, canceled releases,
repeats, exact timing boundaries, stalled/stale timeouts, mid-sequence disable,
native chord handoff, marker collisions, native device normalization, expiry and
bounded storage. The Python checks verify that the tested components are wired
into early routing and excluded from late power processing; they are source
checks, not Android runtime hook tests.

## Xperia acceptance checklist

1. Export EdgeX configuration before changing APKs. The local APK uses a local
   signing key, not the upstream release key; Android may require uninstalling
   the differently signed package before installing this build.
2. Enable EdgeX for Android/System Framework in LSPosed, restart Android, and
   inspect logs for `Power key interceptKeyBeforeQueueing hook installed`.
3. Enable key mappings and POWER; configure only POWER double-click to launch
   Wallet. Leave click and long-press mappings empty/none for native fallback.
4. With the screen on and unlocked: double press opens Wallet once and does not
   first lock the display. A single press still sleeps after the recognition
   delay. Test two separated single presses and repeated double presses.
5. With the display off: a single press still wakes. Test double press from the
   lockscreen; foreground actions can remain queued until unlock by existing
   LockscreenActionPolicy. Check native long hold while held and on release.
6. Test POWER+VOLUME_DOWN screenshot and POWER+VOLUME_UP in both press orders;
   confirm that release leaves no stuck key and volume remapping still works.
7. Disable POWER mapping, disable keys globally, and enable game mode separately;
   fresh power presses must follow native policy. Change settings or sleep the
   display during a held press and confirm that its release completes.
8. Record firmware build, LSPosed version and logs for every unexpected result.

## Rollback

The accompanying `ROLLBACK.sh` restores exactly the source files changed by this
patch, first verifying that they still match the patched hashes. It preserves
unrelated files and declines to overwrite subsequent edits. It is tested against
a separate patched copy, not against the working checkout. An installed APK is
rolled back separately by reinstalling the previous compatible build and
restarting Android.

## Owning sources

- [EdgeX Issue #95](https://github.com/fcmfcm1999/EdgeX/issues/95)
- [AOSP Android 15 InputManagerService callback](https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/services/core/java/com/android/server/input/InputManagerService.java)
- [AOSP Android 15 PhoneWindowManager policy](https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/services/core/java/com/android/server/policy/PhoneWindowManager.java)
- [AOSP Android 15 injected device normalization](https://android.googlesource.com/platform/frameworks/native/+/android-15.0.0_r1/services/inputflinger/dispatcher/InputDispatcher.cpp)

## 200 ms follow-up

The power window changed from the platform default 300 ms to 200 ms. Added tests
verify fallback at release+200 ms and acceptance at a 199 ms gap versus rejection
at exactly 200 ms. This shortens EdgeX recognition by 100 ms; it does not remove
any additional Sony native policy delay. Earlier 77-test results above describe
the initial patch; this follow-up has 80 passing JVM tests.
