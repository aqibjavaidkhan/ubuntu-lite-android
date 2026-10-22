Ubuntu desktop runtime
======================

The APK carries Ubuntu Base ARM64. On first setup it obtains XFCE, TigerVNC,
noVNC/websockify, LibreOffice and Synaptic from the official Ubuntu Jammy ARM64
repositories. Firefox is downloaded from Mozilla's official Linux ARM64 channel.
The downloaded packages remain in the Android app-private Ubuntu rootfs.

The desktop is a genuine Ubuntu XFCE session rendered in the APK through noVNC.
The package manager is Synaptic against Ubuntu .deb repositories. Snap Store and
snap packages are not included: they rely on snapd/system services unavailable in
this unrooted PRoot environment. The phone's Android OS remains installed and
unchanged.

First setup needs an internet connection and multiple gigabytes of free storage.
