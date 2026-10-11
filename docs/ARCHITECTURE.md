# Architecture: Photo → Edit → Caption → Instagram Stories

This document is the source of truth for the feature. Implementation must follow it. If a change in approach is needed, update this document first, then the code.

---

## 1. Overview

The user can:

1. Capture a photo with the device camera.
2. Draw freehand on the photo and add stickers from the bundled Project Status sticker library.
3. Export the edited photo to the gallery, or discard it.
4. Write a caption after taking the photo.
5. Share the photo and caption directly to Instagram Stories, opening the Stories composer with no system share sheet.

---

## 2. Key decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | The caption is delivered to Instagram as an **interactive sticker** (transparent PNG) **plus a clipboard copy** of the caption text. | Instagram's Stories intent has no caption/text field. The sticker makes the caption visible and preloaded when Stories opens; the clipboard lets users paste it as native Instagram text (tappable @mentions and #hashtags). |
| D2 | Instagram is opened with the `com.instagram.share.ADD_TO_STORY` intent and an **explicit package** (`com.instagram.android`). | Guarantees Stories opens directly, never the system chooser. |
| D3 | Editor content is modeled as **layers in normalized image coordinates (0..1)**, not pixels on a screen canvas. | Resolution-independent export, simple undo/redo, serializable state that survives process death. |
| D4 | Final images are produced by **replaying layers onto an output bitmap**, never by screenshotting the UI. There are two render targets: **share** (long side ≤ 1920px) and **gallery** (full capture resolution, long side capped at 4096px). | Crisp output independent of screen size. Instagram recompresses to ~1080×1920, so a larger share image is wasted; the user's gallery copy should not lose quality. The 4096px cap bounds memory use. |
| D5 | Stickers are **bundled assets** exported from Figma at design time. The app never calls the Figma API. | Offline, fast, no auth or runtime dependency on Figma. |
| D6 | The caption is kept **separate from the edited photo**. Gallery export contains photo + drawings + stickers, not the caption. | The caption is a distinct, movable element in Instagram. |
| D7 | **Single Gradle module** (`:app`), organized by feature packages. | One linear feature does not justify multi-module build logic. Module-like boundaries are kept by package rules (section 6). |
| D8 | Captures and editor sessions live in **app-private persistent storage** (`noBackupFilesDir`), not the cache. Only derived share files live in the cache. | The app is backgrounded (and may be killed) while the user is in Instagram; the system may clear the cache at that time, which would make restoring the session impossible. `noBackupFilesDir` is excluded from cloud backup and device transfer. |
| D9 | Session state is owned by a single **`SessionStore`** that persists it as an atomically written, versioned **JSON snapshot file**. `SavedStateHandle` holds only the session ID. | Stroke point lists can easily exceed the saved-state size limit (`TransactionTooLargeException`). A single writer prevents lost updates between screens; atomic writes prevent corruption if the process dies mid-write. |
| D10 | The captured photo is **normalized once, right after capture**: EXIF orientation and front-camera mirroring are applied and `capture.jpg` is rewritten upright with no orientation tag. | Every later decode is a plain decode, and normalized layer coordinates always refer to the same pixel frame. No code path can forget to rotate or mirror. |
| D11 | Photos are captured in **portrait 16:9**, with the preview at the same aspect ratio. | Stories are 9:16; a 16:9 capture fills the Story with no letterboxing. |

---

## 3. Non-goals

Do not implement any of the following:

- Instagram Graph API / Content Publishing API, Facebook Login, or any backend.
- Burned-in caption mode, or a caption text layer in the editor.
- Instagram Feed sharing.
- Remote sticker delivery (the sticker repository should make it possible later, but it is not built now).
- Video capture or editing.
- Multiple concurrent editing sessions or a draft history. There is at most one active session, and it is not resumed after a fresh launch (section 8).

---

## 4. Platform constraints

- **No caption field in the Stories intent.** The intent accepts a background asset, an interactive sticker asset, and background colors only. This is why D1 exists.
- **Facebook App ID required.** Meta rejects Story shares without a valid App ID passed as the `source_application` extra. The App ID must be registered at developers.facebook.com.
- **Package visibility (Android 11+).** The manifest must declare a `<queries>` entry for `com.instagram.android`; otherwise Instagram appears not to be installed.
- **URI permissions.** Assets are shared through a FileProvider. The background URI is covered by the read-permission flag on the intent; the sticker URI is passed as an extra and **must be granted explicitly** to the Instagram package. Missing this grant is the most common cause of the sticker silently not appearing.
- **No result from Instagram.** The app cannot know whether the user posted. Never assume success.
- **minSdk 26** (decided, matches the project). Affects gallery export (section 12) and clipboard UX (section 15).

