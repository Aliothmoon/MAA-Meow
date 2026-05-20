<div align="center">
<img alt="LOGO" src="/docs/Logo.png" width="256" height="256" />

# MAA Meow 🐱

**在 Android 设备上原生运行 [MAA](https://github.com/MaaAssistantArknights/MaaAssistantArknights)**

基于图像识别技术，一键完成全部日常任务

[![GitHub Release](https://img.shields.io/github/v/release/Aliothmoon/MAA-Meow?style=flat-square&label=Latest)](https://github.com/Aliothmoon/MAA-Meow/releases/latest)
[![License](https://img.shields.io/github/license/Aliothmoon/MAA-Meow?style=flat-square)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/Aliothmoon/MAA-Meow?style=flat-square)](https://github.com/Aliothmoon/MAA-Meow/stargazers)
[![GitHub Downloads](https://img.shields.io/github/downloads/Aliothmoon/MAA-Meow/total?style=flat-square&label=Downloads)](https://github.com/Aliothmoon/MAA-Meow/releases)

[下载最新版](https://github.com/Aliothmoon/MAA-Meow/releases/latest) · [问题反馈](https://github.com/Aliothmoon/MAA-Meow/issues) · [QQ 交流群](https://qm.qq.com/q/j4CFbeDQXu)

**[English](README_EN.md)** | **中文**

</div>

---

> 无需 Root 权限，明日方舟可后台！正在开发中，功能不稳定，欢迎尝鲜体验～

<p align="center">
  <img src="docs/screenshots/home.png" width="200" />
  <img src="docs/screenshots/background_task.png" width="200" />
  <img src="docs/screenshots/schedule.png" width="200" />
  <img src="docs/screenshots/auto_controls.png" width="200" />
</p>

## 特性

|  | 特性 | 说明 |
|---|---|---|
| 🧠 | **原生运行 MAA Core** | 直接在 Android 上运行自动化逻辑，无需 PC 或模拟器 |
| 🪟 | **双模式运行** | 前台悬浮控制面板 / 后台虚拟显示器无界面运行 |
| 📦 | **完整任务支持** | 理智作战、公招识别、基建托管、抄作业、自动肉鸽等 |
| ⏱️ | **定时任务** | 按预设时间自动启动任务，适合日常挂机 |
| 🔄 | **自动更新** | 启动时自动检查并下载应用和资源更新 |

## 运行要求

| 项目 | 要求 |
|---|---|
| 系统版本 | Android 9+（API 28） |
| 权限方案 | [Shizuku](https://shizuku.rikka.app/) 已运行并授权，或设备已 Root |
| 设备架构 | arm64-v8a 或 x86_64 |

## 文档

| 文档 | 说明 |
|---|---|
| [构建指南](docs/BUILDING.md) | 从源码构建 APK |
| [外部自动化集成](docs/AUTOMATION.md) | 通过 Intent / am 命令与 MacroDroid、Tasker 联动 |
| [Roadmap](docs/ROADMAP.md) | 功能规划与进度 |
| [第三方代码声明](docs/THIRD_PARTY_NOTICES.md) | 引用的开源组件及许可证 |

## 参与贡献

欢迎提交 Pull Request！无论是修复 Bug、优化体验还是实现新功能，我们都非常感谢。

1. Fork 本仓库
2. 创建你的分支 (`git checkout -b feat/your-feature`)
3. 提交更改 (`git commit -m 'feat: 添加某某功能'`)
4. 推送到远程 (`git push origin feat/your-feature`)
5. 发起 Pull Request

> 提交信息请遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范（`feat:`、`fix:`、`docs:` 等）。
> 首次构建请参阅 [构建指南](docs/BUILDING.md)。

如果觉得项目有用，欢迎点一个 Star ⭐ 让更多人看到！

## 致谢

- [MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights) — 明日方舟游戏小助手，基于图像识别技术
- [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) — Display and control your Android device.
- [Shizuku](https://github.com/RikkaApps/Shizuku) — Using system APIs directly with adb/root privileges from normal apps through a Java process started with app_process.

## 许可证

本项目以 [AGPL-3.0](LICENSE) 许可证发布。第三方代码保留其原始许可证，详见[第三方代码声明](docs/THIRD_PARTY_NOTICES.md)。
