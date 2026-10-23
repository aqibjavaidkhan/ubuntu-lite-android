#!/bin/bash
set -Eeuo pipefail
export HOME=/root
export TMPDIR=/tmp
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# The Android app runs Ubuntu inside PRoot. TigerVNC's traditional wrapper
# uses xauth lock files and X server hard-link lock files, which can fail in
# restricted app filesystems. Use Xtigervnc directly with -nolock and -ac.
mkdir -p /tmp /var/tmp /tmp/runtime-root /tmp/.X11-unix /root/.vnc
chmod 1777 /tmp /var/tmp /tmp/.X11-unix
chmod 700 /tmp/runtime-root /root /root/.vnc
export XDG_RUNTIME_DIR=/tmp/runtime-root

# Clean up an older display left behind after force-stopping the Android app.
pkill -f '[X]tigervnc.*:1' >/dev/null 2>&1 || true
pkill -f '[X]vnc.*:1' >/dev/null 2>&1 || true
sleep 1
rm -f /tmp/.X1-lock /tmp/.X11-unix/X1 /tmp/.X0-lock /tmp/.X11-unix/X0
rm -f /root/.Xauthority-c /root/.Xauthority-l /root/.vnc/*.pid 2>/dev/null || true

echo "Starting TigerVNC directly (no hard-link lock files)..."
Xtigervnc :1 \
  -rfbport 5901 \
  -localhost yes \
  -SecurityTypes None \
  -geometry 720x1480 \
  -depth 24 \
  -dpi 150 \
  -nolock \
  -ac \
  -noreset \
  > /root/.vnc/xtigervnc.log 2>&1 &
vnc_pid=$!

# Wait for the X/VNC server to be ready, and surface its own log on failure.
vnc_ready=0
for attempt in $(seq 1 20); do
  if ! kill -0 "$vnc_pid" 2>/dev/null; then
    echo "ERROR: Xtigervnc exited before the display became ready."
    cat /root/.vnc/xtigervnc.log 2>/dev/null || true
    exit 10
  fi
  if (echo > /dev/tcp/127.0.0.1/5901) >/dev/null 2>&1; then
    vnc_ready=1
    break
  fi
  sleep 1
done
if [ "$vnc_ready" -ne 1 ]; then
  echo "ERROR: TigerVNC did not open localhost port 5901."
  cat /root/.vnc/xtigervnc.log 2>/dev/null || true
  exit 11
fi

export DISPLAY=:1
unset XAUTHORITY
echo "TigerVNC display is ready. Starting XFCE..."
dbus-launch --exit-with-session startxfce4 > /root/.vnc/xfce.log 2>&1 &
xfce_pid=$!
sleep 2
if ! kill -0 "$vnc_pid" 2>/dev/null; then
  echo "ERROR: TigerVNC stopped while XFCE was starting."
  cat /root/.vnc/xtigervnc.log 2>/dev/null || true
  cat /root/.vnc/xfce.log 2>/dev/null || true
  exit 12
fi

echo "UBUNTU_DESKTOP_DISPLAY_READY"
echo "Starting the private noVNC bridge on localhost:6080..."
exec websockify --heartbeat=30 --web=/usr/share/novnc 127.0.0.1:6080 127.0.0.1:5901