---

## 5. Tech stack

- Kotlin, Jetpack Compose, Material 3
- Single Activity, Navigation Compose with type-safe routes (kotlinx.serialization)
- kotlinx.serialization JSON for the session snapshot; `androidx.core.util.AtomicFile` for atomic writes
- MVVM with unidirectional state (immutable UI state, one-shot events via a channel)
- Hilt (via KSP) for dependency injection. **Verify in milestone 1** that Hilt + KSP work with AGP 9 built-in Kotlin and the configuration cache. If they don't, fall back to manual DI (a single `AppContainer` created in the `Application`) and update this document.
- Coroutines and Flow
- CameraX (core, camera2, lifecycle, view or compose viewfinder)
- AndroidX ExifInterface
- FileProvider and MediaStore for file sharing and gallery export
- `BuildConfig` generation enabled (`buildFeatures { buildConfig = true }`; AGP 9 disables it by default) for the Facebook App ID
- Roborazzi + Robolectric (native graphics mode) for screenshot tests (verify AGP 9 compatibility when first added)

No WorkManager: cleanup runs at app start (section 12).

---

## 6. Package structure

Single module `:app`, base package `app.storymaker`:

```
app.storymaker
├── MainActivity, StoryMakerApp (Application), navigation/   NavHost and routes
├── camera/      Camera screen and ViewModel (CameraX), capture normalization trigger
├── editor/      Editor screen and ViewModel, layer model, reducer, canvas rendering, gestures, sticker tray
├── caption/     Caption screen and ViewModel, caption sticker preview
├── share/       Share orchestration, Instagram Story launcher, availability check
├── media/       SessionStore, session snapshot model, photo normalizer, decoder, image exporter,
│                caption renderer, gallery saver, file locations, cleanup
├── stickers/    Sticker manifest and sticker repository (assets in res/drawable)
└── ui/          Theme (existing ui/theme), shared components and gesture utilities
```

Rules:

- Feature packages (`camera`, `editor`, `caption`, `share`) depend on `media`, `stickers`, and `ui`, never on each other. Shared types (including the layer model used by the exporter) move down into `media` or `ui`.
- `media`, `stickers`, and `ui` never depend on feature packages.
- Navigation passes only identifiers (session ID), never bitmaps.
- No business logic in composables; composables render state and emit intents.

---

## 7. User flow and navigation

```
Camera ──capture──▶ Editor ──next──▶ Caption ──share──▶ Instagram Stories
  ▲                  │  ▲               │
  └──── discard ─────┘  └──── back ─────┘
            (Export to gallery available in the Editor)
```

- Back from Caption returns to the Editor with all layers and undo history intact.
- Discard in the Editor (after confirmation) deletes the session and returns to the Camera.
- When a share is launched, the session records `sharedAt`. When the Caption screen is shown and `sharedAt` is set, it offers: Share again, Save to gallery, New photo. Because `sharedAt` is in the snapshot, this survives process death.
- "New photo" deletes the session and returns to the Camera.

---

## 8. State management and process death

### Screen state

- Each screen has one ViewModel exposing a single immutable UI state, derived from the `SessionStore` state plus screen-local UI state (e.g. dialogs, in-progress stroke).

### Session and SessionStore

- A capture creates a session with a new ID. Its files live in `noBackupFilesDir/sessions/<id>/`: the normalized `capture.jpg` and `session.json`.
- `SessionStore` (app-wide singleton in `media/`) is the **only** code that reads or writes session files. It holds the current session state in memory as a `StateFlow`; ViewModels read from it and send updates through it. No ViewModel writes files directly.
- **Writes:**
  - Serialized behind a `Mutex`, so the Editor and Caption screens can never overwrite each other's changes.
  - Atomic, via `AtomicFile`: write to a temporary file, then rename over the old one. A crash mid-write leaves the previous snapshot intact.
  - Debounced (~300ms after the last change) and flushed immediately when the app goes to the background (`ProcessLifecycleOwner` `ON_STOP`).
  - Run in an application-scoped coroutine scope owned by `SessionStore`, not `viewModelScope`, so a pending write is not cancelled when a ViewModel is cleared.
