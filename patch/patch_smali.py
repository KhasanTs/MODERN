#!/usr/bin/env python3
import pathlib
import sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

def find_unique(name):
    hits = [p for p in ROOT.rglob(name) if "original" not in p.parts]
    exact = [p for p in hits if "/com/example/" in p.as_posix().replace("\\", "/")]
    if len(exact) == 1:
        return exact[0]
    if len(hits) == 1:
        return hits[0]
    if not hits:
        raise SystemExit(f"ERROR: target smali not found: {name}")
    raise SystemExit("ERROR: ambiguous target smali:\n" + "\n".join(map(str, hits)))

def replace_region(lines, start_pred, end_pred, replacement, label):
    start = next((i for i,l in enumerate(lines) if start_pred(l)), None)
    if start is None:
        raise SystemExit(f"ERROR: {label}: start marker not found")
    end = None
    for i in range(start, len(lines)):
        if end_pred(lines[i]):
            end = i
            break
    if end is None:
        raise SystemExit(f"ERROR: {label}: end marker not found")
    return lines[:start] + replacement.strip("\n").splitlines() + lines[end+1:]

def patch_viewmodel():
    p = find_unique("VideoViewModel$filteredVideos$1.smali")
    lines = p.read_text(encoding="utf-8").splitlines()
    # Exact search block discovered in the supplied 3.6.21 decompiler dump.
    new = [
        '    invoke-virtual {v2}, Lcom/example/data/Video;->getTitle()Ljava/lang/String;',
        '    move-result-object v3',
        '',
        '    invoke-virtual {v2}, Lcom/example/data/Video;->getChannel()Ljava/lang/String;',
        '    move-result-object v2',
        '',
        '    invoke-static {v1, v3, v2}, Lcom/example/utils/SmartSearch;->matches(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z',
        '    move-result v2',
        '',
        '    if-eqz v2, :cond_70',
    ]
    lines = replace_region(
        lines,
        lambda l: l.strip() == 'invoke-virtual {v2}, Lcom/example/data/Video;->getTitle()Ljava/lang/String;',
        lambda l: l.strip() == 'if-eqz v2, :cond_70',
        "\n".join(new),
        "VideoViewModel search"
    )
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return p

def patch_home():
    p = find_unique("HomeHeaderKt.smali")
    lines = p.read_text(encoding="utf-8").splitlines()
    new = [
        '    invoke-virtual {v17}, Lcom/example/data/SearchHistory;->getQuery()Ljava/lang/String;',
        '    move-result-object v0',
        '',
        '    invoke-static {v1, v0}, Lcom/example/utils/SmartSearch;->matches(Ljava/lang/String;Ljava/lang/String;)Z',
        '    move-result v0',
        '',
        '    if-eqz v0, :cond_29d',
    ]
    lines = replace_region(
        lines,
        lambda l: l.strip().startswith('invoke-virtual/range {v17') and 'SearchHistory;->getQuery()Ljava/lang/String;' in l,
        lambda l: l.strip() == 'if-eqz v0, :cond_29d',
        "\n".join(new),
        "HomeHeader search"
    )
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return p

def patch_library():
    p = find_unique("LibraryTabScreenKt.smali")
    lines = p.read_text(encoding="utf-8").splitlines()
    # Start at the title read belonging to the saved-video filter.
    starts = [i for i,l in enumerate(lines) if l.strip() == 'invoke-virtual {v13}, Lcom/example/data/SavedVideo;->getTitle()Ljava/lang/String;']
    if not starts:
        raise SystemExit(f"ERROR: Library search start not found in {p}")
    start = starts[-1]
    end = None
    for i in range(start, len(lines)):
        if lines[i].strip() == 'if-eqz v13, :cond_76b':
            end = i
            break
    if end is None:
        raise SystemExit(f"ERROR: Library search end not found in {p}")
    new = [
        '    invoke-virtual {v13}, Lcom/example/data/SavedVideo;->getTitle()Ljava/lang/String;',
        '    move-result-object v14',
        '',
        '    invoke-static/range {v30 .. v30}, Lcom/example/ui/screens/library/LibraryTabScreenKt;->b(Landroidx/compose/runtime/MutableState;)Ljava/lang/String;',
        '    move-result-object v15',
        '',
        '    invoke-virtual {v13}, Lcom/example/data/SavedVideo;->getChannel()Ljava/lang/String;',
        '    move-result-object v13',
        '',
        '    invoke-static {v15, v14, v13}, Lcom/example/utils/SmartSearch;->matches(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Z',
        '    move-result v13',
        '',
        '    if-eqz v13, :cond_76b',
    ]
    lines = lines[:start] + new + lines[end+1:]
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return p

def patch_update():
    p = find_unique("UpdateSectionKt.smali")
    lines = p.read_text(encoding="utf-8").splitlines()

    def noop_method(method_signature, label):
        nonlocal lines
        start_i = next((i for i,l in enumerate(lines)
                        if l.strip() == method_signature), None)
        if start_i is None:
            raise SystemExit(f"ERROR: {label}: method start not found in {p}")
        end_i = next((i for i in range(start_i + 1, len(lines))
                      if lines[i].strip() == '.end method'), None)
        if end_i is None:
            raise SystemExit(f"ERROR: {label}: method end not found in {p}")
        new_block = [
            method_signature,
            '    .registers 3',
            '',
            f'    # Disabled in patched build: {label}.',
            '    return-void',
            '.end method'
        ]
        lines = lines[:start_i] + new_block + lines[end_i + 1:]

    noop_method(
        '.method public static final a(ZLandroidx/compose/runtime/Composer;I)V',
        'GlobalUpdateChecker / update prompt entry point'
    )

    noop_method(
        '.method public static final c(Lcom/example/viewmodel/VideoViewModel;ZZLandroidx/compose/runtime/Composer;I)V',
        'update settings section'
    )

    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return p

def patch_update_manager():
    p = find_unique("UpdateManager.smali")
    lines = p.read_text(encoding="utf-8").splitlines()
    signature = '.method public final checkForUpdates(Ljava/lang/String;Lkotlin/coroutines/Continuation;)Ljava/lang/Object;'
    start_i = next((i for i,l in enumerate(lines) if l.strip() == signature), None)
    if start_i is None:
        raise SystemExit(f"ERROR: UpdateManager checkForUpdates start not found in {p}")
    end_i = next((i for i in range(start_i + 1, len(lines))
                  if lines[i].strip() == '.end method'), None)
    if end_i is None:
        raise SystemExit(f"ERROR: UpdateManager checkForUpdates end not found in {p}")
    new_block = [
        signature,
        '    .registers 3',
        '',
        '    # Disabled in patched build: never query for release updates.',
        '    const/4 p0, 0x0',
        '    return-object p0',
        '.end method'
    ]
    lines = lines[:start_i] + new_block + lines[end_i + 1:]
    p.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return p

patched = [patch_viewmodel(), patch_home(), patch_library(), patch_update(), patch_update_manager()]
print("PATCH_OK")
for p in patched:
    print(p)
