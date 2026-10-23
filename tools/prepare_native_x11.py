#!/usr/bin/env python3
"""Fetch pinned Termux:X11 sources and adapt it for the mobile-first Ubuntu UI."""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKOUT = ROOT / ".native-x11" / "termux-x11"
PIN = "7e5f5c5c9226e2cdab392b2efba91bc4bd86463e"
URL = "https://github.com/termux/termux-x11.git"


def patch_file(path: Path, replacements):
    source = path.read_text(encoding="utf-8")
    changed = False
    for old, new, label in replacements:
        if new in source:
            continue
        if old not in source:
            raise SystemExit(f"Expected {label} patch point not found in {path}.")
        source = source.replace(old, new, 1)
        changed = True
    if changed:
        path.write_text(source, encoding="utf-8")


def main():
    CHECKOUT.parent.mkdir(parents=True, exist_ok=True)
    if not (CHECKOUT / ".git").is_dir():
        subprocess.run(["git", "clone", "--recurse-submodules", "--filter=blob:none",
                        URL, str(CHECKOUT.relative_to(ROOT))], cwd=ROOT, check=True)
    subprocess.run(["git", "fetch", "origin", PIN], cwd=CHECKOUT, check=True)
    subprocess.run(["git", "checkout", "--detach", PIN], cwd=CHECKOUT, check=True)
    subprocess.run(["git", "submodule", "update", "--init", "--recursive"], cwd=CHECKOUT, check=True)

    entry = CHECKOUT / "lorie/src/main/java/com/termux/x11/CmdEntryPoint.java"
    patch_file(entry, [(
        'URL res = loader != null ? loader.getResource(path) : null;\n        String libPath = res != null ? res.getFile().replace("file:", "") : null;',
        'String nativeDir = System.getenv("UBUNTU_LITE_NATIVE_LIBRARY_DIR");\n'
        '        URL res = loader != null ? loader.getResource(path) : null;\n'
        '        String libPath = nativeDir != null && !nativeDir.isEmpty()\n'
        '                ? new java.io.File(nativeDir, "libXlorie.so").getAbsolutePath()\n'
        '                : (res != null ? res.getFile().replace("file:", "") : null);',
        "native library loader")])

    activity = CHECKOUT / "lorie/src/main/java/com/termux/x11/MainActivity.java"
    patch_file(activity, [
        ("oldFullscreen = prefs.fullscreen.get();", "oldFullscreen = true;", "full-screen initialization"),
        ("boolean isFullscreen = prefs.fullscreen.get();", "boolean isFullscreen = true;", "immersive full-screen mode"),
        ("boolean fullscreen = prefs.fullscreen.get();", "boolean fullscreen = true;", "full-screen window settings"),
        ("boolean showNow = !isInPictureInPictureMode && getLorieView().connected() && prefs.showAdditionalKbd.get() && prefs.additionalKbdVisible.get();",
         "boolean showNow = false; // Hidden for the mobile-first touch UI.", "extra-key toolbar visibility"),
        ("public void onBackPressed() {\n    }", "public void onBackPressed() {\n        finish();\n    }", "back navigation from native desktop")
    ])

    lorie_app = CHECKOUT / "lorie/src/main/java/com/termux/x11/LorieApp.java"
    patch_file(lorie_app, [
        ("NotificationChannel channel = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_HIGH);",
         "NotificationChannel channel = new NotificationChannel(channelId, channelId, NotificationManager.IMPORTANCE_LOW);",
         "quiet X11 notification channel"),
        ("channel.setImportance(NotificationManager.IMPORTANCE_HIGH);",
         "channel.setImportance(NotificationManager.IMPORTANCE_LOW);", "quiet X11 channel priority"),
        ("public void onActivityResumed(MainActivity activity) {\n        notificationManager.notify(NOTIFICATION_ID, buildNotification(activity));\n    }",
         "public void onActivityResumed(MainActivity activity) {\n        // The desktop should not show a Termux:X11 heads-up banner.\n        notificationManager.cancel(NOTIFICATION_ID);\n    }",
         "X11 notification suppression")
    ])

    prefs = CHECKOUT / "lorie/src/main/res/xml/preferences.xml"
    patch_file(prefs, [
        ('app:key="fullscreen" app:defaultValue="false"', 'app:key="fullscreen" app:defaultValue="true"', "full-screen default"),
        ('app:key="showAdditionalKbd" app:defaultValue="true"', 'app:key="showAdditionalKbd" app:defaultValue="false"', "hidden extra-key default"),
        ('app:key="additionalKbdVisible" app:defaultValue="true"', 'app:key="additionalKbdVisible" app:defaultValue="false"', "hidden extra-key state")
    ])
    print("Prepared pinned native X11: immersive screen, hidden extra keys, quiet notification, phone storage mount.")


if __name__ == "__main__":
    main()
