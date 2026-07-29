# Android build memory profile

VPS builds use:

- Gradle heap: 1024 MB;
- metaspace: 384 MB;
- one Gradle worker;
- parallel execution disabled;
- Kotlin compiler in-process;
- incremental Kotlin compilation disabled;
- sequential Helpify and MyDealer builds.

When RAM plus swap is below 3000 MB, the installer attempts to enable a
2048 MB swap file at `/var/lib/helpify-mydealer/android-build.swap`.
