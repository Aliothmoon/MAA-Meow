# 构建指南

## 环境准备

- 安装 [Eclipse Temurin JDK 25](https://adoptium.net/zh-CN/temurin/releases?version=25)

- 安装 [Android Studio](https://developer.android.com/studio)

- 下载 MAA Core 预编译产物（so 库 + 资源文件）

  ```bash
  python scripts/setup_maa_core.py
  ```

  MAA 发布包里的 `libMaaAndroidNativeControlUnit.so` 取自 MaaFramework 最新正式版，可能落后于应用依赖的特性（如多点触控需要 >= v5.13.0-beta.3）。需要时用 `--maafw-tag` 指定 MaaFramework 版本替换：

  ```bash
  python scripts/setup_maa_core.py --maafw-tag v5.13.0-beta.5
  ```

## 构建步骤

- 使用 Android Studio 打开此文件夹，在 Settings - Build, Execution, Deployment - Build Tools - Gradle - Gradle Projects - Gradle JDK 选择此前安装的 temurin-25

- 运行 Sync Project with Gradle Files，Android Studio 将自行安装其他依赖，完成后运行 Assemble app Run Configuration 即可构建apk。
