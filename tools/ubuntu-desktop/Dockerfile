FROM --platform=linux/arm64 ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LANGUAGE=C:en \
    LC_ALL=C.UTF-8 \
    HOME=/root \
    PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# Enable all official Ubuntu Jammy components before installing desktop applications.
RUN set -eux; \
    if [ -f /etc/apt/sources.list ]; then \
      sed -i -E '/^[[:space:]]*deb / { / universe/! s/$/ universe/; / multiverse/! s/$/ multiverse/; }' /etc/apt/sources.list; \
    fi; \
    for f in /etc/apt/sources.list.d/*.sources; do \
      if [ -f "$f" ]; then \
        sed -i -E '/^Components:/ { /universe/! s/$/ universe/; /multiverse/! s/$/ multiverse/; }' "$f"; \
      fi; \
    done

# Prevent package post-install scripts from trying to start system services.
RUN printf '#!/bin/sh\nexit 101\n' > /usr/sbin/policy-rc.d && chmod 755 /usr/sbin/policy-rc.d

RUN apt-get update && apt-get install -y --no-install-recommends \
      xfce4 xfce4-goodies xfce4-terminal thunar mousepad \
      dbus-x11 dbus-user-session \
      tigervnc-standalone-server tigervnc-common novnc websockify \
      synaptic plank pavucontrol \
      ca-certificates curl wget bzip2 xz-utils \
      fonts-dejavu-core fonts-noto-core fonts-noto-color-emoji fonts-liberation2 \
      xdg-utils desktop-file-utils gvfs gvfs-backends \
      x11-xserver-utils locales sudo \
      adwaita-icon-theme hicolor-icon-theme gsettings-desktop-schemas librsvg2-common \
      psmisc procps iproute2 iputils-ping net-tools \
    && locale-gen en_US.UTF-8 \
    && mkdir -p /run /tmp /var/tmp /root/.vnc /root/.config/xfce4/xfconf/xfce-perchannel-xml /usr/local/bin \
    && chmod 1777 /tmp /var/tmp

RUN mkdir -p /usr/local/share/applications /usr/share/applications \
      /usr/share/backgrounds/ubuntu-lite \
      /root/.config/xfce4/xfconf/xfce-perchannel-xml \
      /root/.config/autostart \
      /root/.config/plank/dock1/launchers

# A lightweight Garuda-inspired dark mountain wallpaper bundled inside the rootfs.
RUN cat > /usr/share/backgrounds/ubuntu-lite/garuda-inspired.svg <<'WALLPAPER'
<svg xmlns="http://www.w3.org/2000/svg" width="1600" height="900" viewBox="0 0 1600 900">
 <defs>
  <linearGradient id="sky" x1="0" y1="0" x2=".3" y2="1"><stop offset="0" stop-color="#07111f"/><stop offset=".58" stop-color="#13293b"/><stop offset="1" stop-color="#263b4b"/></linearGradient>
  <radialGradient id="glow"><stop stop-color="#e95420" stop-opacity=".62"/><stop offset="1" stop-color="#e95420" stop-opacity="0"/></radialGradient>
  <linearGradient id="snow" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#eff6fb"/><stop offset=".55" stop-color="#9eb8ca"/><stop offset="1" stop-color="#58758c"/></linearGradient>
  <linearGradient id="shadow" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#273d50"/><stop offset="1" stop-color="#06101c"/></linearGradient>
 </defs>
 <rect width="1600" height="900" fill="url(#sky)"/>
 <circle cx="1210" cy="265" r="260" fill="url(#glow)"/><circle cx="1210" cy="265" r="55" fill="#ed673e" opacity=".86"/>
 <path d="M0 510 L110 440 185 470 300 330 390 435 520 260 610 390 735 310 860 445 975 300 1090 420 1240 250 1390 420 1490 350 1600 420 V900 H0Z" fill="#203549"/>
 <path d="M160 900 L300 330 380 455 455 408 520 260 590 421 660 365 760 500 850 420 970 555 1090 420 1165 490 1240 250 1335 440 1390 420 1510 900Z" fill="url(#shadow)"/>
 <path d="M300 330 L341 490 380 455 420 485 455 408 475 505 520 260 550 435 590 421 630 510 660 365 690 485 760 500 810 570 850 420 900 540 970 555 1035 590 1090 420 1130 535 1165 490 1200 550 1240 250 1270 430 1335 440 1390 420 1430 560 1510 900 H160Z" fill="#14283b"/>
 <path d="M300 330 L345 470 380 455 420 485 455 408 475 490 520 260 552 425 590 421 630 500 660 365 695 480 760 500 810 555 850 420 900 530 970 555 1035 580 1090 420 1130 520 1165 490 1200 540 1240 250 1270 420 1335 440 1390 420 1430 545 1510 900Z" fill="url(#snow)" opacity=".94"/>
 <path d="M520 260 L536 386 552 425 590 421 630 500 590 468 566 470 550 420Z M1240 250 L1250 370 1270 420 1335 440 1300 424 1278 384 1262 362Z" fill="#f7fbff" opacity=".72"/>
 <path d="M0 655 L155 585 300 650 445 560 590 670 730 590 885 705 1035 610 1190 700 1335 580 1480 660 1600 600 V900 H0Z" fill="#0b1926" opacity=".86"/>
 <path d="M0 780 L220 690 400 740 575 660 760 780 930 690 1100 770 1290 655 1460 745 1600 700 V900 H0Z" fill="#050d17" opacity=".9"/>
 <path d="M0 865 Q420 820 800 866 T1600 845" fill="none" stroke="#e95420" stroke-opacity=".55" stroke-width="3"/>
</svg>
WALLPAPER

RUN cat > /root/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-desktop.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfce4-desktop" version="1.0">
 <property name="backdrop" type="empty">
  <property name="screen0" type="empty">
   <property name="monitorVNC-0" type="empty">
    <property name="workspace0" type="empty">
     <property name="color-style" type="int" value="0"/>
     <property name="image-style" type="int" value="5"/>
     <property name="last-image" type="string" value="/usr/share/backgrounds/ubuntu-lite/garuda-inspired.svg"/>
    </property>
   </property>
  </property>
 </property>
</channel>
XML

RUN cat > /root/.config/xfce4/xfconf/xfce-perchannel-xml/xsettings.xml <<'XML'
<?xml version="1.0" encoding="UTF-8"?>
<channel name="xsettings" version="1.0">
 <property name="Net" type="empty">
  <property name="ThemeName" type="string" value="Adwaita-dark"/>
  <property name="IconThemeName" type="string" value="Adwaita"/>
 </property>
 <property name="Gtk" type="empty">
  <property name="FontName" type="string" value="Noto Sans 10"/>
 </property>
</channel>
XML

RUN cat > /usr/local/bin/ubuntu-lite-desktop-init.sh <<'SCRIPT'
#!/bin/bash
# Apply visual preferences after XFCE and xfconf have started; never block desktop startup.
sleep 3
WALL=/usr/share/backgrounds/ubuntu-lite/garuda-inspired.svg
for monitor in $(xrandr --query 2>/dev/null | awk '/ connected/{print $1}'); do
  for screen in screen0; do
    xfconf-query -c xfce4-desktop -p "/backdrop/$screen/monitor$monitor/workspace0/last-image" --create -t string -s "$WALL" >/dev/null 2>&1 || true
    xfconf-query -c xfce4-desktop -p "/backdrop/$screen/monitor$monitor/workspace0/image-style" --create -t int -s 5 >/dev/null 2>&1 || true
  done
done
xfconf-query -c xsettings -p /Net/ThemeName --create -t string -s "Adwaita-dark" >/dev/null 2>&1 || true
xfconf-query -c xfce4-panel -p /panels/panel-1/position --create -t string -s "p=6;x=0;y=0" >/dev/null 2>&1 || true
SCRIPT
RUN chmod 755 /usr/local/bin/ubuntu-lite-desktop-init.sh
RUN cat > /root/.config/autostart/ubuntu-lite-theme.desktop <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Ubuntu Lite Desktop Theme
Comment=Apply the dark mountain desktop theme
Exec=/usr/local/bin/ubuntu-lite-desktop-init.sh
OnlyShowIn=XFCE;
X-GNOME-Autostart-enabled=true
DESKTOP

RUN cat > /usr/share/applications/ubuntu-software-manager.desktop <<'DESKTOP'
[Desktop Entry]
Version=1.0
Type=Application
Name=Software Store
GenericName=Package Manager
Comment=Install applications from Ubuntu repositories
Exec=/usr/sbin/synaptic
Icon=system-software-install
Terminal=false
Categories=System;PackageManager;
StartupNotify=true
DESKTOP
RUN cat > /usr/share/applications/ubuntu-files.desktop <<'DESKTOP'
[Desktop Entry]
Version=1.0
Type=Application
Name=Files
Exec=thunar
Icon=system-file-manager
Terminal=false
Categories=System;FileManager;
DESKTOP
RUN cat > /usr/share/applications/ubuntu-terminal.desktop <<'DESKTOP'
[Desktop Entry]
Version=1.0
Type=Application
Name=Terminal
Exec=xfce4-terminal
Icon=utilities-terminal
Terminal=false
Categories=System;TerminalEmulator;
DESKTOP
RUN cat > /usr/share/applications/ubuntu-editor.desktop <<'DESKTOP'
[Desktop Entry]
Version=1.0
Type=Application
Name=Text Editor
Exec=mousepad
Icon=accessories-text-editor
Terminal=false
Categories=Utility;TextEditor;
DESKTOP

RUN for item in ubuntu-files ubuntu-terminal ubuntu-editor ubuntu-software-manager; do \
      printf '[PlankDockItemPreferences]\nLauncher=file:///usr/share/applications/%s.desktop\n' "$item" \
        > "/root/.config/plank/dock1/launchers/$item.dockitem"; \
    done
RUN cat > /root/.config/autostart/ubuntu-lite-dock.desktop <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=Ubuntu Lite Dock
Comment=Lightweight application dock
Exec=plank
OnlyShowIn=XFCE;
X-GNOME-Autostart-enabled=true
DESKTOP

# Run Synaptic directly; this rootless PRoot image has no host polkit/systemd.
RUN if [ -f /usr/share/applications/synaptic.desktop ]; then \
      sed -i 's|^Exec=.*|Exec=/usr/sbin/synaptic|' /usr/share/applications/synaptic.desktop; \
    fi

RUN cat > /root/.vnc/xstartup <<'XSTART'
#!/bin/sh
unset SESSION_MANAGER
unset DBUS_SESSION_BUS_ADDRESS
export HOME=/root
export XDG_RUNTIME_DIR=/tmp/runtime-root
mkdir -p "$XDG_RUNTIME_DIR"
chmod 700 "$XDG_RUNTIME_DIR"
exec dbus-launch --exit-with-session startxfce4
XSTART

RUN chmod 755 /root/.vnc/xstartup && \
    mkdir -p /tmp/runtime-root /tmp/.X11-unix && \
    chmod 700 /tmp/runtime-root && \
    dbus-uuidgen > /etc/machine-id && \
    touch /root/.ubuntu-lite-desktop-ready /root/.ubuntu-preinstalled-desktop-v1 && \
    rm -rf /var/cache/apt/archives/*.deb /var/cache/debconf/*-old /tmp/* /var/tmp/* && \
    find /var/log -type f -exec truncate -s 0 {} \; && \
    chmod 1777 /tmp /var/tmp
