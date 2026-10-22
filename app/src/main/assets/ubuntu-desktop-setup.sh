#!/bin/bash
set -Eeuo pipefail
export HOME=/root
export DEBIAN_FRONTEND=noninteractive
export TMPDIR=/tmp
mkdir -p /tmp /var/tmp /run /root/.vnc /usr/local/bin /opt
chmod 1777 /tmp /var/tmp

echo "Updating official Ubuntu package indexes..."
apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=40 update

echo "Installing XFCE desktop, VNC display server, Ubuntu package manager and LibreOffice..."
apt-get -o Acquire::Retries=3 -o Acquire::http::Timeout=40 install -y --no-install-recommends \
  xfce4 xfce4-terminal dbus-x11 \
  tigervnc-standalone-server tigervnc-common novnc websockify \
  synaptic libreoffice libreoffice-gtk3 \
  ca-certificates curl wget bzip2 xz-utils \
  fonts-dejavu-core fonts-noto-core fonts-noto-color-emoji fonts-liberation2 \
  xdg-utils desktop-file-utils gvfs gvfs-backends \
  x11-xserver-utils locales sudo

echo "Installing the official Mozilla Firefox ARM64 build (Ubuntu's Jammy firefox package is a Snap transition package)..."
rm -f /tmp/firefox-archive
curl --fail --location --retry 4 --retry-delay 2 --connect-timeout 20 \
  "https://download.mozilla.org/?product=firefox-latest-ssl&os=linux64-aarch64&lang=en-US" \
  --output /tmp/firefox-archive
mkdir -p /opt
rm -rf /opt/firefox
tar -xf /tmp/firefox-archive -C /opt
test -x /opt/firefox/firefox
ln -sfn /opt/firefox/firefox /usr/local/bin/firefox
rm -f /tmp/firefox-archive

mkdir -p /usr/local/share/applications
cat > /usr/share/applications/ubuntu-software-manager.desktop <<'DESKTOP'
[Desktop Entry]
Version=1.0
Type=Application
Name=Ubuntu Software Manager
GenericName=Package Manager
Comment=Browse, install and remove applications from Ubuntu repositories
Exec=/usr/sbin/synaptic
Icon=system-software-install
Terminal=false
Categories=System;PackageManager;
StartupNotify=true
DESKTOP

cat > /usr/share/applications/firefox-mozilla.desktop <<'DESKTOP'
[Desktop Entry]
Version=1.0
Type=Application
Name=Firefox Web Browser
Comment=Browse the Web
Exec=/opt/firefox/firefox %u
Icon=/opt/firefox/browser/chrome/icons/default/default128.png
Terminal=false
Categories=Network;WebBrowser;
StartupNotify=true
DESKTOP
chmod 644 /usr/share/applications/ubuntu-software-manager.desktop /usr/share/applications/firefox-mozilla.desktop

# Synaptic is launched as the effective PRoot user. Its normal pkexec desktop entry
# expects host systemd/polkit, which is unavailable in this no-root app environment.
if [ -f /usr/share/applications/synaptic.desktop ]; then
  sed -i 's|^Exec=.*|Exec=/usr/sbin/synaptic|' /usr/share/applications/synaptic.desktop
fi

mkdir -p /root/.vnc /tmp/runtime-root
chmod 700 /tmp/runtime-root
cat > /root/.vnc/xstartup <<'XSTART'
#!/bin/sh
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
export HOME=/root
export XDG_RUNTIME_DIR=/tmp/runtime-root
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
exec dbus-launch --exit-with-session startxfce4
XSTART
chmod 755 /root/.vnc/xstartup

# Let the first-run marker be written only after the entire desktop and application setup succeeds.
touch /root/.ubuntu-lite-desktop-ready
echo "UBUNTU_DESKTOP_INSTALL_COMPLETE"
