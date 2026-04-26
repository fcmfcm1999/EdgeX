# EdgeX

> A tribute to Xposed Edge. EdgeX is an LSPosed/Xposed edge gesture module for Android 15+ that hooks system input and SystemUI so edge gestures, hardware keys, and common Android actions can be configured in one place.

<p align="center">
  <img src="docs/icon/logo.png" alt="EdgeX Logo" width="220" />
</p>

<p align="center">
  <a href="https://www.android.com/"><img src="https://img.shields.io/badge/platform-Android%2015%2B-green.svg" alt="Platform Android" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/language-Kotlin-7F52FF.svg" alt="Language Kotlin" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPLv3-blue.svg" alt="License GPLv3" /></a>
</p>

<p align="center">
  <strong><a href="README_CN.md">中文</a></strong>
</p>

## Overview

EdgeX is not a regular floating-window utility. The app process is only the configuration surface; the module logic runs inside LSPosed/Xposed-injected system processes:

- `system`: edge touch handling, hardware key interception, and system actions.
- `com.android.systemui`: freezer drawer, universal copy overlay, and other SystemUI-side surfaces.
- `com.fan.edgex`: settings UI and cross-process configuration storage.

It is intended for rooted LSPosed users who want Xposed Edge-style shortcuts on modern Android: Back, Home, Recents, screenshots, app launch, app shortcuts, shell commands, freezer drawer, universal copy, and more.

## Features

- **Edge gestures**: segmented left, right, top, and bottom edge zones, plus full-edge low-priority zones.
- **Gesture events**: single tap, double tap, long press, and directional swipes.
- **Hardware keys**: configure click, double-click, and long-press actions for Volume Up, Volume Down, and Power.
- **System actions**: Back, Home, Recents, notifications, lock screen, screenshot, volume, brightness, and more.
- **Apps and shortcuts**: launch selected apps or app shortcuts, with a root fallback for restricted shortcut discovery.
- **Freezer drawer**: manage frozen apps, open a side drawer, unfreeze and launch apps, then refreeze them.
- **Universal copy**: collect accessible text from the current screen and copy selected text blocks from an overlay.
- **Shell commands**: bind custom commands to gestures or keys, with optional `su` execution.
- **Media controls**: play/pause, stop, previous track, and next track.
- **Debug and theming**: gesture-zone debug overlay, SystemUI restart shortcut, and configurable accent colors.

## Requirements

- Android 15 or later.
- LSPosed / Xposed environment with Xposed API 82 or later.
- Current build config: `minSdk 35`, `targetSdk 36`, `compileSdk 36`.
- Required LSPosed scopes:
  - `system` / System Framework
  - `com.android.systemui` / System UI

### Root Notes

- Freezing and unfreezing apps usually requires Root. EdgeX uses framework access where possible and falls back to `su`-backed flows where needed.
- App shortcuts are loaded through Android APIs first. If access is restricted, EdgeX may use `dumpsys shortcut`, which usually requires Root.
- Shell commands only need Root when the command itself needs it or when the action is configured to run through `su`.
- ROM changes, SELinux policy, and LSPosed runtime behavior can affect individual actions.

## Installation

1. Install the EdgeX APK.
2. Enable EdgeX in LSPosed.
3. Select at least the `system` and `com.android.systemui` scopes.
4. Reboot the device. Restarting SystemUI may be enough for some changes, but a full reboot is recommended after first activation or scope changes.
5. Open EdgeX, enable gestures or keys, and assign actions.

## Usage Tips

- Turn on debug mode first if you want to verify where edge zones are being detected.
- If gestures do not trigger, check the LSPosed scopes, module enablement, and whether the device was rebooted after enabling the module.
- If freezer or shortcut fallback actions fail, check `su` authorization and your root manager logs.
- Use the in-app SystemUI restart entry after changing SystemUI-side behavior.

## Tested Environment

| Device                 | Android | Xposed Environment                                             | Root Solution                                 |
|------------------------|---------|----------------------------------------------------------------|-----------------------------------------------|
| Pixel 9                | 16      | [`LSPosed 1.9.2-it(7455)`](https://github.com/LSPosed/Lsposed) | [KernelSU](https://github.com/tiann/KernelSU) |
| Android Virtual Device | 16      | [`Vector 2.0(3021)`](https://github.com/JingMatrix/Vector)     | [Magisk](https://github.com/topjohnwu/Magisk) |

This is the currently verified development setup, not a strict compatibility limit. Other devices and ROMs may require additional adaptation.

## Reporting Issues

Useful issue details:

- Device model, Android version, and ROM.
- LSPosed / Xposed version.
- Enabled scopes.
- Trigger path, for example `Mid-Right Edge, Swipe Left`.
- Xposed logs and reproduction steps.

## Credits

EdgeX is inspired by the interaction model of Xposed Edge and reimplements the core ideas for Android 15+ and current LSPosed environments.

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).
