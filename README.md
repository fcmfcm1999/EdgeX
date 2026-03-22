# EdgeX Module

> **致敬**: 本模块向 Xposed Edge 项目致敬，借鉴其思路实现 Android 手势与快捷方式的增强功能。

## 功能概述

- **手势与快捷方式增强**：为系统提供自定义手势识别和快捷方式入口。
- **统一配置管理**：通过 `buildSrc` 中的 `Configs.kt` 统一管理 `compileSdk`、`minSdk`、`targetSdk`、`versionCode`、`versionName` 等参数（内部实现细节）。
- **自动化发布**：GitHub Actions 在推送以 `v` 开头的 tag 时自动构建 Release APK，APK 文件名格式为 `EdgeX-v{version}-{YYYYMMDD}.apk`，并同步创建 GitHub Release。
- **版本同步**：工作流会把 Git tag 中的版本号注入 Gradle 环境变量，确保 APK 内部的 `versionName` 与发布的标签保持一致。
- **本地调试友好**：未设置环境变量时，Gradle 会回退到 `Configs.kt` 中的默认值，方便日常开发。

## 使用方式

1. **本地构建**（使用默认配置）
   ```bash
   ./gradlew assembleRelease
   ```
   如需临时覆盖版本号，可在命令行传入环境变量：
   ```bash
   VERSION_NAME=1.2.0 VERSION_CODE=120 ./gradlew assembleRelease
   ```

2. **发布新版本**
   ```bash
   # 更新 Configs.kt 中的 versionCode / versionName（可选）
   git add .
   git commit -m "chore: bump version"
   # 创建并推送标签，标签必须以 v 开头，例如 v1.0.1
   git tag v1.0.1
   git push origin v1.0.1
   ```
   GitHub Actions 将自动完成构建、重命名 APK 并创建 Release。

---

*© 2026 Chengming Fan. All rights reserved.*
