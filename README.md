# StoryMaker

Android app: take a photo, draw on it and add stickers, write a caption, and share it straight to Instagram Stories.

- Architecture and decisions: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) (source of truth)
- UI design handoff: [`docs/design/`](docs/design/)

## How it works

The app has a single, linear flow:

1. **Capture**: grant camera access and take a photo with the in-app camera (portrait 16:9, front or back camera, flash, grid).
2. **Edit**: draw freehand and place stickers from the bundled Project Status sticker library. Leaving the editor discards the photo (after a confirmation).
3. **Caption**: write a caption.
4. **Share**: preview the result, then open Instagram Stories directly, or save it to the gallery.

Instagram's Stories intent has no text field, so the caption is delivered as a movable transparent sticker ("Show on photo") and can also be copied to the clipboard ("Copy to clipboard"). The caption is never burned into the photo or the gallery copy. Stories is opened with an explicit-package intent; the app never shows the system share sheet.

## Tech stack

Kotlin, Jetpack Compose + Material 3, Navigation Compose (type-safe routes), Hilt, Coroutines/Flow, CameraX, kotlinx.serialization. Single module (`:app`), `minSdk` 26, `targetSdk` 37.

## Requirements

- A recent Android Studio that supports AGP 9.x
- Android SDK 37
- JDK: the Gradle daemon runs on Java 25, which Gradle provisions automatically
- Instagram installed on the device to test sharing

## Setup

1. Open the project in Android Studio (or use the Gradle wrapper). Android Studio creates `local.properties` with your SDK path.
2. Add the Facebook App ID used for Instagram Stories sharing to `local.properties`:

   ```properties
   facebook.appId=YOUR_FACEBOOK_APP_ID
   ```

   Alternatively pass `-Pfacebook.appId=…` to Gradle or set the `FACEBOOK_APP_ID` environment variable (useful on CI). `local.properties` takes precedence, then the Gradle property, then the environment variable.

   Debug builds work without it, but sharing to Instagram needs it. Release builds fail if it is missing. Register the App ID at [developers.facebook.com](https://developers.facebook.com).

## Build

```bash
./gradlew assembleDebug    # debug APK
./gradlew installDebug     # install on a connected device
./gradlew lint             # Android lint
```

To try a release build locally, pass `-Pfacebook.appId=…` and sign the APK with the debug key.

## Tests

```bash
./gradlew test                     # JVM + Robolectric unit tests (app/src/test)
./gradlew connectedAndroidTest     # instrumented tests (needs a device; uninstalls the app afterwards)
./gradlew verifyRoborazziDebug     # compare screenshots against app/src/test/screenshots
./gradlew recordRoborazziDebug     # re-record screenshot references after intended UI changes
```

Run a single test class or method:

```bash
./gradlew :app:testDebugUnitTest --tests "app.storymaker.navigation.RoutesTest"
```

## Project structure

```
app/src/main/java/app/storymaker/
├── navigation/   NavHost and routes
├── camera/       permission and capture screens (CameraX)
├── editor/       draw/sticker editor, layer reducer, gestures
├── caption/      caption screen
├── share/        share screen, Instagram Stories launcher
├── media/        SessionStore, layer model, image export, gallery save, cleanup
├── stickers/     sticker catalog and repository
└── ui/           theme (design tokens, Manrope) and shared components
docs/             architecture doc, design handoff, licenses
tools/stickers/   sticker extraction script
```

## Stickers

Stickers come from the Figma file [Project Status Stickers](https://www.figma.com/design/Ul8AkOtTiNQa6uHNumE0xI/Project-Status-Stickers), exported to [`docs/design/stickers/status_stickers.svg`](docs/design/stickers/status_stickers.svg). The `sticker_*.xml` drawables are generated from it; don't edit them by hand. To regenerate:

```bash
python3 tools/stickers/extract_stickers.py
```

Sticker IDs must stay stable, because saved editor sessions refer to them.

## Licenses

The bundled Manrope font is licensed under the SIL Open Font License; see [`docs/licenses/Manrope-OFL.txt`](docs/licenses/Manrope-OFL.txt).
