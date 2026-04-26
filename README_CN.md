# EdgeX

> 向 `Xposed Edge` 致敬。`EdgeX` 是一个基于 LSPosed/Xposed 的 Android 手势增强模块，用于在屏幕边缘提供自定义手势触发、快捷动作和应用冷冻抽屉能力。

<p align="center">
  <img src="docs/icon/logo.png" alt="EdgeX Banner" width="240" />
</p>

<p align="center">
  <a href="https://www.android.com/"><img src="https://img.shields.io/badge/platform-Android%2015%2B-green.svg" alt="Platform Android" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/language-Kotlin-7F52FF.svg" alt="Language Kotlin" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPLv3-blue.svg" alt="License GPLv3" /></a>
</p>

<p align="center">
  <strong><a href="README.md">English</a></strong>
</p>

## 项目简介

`EdgeX` 通过 Hook `android` 与 `com.android.systemui` 进程，在系统输入链路中接管屏幕边缘手势，并把这些手势映射到系统动作、快捷方式或应用冷冻抽屉。

它不是普通意义上的独立 App。桌面图标仅用于配置模块行为，真正的手势识别和浮层能力运行在 LSPosed/Xposed 注入后的系统进程中。

## 当前特性

- **边缘手势**: 6 个自定义触控区，支持点击、双击、长按及四向滑动。
- **系统动作**: 快速触发返回、桌面、最近任务、截屏、电源菜单等功能。
- **全局复制**: 任意界面一键提取文字，告别无法复制的烦恼。
- **应用冻结**: 灵活管理「冷冻」应用，通过内置抽屉实现秒开。
- **应用快捷方式**: 支持 Activity 快捷方式、支付宝/微信快捷支付及系统工具。
- **shell 命令**: 支持用户自定义的shell命令

## 环境要求

### 必需条件

- Android 15 及以上
  - 当前构建配置：`minSdk = 35`
  - `compileSdk = 36`
  - `targetSdk = 36`
- 支持 Xposed API 82 的 LSPosed / Xposed 环境
- 已正确授予模块在 LSPosed 中的作用域：
  - `android`
  - `com.android.systemui`

### 与权限/Root 相关的说明

- `Freezer` 的冻结与解冻依赖 `su` 执行 `pm disable/enable`，通常需要 Root。
- 应用快捷方式读取优先走系统接口；若系统限制读取，模块会尝试通过 `dumpsys shortcut` 方式加载，这同样依赖 Root 能力。
- 部分动作是否可用，取决于系统 ROM、SELinux 策略以及 LSPosed 运行环境。

## 已测试环境

- Device: Pixel 9
- Android: 16
- LSPosed: `1.9.2-it(7455)`

这只是当前已验证环境，不代表仅支持这一组合。

## 安装与配置

### 1. 安装模块

可直接安装已编译 APK，或自行从源码构建后安装。

### 2. 在 LSPosed 中启用

启用 `EdgeX` 模块，并将作用域至少勾选为：

- `System Framework`（对应包名 `android`）
- `System UI`（对应包名 `com.android.systemui`）

### 3. 重启相关进程

推荐完整重启设备；如果只是调整配置，通常也可以：

- 使用 App 内的 `Restart SystemUI`
- 或手动重启 `SystemUI`

### 4. 配置手势

打开 `EdgeX` App 后，可以：

- 在主页面总开关中启用或禁用手势识别
- 进入 `Gestures` 页面配置六个分区
- 为每个手势事件选择具体动作
- 在 `Freezer` 页面管理冷冻应用列表

## License

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。

---

如果这个项目对你有帮助，欢迎提 Issue 或 PR 来一起完善它。
