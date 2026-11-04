# Third-Party Notices

This project includes code from the following open-source projects, distributed under their respective original licenses.

## scrcpy

- **Project**: [Genymobile/scrcpy](https://github.com/Genymobile/scrcpy)
- **Copyright**: Copyright 2018 Genymobile
- **License**: [Apache License 2.0](../../../LICENSE-Apache-2.0)
- **Original source**: [server/src/main/java/com/genymobile/scrcpy](https://github.com/Genymobile/scrcpy/tree/master/server/src/main/java/com/genymobile/scrcpy)
- **Location in this project**: [`app/src/main/java/com/aliothmoon/maameow/third/`](../../../app/src/main/java/com/aliothmoon/maameow/third/)

### Purpose

This code is used to construct an Android `Context` inside the Shizuku user-service process, and to access Android Hidden APIs via reflection, enabling virtual-display management, input-event injection, screen-information retrieval, and other features.

### Included files

| File | Description |
|------|------|
| `third/FakeContext.java` | A fake Android Context used to obtain system services in a process without an Activity |
| `third/Ln.java` | Logging utility that writes to both Android Logger and standard output |
| `third/Workarounds.java` | Android compatibility handling that sets up ActivityThread and Looper |
| `third/Command.java` | Shell command execution utility |
| `third/IO.java` | I/O utility class |
| `third/Size.java` | Size data class |
| `third/DisplayInfo.java` | Display information data class |
| `third/wrappers/ServiceManager.java` | Reflection wrapper around Android ServiceManager to obtain system service instances |
| `third/wrappers/DisplayManager.java` | Reflection wrapper around DisplayManagerGlobal to manage display info and virtual displays |
| `third/wrappers/InputManager.java` | Reflection wrapper around InputManager to inject input events |
| `third/wrappers/WindowManager.java` | Reflection wrapper around IWindowManager to manage rotation, display size, IME policy, etc. |
| `third/wrappers/ActivityManager.java` | Reflection wrapper around ActivityManagerNative to obtain ContentProviders and start activities |
| `third/wrappers/PowerManager.java` | Reflection wrapper around IPowerManager to query screen state |
| `third/wrappers/StatusBarManager.java` | Reflection wrapper around IStatusBarService to control the notification shade and quick settings panel |
| `third/wrappers/SurfaceControl.java` | Reflection wrapper around SurfaceControl to manage physical display tokens and power modes |

### Notable changes

The following are the main changes relative to the original scrcpy source:

- Renamed the package from `com.genymobile.scrcpy` to `com.aliothmoon.maameow.third`
- Removed code related to screen recording, video encoding, and audio capture, keeping only the system-service reflection wrappers
- Added `ActivityManager`, `SurfaceControl`, `StatusBarManager`, `PowerManager`, and other wrappers
- Added `createNewVirtualDisplay()` to `DisplayManager` to create an independent virtual display
- Added `captureDisplay()`, `setForcedDisplaySize()`, `clearForcedDisplaySize()`, and other methods to `WindowManager`
- Changed the log TAG and prefix in `Ln` to this project's identifier

---

## Lucide

- **Project**: [lucide-icons/lucide](https://github.com/lucide-icons/lucide)
- **License**: [ISC License](https://github.com/lucide-icons/lucide/blob/main/LICENSE)
- **Location in this project**: [`app/src/main/java/com/aliothmoon/maameow/presentation/components/LucideIcons.kt`](../../../app/src/main/java/com/aliothmoon/maameow/presentation/components/LucideIcons.kt)
- **Usage**: Tab icons for Copilots / Copilot sets on the copilot search page (`swords`, `list-todo`), converted from the original SVG paths to Compose `ImageVector`

---

## MaaAssistantArknights

- **Project**: [MaaAssistantArknights](https://github.com/MaaAssistantArknights/MaaAssistantArknights)
- **License**: [AGPL-3.0](https://www.gnu.org/licenses/agpl-3.0.html)
- **Usage**: The prebuilt artifacts (`libMaaCore.so` and resource files) are downloaded via `scripts/setup_maa_core.py` and dynamically loaded through JNA at runtime.
