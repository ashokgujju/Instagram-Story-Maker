# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Purpose

The app has exactly one feature, a linear flow:

1. **Capture**: grant camera access and take a photo with an in-app camera interface (not a hand-off to the system camera app; no gallery import).
2. **Edit**: draw freehand and place stickers from the bundled sticker library on a canvas. Back from the editor **discards** the photo (with confirmation).
3. **Caption**: enter a caption for the photo.
4. **Share**: preview the result, then open Instagram Stories directly (never the system share sheet) with the photo and caption preloaded, or **export** (Save) it to the gallery.

Keep scope to this flow; don't add unrelated features.

## Architecture

**`docs/ARCHITECTURE.md` is the source of truth** for design decisions, package structure, file storage, the Instagram intent contract, edge cases, testing, and the milestone plan. Read it before implementing anything. If an approach needs to change, update that document first, then the code.

**`docs/design/`** is the UI design handoff: follow its PNGs/HTML for visuals, layout, and copy. Its implementation notes are suggestions only; where they conflict with the architecture doc (which lists the deliberate deviations in sections 7 and 8), the architecture doc wins.

The decisions most likely to be gotten wrong without reading it:

- The caption reaches Instagram as a transparent PNG **sticker** (`interactive_asset_uri`, controlled by the "Show on photo" switch) plus an optional **clipboard copy** ("Copy to clipboard" switch), because the Stories intent has no text field. The caption is never burned into the photo or the gallery export.
- Instagram is launched with an **explicit-package** `com.instagram.share.ADD_TO_STORY` intent; never `Intent.createChooser`. The design's "More" (share sheet) button is intentionally not built.
- Editor layers use **normalized (0..1) image coordinates**; output images are rendered by replaying layers, never by screenshotting UI.
- Captures and editor sessions live in **`noBackupFilesDir`**, not the cache. A single `SessionStore` is the only code that reads/writes session files (atomic, serialized, versioned JSON snapshot); `SavedStateHandle` holds only the session ID.
- The captured photo is **normalized once right after capture** (orientation + front-camera mirroring baked in), so no later code rotates or mirrors.
- Share files in `cache/share/` are never deleted on discard/New photo/launch; only the age-based cleanup at app start removes them.
- Stickers come only from the Figma file "Project Status Stickers" (https://www.figma.com/design/Ul8AkOtTiNQa6uHNumE0xI/Project-Status-Stickers). Its export lives at `docs/design/stickers/status_stickers.svg`; the `sticker_*.xml` drawables are generated from it by `python3 tools/stickers/extract_stickers.py` (never edit them by hand), and sticker IDs are stable because session snapshots store them. The stickers in the design handoff are placeholders; don't ship them or substitute other artwork.
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
./gradlew connectedAndroidTest          # instrumented tests (app/src/androidTest), needs a device (uninstalls the app afterwards)
./gradlew recordRoborazziDebug          # re-record screenshot references (app/src/test/screenshots) after intended UI changes
./gradlew verifyRoborazziDebug          # compare screenshots against the references; fails on any difference
./gradlew :app:testDebugUnitTest --tests "app.storymaker.navigation.RoutesTest"     # single test class
./gradlew :app:testDebugUnitTest --tests "app.storymaker.navigation.RoutesTest.startRoute_withCameraPermission_isCapture"  # single test method
```

`local.properties` holds the local SDK path and the `facebook.appId` used for Instagram sharing; it is gitignored and not portable. The App ID can also come from `-Pfacebook.appId=…` or the `FACEBOOK_APP_ID` environment variable. Release builds (and therefore `./gradlew build`) fail without it; debug builds allow it to be empty.

## Build setup notes

- **AGP 9.x with built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin; only `com.android.application` and `org.jetbrains.kotlin.plugin.compose` are applied. Don't add the standalone Kotlin Android plugin.
- **Version catalog**: all dependency and plugin versions live in `gradle/libs.versions.toml`; reference them via `libs.*` in build scripts. Compose library versions come from the Compose BOM, so Compose artifacts are declared without versions.
- **SDK levels**: `compileSdk`/`targetSdk` 37, `minSdk` 26. Java source/target compatibility is 11.
- **Toolchain**: Gradle 9.6 wrapper; the Gradle daemon JVM is pinned to Java 25 (`gradle/gradle-daemon-jvm.properties`, auto-provisioned via the foojay resolver).
- **BuildConfig** generation is off by default in AGP 9; enable it with `buildFeatures { buildConfig = true }` when adding the App ID field.
- **Configuration cache** is enabled in `gradle.properties`, so build logic must be configuration-cache compatible (e.g. read `local.properties` / env vars through the `providers` API).
- **R8**: release builds use the AGP 9 `optimization { }` block over the whole app. Don't reintroduce the template's `packageScope` (limiting R8 to `androidx.**`/`kotlin.**`/`kotlinx.**`): it made release builds crash on launch with an `IllegalAccessError`. Keep rules go in `app/src/main/keepRules/` (all files there are combined), not a `proguard-rules.pro`. To test a release build locally, pass `-Pfacebook.appId=…` and sign the APK with the debug key.
- Repositories are centrally declared in `settings.gradle.kts` with `FAIL_ON_PROJECT_REPOS`; don't add `repositories {}` in module build files.

## Theming

The app uses a **fixed dark theme** built from the handoff's design tokens with the bundled **Manrope** font (architecture doc section 7): no Material You dynamic color and no light theme. `ui/theme/` holds the tokens (`Color.kt`), Manrope typography (`Type.kt`), and `StoryMakerTheme` with the fixed color scheme and shapes (`Theme.kt`). Shared pill buttons, round icon buttons, the top bar, and the `StoryScreen` layout (background, content color, insets) live in `ui/components/`; build screens from these instead of raw Material components. The accent `#FF5A36` is reserved for the "Share to Instagram Stories" button. Touch targets are at least 48dp even where the mockups show 44px. Wrap any new screen/preview in `StoryMakerTheme`. `MainActivity` calls `enableEdgeToEdge()`, so screens must handle window insets (e.g. via `Scaffold`'s `innerPadding`).
