# RuVideoHub 3.6.21 — exact APK patch v3

This package is designed for the supplied official `RuVideoHub_3.6.21.apk` and does **not** replace its UI, player, navigation, or service modules with the older `fix-main` project.

Changes:

- SmartSearch is added from the user's FIX logic.
- Exactly 7 confirmed search call sites are patched in the supplied 3.6.21 build:
  - `VideoViewModel$filteredVideos$1`: title + channel
  - `VideoRepository.fallbackToLocal`: title + channel
  - `HomeHeaderKt`: search history
  - `LibraryTabScreenKt`: saved-video title + channel
- The two updater UI entry points in `UpdateSectionKt` are disabled.
- `UpdateManager.checkForUpdates()` is disabled.
- Only `classes2.dex` is disassembled/reassembled; `classes.dex` and original resources stay untouched.
- `SmartSearchBridge` is compiled into an additional dex.

The workflow deliberately fails if the expected 7 search call sites or the 3 update-disabling targets are not found, instead of producing an unmodified APK.
