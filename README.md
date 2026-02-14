# MAA Meow 🐱

在 Android 设备上 **原生运行 MAA**

无需Root权限，明日方舟可后台！正在开发中，功能不稳定，欢迎尝鲜体验～

基于图像识别技术，一键完成全部日常任务！

- 基于 [MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights)
- Jetpack Compose 构建 UI
- 后台模式: 后台运行《明日方舟》, 无需修改分辨率
- 前台模式: 悬浮控制面板
- [点击此处下载最新版本](https://github.com/Aliothmoon/MAA-Meow/releases/latest)

## 这有什么不同？

🧠 **原生运行 MAA Core** — 直接在 Android 上运行自动化逻辑

🚀 **无需模拟器** — 不需要PC

🪟 **双模式运行** — 前台悬浮控制面板 / 后台虚拟显示器无界面运行

📦 **完整任务支持** — 刷理智、公招识别、基建托管、肉鸽，以及其他 MAA 支持的任务

## 运行要求

- Android 9+（API 28）
- 设备上运行 [Shizuku](https://shizuku.rikka.app/) 且已获取权限
- arm64-v8a 或 x86_64 设备

## 构建

```bash
# 下载 MAA Core 预编译产物（so 库 + 资源文件）
python scripts/setup_maa_core.py

# 构建
./gradlew assembleDebug
```

## 第三方代码

本项目包含来自以下开源项目的代码：

| 组件                                                                                                   | 来源                                                                                      | 许可证                                                               |
|------------------------------------------------------------------------------------------------------|-----------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| MAA Core（自动化引擎，SO 库 + 资源文件）                                                                          | [MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights) | [AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html)            |
| [`app/src/main/java/com/aliothmoon/maameow/third/`](app/src/main/java/com/aliothmoon/maameow/third/) | [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy)                               | [Apache License 2.0](LICENSE-Apache-2.0) |

详细的第三方代码声明、修改说明及文件清单见 [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md)。

## 致谢

感谢以下开源项目，本项目的实现离不开它们的贡献：

- [MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights) — 一款明日方舟游戏小助手 基于图像识别技术，一键完成全部日常任务！
- [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) — This application mirrors Android devices (video and audio) connected via USB or TCP/IP and allows control using the computer's keyboard and mouse. It does not require root access or an app installed on the device. It works on Linux, Windows, and macOS.
- [Shizuku](https://github.com/RikkaApps/Shizuku) — Using system APIs directly with adb/root privileges from normal apps through a Java process started with app_process.

## 许可证

本项目以 [AGPL-3.0](LICENSE) 许可证发布。

第三方代码保留其原始许可证，详见上方说明。
