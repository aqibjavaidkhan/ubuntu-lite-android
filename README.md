# Preinstalled Ubuntu Desktop for Android (ARM64)

This project builds an Android APK containing a **preinstalled Ubuntu 22.04 ARM64 desktop filesystem**. The APK includes the Ubuntu OS userland, XFCE, Mozilla Firefox, LibreOffice, Synaptic graphical package manager, desktop fonts and utilities, and the PRoot native runtime.

## First launch — no package downloads

The GitHub Actions workflow creates the complete ARM64 root filesystem during the APK build, installs the desktop and software into that filesystem, compresses it, and embeds it in the APK. On the phone, the first launch only extracts that bundled filesystem into the app's private storage and starts the desktop session. It should not run `apt install`, fetch Firefox, or download the desktop packages during startup.

The extracted desktop uses XFCE displayed through a VNC/noVNC view embedded in the app. App/file system startup still requires free local storage. Allow several GB of free space for the expanded Ubuntu image. The APK itself is much larger than the earlier 27 MB base-only builds because it contains the installed software.

## Included software

- Ubuntu 22.04 LTS ARM64 userland from the official Ubuntu image
- XFCE desktop and XFCE Terminal
- Mozilla Firefox for Linux ARM64
- LibreOffice
- Synaptic graphical package manager
- Ubuntu APT repositories configured with official components

Snap Store and snap packages are not included; they require snapd/system-service functionality that this unrooted PRoot runtime does not provide. Synaptic/apt can use Ubuntu .deb repositories, but application install/upgrade operations inside PRoot may still be affected by Android filesystem and PRoot limitations. The Android kernel and Android operating system are not replaced.

## Build a preinstalled APK

1. Open the repository's **Actions** tab.
2. Select **Build preinstalled Ubuntu Desktop APK**.
3. Wait for the ARM64 image build and APK payload checks to finish.
4. Download the artifact named **Ubuntu-Desktop-Preinstalled-ARM64-APK**. The artifact ZIP contains `app-debug.apk`.

The build verifies that the embedded compressed filesystem contains the preinstalled desktop marker, Firefox, LibreOffice, and Synaptic, and that the asset is well above the size of the old base-only rootfs.

Minimum Android version: Android 11 (API 30), ARM64. The APK is debug-signed for personal sideloading; Play Protect may still show a generic warning for a sideloaded/debug build. The desktop has not been hardware-tested on every Android device.

## Reproducibility and notices

The image build uses official Ubuntu Jammy ARM64 packages and Mozilla's official Linux ARM64 Firefox download. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).