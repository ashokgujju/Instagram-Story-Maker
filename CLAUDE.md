# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

The app has exactly one feature, a linear flow:

1. Capture a photo with the device camera.
2. Edit it on a canvas: freehand drawing, plus stickers from a bundled sticker library.
3. Write a caption for the photo.
4. Share the photo and caption directly to Instagram Stories.

Keep scope to this flow; don't add unrelated features.

### Caption delivery to Instagram (decided)

Instagram's Stories share intent accepts images only (a background asset and one sticker asset), not caption text. The caption is therefore delivered as:

- **Sticker layer**: render the caption as text onto a transparent PNG and pass it as the Stories *sticker* asset. The edited photo (with user drawings and library stickers flattened into it) is the *background* asset. This keeps the caption movable/resizable in Instagram.
- **Clipboard backup**: also copy the caption text to the clipboard on share, so the user can paste it as native Instagram text.

Since the caption isn't editable once in Instagram, the caption screen must show an accurate preview of the rendered caption before sharing.

## Project state

StoryMaker is a single-module Android app (`:app`, package `app.storymaker`) built with Jetpack Compose + Material 3. It is currently the stock Android Studio "Empty Activity" template: `MainActivity` hosts a `Greeting` composable inside `StoryMakerTheme`. There is no app-specific architecture yet (no navigation, DI, ViewModels, or data layer), so new structure should be introduced deliberately rather than inferred from existing code.

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

`local.properties` holds the local SDK path and is not portable.

## Build setup notes

- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin; only `com.android.application` and `org.jetbrains.kotlin.plugin.compose` are applied. Don't add the standalone Kotlin Android plugin.
- **Version catalog**: all dependency and plugin versions live in `gradle/libs.versions.toml`; reference them via `libs.*` in build scripts. Compose library versions come from the Compose BOM, so Compose artifacts are declared without versions.
- **SDK levels**: `compileSdk`/`targetSdk` 37, `minSdk` 26. Java source/target compatibility is 11.
- **Toolchain**: Gradle 9.6 wrapper; the Gradle daemon JVM is pinned to Java 25 (`gradle/gradle-daemon-jvm.properties`, auto-provisioned via the foojay resolver).
- **Configuration cache** is enabled in `gradle.properties`, so build logic must be configuration-cache compatible.
- **R8**: release builds use the AGP 9 `optimization { }` block (scoped to `androidx.**`, `kotlin.**`, `kotlinx.**`). Keep rules go in `app/src/main/keepRules/` (all files there are combined), not a `proguard-rules.pro`.
- Repositories are centrally declared in `settings.gradle.kts` with `FAIL_ON_PROJECT_REPOS`; don't add `repositories {}` in module build files.

## Theming

`ui/theme/Theme.kt` defines `StoryMakerTheme`, which uses Material You dynamic color on Android 12+ and falls back to the static schemes from `Color.kt`. Wrap any new screen/preview in `StoryMakerTheme`. `MainActivity` calls `enableEdgeToEdge()`, so screens must handle window insets (e.g. via `Scaffold`'s `innerPadding`).
