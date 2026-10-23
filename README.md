# Ubuntu Desktop for Android — ARM64

An experimental Android app that aims to provide an Ubuntu 22.04 ARM64 userspace with an XFCE desktop on Android phones and tablets, without replacing Android or requiring root.

## Project goal

The goal is a practical, mobile-friendly Linux desktop experience on an Android phone/tablet, with a real Linux userspace and the ability to run Linux applications such as Firefox. This is **not a replacement operating system** and it is not Plasma Mobile.

## Development status (updated 2026-10-23)

- Android launcher / home UI: implemented in the current source.
- Ubuntu ARM64 userspace, XFCE, Firefox and PRoot packaging: build pipeline produces a bundled APK.
- Native X11 display path: experimental build exists.
- APK compilation and package-integrity checks: passing in GitHub Actions.
- **Physical-device runtime: currently failing/unverified.** The latest APK crashes immediately on the user's phone. A successful CI build does not prove that the app launches on-device.
- Android emulator smoke test: not passing reliably, so it has not established a working runtime.
- Browser, touch interaction, and full XFCE desktop session on the target phone/tablet: not yet verified.

## Downloads

Open [GitHub Releases](https://github.com/aqibjavaidkhan/ubuntu-lite-android/releases) to see the available APK builds.

**Important:** No release is currently confirmed as working on the target phone. The oldest release is retained for comparison, but it must not be described as a known-working APK until it has been launched successfully on the phone. The latest experimental native-X11 APK is a diagnostic preview, not a verified stable release.

## Technical approach

- Android host application, minimum Android 11 / ARM64 target.
- Ubuntu 22.04 ARM64 userspace running in PRoot.
- XFCE desktop and Linux applications packaged into the APK.
- Experimental native X11 display integration.
- Android remains the host operating system; hardware support and access to all Linux desktop applications are not guaranteed.

## Next priorities

1. Trace the immediate launch crash using device crash logs.
2. Identify the last commit that actually launched successfully on the target phone, if available.
3. Restore that known-good state and publish a clearly labelled APK.
4. Verify first launch, XFCE startup, display, touch, and browser on the target phone before calling a release stable.

## Build verification

GitHub Actions verifies APK packaging and bundled filesystem contents. These checks are useful but do **not** replace testing on a physical Android device. Please report the Android device model, Android version, and crash/logcat output when contributing a launch diagnosis.
