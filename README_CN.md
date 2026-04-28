# EdgeX

> 向 Xposed Edge致敬。EdgeX 是一个面向 Android 15+ 的 LSPosed/Xposed 边缘手势增强模块，通过系统级输入 Hook，把屏幕边缘手势、硬件按键和常用系统动作连接起来。

<p align="center">
  <img src="docs/icon/logo.png" alt="EdgeX Logo" width="220" />
</p>

<p align="center">
  <a href="https://www.android.com/"><img src="https://img.shields.io/badge/platform-Android%2015%2B-green.svg" alt="Platform Android" /></a>
  <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/language-Kotlin-7F52FF.svg" alt="Language Kotlin" /></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPLv3-blue.svg" alt="License GPLv3" /></a>
</p>

<p align="center">
  <strong><a href="README.md">English</a></strong>
</p>

## 简介

EdgeX 不是一个普通的悬浮窗工具，而是一个需要在 LSPosed/Xposed 中启用的系统增强模块。应用本体负责配置，实际的手势识别、按键拦截和浮层展示全部运行在被注入的 `system_server` 进程中：

- `android`（system_server）：处理边缘触摸、硬件按键和系统动作，同时托管冰箱抽屉、剪贴板历史浮层、全局复制选择层等所有系统侧浮层。
- `com.fan.edgex`：提供配置界面和跨进程设置存储。

适合希望用边缘手势完成返回、回到桌面、截屏、启动应用、执行 Shell 命令、打开冰箱抽屉、查看剪贴板历史等操作的 Root / LSPosed 用户。

## 功能

- **边缘手势**：支持左、右、上、下四条屏幕边缘的分段区域和全边缘低优先级区域。
- **手势类型**：支持单击、双击、长按和方向滑动，不同边缘会展示适合该方向的滑动动作。
- **硬件按键**：支持音量加、音量减、电源键的单击、双击、长按动作配置。
- **系统动作**：返回、主页、最近任务、展开通知栏、锁屏、截屏、音量、亮度等。
- **应用与快捷方式**：启动指定应用，触发应用快捷方式，支持受限场景下通过 Root 读取快捷方式。
- **冰箱抽屉**：管理冻结应用，从边缘抽屉快速解冻并启动，支持重新冻结。
- **剪贴板历史**：在底部浮层中展示最近 50 条剪贴板记录，点击任意条目即可将文本注入当前输入框，也可逐条删除。
- **全局复制**：从当前界面提取可访问文本，弹出选择层后复制需要的内容。
- **Shell 命令**：为手势或按键绑定自定义 Shell 命令，可选择普通执行或通过 `su` 执行。
- **音乐控制**：播放/暂停、停止、上一曲、下一曲等媒体按键动作。
- **调试与主题**：提供手势区域调试显示、SystemUI 重启入口、触发时震动反馈和主题色配置。

## 环境要求

- Android 15 及以上。
- LSPosed / Xposed 环境，最低 Xposed API 82。
- 当前构建配置：`minSdk 35`、`targetSdk 36`、`compileSdk 36`。
- LSPosed 作用域勾选：
  - `android` / System Framework（system_server）

### Root 相关说明

- 冰箱冻结/解冻通常需要 Root，模块会通过系统接口或 `su` 执行相关操作。
- 应用快捷方式优先通过 Android API 读取；如果系统限制访问，会尝试通过 `dumpsys shortcut` 读取，这通常也需要 Root。
- Shell 命令是否需要 Root 取决于你绑定的命令本身。
- 不同 ROM、SELinux 策略和 LSPosed 版本可能影响部分动作的可用性。

## 安装与启用

1. 安装 EdgeX APK。
2. 打开 LSPosed，启用 EdgeX 模块。
3. 在作用域中勾选 `android`（System Framework）。
4. 重启设备。首次启用或修改作用域后建议完整重启。
5. 打开 EdgeX，开启手势或按键总开关并配置动作。

## 使用建议

- 第一次配置时，可以先在主页面打开调试模式，确认触发区域是否符合预期。
- 手势无效时，优先检查 LSPosed 作用域、模块是否已启用、设备是否已重启。
- 冰箱和 Root 快捷方式异常时，检查 `su` 授权以及 Root 管理器日志。
- 修改 SystemUI 相关功能后，可以使用应用内的「重启 SystemUI」入口让配置更快生效。

## 已验证环境

| 设备                     | Android | Xposed 环境                                                      | Root 方案                                       |
|------------------------|---------|----------------------------------------------------------------|-----------------------------------------------|
| Pixel 9                | 16      | [`LSPosed 1.9.2-it(7455)`](https://github.com/LSPosed/Lsposed) | [KernelSU](https://github.com/tiann/KernelSU) |
| Android Virtual Device | 16      | [`Vector 2.0(3021)`](https://github.com/JingMatrix/Vector)     | [Magisk](https://github.com/topjohnwu/Magisk) |

以上只是当前开发验证环境，不代表唯一支持组合。其他设备和 ROM 可能需要额外适配。

## 反馈

提交 Issue 时建议附上：

- 设备型号、Android 版本、ROM 名称。
- LSPosed / Xposed 版本。
- 已勾选的作用域。
- 触发方式，例如「右侧中部，左划」。
- Xposed 日志或复现步骤。

## 支持

如果 EdgeX 对你有帮助，欢迎通过 [Ko-fi](https://ko-fi.com/fantasy1999) 支持项目开发。

## 致谢

EdgeX 的功能参考了 Xposed Edge / Xposed Edge Pro 的交互模式，并针对 Android 15+ 和当前 LSPosed 环境重新实现。

## License

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。
