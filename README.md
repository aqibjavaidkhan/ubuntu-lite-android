# Ubuntu Desktop for Android — ARM64

An experimental Android app exploring an Ubuntu 22.04 ARM64 userspace with XFCE on Android phones and tablets, without replacing Android or requiring root.

## Project status: paused

**Development and new APK builds are paused for now.** The project can be revisited later when the tooling and Android/Linux desktop integration are more mature. Do not start another build or emulator test just to produce another unverified APK.

## Last-known-good reference

A screenshot supplied by the project owner on 2026-10-23 shows an XFCE desktop rendered on their Android phone through **Termux:X11**: desktop wallpaper, Applications panel, desktop icons, and a dock are visible.

This confirms that the desktop display worked in that setup at least once. It does **not** identify the exact APK, commit, or configuration, and it does not prove that the current standalone APK works. Keep the screenshot as the visual target when development resumes.

Reference architecture to investigate in the future:
- Android + Termux:X11 display path
- Ubuntu/Debian ARM64 userland in PRoot
- XFCE desktop session
- Verify the exact launch commands/configuration before trying to bundle it into a standalone APK

## Current verification status

- The project has Android launcher code and experimental Ubuntu ARM64/XFCE packaging.
- CI has produced APK packages, but package success is not the same as successful launch on a phone.
- The latest standalone APK was reported to crash immediately on the owner's phone.
- The screenshot above is evidence of a working Termux:X11 desktop setup, not evidence that a standalone APK is working.
- No standalone APK is currently confirmed as a known-working release for the target phone.
- No new emulator test is planned while the project is paused.

## Downloads and source

- [Available GitHub Releases](https://github.com/aqibjavaidkhan/ubuntu-lite-android/releases)
- [Source repository](https://github.com/aqibjavaidkhan/ubuntu-lite-android)

Existing releases are retained for historical comparison. Do not label any release as stable/working unless it is verified on the target phone.

## When development resumes

1. Recover the exact Termux:X11 startup commands and configuration that produced the desktop in the reference screenshot.
2. Locate the corresponding last-known-good source/configuration, if it exists in repository history.
3. Diagnose the standalone APK launch crash before attempting another large build.
4. Build only after the startup path is understood, then verify launch, XFCE, touch, keyboard, and browser on the physical device.

## Technical notes

This is not a replacement operating system and does not boot a separate Linux kernel. Linux applications run in a user-space environment under Android restrictions; compatibility and hardware access vary by device. Firefox and other desktop applications are goals, not guarantees of compatibility.
