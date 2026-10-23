# Ubuntu Desktop for Android — ARM64

An experimental project exploring a Linux desktop experience on Android phones and tablets, without replacing Android or requiring root.

## Project goal

The long-term goal is a polished, mobile-friendly Linux desktop with an Ubuntu/Debian userspace, XFCE, and support for Linux applications. This is not a replacement operating system and is not Plasma Mobile.

## Current status — paused (2026-10-23)

**Development and new APK builds are paused for now.** The project will be revisited when a more reliable approach or better technology is available. No new emulator runs or APK builds should be started as part of routine work while the project is paused.

- A screenshot supplied by the project owner confirms that a desktop session was displayed on the phone through **Termux:X11**.
- That screenshot is a reference for the desired desktop appearance and a clue for a future recovery; it does not identify the exact working commit or prove that the current standalone APK works.
- The latest standalone APK crashes immediately on the target phone.
- GitHub Actions can compile and package an APK, but a successful build does not prove it will launch on the phone.
- The emulator smoke test did not establish a reliable working runtime.

## Available builds

Existing releases are retained for reference: [GitHub Releases](https://github.com/aqibjavaidkhan/ubuntu-lite-android/releases).

**No standalone APK is currently confirmed as working on the target phone.** Do not label any existing release stable or ask the owner to download the same failing build again.

## Future restart checklist

When work resumes:

1. Use the Termux:X11 screenshot as the visual reference.
2. Inspect the old launcher/display approach and repository history to identify the last known-good setup, if recoverable.
3. Diagnose the immediate standalone APK launch crash before attempting another large build.
4. Only publish a new APK after the owner can confirm that it launches and displays the desktop on the target phone.
5. Verify touch interaction and Linux app launching before calling it stable.

## Technical notes

The current experimental approach bundles an Ubuntu ARM64 userspace with PRoot and XFCE into an Android app. Native X11 integration has been explored, but device compatibility and the full desktop experience remain unverified. Android remains the host OS.
