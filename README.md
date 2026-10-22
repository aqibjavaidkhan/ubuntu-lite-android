# Ubuntu Desktop for Android (ARM64)

This private project builds an Android APK that runs an Ubuntu 22.04 ARM64 userland in app-private storage using PRoot. The app opens a real Ubuntu XFCE desktop session inside the app (not a fake desktop launcher and not a terminal-only screen).

## Desktop included

On first launch, tap **Install Ubuntu Desktop**. The app downloads and installs these packages from the official Ubuntu Jammy ARM64 repositories:

- XFCE desktop and terminal
- TigerVNC plus noVNC/websockify, rendered in the app's embedded WebView
- LibreOffice
- Synaptic graphical package manager for Ubuntu's .deb/apt repositories
- fonts, GVFS and common desktop utilities

Firefox is downloaded from Mozilla's official Linux ARM64 release channel and registered in the Ubuntu application menu. Ubuntu's Jammy `firefox` apt package is a Snap transition package, so this build uses Mozilla's standalone Linux binary instead.

The APK bundles Ubuntu Base and the PRoot runtime. The first setup downloads the desktop and office software, so use Wi-Fi and allow several GB of free storage. Reopen the APK after setup and it starts the Ubuntu desktop directly.

## Build

1. Open **Actions**.
2. Select **Build standalone Ubuntu Lite APK**.
3. Run the workflow on `main`, or wait for the push-triggered build.
4. Download `Ubuntu-Lite-standalone-ARM64-APK`. The artifact ZIP contains `app-debug.apk`.

Minimum Android version: Android 11 (API 30), ARM64. The APK is debug-signed for personal installation. It is not a replacement OS image and does not modify Android.

## App/package limitations

The desktop runs unrooted on Android's existing kernel. Ubuntu's normal deb/apt package model is available via Synaptic and apt. Snap Store / snap packages and features requiring a running systemd/snapd service are not provided in this PRoot environment. The app has not been verified as a device OS port for the Samsung S24 Ultra; actual hardware testing on the phone remains necessary.

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for third-party notices.
