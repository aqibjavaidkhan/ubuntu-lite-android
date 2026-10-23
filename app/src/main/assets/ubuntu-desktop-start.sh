#!/bin/bash
set -Eeuo pipefail
export HOME=/root
export TMPDIR=/tmp
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export DISPLAY=:0
export XAUTHORITY=/tmp/.Xauthority
export XDG_RUNTIME_DIR=/tmp/runtime-root
export XKB_CONFIG_ROOT=/usr/share/X11/xkb
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"

echo "Waiting for the native Android X11 display on :0..."
x_ready=0
for attempt in $(seq 1 60); do
  if xdpyinfo -display "$DISPLAY" >/dev/null 2>&1; then
    x_ready=1
    break
  fi
  sleep 1
done
if [ "$x_ready" -ne 1 ]; then
  echo "ERROR: Native X11 display :0 did not become ready."
  exit 13
fi

echo "Native X11 is ready. Starting XFCE without VNC/noVNC..."
dbus-launch --exit-with-session startxfce4 > /root/.ubuntu-lite-xfce.log 2>&1 &
xfce_pid=$!
(
  for attempt in $(seq 1 20); do
    if pgrep -x xfconfd >/dev/null 2>&1; then break; fi
    sleep 1
  done
  xfconf-query -c xsettings -p /Gtk/FontName --create -t string -s "Noto Sans 12" >/dev/null 2>&1 || true
  xfconf-query -c xfce4-panel -p /panels/panel-1/size --create -t int -s 48 >/dev/null 2>&1 || true
  xfconf-query -c xfce4-panel -p /panels/panel-1/icon-size --create -t int -s 34 >/dev/null 2>&1 || true
  xfconf-query -c xfce4-panel -p /panels/panel-1/autohide-behavior --create -t int -s 0 >/dev/null 2>&1 || true
  xfconf-query -c xfwm4 -p /general/title_font --create -t string -s "Noto Sans Bold 12" >/dev/null 2>&1 || true
) &
for attempt in $(seq 1 60); do
  if ! kill -0 "$xfce_pid" 2>/dev/null; then
    echo "ERROR: XFCE exited during startup."
    cat /root/.ubuntu-lite-xfce.log 2>/dev/null || true
    exit 14
  fi
  if pgrep -x xfce4-session >/dev/null 2>&1; then
    echo "UBUNTU_DESKTOP_DISPLAY_READY"
    break
  fi
  sleep 1
done
wait "$xfce_pid"
