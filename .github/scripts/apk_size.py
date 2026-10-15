"""Prints what an APK is made of, for the APK size report workflow.

Usage: python3 apk_size.py app.apk [other.apk ...]
"""

import collections
import os
import sys
import zipfile
import zlib


def group_of(name):
    if name.startswith("lib/"):
        return "native libs (lib/)"
    if name.startswith("assets/"):
        return "assets"
    if name.endswith(".dex"):
        return "code (dex)"
    if name.startswith("res/") or name == "resources.arsc":
        return "resources"
    if name.startswith("META-INF/"):
        return "META-INF"
    return "other"


def report(path):
    apk = zipfile.ZipFile(path)
    groups = collections.defaultdict(lambda: [0, 0])
    entries = []
    stored_libs_deflated = 0
    for info in apk.infolist():
        group = groups[group_of(info.filename)]
        group[0] += info.compress_size
        group[1] += info.file_size
        entries.append(info)
        if info.filename.endswith(".so") and info.compress_type == zipfile.ZIP_STORED:
            stored_libs_deflated += len(zlib.compress(apk.read(info.filename), 6))

    print(f"== {path}: {os.path.getsize(path) / 1e6:.1f} MB")
    for name, (packed, unpacked) in sorted(groups.items(), key=lambda g: -g[1][0]):
        print(f"  {name:20} in APK {packed / 1e6:7.2f} MB   unpacked {unpacked / 1e6:7.2f} MB")
    print("  largest entries:")
    for info in sorted(entries, key=lambda i: -i.compress_size)[:12]:
        kind = "stored " if info.compress_type == zipfile.ZIP_STORED else "deflate"
        print(f"    {info.compress_size / 1e6:7.2f} MB {kind} {info.filename}")
    if stored_libs_deflated:
        print(f"  native libs if compressed in the APK: ~{stored_libs_deflated / 1e6:.1f} MB")
    print()


for apk_path in sys.argv[1:]:
    report(apk_path)
