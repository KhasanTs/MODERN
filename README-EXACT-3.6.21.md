# RuVideoHub 3.6.21 — exact APK reconstruction patch

Этот каталог предназначен для твоего репозитория `KhasanTs/MODERN`.

## Почему здесь APK, а не исходники 3.6.21

У тебя есть реальный APK 3.6.21, но исходники именно той ревизии не опубликованы. Поэтому здесь используется другой принцип: **не переделывать интерфейс и плеер с нуля, а взять твой APK как основу и перепаковать его с минимальными изменениями**.

Это максимально близкий вариант к исходному APK по UI, Compose-экранам, плееру, изображениям, ресурсам, навигации и остальному поведению. Используется декодирование APK без декодирования ресурсов, затем патчится только нужный smali-код.

## Что меняется

1. `SmartSearchBridge.java` — версия SmartSearch из твоего `fix-main.zip`.
2. В целевых Kotlin-классах вызовы `kotlin.text.StringsKt...contains(...)` заменяются на `SmartSearchBridge.containsSmart(...)`.
3. `UpdateSectionKt.GlobalUpdateChecker` делается пустым.
4. `UpdateSectionKt.UpdateSection` делается пустым.
5. Если в декомпилированном `BuildConfig` есть `UPDATER_ENABLED`, он принудительно ставится в `false`.

Остальной код APK не пересобирается из нового исходного проекта и не заменяется старой версией из `fix-main`.

## Сборка

Workflow:

`/.github/workflows/build-exact.yml`

После push или ручного запуска он:

`APK → Apktool → SmartSearch/update patch → rebuild → helper dex → zipalign → sign → Artifact`

Artifact:

`RuVideoHub-3.6.21-smart-search-no-update`

## Важное ограничение подписи

Результат будет подписан новым debug-ключом. Это означает, что Android может не разрешить установить его поверх официального приложения с другой подписью. В таком случае официальное приложение сначала придётся удалить.

## Как добавить в MODERN

Распакуй содержимое этого ZIP **в корень локального репозитория `MODERN`**.

В терминале VS Code проверь:

```powershell
git remote -v
```

Должен быть твой репозиторий `KhasanTs/MODERN`.

Затем:

```powershell
git add input/RuVideoHub_3.6.21.apk patch scripts .github/workflows/build-exact.yml README-EXACT-3.6.21.md
git commit -m "Add exact RuVideoHub 3.6.21 smart search no update build"
git push origin main
```

После push открой GitHub Actions и дождись artifact.