- **Write failure** (e.g. disk full): keep the in-memory state, show a non-blocking error, and retry on the next change. Never lose the in-memory session because a write failed.

### Snapshot contents (`session.json`)

| Field | Purpose |
|---|---|
| `version` | Snapshot schema version |
| `sessionId`, `createdAt` | Identity |
| `photoWidth`, `photoHeight` | Pixel size of the normalized capture |
| `layers` | Current stroke and sticker layers |
| `undoStack`, `redoStack` | Capped at 50 entries each; oldest entries are dropped |
| `tool` | Current tool, color, brush size |
| `caption` | Caption text as typed |
| `revision` | Incremented on every layer change |
| `exportedRevision` | `revision` at the last successful gallery export (null if none); Export is disabled while it equals `revision` |
| `sharedAt` | Timestamp of the last share launch (null if none) |

### Process death and fresh launch

- `SavedStateHandle` holds only the session ID (it is also the navigation argument). The navigation back stack restores itself; on restore, ViewModels ask `SessionStore` to load that session from disk.
- **Active session pointer:** `noBackupFilesDir/active_session` records the current session ID. It is written when a session is created and cleared when a session is discarded or replaced.
- **Fresh launch vs. restore** is decided in `MainActivity.onCreate`, since app-start code can't tell them apart (the Application starts in both cases):
  - `savedInstanceState == null` (fresh launch: first start, app swiped away, or reopened later): the previous session is abandoned. `SessionStore` deletes the active session and clears the pointer; the app starts at the Camera.
  - `savedInstanceState != null` (restore after process death): the session named in the restored navigation state is loaded.

### Restore failures

The app must never crash on restore:

| Failure | Behavior |
|---|---|
| `session.json` unreadable, corrupt, or from an unsupported `version`; photo present | Restore the photo with no edits and an empty caption; show a short message |
| `capture.jpg` missing | Delete the session, go to the Camera, and show "Your previous photo couldn't be restored" |

### Derived state

- Rendered share assets are derived, temporary state. Regenerate them if missing; never persist their URIs as source of truth.

---

## 9. Camera

- Use cases: Preview and ImageCapture, bound to the lifecycle. Capture mode: minimize latency.
- Aspect ratio and size: portrait 16:9 for both preview and capture (D11), via `ResolutionSelector` with a 16:9 aspect ratio strategy and a resolution target capped at about 4K (long side ~3840px). Never capture at the sensor's full resolution.
- Controls: shutter, flash toggle, front/back switch.
- Output: a JPEG written to `noBackupFilesDir/sessions/<id>/capture.jpg`, not the user's gallery or the cache. Capturing a new photo replaces (deletes) the previous session.
- **Normalization (D10):** immediately after capture, off the main thread, decode the JPEG, apply its EXIF orientation (including the flip variants), apply front-camera mirroring if the result does not already match the preview, and re-encode `capture.jpg` upright with the orientation tag removed. Navigate to the Editor only after this completes. Verify on real devices in milestone 2 that the front-camera result matches the preview exactly once (not unmirrored, not double-mirrored).
- Permission: request CAMERA at runtime with a rationale screen and a "permanently denied → open app settings" state.
- Manifest: `<uses-feature android:name="android.hardware.camera.any" />`.
- UI states: PermissionRequired, Ready, Capturing, Processing (normalization), Error. Error covers camera unavailable or in use by another app, capture failure, and storage write failure, each with a retry action.

---

## 10. Editor

### Layer model

All positions and sizes are normalized to the normalized capture (0..1), so edits are independent of display size.

- **StrokeLayer:** list of normalized points, color, stroke width as a fraction of image width.
- **StickerLayer:** sticker ID, normalized center, scale as a fraction of image width, rotation in degrees.

Editor state also holds the undo stack and redo stack (each capped at 50 entries), current tool (draw with color and width, or sticker mode), and the selected layer ID. State changes go through a pure reducer so they can be unit-tested; the resulting state is pushed to `SessionStore`.

### Rendering

