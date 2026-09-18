#!/usr/bin/env python3
from __future__ import annotations

import argparse
import re
from pathlib import Path

TARGETS = [
    "com/example/viewmodel/VideoViewModel.smali",
    "com/example/data/VideoRepository.smali",
    "com/example/ui/screens/home/components/HomeHeader.smali",
    "com/example/ui/screens/library/LibraryTabScreen.smali",
]

HELPER_OWNER = "Lcom/example/utils/SmartSearchBridge;"
HELPER_2 = HELPER_OWNER + "->containsSmart(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z"
HELPER_3 = HELPER_OWNER + "->containsSmart(Ljava/lang/CharSequence;Ljava/lang/CharSequence;Z)Z"

DIRECT_RE = re.compile(
    r"^(?P<indent>\s*)invoke-static(?P<range>/range)?\s+(?P<regs>\{[^}]+\}),\s+"
    r"Lkotlin/text/StringsKt[^;]*;->contains"
    r"(?P<sig>\(Ljava/lang/CharSequence;Ljava/lang/CharSequence;(?:Z)?\)Z)$"
)

DEFAULT_RE = re.compile(
    r"^(?P<indent>\s*)invoke-static(?P<range>/range)?\s+(?P<regs>\{[^}]+\}),\s+"
    r"Lkotlin/text/StringsKt[^;]*;->contains\$default"
    r"\(Ljava/lang/CharSequence;Ljava/lang/CharSequence;ZILjava/lang/Object;\)Z$"
)


def parse_regs(regs: str) -> list[str] | None:
    body = regs.strip()[1:-1].strip()
    if ".." in body:
        left, right = [x.strip() for x in body.split("..", 1)]
        def reg_num(r: str) -> int:
            return int(r[1:])
        if not (left.startswith(("v", "p")) and right.startswith(("v", "p"))):
            return None
        a, b = reg_num(left), reg_num(right)
        prefix = left[0]
        if b < a:
            return None
        return [f"{prefix}{i}" for i in range(a, b + 1)]
    return [x.strip() for x in body.split(",") if x.strip()]


def replace_search_invokes(text: str) -> tuple[str, int]:
    changed = 0
    out: list[str] = []
    for line in text.splitlines(keepends=True):
        m = DEFAULT_RE.match(line.rstrip("\r\n"))
        if m:
            regs = parse_regs(m.group("regs"))
            if regs and len(regs) >= 3:
                newline = "\n" if line.endswith("\n") else ""
                if line.endswith("\r\n"):
                    newline = "\r\n"
                out.append(f'{m.group("indent")}invoke-static {{{regs[0]}, {regs[1]}, {regs[2]}}}, {HELPER_3}{newline}')
                changed += 1
                continue

        m = DIRECT_RE.match(line.rstrip("\r\n"))
        if m:
            sig = m.group("sig")
            if sig == "(Ljava/lang/CharSequence;Ljava/lang/CharSequence;)Z":
                owner = HELPER_2
            else:
                owner = HELPER_3
            prefix = "invoke-static" + ("/range" if m.group("range") else "")
            newline = "\n" if line.endswith("\n") else ""
            if line.endswith("\r\n"):
                newline = "\r\n"
            out.append(f'{m.group("indent")}{prefix} {m.group("regs")}, {owner}{newline}')
            changed += 1
            continue

        out.append(line)
    return "".join(out), changed


def patch_updater_flag(root: Path) -> int:
    changed = 0
    for p in root.rglob("BuildConfig.smali"):
        text = p.read_text(encoding="utf-8", errors="ignore")
        new = re.sub(
            r"(\.field\s+[^\n]*\bUPDATER_ENABLED:Z\s*=\s*)true\b",
            r"\1false",
            text,
            count=1,
        )
        if new != text:
            p.write_text(new, encoding="utf-8")
            changed += 1
    return changed


def noop_unit_methods(root: Path) -> list[str]:
    patched: list[str] = []
    for p in root.rglob("UpdateSectionKt.smali"):
        lines = p.read_text(encoding="utf-8", errors="ignore").splitlines(keepends=True)
        out: list[str] = []
        i = 0
        while i < len(lines):
            line = lines[i]
            if line.startswith(".method") and re.search(r"\b(GlobalUpdateChecker|UpdateSection)\(", line) and line.rstrip().endswith(")V"):
                header = line
                method: list[str] = []
                j = i + 1
                while j < len(lines) and lines[j].strip() != ".end method":
                    method.append(lines[j])
                    j += 1
                if j >= len(lines):
                    raise RuntimeError(f"Unclosed method in {p}: {header.strip()}")

                directives = []
                for inner in method:
                    s = inner.strip()
                    if s.startswith(".locals ") or s.startswith(".registers "):
                        directives.append(inner)
                        break
                out.append(header)
                if directives:
                    out.extend(directives)
                else:
                    out.append("    .registers 0\n")
                out.append("    return-void\n")
                out.append(".end method\n")
                patched.append(f"{p}:{header.strip()}")
                i = j + 1
                continue
            out.append(line)
            i += 1
        if out != lines:
            p.write_text("".join(out), encoding="utf-8")
    return patched


def patch_targets(root: Path) -> tuple[int, list[str]]:
    total = 0
    details: list[str] = []
    for suffix in TARGETS:
        matches = list(root.rglob(Path(suffix).name))
        matches = [p for p in matches if p.as_posix().endswith(suffix)]
        if not matches:
            details.append(f"MISSING {suffix}")
            continue
        local = 0
        for p in matches:
            text = p.read_text(encoding="utf-8", errors="ignore")
            new, count = replace_search_invokes(text)
            if count:
                p.write_text(new, encoding="utf-8")
                local += count
                details.append(f"SMART {p.relative_to(root)}: {count}")
        total += local
        if local == 0:
            details.append(f"WARNING no Kotlin contains call found in {suffix}")
    return total, details


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("decoded_dir", type=Path)
    args = ap.parse_args()
    root = args.decoded_dir
    if not root.is_dir():
        raise SystemExit(f"Decoded APK directory not found: {root}")

    updater_flag = patch_updater_flag(root)
    search_total, search_details = patch_targets(root)
    updater_methods = noop_unit_methods(root)

    print(f"UPDATER_ENABLED fields changed: {updater_flag}")
    for item in search_details:
        print(item)
    print(f"SmartSearch call sites changed: {search_total}")
    print(f"UpdateSection/GlobalUpdateChecker methods disabled: {len(updater_methods)}")
    for item in updater_methods:
        print(item)

    if search_total == 0:
        raise SystemExit(
            "SmartSearch patch did not find any target contains() call. "
            "Refusing to produce an APK without the requested search modification."
        )
    if not updater_methods and updater_flag == 0:
        raise SystemExit(
            "No updater guard or updater UI method was found. "
            "Refusing to claim update prompts are disabled."
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
