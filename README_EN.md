<div align="center">
<img alt="LOGO" src="/docs/Logo.png" width="256" height="256" />

# MAA Meow 🐱

**Run [MAA](https://github.com/MaaAssistantArknights/MaaAssistantArknights) natively on Android**

One-click automation for all daily tasks, powered by image recognition

[![GitHub Release](https://img.shields.io/github/v/release/Aliothmoon/MAA-Meow?style=flat-square&label=Latest)](https://github.com/Aliothmoon/MAA-Meow/releases/latest)
[![License](https://img.shields.io/github/license/Aliothmoon/MAA-Meow?style=flat-square)](LICENSE)
[![GitHub Stars](https://img.shields.io/github/stars/Aliothmoon/MAA-Meow?style=flat-square)](https://github.com/Aliothmoon/MAA-Meow/stargazers)
[![GitHub Downloads](https://img.shields.io/github/downloads/Aliothmoon/MAA-Meow/total?style=flat-square&label=Downloads)](https://github.com/Aliothmoon/MAA-Meow/releases)

[Download](https://github.com/Aliothmoon/MAA-Meow/releases/latest) · [Issues](https://github.com/Aliothmoon/MAA-Meow/issues) · [QQ Group](https://qm.qq.com/q/j4CFbeDQXu)

**English** | **[中文](README.md)**

</div>

---

> No root required. Run Arknights in the background! Still in development — expect instability. Feedback is welcome!

<p align="center">
  <img src="docs/screenshots/home.png" width="200" />
  <img src="docs/screenshots/background_task.png" width="200" />
  <img src="docs/screenshots/schedule.png" width="200" />
  <img src="docs/screenshots/auto_controls.png" width="200" />
</p>

## Features

|  | Feature | Description |
|---|---|---|
| 🧠 | **Native MAA Core** | Run automation logic directly on Android — no PC or emulator needed |
| 🪟 | **Dual Mode** | Foreground floating panel / Background virtual display (headless) |
| 📦 | **Full Task Support** | Sanity battles, recruitment, infrastructure, copilot, roguelike, and more |
| ⏱️ | **Scheduled Tasks** | Auto-start tasks at preset times — perfect for daily routines |
| 🔄 | **Auto Update** | Automatically check and download app & resource updates on launch |

## Requirements

| Item | Requirement |
|---|---|
| OS | Android 9+ (API 28) |
| Permissions | [Shizuku](https://shizuku.rikka.app/) running & authorized, or rooted device |
| Architecture | arm64-v8a or x86_64 |

## Documentation

| Document | Description |
|---|---|
| [Build Guide](docs/BUILDING.md) | Build APK from source |
| [External Automation](docs/AUTOMATION.md) | Launch profiles via Intent / am with MacroDroid or Tasker |
| [Roadmap](docs/ROADMAP.md) | Feature plans & progress |
| [PR Guidelines](docs/PULL_REQUEST_GUIDELINES_EN.md) | Pull request title, description, verification, and review conventions |
| [Third-Party Notices](docs/THIRD_PARTY_NOTICES.md) | Open-source components & licenses |

## Contributing

Pull requests are welcome! Whether it's a bug fix, UX improvement, or a new feature — we appreciate every contribution.

1. Fork this repository
2. Create your branch (`git checkout -b feat/your-feature`)
3. Commit your changes (`git commit -m 'feat: add some feature'`)
4. Push to remote (`git push origin feat/your-feature`)
5. Open a Pull Request

> Please follow the [Conventional Commits](https://www.conventionalcommits.org/) specification (`feat:`, `fix:`, `docs:`, etc.).
> See the [Build Guide](docs/BUILDING.md) for first-time setup.
> Read the [PR Guidelines](docs/PULL_REQUEST_GUIDELINES_EN.md) before opening a pull request.

If you find this project useful, consider giving it a Star ⭐ to help others discover it!

## Acknowledgements

- [MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights) — Arknights assistant based on image recognition
- [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy) — Display and control your Android device.
- [Shizuku](https://github.com/RikkaApps/Shizuku) — Using system APIs directly with adb/root privileges from normal apps through a Java process started with app_process.

## License

This project is licensed under [AGPL-3.0](LICENSE). Third-party code retains its original license — see [Third-Party Notices](docs/THIRD_PARTY_NOTICES.md).
