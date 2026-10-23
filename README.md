# Ubuntu Lite Desktop for Android (ARM64)

This experimental branch builds an Android APK with a preinstalled Ubuntu 22.04 ARM64 userland, XFCE desktop, and a native X11 display renderer. It runs as an unrooted Android app through PRoot; it does **not** replace Android or the device kernel.

## Display architecture

The native-display branch embeds the Termux:X11 (Lorie) renderer in the APK. XFCE is configured to use `DISPLAY=:0`, and the Android app launches the native X11 server and display activity. The native build is designed to run **without TigerVNC, Xvnc, websockify, or noVNC**. Build verification explicitly checks that those components are absent from the Ubuntu filesystem and APK.

This is not a full Linux boot or a replacement operating system: Ubuntu applications run in a user-space Linux environment within Android's restrictions. Hardware acceleration, peripheral access, and compatibility can vary by device and Android version.

## Bundled software

The image currently includes:

- Ubuntu 22.04 LTS ARM64 userland and XFCE desktop
- XFCE Terminal, Thunar file manager, and Mousepad editor
- Mozilla Firefox for Linux ARM64
- Synaptic package manager and Ubuntu APT configuration
- Plank dock, desktop theme, fonts, network utilities, Python 3, Git, and build tools
- The ARM64 PRoot runtime and native X11 renderer

LibreOffice is intentionally excluded from the current lightweight image. Snap packages are not included because snapd requires service/system integration that this unrooted PRoot setup does not provide. APT/.deb packages may be installable, but some Linux applications can still fail under PRoot or Android restrictions; package availability is not a guarantee of compatibility.

## First launch and storage

The GitHub Actions build installs the desktop into a compressed ARM64 root filesystem and embeds it in the APK. First launch extracts that filesystem to the app's private storage and then starts the native X11 display and XFCE session. Keep several GB of free storage available for extraction.

Minimum supported platform: Android 11 (API 30), ARM64. The APK is debug-signed for personal sideloading. Android may show a warning for sideloaded/debug-signed apps.

## Build and download

1. Open the repository's **Actions** tab and run **Build preinstalled Ubuntu Desktop APK** on the `native-x11-display` branch.
2. Wait for all checks to complete successfully, including root filesystem validation, APK assembly, native-library checks, and the VNC/noVNC exclusion check.
3. Download all four build artifacts: `Ubuntu-Desktop-APK-Part-1`, `Ubuntu-Desktop-APK-Part-2`, `Ubuntu-Desktop-APK-Part-3`, and `Ubuntu-Desktop-APK-Checksums`.
4. The APK parts can be joined in order on a computer with `cat ubuntu-desktop-00.part ubuntu-desktop-01.part ubuntu-desktop-02.part > Ubuntu-Desktop-Android11plus.apk`. Verify the resulting checksum against `Ubuntu-Desktop-Android11plus.apk.sha256` before installing.

The optional **Join and Release APK** workflow can assemble and validate the parts as a GitHub Release asset when manually dispatched with a successful build run ID. Keep preview builds private until the full build and device smoke tests are complete.

## Verification status

CI checks verify the packaged filesystem, Firefox, native X11 library, APK structure, checksum, and absence of the VNC/noVNC stack. A successful CI build does **not** prove that the desktop works on every physical device. The latest APK must still be installed and smoke-tested on an actual Android phone/tablet for display startup, touch input, keyboard, audio, browser launch, suspend/resume, and storage behavior.

## Third-party notices

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the relevant software and license references. The project does not claim ownership of Ubuntu, PRoot, Termux:X11, Firefox, Android, or other bundled third-party components.
