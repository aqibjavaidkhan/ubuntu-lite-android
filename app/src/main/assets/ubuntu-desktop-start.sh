#!/bin/bash
set -Eeuo pipefail
export HOME=/root
export TMPDIR=/tmp
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export XAUTHORITY=/root/.Xauthority

# Prepare writable runtime directories inside the Ubuntu rootfs.
mkdir -p /tmp /var/tmp /tmp/runtime-root /root/.vnc /tmp/.X11-unix
chmod 1777 /tmp /var/tmp
chmod 1777 /tmp/.X11-unix
chmod 700 /tmp/runtime-root
chmod 700 /root
export XDG_RUNTIME_DIR=/tmp/runtime-root

# Remove stale X11/VNC locks left by a force-stopped Android process.
vncserver -kill :1 >/dev/null 2>&1 || true
sleep 1
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1
rm -f /tmp/.X0-lock /tmp/.X11-unix/X0
rm -f /root/.Xauthority-c /root/.Xauthority-l
rm -f /root/.vnc/*.pid 2>/dev/null || true
# Keep any existing Xauthority cookie file, but ensure it is private to root.
if [ -e /root/.Xauthority ]; then
  chmod 600 /root/.Xauthority || true
fi

echo "Starting the Ubuntu XFCE desktop on a private VNC display..."
vncserver :1 -localhost yes -SecurityTypes None -geometry 720x1480 -depth 24 -dpi 150

# Fail loudly instead of leaving the Android app on a blank screen.
sleep 2
if ! pgrep -f 'Xtigervnc.*:1' >/dev/null 2>&1 && ! pgrep -f 'Xvnc.*:1' >/dev/null 2>&1; then
  echo "ERROR: TigerVNC did not start. Check /root/.vnc/*.log"
  cat /root/.vnc/*.log 2>/dev/null || true
  exit 10
fi

echo "UBUNTU_DESKTOP_DISPLAY_READY"
echo "Starting the private noVNC bridge on localhost:6080..."
exec websockify --heartbeat=30 --web=/usr/share/novnc 127.0.0.1:6080 127.0.0.1:5901
