#!/usr/bin/env python3
"""Fetch pinned Termux:X11 sources/submodules and patch native library loading."""
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CHECKOUT = ROOT / ".native-x11" / "termux-x11"
PIN = "7e5f5c5c9226e2cdab392b2efba91bc4bd86463e"
URL = "https://github.com/termux/termux-x11.git"

def main():
    CHECKOUT.parent.mkdir(parents=True, exist_ok=True)
    if not (CHECKOUT / ".git").is_dir():
        subprocess.run(["git", "clone", "--recurse-submodules", "--filter=blob:none",
                        URL, str(CHECKOUT.relative_to(ROOT))], cwd=ROOT, check=True)
    subprocess.run(["git", "fetch", "origin", PIN], cwd=CHECKOUT, check=True)
    subprocess.run(["git", "checkout", "--detach", PIN], cwd=CHECKOUT, check=True)
    subprocess.run(["git", "submodule", "update", "--init", "--recursive"], cwd=CHECKOUT, check=True)
    entry = CHECKOUT / "lorie/src/main/java/com/termux/x11/CmdEntryPoint.java"
    source = entry.read_text(encoding="utf-8")
    old = 'URL res = loader != null ? loader.getResource(path) : null;\n        String libPath = res != null ? res.getFile().replace("file:", "") : null;'
    new = ('String nativeDir = System.getenv("UBUNTU_LITE_NATIVE_LIBRARY_DIR");\n'
           '        URL res = loader != null ? loader.getResource(path) : null;\n'
           '        String libPath = nativeDir != null && !nativeDir.isEmpty()\n'
           '                ? new java.io.File(nativeDir, "libXlorie.so").getAbsolutePath()\n'
           '                : (res != null ? res.getFile().replace("file:", "") : null);')
    if new not in source:
        if old not in source:
            raise SystemExit("Pinned Termux:X11 CmdEntryPoint changed; refusing unsafe patch.")
        entry.write_text(source.replace(old, new), encoding="utf-8")
    print("Prepared pinned Termux:X11 source:", PIN)

if __name__ == "__main__":
    main()
