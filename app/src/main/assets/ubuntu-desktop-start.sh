#!/bin/bash
set -Eeuo pipefail
export HOME=/root
export TMPDIR=/tmp
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
export DISPLAY=:0
export XAUTHORITY=/tmp/.Xauthority
export XDG_RUNTIME_DIR=/tmp/runtime-root
export XKB_CONFIG_ROOT=/usr/share/X11/xkb
mkdir -p "$XDG_RUNTIME_DIR" /tmp/.X11-unix
chmod 700 "$XDG_RUNTIME_DIR"
chmod 1777 /tmp /tmp/.X11-unix

# Existing installations also need an unprivileged Firefox user/launcher.
if ! id ubuntu >/dev/null 2>&1 && command -v useradd >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash ubuntu || true
fi
if [ -x /usr/bin/firefox ] && command -v su >/dev/null 2>&1 && id ubuntu >/dev/null 2>&1; then
  cat > /usr/local/bin/ubuntu-firefox <<'FIREFOX'
#!/bin/sh
export HOME=/home/ubuntu
export USER=ubuntu
export LOGNAME=ubuntu
unset DBUS_SESSION_BUS_ADDRESS
cd /home/ubuntu || exit 1
exec su -s /bin/bash -c 'export HOME=/home/ubuntu USER=ubuntu LOGNAME=ubuntu; cd /home/ubuntu; exec /usr/bin/firefox "$@"' ubuntu ubuntu-firefox "$@"
FIREFOX
  chmod 755 /usr/local/bin/ubuntu-firefox
  for launch in /usr/share/applications/ubuntu-browser.desktop /usr/share/applications/firefox.desktop; do
    if [ -f "$launch" ]; then sed -i 's|^Exec=firefox$|Exec=ubuntu-firefox|' "$launch"; fi
  done
fi

echo "Waiting for the native Android X11 display on :0..."
x_ready=0
for attempt in $(seq 1 60); do
  if xset -q >/dev/null 2>&1; then
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
# Upgrade existing installs without deleting home files: install Firefox from
# Mozilla's signed ARM64 repository and clean obsolete VNC packages after XFCE starts.
if ! command -v firefox >/dev/null 2>&1 && [ ! -e /root/.ubuntu-lite-browser-migration.lock ]; then
  touch /root/.ubuntu-lite-browser-migration.lock
  (
    trap 'rm -f /root/.ubuntu-lite-browser-migration.lock' EXIT
    export DEBIAN_FRONTEND=noninteractive
    set +e
    apt-get update
    apt-get install -y --no-install-recommends ca-certificates curl gnupg
    mkdir -p /etc/apt/keyrings /etc/apt/preferences.d
    curl -fsSL https://packages.mozilla.org/apt/repo-signing-key.gpg | gpg --dearmor -o /etc/apt/keyrings/packages.mozilla.org.gpg
    if [ -s /etc/apt/keyrings/packages.mozilla.org.gpg ]; then
      printf 'Types: deb\nURIs: https://packages.mozilla.org/apt\nSuites: mozilla\nComponents: main\nArchitectures: arm64\nSigned-By: /etc/apt/keyrings/packages.mozilla.org.gpg\n' > /etc/apt/sources.list.d/mozilla.sources
      printf 'Package: *\nPin: origin packages.mozilla.org\nPin-Priority: 1000\n\nPackage: firefox*\nPin: release o=Ubuntu\nPin-Priority: -1\n' > /etc/apt/preferences.d/mozilla
      apt-get update && apt-get install -y --no-install-recommends firefox
    fi
    apt-get purge -y --auto-remove tigervnc-standalone-server tigervnc-common novnc websockify
  ) > /root/.ubuntu-lite-browser-migration.log 2>&1 &
fi
wait "$xfce_pid"
