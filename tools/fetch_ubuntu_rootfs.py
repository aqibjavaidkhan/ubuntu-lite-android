#!/usr/bin/env python3
import hashlib, os, re, shutil, subprocess, tarfile, urllib.request
from pathlib import Path

BASE = "https://cdimages.ubuntu.com/ubuntu-base/releases/22.04/release"
NAME = "ubuntu-base-22.04.5-base-arm64.tar.gz"
DEST = Path("app/src/main/assets/ubuntu-rootfs.dat")
WORK = Path(".ubuntu-prebuild")
ROOT = WORK / "rootfs"

def get(url):
    req = urllib.request.Request(url, headers={"User-Agent":"UbuntuLite-Android-build/0.3"})
    with urllib.request.urlopen(req, timeout=240) as r:
        return r.read()

def run(cmd, **kwargs):
    print("+", " ".join(map(str, cmd)), flush=True)
    subprocess.run(list(map(str, cmd)), check=True, **kwargs)

def main():
    shutil.rmtree(WORK, ignore_errors=True)
    ROOT.mkdir(parents=True)
    DEST.parent.mkdir(parents=True, exist_ok=True)
    print("Downloading official Ubuntu Base ARM64 rootfs:", NAME, flush=True)
    data = get(BASE + "/" + NAME)
    sums = get(BASE + "/SHA256SUMS").decode("utf-8", "replace")
    m = re.search(r"^([a-fA-F0-9]{64})\s+\*?" + re.escape(NAME) + r"\s*$", sums, re.M)
    if not m:
        raise RuntimeError("Official SHA256SUMS did not list " + NAME)
    actual = hashlib.sha256(data).hexdigest()
    if actual.lower() != m.group(1).lower():
        raise RuntimeError("Ubuntu rootfs checksum mismatch")
    archive = WORK / NAME
    archive.write_bytes(data)
    print("Verified official rootfs SHA256:", actual, flush=True)
    with tarfile.open(archive, "r:gz") as tf:
        tf.extractall(ROOT, filter="data")

    # QEMU user emulation lets CI install genuine ARM64 Ubuntu packages into
    # the image before it is bundled into the APK.
    run(["sudo", "cp", "/usr/bin/qemu-aarch64-static", ROOT / "usr/bin/qemu-aarch64-static"])
    (ROOT / "etc/resolv.conf").unlink(missing_ok=True)
    (ROOT / "etc/resolv.conf").write_text("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
    sources = ROOT / "etc/apt/sources.list"
    sources.write_text(
        "deb http://ports.ubuntu.com/ubuntu-ports jammy main restricted universe multiverse\n"
        "deb http://ports.ubuntu.com/ubuntu-ports jammy-updates main restricted universe multiverse\n"
        "deb http://ports.ubuntu.com/ubuntu-ports jammy-backports main restricted universe multiverse\n"
        "deb http://ports.ubuntu.com/ubuntu-ports jammy-security main restricted universe multiverse\n"
    )
    (ROOT / "usr/sbin/policy-rc.d").write_text("#!/bin/sh\nexit 101\n")
    (ROOT / "usr/sbin/policy-rc.d").chmod(0o755)
    (ROOT / "tmp").mkdir(exist_ok=True)
    (ROOT / "var/tmp").mkdir(parents=True, exist_ok=True)
    run(["sudo", "mount", "--bind", "/dev", ROOT / "dev"])
    run(["sudo", "mount", "-t", "proc", "proc", ROOT / "proc"])
    run(["sudo", "mount", "--bind", "/sys", ROOT / "sys"])
    try:
        env = os.environ.copy()
        env["DEBIAN_FRONTEND"] = "noninteractive"
        run(["sudo", "chroot", ROOT, "/usr/bin/env", "DEBIAN_FRONTEND=noninteractive",
             "apt-get", "-o", "Acquire::Retries=3", "-o", "Acquire::http::Timeout=60", "update"], env=env)
        run(["sudo", "chroot", ROOT, "/usr/bin/env", "DEBIAN_FRONTEND=noninteractive",
             "apt-get", "-o", "Acquire::Retries=3", "-o", "Acquire::http::Timeout=60",
             "install", "-y", "--no-install-recommends",
             "xfce4", "xfce4-terminal", "dbus-x11",
             "tigervnc-standalone-server", "tigervnc-common", "novnc", "websockify",
             "synaptic", "libreoffice", "libreoffice-gtk3",
             "ca-certificates", "curl", "wget", "bzip2", "xz-utils",
             "fonts-dejavu-core", "fonts-noto-core", "fonts-noto-color-emoji",
             "fonts-liberation2", "xdg-utils", "desktop-file-utils", "gvfs",
             "gvfs-backends", "x11-xserver-utils", "locales", "sudo"])
        firefox = get("https://download.mozilla.org/?product=firefox-latest-ssl&os=linux64-aarch64&lang=en-US")
        ff = WORK / "firefox.tar.bz2"
        ff.write_bytes(firefox)
        run(["sudo", "mkdir", "-p", ROOT / "opt"])
        run(["sudo", "tar", "-xf", ff, "-C", ROOT / "opt"])
        run(["sudo", "chroot", ROOT, "/bin/bash", "-lc",
             "ln -sfn /opt/firefox/firefox /usr/local/bin/firefox; "
             "mkdir -p /usr/share/applications; "
             "printf '[Desktop Entry]\\nVersion=1.0\\nType=Application\\nName=Ubuntu Software Manager\\nExec=/usr/sbin/synaptic\\nIcon=system-software-install\\nTerminal=false\\nCategories=System;PackageManager;\\n' > /usr/share/applications/ubuntu-software-manager.desktop; "
             "printf '[Desktop Entry]\\nVersion=1.0\\nType=Application\\nName=Firefox Web Browser\\nExec=/opt/firefox/firefox %u\\nIcon=/opt/firefox/browser/chrome/icons/default/default128.png\\nTerminal=false\\nCategories=Network;WebBrowser;\\n' > /usr/share/applications/firefox-mozilla.desktop; "
             "sed -i 's|^Exec=.*|Exec=/usr/sbin/synaptic|' /usr/share/applications/synaptic.desktop 2>/dev/null || true; "
             "mkdir -p /root/.vnc /tmp/runtime-root; chmod 1777 /tmp /var/tmp; "
             "printf '#!/bin/sh\\nunset SESSION_MANAGER\\nunset DBUS_SESSION_BUS_ADDRESS\\nexport HOME=/root\\nexport XDG_RUNTIME_DIR=/tmp/runtime-root\\nmkdir -p \\$XDG_RUNTIME_DIR\\nchmod 700 \\$XDG_RUNTIME_DIR\\nexec dbus-launch --exit-with-session startxfce4\\n' > /root/.vnc/xstartup; "
             "chmod 755 /root/.vnc/xstartup; "
             "touch /root/.ubuntu-lite-desktop-ready; "
             "apt-get clean; rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/* /usr/bin/qemu-aarch64-static"])
    finally:
        for mountpoint in ("sys", "proc", "dev"):
            subprocess.run(["sudo", "umount", "-lf", str(ROOT / mountpoint)], check=False)
    # Ensure host ownership is not preserved; Android extracts as app-private files.
    out = WORK / "ubuntu-rootfs.tar.gz"
    with tarfile.open(out, "w:gz", compresslevel=6, format=tarfile.PAX_FORMAT) as tf:
        for p in ROOT.iterdir():
            tf.add(p, arcname=p.name, recursive=True)
    shutil.copyfile(out, DEST)
    print("Prebuilt Ubuntu desktop rootfs bundled:", DEST, DEST.stat().st_size, "bytes", flush=True)

if __name__ == "__main__":
    main()
