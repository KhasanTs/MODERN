#!/usr/bin/env python3
from __future__ import annotations

import re
import sys
from pathlib import Path

KOTLIN = "Lkotlin/text/StringsKt;->j(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"
SMART = "Lcom/example/utils/SmartSearchBridge;->containsSmart(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"


def read(path: Path) -> list[str]:
    return path.read_text(encoding="utf-8", errors="replace").splitlines()


def write(path: Path, lines: list[str]) -> None:
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def method_blocks(lines: list[str]):
    blocks = []
    i = 0
    while i < len(lines):
        if lines[i].startswith(".method"):
            start = i
            j = i + 1
            while j < len(lines) and lines[j].strip() != ".end method":
                j += 1
            if j >= len(lines):
                raise RuntimeError(f"Unclosed method beginning at line {start+1}")
            blocks.append((start, j, lines[start:j+1]))
            i = j + 1
        else:
            i += 1
    return blocks


def find_unique(root: Path, filename: str) -> Path:
    hits = sorted(p for p in root.rglob(filename) if p.is_file())
    if len(hits) != 1:
        raise SystemExit(
            f"ERROR: expected exactly one {filename}, found {len(hits)}\n" +
            "\n".join(str(p) for p in hits)
        )
    return hits[0]


def patch_after_get(lines: list[str], start_marker: str, label: str) -> int:
    """Patch the first Kotlin ignore-case contains call found after any occurrence of a marker.

    The same helper/getter may occur in several generated Compose methods, so we do not
    blindly select the last occurrence. We choose the occurrence whose containing method
    actually has a following Kotlin contains call.
    """
    candidates = [i for i, line in enumerate(lines) if start_marker in line]
    if not candidates:
        raise SystemExit(f"ERROR: {label}: marker not found: {start_marker}")
    for start in candidates:
        for i in range(start + 1, len(lines)):
            if lines[i].strip() == ".end method":
                break
            if KOTLIN in lines[i]:
                lines[i] = lines[i].replace(KOTLIN, SMART)
                return 1
    raise SystemExit(f"ERROR: {label}: Kotlin contains call after marker not found")


def patch_viewmodel(root: Path) -> int:
    p = find_unique(root, "VideoViewModel$filteredVideos$1.smali")
    lines = read(p)
    # There are URL/domain checks before the actual title/channel search. We patch
    # only the contains call immediately after getTitle() and the one after getChannel().
    count = 0
    count += patch_after_get(
        lines,
        "Lcom/example/data/Video;->getTitle()Ljava/lang/String;",
        "VideoViewModel title search",
    )
    count += patch_after_get(
        lines,
        "Lcom/example/data/Video;->getChannel()Ljava/lang/String;",
        "VideoViewModel channel search",
    )
    write(p, lines)
    return count


def patch_repository(root: Path) -> int:
    p = find_unique(root, "VideoRepository.smali")
    lines = read(p)
    blocks = method_blocks(lines)
    # Only the private fallbackToLocal method is a local search fallback.
    target = None
    for start, end, block in blocks:
        if block[0].startswith(".method private final fallbackToLocal("):
            target = (start, end)
            break
    if target is None:
        raise SystemExit("ERROR: VideoRepository fallbackToLocal method not found")
    start, end = target
    sub = lines[start:end+1]
    count = 0
    count += patch_after_get(sub, "Lcom/example/data/Video;->getTitle()Ljava/lang/String;", "VideoRepository title search")
    count += patch_after_get(sub, "Lcom/example/data/Video;->getChannel()Ljava/lang/String;", "VideoRepository channel search")
    lines[start:end+1] = sub
    write(p, lines)
    return count


def patch_home(root: Path) -> int:
    p = find_unique(root, "HomeHeaderKt.smali")
    lines = read(p)
    count = patch_after_get(
        lines,
        "SearchHistory;->getQuery()Ljava/lang/String;",
        "HomeHeader search history",
    )
    write(p, lines)
    return count


def patch_library(root: Path) -> int:
    p = find_unique(root, "LibraryTabScreenKt.smali")
    lines = read(p)
    # The first two StringsKt.j calls near line ~670 are only duration formatting ("ч"/"h").
    # The actual library search is the pair immediately following SavedVideo.getTitle()/getChannel().
    count = 0
    count += patch_after_get(
        lines,
        "Lcom/example/data/SavedVideo;->getTitle()Ljava/lang/String;",
        "Library title search",
    )
    count += patch_after_get(
        lines,
        "Lcom/example/data/SavedVideo;->getChannel()Ljava/lang/String;",
        "Library channel search",
    )
    write(p, lines)
    return count


def find_exact_method(lines: list[str], signature: str):
    for start, end, block in method_blocks(lines):
        if block[0].strip() == signature:
            return start, end, block
    return None


def get_regs(block: list[str]) -> str:
    for line in block:
        s = line.strip()
        if s.startswith(".registers ") or s.startswith(".locals "):
            return line
    raise SystemExit("ERROR: method has no .registers/.locals directive")


def disable_method(path: Path, signature: str, label: str, return_kind: str) -> int:
    lines = read(path)
    target = find_exact_method(lines, signature)
    if target is None:
        raise SystemExit(f"ERROR: {label}: method not found: {signature}")
    start, end, old = target
    regs = get_regs(old)
    if return_kind == "void":
        repl = [old[0], regs, f"    # Disabled by SmartSearch/no-update patch: {label}", "    return-void", ".end method"]
    elif return_kind == "object":
        repl = [old[0], regs, f"    # Disabled by SmartSearch/no-update patch: {label}", "    const/4 v0, 0x0", "    return-object v0", ".end method"]
    else:
        raise ValueError(return_kind)
    lines[start:end+1] = repl
    write(path, lines)
    return 1


