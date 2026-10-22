#!/usr/bin/env python3
import gzip, hashlib, lzma, re, shutil, subprocess, urllib.request
from pathlib import Path

REPOS = ["https://packages-cf.termux.dev/apt/termux-main", "https://packages.termux.dev/apt/termux-main"]
OUT = Path("app/src/main/jniLibs/arm64-v8a")
WORK = Path(".runtime-build")
SYSTEM = {"libc.so","libm.so","libdl.so","liblog.so","libandroid.so","libz.so","libstdc++.so","libgcc_s.so","libpthread.so","libutil.so","librt.so","libresolv.so","libdl_android.so","libnativewindow.so","libvulkan.so","libEGL.so","libGLESv1_CM.so","libGLESv2.so","libGLESv3.so","libOpenSLES.so","libjnigraphics.so","libcamera2ndk.so","libmediandk.so","libaaudio.so","libatomic.so","libcutils.so","libbase.so","libutils.so","libbinder_ndk.so","libsync.so"}

def get(url):
    req=urllib.request.Request(url, headers={"User-Agent":"UbuntuLite-Android-build/0.2"})
    with urllib.request.urlopen(req,timeout=120) as r: return r.read()

def stanzas(raw):
    out={}
    for stanza in re.split(r"\n\s*\n",raw.strip()):
        d={}; last=None
        for line in stanza.splitlines():
            if line.startswith((" ","\t")) and last: d[last]+=" "+line.strip()
            elif ":" in line:
                k,v=line.split(":",1); d[k]=v.strip(); last=k
        if d.get("Package") and d.get("Filename"): out[d["Package"]]=d
    return out

def main():
    shutil.rmtree(WORK,ignore_errors=True); shutil.rmtree(OUT,ignore_errors=True)
    WORK.mkdir(parents=True); OUT.mkdir(parents=True)
    packages=None; repo=None
    for base in REPOS:
        for suffix in ("Packages.xz","Packages.gz","Packages"):
            try:
                data=get(f"{base}/dists/stable/main/binary-aarch64/{suffix}")
                if suffix.endswith(".xz"): data=lzma.decompress(data)
                elif suffix.endswith(".gz"): data=gzip.decompress(data)
                candidate=stanzas(data.decode("utf-8","replace"))
                if "proot" in candidate and "libtalloc" in candidate:
                    packages=candidate; repo=base; break
            except Exception as e: print("index retry:",e)
        if packages: break
    if not packages: raise RuntimeError("Unable to download Termux ARM64 package index")
    selected={}; queue=["proot","libtalloc","libandroid-shmem"]
    while queue:
        name=queue.pop(0)
        if name in selected or name in {"libc","libc++","libpthread","libdl","libm"}: continue
        item=packages.get(name)
        if not item:
            if name.startswith("lib"): raise RuntimeError("Missing dependency "+name)
            continue
        selected[name]=item
        for depgroup in item.get("Depends","").split(","):
            for alternative in depgroup.split("|"):
                m=re.match(r"\s*([A-Za-z0-9+_.:-]+)",alternative)
                if m:
                    dep=m.group(1)
                    if dep in packages and dep.startswith("lib") and dep not in selected and dep not in queue: queue.append(dep)
    roots=[]
    for name,meta in selected.items():
        data=get(repo+"/"+meta["Filename"])
        expected=meta.get("SHA256","").lower()
        if expected and hashlib.sha256(data).hexdigest().lower()!=expected: raise RuntimeError("SHA256 mismatch: "+name)
        deb=WORK/(name+".deb"); deb.write_bytes(data)
        dest=WORK/name; dest.mkdir(parents=True)
        subprocess.run(["dpkg-deb","-x",str(deb),str(dest)],check=True); roots.append(dest)
    proots=[p for r in roots for p in r.rglob("proot") if p.is_file() and p.parent.name=="bin"]
    loaders=[p for r in roots for p in r.rglob("loader") if p.is_file() and p.parent.name=="proot" and "libexec" in p.parts]
    if not proots or not loaders: raise RuntimeError("proot executable/loader missing from downloaded packages")
    shutil.copy2(proots[0].resolve(),OUT/"libproot.so")
    shutil.copy2(loaders[0].resolve(),OUT/"libproot-loader.so")
    libmap={}
    for root in roots:
        for p in root.rglob("lib*.so*"):
            try: src=p.resolve(strict=True)
            except FileNotFoundError: continue
            if not src.is_file(): continue
            m=re.match(r"^(.*?\.so)(?:\..*)?$",p.name)
            if not m: continue
            name=m.group(1)
            if name in SYSTEM or name in libmap: continue
            dst=OUT/name; shutil.copy2(src,dst); libmap[name]=dst
    def needed(p):
        try: out=subprocess.check_output(["readelf","-d",str(p)],stderr=subprocess.DEVNULL,text=True)
        except Exception: return []
        return re.findall(r"Shared library: \[(.*?)\]",out)
    for binary in [OUT/"libproot.so"]+[p for p in OUT.glob("lib*.so") if p.name!="libproot-loader.so"]:
        for dep in needed(binary):
            m=re.match(r"^(.*?\.so)(?:\..*)?$",dep); normal=m.group(1) if m else dep
            if dep!=normal and normal in libmap: subprocess.run(["patchelf","--replace-needed",dep,normal,str(binary)],check=True)
            elif dep not in SYSTEM and normal not in SYSTEM and normal not in libmap:
                raise RuntimeError(f"Unresolved native dependency {dep} in {binary.name}")
        subprocess.run(["patchelf","--set-rpath","$ORIGIN",str(binary)],check=False,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    for p in OUT.iterdir(): p.chmod(0o755)
    print("Bundled PRoot runtime:",[(p.name,p.stat().st_size) for p in OUT.iterdir()])
if __name__=="__main__": main()