A single Compose canvas draws the photo fitted to the available area (letterboxed to the photo's aspect ratio), then each layer mapped from normalized coordinates into the displayed rectangle.

### Interaction

- **Drawing:** drag gestures append points to an in-progress stroke; the stroke is committed as a layer on drag end, which pushes an undo snapshot. Smooth strokes using quadratic curves through point midpoints. Drop points closer than a small threshold to keep snapshots compact.
- **Stickers:** added from a bottom-sheet tray at the center of the image. Tap to select (hit-test topmost first); pan, pinch, and rotate the selected sticker. Delete by dragging onto a trash zone **or** with a delete button shown on the selected sticker.
- **Toolbar:** color palette, brush size, undo, redo, sticker tray, Discard, Export, Next.

---

## 11. Sticker pipeline

1. In Figma, convert any text in the stickers to outlines before exporting (VectorDrawable cannot reference fonts).
2. Export each sticker as SVG.
3. Convert to Android VectorDrawable. Stickers that use effects VectorDrawable cannot express (drop shadows, blurs, blend modes, complex masks) are exported as WebP at 1024px on the long side instead, so they stay sharp when enlarged on a 4096px gallery render.
4. Place them in `res/drawable` with a `sticker_` name prefix. A Kotlin manifest in `stickers/` lists ID, display name (string resource, also used as the content description), resource, and category.
5. The sticker repository exposes the list as a Flow. The editor depends only on the repository, so remote delivery can be added later without editor changes.
6. Vector stickers are rasterized at the exact target size during export so they stay sharp at any scale.

---

## 12. Export and discard

### Image rendering

- Runs off the main thread.
- Decodes the normalized capture (no rotation or mirroring needed, D10) at the size of the render target (D4): long side ≤ 1920px for share, full resolution capped at 4096px for gallery. Use `inSampleSize` decoding plus a final scale.
- Replays all layers onto the bitmap in order.
- On `OutOfMemoryError`, retry once at half the target size; if that also fails, show an error. Never crash.

### Export to gallery

- Contains photo, drawings, and stickers. Does not contain the caption (D6). Saved as JPEG under `Pictures/StoryMaker`.
- **API 29+:** insert into MediaStore with `RELATIVE_PATH = Pictures/StoryMaker` using the pending-file pattern (`IS_PENDING` = 1, write, then 0). No storage permission.
- **API 26–28:** `RELATIVE_PATH` and `IS_PENDING` do not exist. Declare `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` and request it at export time. Write the file to the public `Pictures/StoryMaker` directory, then register it with `MediaScannerConnection.scanFile` so it appears in the gallery.
- On success, set `exportedRevision = revision` in the session. The Export action (in the Editor, and Save to gallery on the Caption screen) shows a "Saved" state and stays disabled until the layers change, to avoid duplicate gallery images.

### Discard

- Confirmation dialog, then delete the session folder, clear the active session pointer, and return to the camera.
- Share files in `cache/share/` are **not** deleted on discard or "New photo"; Instagram may still be reading them. Only the age-based cleanup removes them.

### File locations

| Location | Contents |
|---|---|
| `noBackupFilesDir/sessions/<id>/` | Normalized photo (`capture.jpg`) and session snapshot (`session.json`) |
| `noBackupFilesDir/active_session` | ID of the current session |
| `cache/share/` | Rendered background and caption sticker for sharing (exposed via FileProvider) |
| MediaStore `Pictures/StoryMaker` | User-exported images |

### Cleanup

Runs once at app start, off the main thread:

- `cache/share/`: delete files older than ~24 hours, judged by file modification time only. Files written moments ago are never touched, so cleanup cannot race with a share in progress.
- `sessions/`: delete every session folder except the one named in `active_session`.

Abandoning the active session on a fresh launch is handled separately in `MainActivity` (section 8).

---

## 13. Caption

### Caption screen

- Multi-line text field, limit of about 150 characters with a visible counter.
- Live preview: the screen shows the **actual PNG produced by the caption renderer** (re-rendered ~200ms after typing stops) over a scaled-down version of the edited photo. Never imitate the sticker with Compose text, so the preview always matches what Instagram receives.
- A persistent hint near the Share button: "Your caption will also be copied so you can paste it in Instagram." (shown only when the caption is not empty).
- An empty caption is allowed: the photo is shared without a sticker and nothing is copied to the clipboard.
- Before rendering: trim leading/trailing whitespace and collapse excessive blank lines.
- The caption text is stored in the session (section 8) as the user types.

### Caption sticker rendering spec

- Transparent PNG, separate from the background image.
- Width capped at roughly 80% of a 1080px-wide Story; keep the asset modest so it doesn't land oversized in Instagram. Final sizing is tuned on a real device in milestone 8, since Instagram applies its own scaling.
- Bundled app font (not a system font) for consistent appearance on all devices; emoji fall back to the system emoji font.
- Wrapped, center-aligned text (`StaticLayout`) with a maximum line count consistent with the character limit.
- Rounded pill background with strong contrast (e.g. white text on semi-opaque dark), readable on any photo.
- Transparent padding around the pill so shadows and corners are not clipped.
- Correct handling of emoji and RTL scripts.
- Unique filename per share so Instagram never shows a stale cached sticker.

---

## 14. Instagram Stories share

### Manifest requirements

- `<queries>` entry for package `com.instagram.android`.
- FileProvider with authority `<applicationId>.fileprovider`, URI permissions enabled, exposing the `cache/share/` folder only.

### Intent contents

- Action: `com.instagram.share.ADD_TO_STORY`
- Package: `com.instagram.android` (explicit, so no chooser)
- Extra `source_application`: the Facebook App ID
- Data URI and type: the rendered background image (JPEG)
- Extra `interactive_asset_uri`: the caption sticker PNG (only if the caption is not empty)
- Extras `top_background_color` / `bottom_background_color`: `#RRGGBB` colors sampled (averaged) from the top and bottom edge rows of the rendered background, so any letterboxing matches the photo instead of Instagram's default
- Flag: grant read URI permission
- Explicit read-permission grant of the sticker URI to the Instagram package

The intent is described by a plain Kotlin `StoryShareRequest` (action, package, URIs, extras) that is mapped to an `Intent` in one place, so its construction can be unit-tested without Android framework classes.

### Share pipeline (order matters)

1. Show a brief "Preparing…" state immediately on tap.
2. Render the background image (share target). Pre-render it speculatively when the user enters the Caption screen (and again after the layers change) so only the small sticker is rendered on tap.
3. Render the caption sticker.
4. Write both to `cache/share/` and obtain FileProvider URIs.
5. Copy the caption to the clipboard.
6. Grant URI permissions.
7. Record `sharedAt` in the session.
8. Emit a one-shot launch event; the Activity starts the intent.

### Availability

- Check whether Instagram can handle the intent each time the Caption screen resumes (not only when it is first shown), so installing Instagram via "Get Instagram" and coming back updates the button.
- If unavailable, the share button becomes "Get Instagram" and opens the Play Store listing for `com.instagram.android`.
- Also catch launch failure at tap time (Instagram uninstalled mid-flow) and show the same fallback.

---

## 15. Clipboard UX

- Copy only at share time, never while the user types.
- The user is told **before** sharing, via the persistent hint on the Caption screen (section 13). No toast or snackbar is shown during launch, since the app is leaving the foreground and it would not be seen. On Android 13+ the system also shows its own clipboard confirmation.
- If the caption contains @mentions or #hashtags, the hint adds that pasting it as Instagram text makes them tappable.
- First share only: a one-time coach mark (shown on tap, before launch, dismissed to continue) explaining that the caption sticker can be moved and the caption is on the clipboard. The "seen" flag is stored in SharedPreferences.

---

## 16. Accessibility

- Every icon-only control (shutter, flash, camera switch, undo, redo, tools, delete) has a content description.
- Stickers in the tray use their display name as the content description.
- Sticker deletion has a non-drag alternative (delete button on the selected sticker).
- Touch targets are at least 48dp; color swatches announce their color name.
- The caption character counter is announced to screen readers when the limit is approached.

---

## 17. Edge cases

| Case | Behavior |
|---|---|
| Instagram not installed | "Get Instagram" button linking to Play Store |
| Instagram installed after tapping "Get Instagram" | Availability re-checked on resume; button becomes Share |
| Instagram uninstalled mid-flow | Catch launch failure, show the same fallback |
| Old Instagram ignores the sticker | Clipboard copy still provides the caption |
| Process killed while in Instagram | Caption screen restored from the session snapshot with photo, edits, caption, and post-share options |
| System clears cache while in Instagram | Session is unaffected (D8); share files are regenerated on the next share |
| App swiped away or reopened later | Fresh launch: previous session abandoned, app starts at Camera (section 8) |
| Corrupt snapshot or missing photo on restore | Handled per the restore-failures table (section 8); never crash |
| Process killed mid-write | Previous snapshot intact (atomic writes) |
| Disk full on capture or save | Error shown, in-memory state kept, retry offered |
| Return from Instagram | Offer Share again, Save to gallery, New photo; do not assume the user posted |
| Empty caption | Share photo only; no sticker, no clipboard write |
| Share files read late by Instagram | Share files are never deleted on discard, New photo, or launch; cleanup at app start removes files older than ~24 hours |
| Camera permission permanently denied | Explain and link to app settings |
| Camera unavailable or in use by another app | Camera Error state with retry |
| Landscape or front-camera capture | Normalized once after capture (D10) |
| Out of memory while rendering | Retry once at half size, then show an error |
| Export tapped twice | Disabled until layers change (`exportedRevision`) |
| Facebook App ID missing (debug build) | Share shows a developer-facing error instead of launching an intent Instagram will reject |

---

## 18. Configuration

- The Facebook App ID is read at build time from, in order: `local.properties` key `facebook.appId`, Gradle property `facebook.appId`, environment variable `FACEBOOK_APP_ID`. It is exposed to the app as a `BuildConfig` field (requires `buildFeatures { buildConfig = true }`).
- Read it through the `providers` API so the build stays configuration-cache compatible.
- **Release builds fail** if the App ID is missing. Debug builds allow it to be empty (see section 17).
- The App ID is not a secret (it ships inside the APK); it is kept out of git because it is developer- and environment-specific.
- Document the property name in the README so new developers can set it up.

---

## 19. Testing strategy

Tests are written in the milestone that introduces the code under test (section 20), not deferred.

- **Unit tests (JVM):** editor reducer (add/remove layers, transforms, undo/redo, 50-entry cap), normalized coordinate mapping, snapshot serialization round-trip and version handling, `SessionStore` (serialized writes, write failure keeps state, restore-failure paths), cleanup rules, caption text normalization, edge color sampling, `StoryShareRequest` construction (action, package, extras, URIs).
- **Robolectric tests:** `StoryShareRequest` → `Intent` mapping (flags, grants), `AtomicFile` persistence, photo normalization with EXIF fixtures (all eight orientations, mirrored front-camera samples).
- **Screenshot tests (Roborazzi + Robolectric native graphics):** image exporter output with fixed layers; caption sticker with short, long, max-length, emoji-only, multi-line, and RTL captions; screens in light and dark themes.
- **Instrumented tests:** camera flow with the CameraX test camera; FileProvider URIs readable from another app; gallery export on API 26–28 (legacy path) and 29+ (MediaStore).
- **Manual QA matrix:** Instagram installed vs. missing, logged in vs. out, old Instagram version, front vs. back camera (preview matches result), landscape capture, empty caption, Android 12 vs. 13+ clipboard behavior, process death round-trip ("Don't keep activities"), swipe-away fresh launch, low storage, API 26, and a recent API level.

---

## 20. Milestones

Implement one milestone at a time. Each must build, include and pass its own tests, and be committed before the next begins.

| # | Milestone | Done when |
|---|---|---|
| 1 | Project skeleton | Packages, DI (Hilt verified or manual fallback chosen), BuildConfig + App ID config, type-safe navigation, and empty Camera/Editor/Caption screens build and navigate |
| 2 | Camera and sessions | 16:9 capture at capped resolution into a session folder; normalization verified on real front and back cameras; permission and error states handled; `SessionStore` with atomic, serialized, versioned snapshots, active pointer, fresh-launch vs. restore logic, and restore-failure handling |
| 3 | Stickers | Figma stickers converted (outlined text, WebP fallbacks at 1024px), manifest created, repository exposes them |
| 4 | Editor drawing | Layer model, reducer, freehand drawing, color/size, undo/redo with cap, edits persisted through `SessionStore` and surviving process death |
| 5 | Editor stickers | Add, select, move, pinch, rotate, delete (drag and button) stickers |
| 6 | Export and discard | Gallery render (≤ 4096px, OOM fallback) saved on API 26–28 and 29+; `exportedRevision` disables duplicate exports; discard deletes the session (not share files) and returns to camera |
| 7 | Caption | Caption screen with limit, hint, renderer-backed preview, and sticker renderer meeting the spec; screenshot tests set up |
| 8 | Instagram share | Stories opens directly with background, edge colors, and caption sticker; caption on clipboard; `sharedAt` and post-share options; sticker size tuned on device |
| 9 | Edge cases and polish | All rows in section 17 handled; cleanup at app start; accessibility checklist (section 16) met |
| 10 | QA pass | Manual QA matrix from section 19 completed; issues fixed |

---

## 21. Open questions

- **Facebook App ID:** has a Meta app been registered? Registration can take time and blocks real testing of milestone 8, so start it early.
- Visual design of screens (pending design input; use Material 3 defaults until then).
- Caption sticker style: single default style for v1, or a small set of presets.
- Analytics events, if any.
