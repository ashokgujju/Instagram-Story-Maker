# StoryMaker

Android app: take a photo, draw on it and add stickers, write a caption, and share it straight to Instagram Stories.

- Architecture and decisions: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- UI design handoff: [`docs/design/`](docs/design/)

## Setup

1. Open the project in Android Studio (or use the Gradle wrapper). Android Studio creates `local.properties` with your SDK path.
2. Add the Facebook App ID used for Instagram Stories sharing to `local.properties`:

   ```properties
   facebook.appId=YOUR_FACEBOOK_APP_ID
   ```

   Alternatively pass `-Pfacebook.appId=…` to Gradle or set the `FACEBOOK_APP_ID` environment variable (useful on CI). `local.properties` takes precedence, then the Gradle property, then the environment variable.

   Debug builds work without it, but sharing to Instagram needs it. Release builds fail if it is missing.

## Build

```bash
./gradlew assembleDebug    # debug APK
./gradlew installDebug     # install on a connected device
./gradlew test             # unit tests
./gradlew lint             # Android lint
```

## Licenses

The bundled Manrope font is licensed under the SIL Open Font License; see [`docs/licenses/Manrope-OFL.txt`](docs/licenses/Manrope-OFL.txt).
