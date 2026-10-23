# Ubuntu Lite Desktop for Android (ARM64)

Experimental Android app for an Ubuntu 22.04 ARM64 userspace and XFCE desktop, using PRoot and a native X11 display path.

## Project status: paused

**Development and new APK builds are paused for now.** Revisit the project later when Android/Linux desktop integration and tooling are more mature. Do not trigger another build or emulator test just to produce another unverified APK.

## Last-known-good reference

On 2026-10-23, the project owner supplied a screenshot showing an XFCE desktop rendered on their Android phone through **Termux:X11**. Visible details include the XFCE Applications panel, desktop icons, wallpaper, and dock.

This confirms that the desktop display worked in that Termux:X11 setup at least once. It does not identify the exact commit, APK, or configuration, and it does not prove the current standalone APK works. Use the screenshot as the visual target when work resumes.

Future recovery should start by documenting the exact Termux:X11 launch commands and configuration, then tracing whether that setup can be reproduced from repository history before bundling it into a standalone APK.

## Current verification status

- The branch contains experimental Ubuntu ARM64/XFCE packaging and a native X11 renderer.
- CI/package checks have passed for some builds, but they do not prove physical-device runtime success.
- The latest standalone APK was reported to crash immediately on the owner's phone.
- No standalone APK is currently confirmed as a known-working release for the target phone.
- No new emulator test is planned while the project is paused.

## Existing downloads

See [GitHub Releases](https://github.com/aqibjavaidkhan/ubuntu-lite-android/releases). Existing releases are retained for historical comparison only. Do not label one as stable/working without a successful launch on the target phone.

## Resume checklist

1. Recover the working Termux:X11 launch commands and configuration.
2. Identify the corresponding last-known-good commit or source state, if available.
3. Diagnose the standalone APK crash.
4. Build only after the launch path is understood; then verify XFCE, touch, keyboard, and browser on the physical device.

## Technical limitations

This is not a replacement operating system or a separate Linux kernel boot. Ubuntu apps run in a user-space Linux environment under Android restrictions. Hardware acceleration and application compatibility are not guaranteed.

## Third-party notices

See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for relevant software and license references.
