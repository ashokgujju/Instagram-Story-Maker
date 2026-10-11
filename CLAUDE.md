# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

The app has exactly one feature, a linear flow:

1. **Capture**: take a photo with an in-app camera interface (not a hand-off to the system camera app).
2. **Edit**: draw freehand and place stickers from the bundled sticker library on a canvas, then **export** the edited photo to the gallery or **discard** it.
3. **Caption**: enter a caption for the photo.
4. **Share**: open Instagram Stories directly (never the system share sheet) with the photo and caption preloaded.

Keep scope to this flow; don't add unrelated features.

## Architecture

**`docs/ARCHITECTURE.md` is the source of truth** for design decisions, package structure, file storage, the Instagram intent contract, edge cases, testing, and the milestone plan. Read it before implementing anything. If an approach needs to change, update that document first, then the code.

The decisions most likely to be gotten wrong without reading it:

- The caption reaches Instagram as a transparent PNG **sticker** (`interactive_asset_uri`) plus a **clipboard copy**, because the Stories intent has no text field. The caption is never burned into the photo or the gallery export.
- Instagram is launched with an **explicit-package** `com.instagram.share.ADD_TO_STORY` intent; never `Intent.createChooser`.
- Editor layers use **normalized (0..1) image coordinates**; output images are rendered by replaying layers, never by screenshotting UI.
- Captures and editor sessions live in **`noBackupFilesDir`**, not the cache. A single `SessionStore` is the only code that reads/writes session files (atomic, serialized, versioned JSON snapshot); `SavedStateHandle` holds only the session ID.
- The captured photo is **normalized once right after capture** (orientation + front-camera mirroring baked in), so no later code rotates or mirrors.
- Share files in `cache/share/` are never deleted on discard/New photo/launch; only the age-based cleanup at app start removes them.
- Stickers come only from the Figma file "Project Status Stickers" (https://www.figma.com/design/Ul8AkOtTiNQa6uHNumE0xI/Project-Status-Stickers), exported and bundled as resources. Don't substitute other artwork.
- Work proceeds **one milestone at a time**; each milestone builds, ships its own tests, and is committed before the next.

## Project state

StoryMaker is a **single-module** Android app (`:app`, package `app.storymaker`) built with Jetpack Compose + Material 3. Keep it single-module; organize code by feature packages as described in the architecture doc. It started from the stock Android Studio "Empty Activity" template; check git history for which milestones are done.

## Commands

Use the Gradle wrapper from the repo root:

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build + install on connected device/emulator
./gradlew lint                          # Android lint
./gradlew test                          # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest          # instrumented tests (app/src/androidTest), needs a device
./gradlew :app:testDebugUnitTest --tests "app.storymaker.ExampleUnitTest"            # single test class
./gradlew :app:testDebugUnitTest --tests "app.storymaker.ExampleUnitTest.addition_isCorrect"  # single test method
```

`local.properties` holds the local SDK path and the `facebook.appId` used for Instagram sharing; it is gitignored and not portable.

## Build setup notes

- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin; only `com.android.application` and `org.jetbrains.kotlin.plugin.compose` are applied. Don't add the standalone Kotlin Android plugin.
- **Version catalog**: all dependency and plugin versions live in `gradle/libs.versions.toml`; reference them via `libs.*` in build scripts. Compose library versions come from the Compose BOM, so Compose artifacts are declared without versions.
- **SDK levels**: `compileSdk`/`targetSdk` 37, `minSdk` 26. Java source/target compatibility is 11.
- **Toolchain**: Gradle 9.6 wrapper; the Gradle daemon JVM is pinned to Java 25 (`gradle/gradle-daemon-jvm.properties`, auto-provisioned via the foojay resolver).
- **BuildConfig** generation is off by default in AGP 9; enable it with `buildFeatures { buildConfig = true }` when adding the App ID field.
- **Configuration cache** is enabled in `gradle.properties`, so build logic must be configuration-cache compatible (e.g. read `local.properties` / env vars through the `providers` API).
- **R8**: release builds use the AGP 9 `optimization { }` block (scoped to `androidx.**`, `kotlin.**`, `kotlinx.**`). Keep rules go in `app/src/main/keepRules/` (all files there are combined), not a `proguard-rules.pro`.
- Repositories are centrally declared in `settings.gradle.kts` with `FAIL_ON_PROJECT_REPOS`; don't add `repositories {}` in module build files.

## Theming

`ui/theme/Theme.kt` defines `StoryMakerTheme`, which uses Material You dynamic color on Android 12+ and falls back to the static schemes from `Color.kt`. Wrap any new screen/preview in `StoryMakerTheme`. `MainActivity` calls `enableEdgeToEdge()`, so screens must handle window insets (e.g. via `Scaffold`'s `innerPadding`).
