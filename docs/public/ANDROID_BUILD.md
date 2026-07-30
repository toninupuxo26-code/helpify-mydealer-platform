# Android build and delivery

## Purpose

The Android build pipeline creates installable Helpify and MyDealer debug APK packages from the native Kotlin workspace.

## Build environment

The build runs inside Docker and pins the application-era toolchain:

- Java 11;
- Android platform 31;
- Android Build Tools 31.0.0;
- Gradle 7.0.2;
- Kotlin 1.5.31;
- Android Gradle Plugin 7.0.4.

## Build command

```bash
sudo ./scripts/android_build.sh
```

## Artifacts

```text
/var/lib/helpify-mydealer/android-builds/v0.14.0/
├── Helpify-v0.14.0-debug.apk
├── MyDealer-v0.14.0-debug.apk
├── Helpify-v0.14.0-badging.txt
├── MyDealer-v0.14.0-badging.txt
├── SHA256SUMS.txt
└── build.env
```

Debug APK files are suitable for internal installation and product demonstrations. Store publication uses a separate signed release pipeline.
