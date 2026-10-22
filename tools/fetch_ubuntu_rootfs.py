#!/usr/bin/env python3
import hashlib, re, urllib.request
from pathlib import Path

BASE = "https://cdimages.ubuntu.com/ubuntu-base/releases/22.04/release"
NAME = "ubuntu-base-22.04.5-base-arm64.tar.gz"
DEST = Path("app/src/main/assets/ubuntu-rootfs.tar.gz")

def get(url):
    req=urllib.request.Request(url,headers={"User-Agent":"UbuntuLite-Android-build/0.2"})
    with urllib.request.urlopen(req,timeout=180) as r: return r.read()

def main():
    DEST.parent.mkdir(parents=True,exist_ok=True)
    print("Downloading official Ubuntu Base ARM64 rootfs:",NAME)
    data=get(BASE+"/"+NAME)
    sums=get(BASE+"/SHA256SUMS").decode("utf-8","replace")
    m=re.search(r"^([a-fA-F0-9]{64})\s+\*?"+re.escape(NAME)+r"\s*$",sums,re.M)
    if not m: raise RuntimeError("Official SHA256SUMS did not list "+NAME)
    actual=hashlib.sha256(data).hexdigest()
    if actual.lower()!=m.group(1).lower(): raise RuntimeError("Ubuntu rootfs checksum mismatch")
    DEST.write_bytes(data)
    print("Verified rootfs:",len(data),"bytes, SHA256",actual)
if __name__=="__main__": main()
