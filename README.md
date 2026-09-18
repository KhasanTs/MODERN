# RuVideoHub 3.6.21 — exact APK patch (v2)

Source of truth: the supplied official `RuVideoHub_3.6.21.apk`.

The supplied online-decompiler dump was inspected to identify the exact 3.6.21 bytecode structures. The patch is written against those real structures, not the older FIX project.

Exact patched areas:
- `VideoViewModel$filteredVideos$1.smali`: title/channel filtering -> SmartSearch.
- `HomeHeaderKt.smali`: search-history filtering -> SmartSearch.
- `LibraryTabScreenKt.smali`: saved-video filtering -> SmartSearch.
- `UpdateSectionKt.smali`: update UI/global checker entry points disabled.
- `UpdateManager.smali`: `checkForUpdates()` immediately returns null, so no release check can produce an update dialog.

SmartSearch mirrors the supplied FIX behavior:
- case-insensitive matching;
- `ё` normalized to `е`;
- punctuation-insensitive tokenization;
- stem/form tolerance;
- typo tolerance using edit distance;
- old `contains()` matches remain matches.

GitHub Actions rebuilds the exact APK:
1. decode with Apktool 3.0.3;
2. apply exact smali patch;
3. compile the SmartSearch helper to DEX;
4. rebuild the APK;
5. add helper as `classes3.dex`;
6. zipalign;
7. sign with a generated key;
8. verify with `apksigner`;
9. upload the final APK.

The output uses a new signing certificate. An existing installation signed by a different key must be removed before installing the patched build.
