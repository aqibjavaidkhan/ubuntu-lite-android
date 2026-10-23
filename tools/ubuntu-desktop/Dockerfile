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
      synaptic \
      ca-certificates curl wget bzip2 xz-utils \
      fonts-dejavu-core fonts-noto-core fonts-noto-color-emoji fonts-liberation2 \
      xdg-utils desktop-file-utils gvfs gvfs-backends \
      x11-xserver-utils locales sudo \
      adwaita-icon-theme hicolor-icon-theme gsettings-desktop-schemas \
      psmisc procps iproute2 iputils-ping net-tools \
    && locale-gen en_US.UTF-8 \
    && mkdir -p /run /tmp /var/tmp /root/.vnc /root/.config/xfce4/xfconf/xfce-perchannel-xml /usr/local/bin \
    && chmod 1777 /tmp /var/tmp

RUN mkdir -p /usr/local/share/applications /usr/share/applications

RUN cat > /usr/share/applications/ubuntu-software-manager.desktop <<'DESKTOP'
[Desktop Entry]
Version=1.0
Type=Application
Name=Ubuntu Software
GenericName=Package Manager
Comment=Install and remove applications from Ubuntu repositories
Exec=/usr/sbin/synaptic
Icon=system-software-install
Terminal=false
Categories=System;PackageManager;
StartupNotify=true
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