def patch_updates(root: Path) -> int:
    p = find_unique(root, "UpdateSectionKt.smali")
    total = 0
    total += disable_method(
        p,
        ".method public static final a(ZLandroidx/compose/runtime/Composer;I)V",
        "GlobalUpdateChecker entry point",
        "void",
    )
    total += disable_method(
        p,
        ".method public static final c(Lcom/example/viewmodel/VideoViewModel;ZZLandroidx/compose/runtime/Composer;I)V",
        "UpdateSection settings UI",
        "void",
    )
    return total


def patch_manager(root: Path) -> int:
    p = find_unique(root, "UpdateManager.smali")
    return disable_method(
        p,
        ".method public final checkForUpdates(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;",
        "UpdateManager.checkForUpdates",
        "object",
    )



def patch_m3u_parser(root):
    parsers = list(root.rglob("M3uParser.smali"))

    if len(parsers) != 1:
        raise RuntimeError(
            f"Expected exactly one M3uParser.smali, found {len(parsers)}"
        )

    path = parsers[0]
    text = path.read_text(encoding="utf-8")

    if "M3uNormalizer;->normalize" in text:
        print("M3U normalization already injected:", path)
        return

    method_re = re.compile(
        r"(?ms)^\.method[^\n]*Ljava/lang/String;[^\n]*\n"
        r".*?"
        r"^\.end method"
    )

    candidates = []

    for match in method_re.finditer(text):
        block = match.group(0)
        header = block.split("\n", 1)[0]

        if "Ljava/lang/String;" not in header:
            continue

        if (
            "Ljava/util/List;" not in header
            and "Ljava/util/Collection;" not in header
            and "Lcom/example/data/iptv/" not in header
            and ")Ljava/lang/Object;" not in header
        ):
            continue

        candidates.append(match)

    if not candidates:
        raise RuntimeError(
            "Could not identify String-based M3uParser method"
        )

    selected = None

    for match in candidates:
        if (
            "EXTINF" in match.group(0)
            or "EXTGRP" in match.group(0)
        ):
            selected = match
            break

    if selected is None:
        selected = candidates[0]

    block = selected.group(0)
    header = block.split("\n", 1)[0]

    params = header[
        header.find("(") + 1:
        header.rfind(")")
    ]

    register = 0 if " static " in header else 1
    i = 0
    string_register = None

    while i < len(params):

        if params.startswith("Ljava/lang/String;", i):
            string_register = register
            break

        c = params[i]

        if c in "ZBCSI":
            register += 1
            i += 1

        elif c in "FDJ":
            register += 2
            i += 1

        elif c == "L":
            end = params.find(";", i)
            if end < 0:
                break
            register += 1
            i = end + 1

        elif c == "[":
            i += 1

            while i < len(params) and params[i] == "[":
                i += 1

            if i < len(params) and params[i] == "L":
                end = params.find(";", i)
                if end < 0:
                    break
                i = end + 1
            else:
                i += 1

            register += 1

        else:
            i += 1

    if string_register is None:
        raise RuntimeError(
            "String parameter not found"
        )

    lines = block.splitlines(True)

    insert_index = None

    for n, line in enumerate(lines):
        if line.startswith(".registers ") or line.startswith(".locals "):
            insert_index = n + 1
            break

    if insert_index is None:
        raise RuntimeError(
            "No .registers/.locals found"
        )

    lines.insert(
        insert_index,
        "    invoke-static {p%d}, "
        "Lcom/example/utils/M3uNormalizer;->normalize("
        "Ljava/lang/String;)Ljava/lang/String;\n"
        "    move-result-object p%d\n"
        % (string_register, string_register)
    )

    new_block = "".join(lines)

    text = (
        text[:selected.start()]
        + new_block
        + text[selected.end():]
    )

    path.write_text(text, encoding="utf-8")

    print(
        "M3U normalization injected:",
        path,
        "p" + str(string_register)
    )

def main() -> int:
    if len(sys.argv) != 2:
        print("usage: patch_smali.py <baksmali-output-dir>", file=sys.stderr)
        return 2
    root = Path(sys.argv[1]).resolve()
    patch_m3u_parser(root)
    if not root.is_dir():
        print(f"ERROR: no directory: {root}", file=sys.stderr)
        return 2

    counts = {
        "VideoViewModel": patch_viewmodel(root),
        "VideoRepository": patch_repository(root),
        "HomeHeader": patch_home(root),
        "LibraryTabScreen": patch_library(root),
    }
    update_ui = patch_updates(root)
    update_manager = patch_manager(root)

    search_total = sum(counts.values())
    print("=== Smart Search patch ===")
    for k, v in counts.items():
        print(f"{k}: {v}")
    print(f"SmartSearch call sites changed: {search_total}")
    print(f"Updater UI methods disabled: {update_ui}")
    print(f"UpdateManager checkForUpdates disabled: {update_manager}")

    # Exact targets confirmed from the supplied decompiler dump of the user's APK.
    if search_total != 7:
        raise SystemExit(
            f"ERROR: expected exactly 7 search call sites, patched {search_total}. Refusing to build."
        )
    if update_ui != 2:
        raise SystemExit("ERROR: expected exactly 2 updater UI methods to be disabled. Refusing to build.")
    if update_manager != 1:
        raise SystemExit("ERROR: UpdateManager.checkForUpdates was not disabled. Refusing to build.")

    print("PATCH_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
