Ubuntu Desktop — preinstalled in the APK
=========================================

The APK embeds an Ubuntu 22.04 ARM64 root filesystem that already contains:
- XFCE desktop and XFCE Terminal
- TigerVNC plus noVNC/websockify for the embedded display
- LibreOffice
- Synaptic package manager and Ubuntu APT repository indexes
- Mozilla Firefox's official Linux ARM64 binary
- desktop fonts, icons, and utilities

The first launch extracts this bundled image to Android app-private storage and
starts XFCE. It does not download the desktop packages on the phone. Keep several
GB of free local storage available for extraction and use.

The Ubuntu userland runs through PRoot on Android's existing kernel. Snap/snapd
isn't included because this unrooted environment does not run Ubuntu's usual
system services. Synaptic/apt can use Ubuntu .deb repositories; package mutations
may remain limited by the no-root PRoot filesystem model.

Ubuntu, XFCE, Firefox, LibreOffice, and their dependencies retain their respective
upstream licenses. See the repository's THIRD_PARTY_NOTICES.md.
