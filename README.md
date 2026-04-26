# EdgeX

> Inspired by `Xposed Edge`, `EdgeX` is an LSPosed/Xposed-based Android module that brings customizable edge gestures, quick actions, and an app freezer drawer to your device.

<p align="center">
  <img src="docs/icon/logo.png" alt="EdgeX Banner" width="240" />
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

`EdgeX` hooks into the `android` and `com.android.systemui` processes to capture edge gestures at the system level and map them to actions, shortcuts, and freezer-related UI.

It is not a standalone app in the usual sense. The launcher icon is mainly used to configure the module, while the actual gesture handling and overlay behavior run inside LSPosed/Xposed-injected system processes.

## Features

- **Edge Gestures**: 6 customizable zones supporting tap, double-tap, long-press, and directional swipes.
- **System Actions**: Quick triggers for Back, Home, Recents, Screenshots, Power Menu, and more.
- **Universal Copy**: Effortlessly extract and copy text from any screen.
- **App Freezer**: Freeze idle apps and launch them instantly from a dedicated drawer.
- **Smart Shortcuts**: Rapid access to app shortcuts, QR code payments (Alipay/WeChat), and system tools.
- **Shell Command**: Support customized shell command

## Requirements

### Required

- Android 15 or above
  - Current build config: `minSdk = 35`
  - `compileSdk = 36`
  - `targetSdk = 36`
- LSPosed / Xposed environment compatible with Xposed API 82
- Proper LSPosed scope assignment:
  - `android`
  - `com.android.systemui`

### Notes About Root and Permissions

- `Freezer` freeze/unfreeze actions rely on `su` with `pm disable/enable`, so Root is usually required.
- Shortcut loading first uses system APIs. If access is restricted, the module may fall back to `dumpsys shortcut`, which also depends on Root.
- Some actions may vary depending on ROM behavior, SELinux policy, and LSPosed runtime conditions.

## Tested Environment

- Device: Pixel 9
- Android: 16
- LSPosed: `1.9.2-it(7455)`

This is the currently verified environment, not a strict compatibility limit.

## Installation and Setup

### 1. Install the module

Install a built APK directly, or build from source and install it manually.

### 2. Enable it in LSPosed

Enable the `EdgeX` module and make sure at least these scopes are selected:

- `System Framework` (package: `android`)
- `System UI` (package: `com.android.systemui`)

### 3. Restart related processes

A full device reboot is recommended. After configuration changes, you can also:

- Use `Restart SystemUI` inside the app
- Or restart `SystemUI` manually

### 4. Configure gestures

Open the `EdgeX` app to:

- Enable or disable gesture handling from the main screen
- Configure the 6 gesture zones in `Gestures`
- Assign actions to each gesture event
- Manage freezer app entries in `Freezer`

## License

This project is licensed under the [GNU General Public License v3.0](LICENSE).

---

If this project helps you, feel free to open an Issue or PR.
