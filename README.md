# Ubuntu Lite Android

A standalone Android APK project for running an Ubuntu 22.04 ARM64 userland on Android without rooting the phone and without requiring Termux to be installed separately.

## What is bundled

- Official Ubuntu Base 22.04 ARM64 root filesystem, downloaded and SHA-256 verified during CI, then packaged as an APK asset.
- ARM64 PRoot runtime and its Termux package dependencies, staged into Android's native library directory for execution.
- A native Android command-console UI. First launch extracts Ubuntu into the app-private storage; the app then starts an interactive Bash session.

This first version is a terminal environment, not a graphical Linux desktop. It uses PRoot (fake root), not Android root. Package installs inside Ubuntu require network access and storage space.

## Build an APK

1. Open the private repository's **Actions** tab.
2. Select **Build standalone Ubuntu Lite APK**.
3. Tap **Run workflow** on the `main` branch (or wait for the push-triggered build).
4. When the workflow succeeds, open the run and download the artifact named **Ubuntu-Lite-standalone-ARM64-APK**. The artifact ZIP contains `app-debug.apk`.
5. Install the APK on an ARM64 Android phone. Android may ask to allow installation from the browser/file manager.

The APK includes the Ubuntu root filesystem and PRoot runtime. First launch takes time to unpack the filesystem. It is a debug-signed APK and is intended for personal testing, not Play Store distribution.

## Important limitations

- ARM64 only (appropriate for the Samsung Galaxy S24 Ultra).
- Terminal console only; no X11/desktop environment in this initial version.
- This is source/build setup, not a claim that the APK has already passed a device test. Check the GitHub Actions result and test on the target phone before relying on it.
- Third-party license and attribution notes are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
