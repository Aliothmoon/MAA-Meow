# Build Guide

## Prerequisites

- Install [Eclipse Temurin JDK 25](https://adoptium.net/temurin/releases?version=25)

- Install [Android Studio](https://developer.android.com/studio)

- Download the MAA Core prebuilt artifacts (.so libraries + resource files)

  ```bash
  python scripts/setup_maa_core.py
  ```

  The `libMaaAndroidNativeControlUnit.so` inside MAA releases comes from MaaFramework's latest stable release and may lag behind features the app relies on (e.g. multi-touch needs >= v5.13.0-beta.3). Use `--maafw-tag` to swap in the control unit from a specific MaaFramework tag when needed:

  ```bash
  python scripts/setup_maa_core.py --maafw-tag v5.13.0-beta.5
  ```

## Build Steps

- Open this folder in Android Studio. Under Settings - Build, Execution, Deployment - Build Tools - Gradle - Gradle Projects - Gradle JDK, select the temurin-25 you installed earlier.

- Run "Sync Project with Gradle Files". Android Studio will install the remaining dependencies automatically. Once finished, run the "Assemble app" Run Configuration to build the APK.
