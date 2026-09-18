#!/usr/bin/env python3
from __future__ import annotations
import sys
import zipfile
from pathlib import Path

if len(sys.argv) != 4:
    raise SystemExit("usage: add_dex.py <apk> <helper_classes.dex> <output_apk>")

apk = Path(sys.argv[1])
helper = Path(sys.argv[2])
out = Path(sys.argv[3])

with zipfile.ZipFile(apk, "r") as zin:
    dex_names = [n for n in zin.namelist() if n == "classes.dex" or (n.startswith("classes") and n.endswith(".dex"))]
    indices = []
    for name in dex_names:
        if name == "classes.dex":
            indices.append(1)
        else:
            try:
                indices.append(int(name[len("classes"):-len(".dex")]))
            except ValueError:
                pass
    next_index = max(indices or [1]) + 1
    entry = "classes.dex" if next_index == 1 else f"classes{next_index}.dex"

    existing = set(zin.namelist())
    if entry in existing:
        raise SystemExit(f"Refusing to overwrite existing dex: {entry}")

    with zipfile.ZipFile(out, "w") as zout:
        for info in zin.infolist():
            data = zin.read(info.filename)
            zout.writestr(info, data)
        # Store dex uncompressed; zipalign will align it later.
        zout.write(helper, arcname=entry, compress_type=zipfile.ZIP_STORED)

print(f"Injected helper dex as {entry}")
