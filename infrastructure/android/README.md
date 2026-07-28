# Android build environment

The Android applications are built inside a pinned Docker environment.

## Toolchain

- Ubuntu 20.04
- OpenJDK 11
- Android command-line tools package 7583922
- Android platform 31
- Android Build Tools 31.0.0
- Gradle 7.0.2
- Kotlin 1.5.31
- Android Gradle Plugin 7.0.4

The host requires only Docker. Android SDK files and Java are isolated inside the build image.

## Build

```bash
sudo ./scripts/android_build.sh
```

Artifacts are written to:

```text
/var/lib/helpify-mydealer/android-builds/v0.8.2/
```

The directory contains two debug APK files, package metadata and SHA-256 checksums.
