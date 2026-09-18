#!/usr/bin/env python3
from __future__ import annotations
import sys
import zipfile
from pathlib import Path

if len(sys.argv) != 4:
    raise SystemExit("usage: add_dex.py <apk> <helper.dex> <out.apk>")

apk = Path(sys.argv[1])
helper = Path(sys.argv[2])
out = Path(sys.argv[3])

with zipfile.ZipFile(apk, "r") as zin:
    names = set(zin.namelist())
    nums = []
    for name in names:
        if name == "classes.dex":
            nums.append(1)
        elif name.startswith("classes") and name.endswith(".dex"):
            mid = name[len("classes"):-len(".dex")]
            if mid.isdigit():
                nums.append(int(mid))
    entry = f"classes{max(nums or [1]) + 1}.dex"
    if entry in names:
        raise SystemExit(f"Refusing to overwrite existing {entry}")
    with zipfile.ZipFile(out, "w") as zout:
        for info in zin.infolist():
            # Drop old APK signature artifacts. apksigner will create a fresh signature.
            upper = info.filename.upper()
            if upper.startswith("META-INF/") and upper.endswith((".RSA", ".DSA", ".EC", ".SF", ".MF")):
                continue
            data = zin.read(info.filename)
            zout.writestr(info, data)
        zout.write(helper, arcname=entry, compress_type=zipfile.ZIP_STORED)
print(f"Injected helper as {entry}")
