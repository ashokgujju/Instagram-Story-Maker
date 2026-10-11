# Architecture: Photo → Edit → Caption → Instagram Stories

This document is the source of truth for the feature. Implementation must follow it. If a change in approach is needed, update this document first, then the code.

**Visual design:** `docs/design/` holds the UI design handoff (`HANDOFF.md`, screen PNGs as the visual target, static HTML for exact sizes, colors, and copy). Follow it for **visuals, layout, and copy**. Its "Android implementation notes" are suggestions only; where they conflict with this document, this document wins (see section 8 for the list of overrides).

---

## 1. Overview

The user can:

1. Grant camera access and capture a photo with the device camera (the camera is the only way in; no gallery import).
2. Draw freehand on the photo and add stickers from the bundled Project Status sticker library.
3. Write a caption after taking the photo.
4. Preview the final result, then share it directly to Instagram Stories (opening the Stories composer with no system share sheet), save it to the gallery (export), or discard it.

---

## 2. Key decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | The caption is delivered to Instagram as an **interactive sticker** (transparent PNG), controlled by the **"Show on photo"** switch (on by default). A **clipboard copy** of the caption is controlled by the **"Copy to clipboard"** switch (off by default). | Instagram's Stories intent has no caption/text field. The sticker shows the caption preloaded when Stories opens and stays movable/resizable in Instagram; the clipboard lets users paste it as native Instagram text (tappable @mentions and #hashtags). The switches follow the design. |
| D2 | Instagram is opened with the `com.instagram.share.ADD_TO_STORY` intent and an **explicit package** (`com.instagram.android`). There is no generic share-sheet action anywhere in the app. | Guarantees Stories opens directly, never the system chooser. The design's "More" (share sheet) button is intentionally dropped. |
| D3 | Editor content is modeled as **layers in normalized image coordinates (0..1)**, not pixels on a screen canvas. | Resolution-independent export, simple undo/redo, serializable state that survives process death. |
| D4 | Final images are produced by **replaying layers onto an output bitmap**, never by screenshotting the UI. There are two render targets: **share** (long side ≤ 1920px) and **gallery** (full capture resolution, long side capped at 4096px). | Crisp output independent of screen size. Instagram recompresses to ~1080×1920, so a larger share image is wasted; the user's gallery copy should not lose quality. The 4096px cap bounds memory use. |
| D5 | Stickers are **bundled assets** exported from Figma at design time. The app never calls the Figma API. | Offline, fast, no auth or runtime dependency on Figma. |
| D6 | The caption is **never burned into the photo**. Gallery export contains photo + drawings + stickers, not the caption. | The caption is a distinct, movable element in Instagram. |
| D7 | **Single Gradle module** (`:app`), organized by feature packages. | One linear feature does not justify multi-module build logic. Module-like boundaries are kept by package rules (section 6). |
| D8 | Captures and editor sessions live in **app-private persistent storage** (`noBackupFilesDir`), not the cache. Only derived share files live in the cache. | The app is backgrounded (and may be killed) while the user is in Instagram; the system may clear the cache at that time, which would make restoring the session impossible. `noBackupFilesDir` is excluded from cloud backup and device transfer. |
| D9 | Session state is owned by a single **`SessionStore`** that persists it as an atomically written, versioned **JSON snapshot file**. `SavedStateHandle` holds only the session ID. | Stroke point lists can easily exceed the saved-state size limit (`TransactionTooLargeException`). A single writer prevents lost updates between screens; atomic writes prevent corruption if the process dies mid-write. |
| D10 | The captured photo is **normalized once, right after capture**: EXIF orientation and front-camera mirroring are applied and `capture.jpg` is rewritten upright with no orientation tag. | Every later decode is a plain decode, and normalized layer coordinates always refer to the same pixel frame. No code path can forget to rotate or mirror. |
| D11 | Photos are captured in **portrait 16:9**, with the preview at the same aspect ratio. | Stories are 9:16; a 16:9 capture fills the Story with no letterboxing. Matches the design's 9:16 camera preview. |
| D12 | **Export = "Save" on the Share screen. Discard = Back/Close from the editor**, with a confirmation dialog. | The task requires export and discard; the design has no editor buttons for either, so they map onto the design's existing actions instead of adding new ones. |
| D13 | **Fixed dark theme** built from the handoff's design tokens, with the bundled **Manrope** font. No Material You dynamic color, no light theme. | The design is dark-only with a specific palette and typeface. |

---

## 3. Non-goals

Do not implement any of the following:

- Instagram Graph API / Content Publishing API, Facebook Login, or any backend.
- Burned-in caption mode, or a caption text layer in the editor.
- A generic system share sheet ("More" button) or Instagram Feed sharing.
- Gallery / photo picker import.
- Text tool (beyond the caption), filters, or cropping.
- Remote sticker delivery (the sticker repository should make it possible later, but it is not built now).
- Video capture or editing.
- Multiple concurrent editing sessions or a draft history. There is at most one active session, and it is not resumed after a fresh launch (section 9).
- A light theme.

---

## 4. Platform constraints

- **No caption field in the Stories intent.** The intent accepts a background asset, an interactive sticker asset, and background colors only. This is why D1 exists.
- **Instagram decides sticker placement.** The interactive sticker is placed by Instagram (centered by default); the app cannot set its position. Previews must reflect this (section 14).
- **Facebook App ID required.** Meta rejects Story shares without a valid App ID passed as the `source_application` extra. The App ID must be registered at developers.facebook.com.
- **Package visibility (Android 11+).** The manifest must declare a `<queries>` entry for `com.instagram.android`; otherwise Instagram appears not to be installed.
- **URI permissions.** Assets are shared through a FileProvider. The background URI is covered by the read-permission flag on the intent; the sticker URI is passed as an extra and **must be granted explicitly** to the Instagram package. Missing this grant is the most common cause of the sticker silently not appearing.
- **No result from Instagram.** The app cannot know whether the user posted. Never assume success.
- **minSdk 26** (decided, matches the project). Affects gallery export (section 13) and clipboard UX (section 16).

---

## 5. Tech stack

- Kotlin, Jetpack Compose, Material 3
- Single Activity, Navigation Compose with type-safe routes (kotlinx.serialization)
- kotlinx.serialization JSON for the session snapshot; atomic writes via a temporary file plus `java.nio.file.Files.move(ATOMIC_MOVE)` (available from API 26, and testable on the plain JVM)
- MVVM with unidirectional state (immutable UI state, one-shot events via a channel)
- Hilt (via KSP) for dependency injection. Verified in milestone 1: Hilt 2.60.1 + KSP 2.3.12 work with AGP 9.4 built-in Kotlin (2.2.10) and the configuration cache.
- Coroutines and Flow
- CameraX (core, camera2, lifecycle, view or compose viewfinder)
- AndroidX ExifInterface
- FileProvider and MediaStore for file sharing and gallery export
- Manrope bundled as a single variable font (`res/font/manrope.ttf`, OFL license in `docs/licenses/`); each weight selects a point on the weight axis. Used by both the Compose theme and the caption renderer
- Material Symbols Outlined icons as individual vector drawables (`res/drawable/ic_*.xml`, tint attribute removed so Compose `Icon` tints them), added only as needed
- `BuildConfig` generation enabled (`buildFeatures { buildConfig = true }`; AGP 9 disables it by default) for the Facebook App ID
- Roborazzi + Robolectric (native graphics mode) for screenshot tests (verify AGP 9 compatibility when first added)

No WorkManager: cleanup runs at app start (section 13).

---

## 6. Package structure

Single module `:app`, base package `app.storymaker`:

```
app.storymaker
├── MainActivity, StoryMakerApp (Application), navigation/   NavHost and routes
├── camera/      Permission screens, Capture screen and ViewModel (CameraX), capture normalization trigger
├── editor/      Editor screen (Draw/Stickers tabs) and ViewModel, reducer, canvas rendering, gestures, sticker sheet
├── caption/     Caption screen and ViewModel
├── share/       Share screen and ViewModel, share orchestration, Instagram Story launcher, availability check
├── media/       SessionStore, session snapshot model, layer model, photo normalizer, decoder, image exporter,
│                caption renderer, gallery saver, file locations, cleanup
├── stickers/    Sticker manifest and sticker repository (assets in res/drawable)
└── ui/          theme/ (design tokens, typography, shapes), shared components (pill buttons, round icon buttons), gesture utilities
```

Rules:

- Feature packages (`camera`, `editor`, `caption`, `share`) depend on `media`, `stickers`, and `ui`, never on each other. Shared types (including the layer model used by the exporter) live in `media` or `ui`.
- `media`, `stickers`, and `ui` never depend on feature packages.
- Navigation passes only identifiers (session ID), never bitmaps. Feature ViewModels read the session ID from `SavedStateHandle` by its key (`sessionId`) rather than importing route classes, so features never depend on `navigation/`.
- Short app-level messages (snackbars) are shown by a single `SnackbarHost` in the nav host; screens report messages through callbacks, and `SessionStore` write errors are collected there.
- No business logic in composables; composables render state and emit intents.

---

## 7. Visual design

The handoff (`docs/design/`) is the visual target. 1 CSS px in the mockups ≈ 1dp. Use the HTML files for exact sizes, colors, and copy.

### Theme

- `StoryMakerTheme` provides a fixed dark `ColorScheme` mapped from the handoff tokens, Manrope typography, and the handoff shapes. Dynamic color and the light scheme are removed (D13). The theme ignores the system dark/light setting.
- Tokens (from `HANDOFF.md`): background `#0E0E0E`; surface `#1A1A1A`; surfaceHigh `#1F1F1F`; surfaceHigher `#262626` / `#2A2A2A`; onBackground `#FFFFFF`; onSurfaceVariant `#B5B5B5`; muted `#9A9A9A`; primary button `#FFFFFF` on `#111111` text; accent `#FF5A36` with `#111111` text, used **only** for "Share to Instagram Stories".
- Brush swatches: `#FFFFFF` (default), `#111111`, `#FF5A36`, `#FFD23F`, `#4ADE80`, `#3B82F6`, `#8C7BFF`.
- Type: Manrope. Screen title 28/800, top app bar title 20/700, body 16/400 (line height 1.5), buttons 15–16/600–700, secondary 13/400–600.
- Shapes: buttons are pills; photo canvas radius 20; bottom sheet top corners 24; sticker tiles and cards 16; inputs 12; dialogs 28. Camera preview bottom corners 28.
- Spacing: side padding 16 (24 on permission screens); 16 between sections.
- Touch targets: **48dp minimum** (mockups use 44px; round up).
- Status and navigation bars are transparent with light icons; screens handle insets (edge-to-edge).

### Screens

| Route | Handoff file | Notes |
|---|---|---|
| Permission | `Permission` | Shown before the system prompt |
| Permission denied | `PermissionDenied` | Permanently denied state |
| Capture | `Main` | Rule-of-thirds grid overlay, close, flash, shutter, switch camera |
| Editor | `Draw`, `Stickers` | One screen with Draw/Stickers tabs |
| Caption | `Caption` | Caption field and the two switches |
| Share | `Share` | Final preview, Share to Instagram Stories, Save; "More" removed; "New photo" added |

`SystemPrompt` is Android's own dialog and is not built.

### Deviations from the handoff

| Handoff | This app | Why |
|---|---|---|
| "More" button (system share sheet) on Share | Removed; Save takes the full width | D2: never the share sheet |
| No "New photo" action | "New photo" icon button (photo_camera) in the Share screen top bar | Lets the user start over after sharing |
| Caption pill at bottom-centre in the Share preview | Caption pill shown **centered**, as Instagram will place it, with a "You can move it in Instagram" hint | Preview must be accurate (section 4) |
| "Show on photo" draws the caption into the export | Caption becomes the Instagram interactive sticker | D1, D6 |
| Toast after copying to clipboard | No toast; the switch subtitle explains it | The app is leaving the foreground; a toast would not be seen (section 16) |
| Drag-to-trash (not in handoff) | Not built; ✕ button on the selected sticker | Follow the design |
| Placeholder stickers (star, heart, WOW…) | Figma "Project Status Stickers" set | The handoff stickers are placeholders (section 12) |

---

## 8. User flow and navigation

```
Permission ──Continue──▶ (system dialog) ──granted──▶ Capture
    │                          └─ permanently denied ──▶ Permission denied ──Open app settings──▶ Android Settings
    │
Capture ──shutter──▶ Editor [Draw ⇄ Stickers] ──Next──▶ Caption ──Continue──▶ Share ──▶ Instagram Stories
  ▲                    │                       ▲          │       ▲          │
  └─ Back/Close ───────┘ (discard, confirmed)  └── back ──┘       └── back ──┘
  ▲                                                                          │
  └──────────────────────────────── New photo ───────────────────────────────┘
```

- **Start destination:** Capture if CAMERA is granted, otherwise Permission.
- **Close (X) / "Not now"** on the Permission, Permission denied, and Capture screens closes the app (`finish()`). There is no home screen; the camera is the only way in.
- **Editor Back** (top-left arrow and system back): if the session has any layers, show a "Discard photo?" confirmation; confirming deletes the session and returns to Capture (D12). With no layers, discard without asking.
- **Caption Back** returns to the Editor with layers and undo history intact. **Share Back** returns to Caption.
- **Share screen** is the hub after sharing: when the user returns from Instagram they land here with everything intact and can Share again, Save, or tap **New photo** (deletes the session, returns to Capture; asks for confirmation if the current edits haven't been saved or shared).
- When a share is launched, the session records `sharedAt` (used by the New photo confirmation and analytics, if added).

### Handoff implementation notes overridden by this document

| Handoff suggestion | This document |
|---|---|
| Strokes stored as pixel paths, stickers as pixel offsets | Normalized coordinates (D3) |
| Single 1080×1920 export, centre-cropped | Share and gallery render targets (D4); 16:9 capture means no crop is needed |
| Background PNG, no sticker asset | Background JPEG plus caption interactive sticker (section 15) |
| Caption burned into export | Interactive sticker (D1, D6) |
| Exported file saved to cache as the only artifact | Share files in `cache/share/`, session in `noBackupFilesDir` (D8) |
| "Apply EXIF rotation, then go to Draw" | Full normalization including mirroring (D10) |
| `ModalBottomSheet` for stickers | Non-modal sheet so the canvas stays interactive (section 11) |

---

## 9. State management and process death

### Screen state

- Each screen has one ViewModel exposing a single immutable UI state, derived from the `SessionStore` state plus screen-local UI state (e.g. dialogs, in-progress stroke, search query).

### Session and SessionStore

- A capture creates a session with a new ID. Its files live in `noBackupFilesDir/sessions/<id>/`: the normalized `capture.jpg` and `session.json`.
- `SessionStore` (app-wide singleton in `media/`) is the **only** code that reads or writes session files. It holds the current session state in memory as a `StateFlow`; ViewModels read from it and send updates through it. No ViewModel writes files directly.
- **Writes:**
  - Serialized behind a `Mutex`, so screens can never overwrite each other's changes.
  - Atomic: write `session.json.tmp`, sync it, then `Files.move(ATOMIC_MOVE, REPLACE_EXISTING)` over the old file. A crash mid-write leaves the previous snapshot intact.
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
| `editorTab` | Draw or Stickers |
| `brush` | Current color and size |
| `caption` | Caption text as typed |
| `showOnPhoto` | "Show on photo" switch (default true) |
| `copyToClipboard` | "Copy to clipboard" switch (default false) |
| `revision` | Incremented on every layer change |
| `exportedRevision` | `revision` at the last successful gallery save (null if none); Save shows "Saved" and is disabled while it equals `revision` |
| `sharedAt` | Timestamp of the last share launch (null if none) |

### Process death and fresh launch

- `SavedStateHandle` holds only the session ID (it is also the navigation argument). The navigation back stack restores itself; on restore, ViewModels ask `SessionStore` to load that session from disk.
- **Active session pointer:** `noBackupFilesDir/active_session` records the current session ID. It is written when a session is created and cleared when a session is discarded or replaced.
- **Fresh launch vs. restore** is decided in `MainActivity.onCreate`, since app-start code can't tell them apart (the Application starts in both cases):
  - `savedInstanceState == null` (fresh launch: first start, app swiped away, or reopened later): the previous session is abandoned. `SessionStore` deletes the active session and clears the pointer; the app starts at Capture (or Permission).
  - `savedInstanceState != null` (restore after process death): the session named in the restored navigation state is loaded.

### Restore failures

The app must never crash on restore:

| Failure | Behavior |
|---|---|
| `session.json` unreadable, corrupt, or from an unsupported `version`; photo present | Restore the photo with no edits and an empty caption; show a short message |
| `capture.jpg` missing | Delete the session, go to Capture, and show "Your previous photo couldn't be restored" |

### Derived state

- Rendered share assets are derived, temporary state. Regenerate them if missing; never persist their URIs as source of truth.

### Snapshot schema policy

- New fields are added with default values, so older `session.json` files stay readable (parsing ignores unknown keys).
- `version` is bumped only for incompatible changes; a file with a newer `version` than the app supports is treated as unreadable (restore without edits).

---

## 10. Permission and camera

### Permission (screens `Permission`, `PermissionDenied`)

- Manifest: `android.permission.CAMERA` and `<uses-feature android:name="android.hardware.camera.any" />`.
- Permission screen first; **Continue** launches `ActivityResultContracts.RequestPermission()`.
- Result handling:
  - Granted ("While using the app" or "Only this time"): go to Capture.
  - Denied and `shouldShowRequestPermissionRationale` is true: stay on the Permission screen.
  - Denied and rationale is false (permanently denied): show Permission denied. "Open app settings" fires `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `package:<applicationId>`.
- Re-check the permission on resume, so returning from Settings with access granted goes straight to Capture.
- If the permission is revoked while on Capture (e.g. "Only this time" expired), return to the Permission screen.

### Capture (screen `Main`)

- Use cases: Preview and ImageCapture, bound to the lifecycle. Capture mode: minimize latency.
- Aspect ratio and size: portrait 16:9 for both preview and capture (D11), via `ResolutionSelector` with a 16:9 aspect ratio strategy and a resolution target capped at about 4K (long side ~3840px). Never capture at the sensor's full resolution.
- Layout per design: preview fills the top of the screen with 28dp bottom corners and a rule-of-thirds grid overlay; close (top-left) and flash (top-right) float over it; shutter (centre) and switch camera (right) sit below it.
- Flash toggle cycles off → auto → on, with matching icons. It is hidden when the current camera has no flash unit (typically the front camera).
- **Orientation:** the activity is locked to portrait (the design is portrait-only). An `OrientationEventListener` feeds the device's physical rotation to `ImageCapture.targetRotation`, so photos taken with the phone held sideways come out upright and landscape. Android 16 ignores the portrait lock on large screens (600dp and wider); phones keep it, and large-screen layouts are out of scope for now.
- Output: a JPEG written to `noBackupFilesDir/sessions/<id>/capture.jpg`, not the user's gallery or the cache. Capturing a new photo replaces (deletes) the previous session.
- **Normalization (D10):** immediately after capture, off the main thread, decode the JPEG, apply its EXIF orientation (including the flip variants), mirror front-camera photos, and re-encode `capture.jpg` upright with no EXIF. If the photo needs no transform (back camera, no orientation tag) it is left as is. Navigate to the Editor only after this completes.
- **Mirroring rule (verified on device in milestone 2):** capture requests no mirroring from CameraX (`ImageCapture.Metadata.isReversedHorizontal = false`), and the normalizer flips horizontally when the photo is from the front camera **or** its EXIF orientation is a flip variant (`flip = exifFlip || isFrontCamera`), so a photo is never mirrored twice. On the test phone (OPPO CPH2619, Android 16) back-camera JPEGs arrive upright with no orientation tag, and front-camera results match the mirrored preview exactly once.
- UI states: Ready, Capturing, Processing (normalization), Error. Error covers camera unavailable or in use by another app, capture failure, and storage write failure, each with a retry action.

---

## 11. Editor (screens `Draw`, `Stickers`)

### Layout

- Top bar: Back (left), undo and redo (centre, Draw tab only, disabled when the stack is empty), **Next** (right, white pill).
- Photo canvas with radius 20, fitted to the space between the top bar and the controls.
- Draw/Stickers segmented tab bar below the canvas.
- **Draw tab:** 7 color swatches (selected swatch has a ring) and a brush-size slider with small/large dot end markers.
- **Stickers tab:** a **non-modal** bottom sheet (e.g. `BottomSheetScaffold`, partially expanded by default) with search, category chips, and a 4-column sticker grid. The canvas above the sheet stays interactive so a placed sticker can be adjusted while the sheet is open. Dragging the sheet down collapses it to show the full canvas.

### Layer model

All positions and sizes are normalized to the normalized capture (0..1), so edits are independent of display size.

- **StrokeLayer:** list of normalized points, color, stroke width as a fraction of image width.
- **StickerLayer:** sticker ID, normalized center, scale as a fraction of image width, rotation in degrees.

Editor state also holds the undo stack and redo stack (each capped at 50 entries, one stack shared by strokes and sticker actions), the current tab, brush color and size, and the selected sticker ID. State changes go through a pure reducer so they can be unit-tested; the resulting state is pushed to `SessionStore`.

### Rendering

A single Compose canvas draws the photo fitted to the available area (letterboxed to the photo's aspect ratio), then each layer mapped from normalized coordinates into the displayed rectangle.

### Interaction

- **Drawing (Draw tab):** drag gestures append points to an in-progress stroke; the stroke is committed as a layer on drag end, which pushes an undo entry. Smooth strokes using quadratic curves through point midpoints. Drop points closer than a small threshold to keep snapshots compact. Brush size slider runs 2–24dp (default 8dp), converted to a fraction of image width using the displayed photo width when the stroke starts.
- **Stickers (Stickers tab):**
  - Tapping a tile places the sticker at the centre of the photo and selects it.
  - Tap a sticker to select it (hit-test topmost first); tap empty canvas to deselect.
  - The selected sticker shows a dashed bounding box, a **remove (✕) button** at the top-left, and a **resize/rotate handle** at the bottom-right. Dragging the handle scales and rotates the sticker around its centre with one finger.
  - Pan with one finger; pinch to scale and twist to rotate with two fingers.
  - Each completed gesture (place, move, transform, remove) pushes one undo entry.
- **Next** goes to Caption (enabled even with no edits).

---

## 12. Sticker pipeline

The stickers in the handoff mockups are **placeholders**. The shipped set is the Figma "Project Status Stickers" file (https://www.figma.com/design/Ul8AkOtTiNQa6uHNumE0xI/Project-Status-Stickers); the sheet layout (search, chips, 4-column grid, tile style) follows the design.

1. In Figma, convert any text in the stickers to outlines before exporting (VectorDrawable cannot reference fonts).
2. Export each sticker as SVG.
3. Convert to Android VectorDrawable. Stickers that use effects VectorDrawable cannot express (drop shadows, blurs, blend modes, complex masks) are exported as WebP at 1024px on the long side instead, so they stay sharp when enlarged on a 4096px gallery render.
4. Place them in `res/drawable` with a `sticker_` name prefix. A Kotlin manifest in `stickers/` lists ID, display name (string resource, also used as the content description), resource, category, and search tags.
5. **Categories and tags come from the Figma set**, not the placeholder chips (All / Shapes / Faces / Words). "All" is always the first chip; the remaining chips are the set's categories, defined when the stickers are exported (milestone 3).
6. Search filters by display name and tags (case-insensitive, contains), combined with the selected category chip.
7. The sticker repository exposes the list as a Flow. The editor depends only on the repository, so remote delivery can be added later without editor changes.
8. Vector stickers are rasterized at the exact target size during export so they stay sharp at any scale.

---

## 13. Export (Save) and discard

### Image rendering

- Runs off the main thread.
- Decodes the normalized capture (no rotation or mirroring needed, D10) at the size of the render target (D4): long side ≤ 1920px for share, full resolution capped at 4096px for gallery. Use `inSampleSize` decoding plus a final scale.
- Replays all layers onto the bitmap in order.
- On `OutOfMemoryError`, retry once at half the target size; if that also fails, show an error. Never crash.

### Save to gallery (export)

- Triggered by **Save** on the Share screen (D12).
- Contains photo, drawings, and stickers. Does not contain the caption (D6). Saved as JPEG under `Pictures/StoryMaker`.
- **API 29+:** insert into MediaStore with `RELATIVE_PATH = Pictures/StoryMaker` using the pending-file pattern (`IS_PENDING` = 1, write, then 0). No storage permission.
- **API 26–28:** `RELATIVE_PATH` and `IS_PENDING` do not exist. Declare `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` and request it at save time. Write the file to the public `Pictures/StoryMaker` directory, then register it with `MediaScannerConnection.scanFile` so it appears in the gallery.
- On success, set `exportedRevision = revision` in the session and show a brief "Saved to gallery" snackbar. Save shows a "Saved" state and stays disabled until the layers change, to avoid duplicate gallery images.

### Discard

- Triggered by Back from the Editor (D12, section 8). Confirmation dialog ("Discard photo?", Discard / Keep editing), then delete the session folder, clear the active session pointer, and return to Capture.
- "New photo" on the Share screen does the same, confirming only if the current edits haven't been saved or shared.
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

Abandoning the active session on a fresh launch is handled separately in `MainActivity` (section 9).

---

## 14. Caption and Share screens

### Caption screen (`Caption`)

- Top bar: Back and the title "Caption".
- Thumbnail of the edited photo (photo + layers, no caption) beside a multi-line caption field ("Write a caption…") with a 0/150 counter below it. Input is capped at 150 characters.
- Settings card with two switches:
  - **Show on photo** (default on). Subtitle: "Adds the caption as text on your story".
  - **Copy to clipboard** (default off). Subtitle: "Paste it in Instagram if you'd rather type it there". When the caption contains @mentions or #hashtags, the subtitle adds that pasting makes them tappable.
- **Continue** (white pill) goes to Share. An empty caption is allowed.
- Caption text and switch states are stored in the session as the user changes them.
- Before rendering: trim leading/trailing whitespace and collapse excessive blank lines.

### Share screen (`Share`)

- Top bar: Back, the title "Ready to share", and a **New photo** icon button on the right.
- Large 9:16 preview: the share render (photo + layers) with, when "Show on photo" is on and the caption is not empty, the **actual caption sticker PNG from the renderer** drawn centered at the scale Instagram will initially show it, plus a small "You can move it in Instagram" hint. Never imitate the sticker with Compose text.
- **Share to Instagram Stories** (accent pill, full width), or **Get Instagram** when Instagram is unavailable (section 15).
- **Save** (secondary pill, full width since "More" is removed): gallery export (section 13).
- The share background is pre-rendered when the screen opens, so tapping Share only renders the small caption sticker.

### Caption sticker rendering spec

- Transparent PNG, separate from the background image.
- Style follows the design's caption label: white rounded label, `#111111` text, Manrope 700, generous horizontal padding.
- Width capped at roughly 80% of a 1080px-wide Story; keep the asset modest so it doesn't land oversized in Instagram. Final sizing is tuned on a real device in milestone 8, since Instagram applies its own scaling.
- Bundled Manrope font (not a system font) for consistent appearance on all devices; emoji fall back to the system emoji font.
- Wrapped, center-aligned text (`StaticLayout`) with a maximum line count consistent with the character limit.
- Transparent padding around the label so shadows and corners are not clipped.
- Correct handling of emoji and RTL scripts.
- Unique filename per share so Instagram never shows a stale cached sticker.

---

## 15. Instagram Stories share

### Manifest requirements

- `<queries>` entry for package `com.instagram.android`.
- FileProvider with authority `<applicationId>.fileprovider`, URI permissions enabled, exposing the `cache/share/` folder only.

### Intent contents

- Action: `com.instagram.share.ADD_TO_STORY`
- Package: `com.instagram.android` (explicit, so no chooser)
- Extra `source_application`: the Facebook App ID
- Data URI and type: the rendered background image (JPEG)
- Extra `interactive_asset_uri`: the caption sticker PNG (only if "Show on photo" is on and the caption is not empty)
- Extras `top_background_color` / `bottom_background_color`: `#RRGGBB` colors sampled (averaged) from the top and bottom edge rows of the rendered background, so any letterboxing matches the photo instead of Instagram's default
- Flag: grant read URI permission
- Explicit read-permission grant of the sticker URI to the Instagram package

The intent is described by a plain Kotlin `StoryShareRequest` (action, package, URIs, extras) that is mapped to an `Intent` in one place, so its construction can be unit-tested without Android framework classes.

### Share pipeline (order matters)

1. Show a brief "Preparing…" state on the Share button immediately on tap.
2. Use the pre-rendered background (share target); render it now if it is missing or stale.
3. Render the caption sticker (if enabled and the caption is not empty).
4. Write files to `cache/share/` and obtain FileProvider URIs.
5. Copy the caption to the clipboard (only if "Copy to clipboard" is on and the caption is not empty).
6. Grant URI permissions.
7. Record `sharedAt` in the session.
8. Emit a one-shot launch event; the Activity starts the intent.

### Availability

- Check whether Instagram can handle the intent each time the Share screen resumes (not only when it is first shown), so installing Instagram via "Get Instagram" and coming back updates the button.
- If unavailable, the share button becomes "Get Instagram" and opens the Play Store listing for `com.instagram.android`.
- Also catch launch failure at tap time (Instagram uninstalled mid-flow) and show the same fallback.

---

## 16. Clipboard UX

- Copy only at share time, only when "Copy to clipboard" is on and the caption is not empty. Never copy while the user types.
- The user is told **before** sharing, via the switch subtitle on the Caption screen. No toast or snackbar is shown during launch (the handoff's toast is dropped), since the app is leaving the foreground and it would not be seen. On Android 13+ the system also shows its own clipboard confirmation.
- First share only: a one-time coach mark (shown on tap, before launch, dismissed to continue) explaining that the caption sticker can be moved in Instagram and, if copying is on, that the caption is on the clipboard. The "seen" flag is stored in SharedPreferences.

---

## 17. Accessibility

- Every icon-only control (close, flash, shutter, switch camera, back, undo, redo, remove sticker, resize handle, New photo) has a content description. The handoff HTML `aria-label` values are a good starting point.
- Stickers in the sheet use their display name as the content description.
- Sticker removal has a non-gesture alternative (✕ button on the selected sticker).
- Touch targets are at least 48dp; color swatches announce their color name and selected state.
- The caption character counter is announced to screen readers when the limit is approached.
- Text meets contrast on the dark palette (muted `#9A9A9A` on `#0E0E0E` is for secondary text only).

---

## 18. Edge cases

| Case | Behavior |
|---|---|
| Instagram not installed | "Get Instagram" button linking to Play Store |
| Instagram installed after tapping "Get Instagram" | Availability re-checked on resume; button becomes Share |
| Instagram uninstalled mid-flow | Catch launch failure, show the same fallback |
| Old Instagram ignores the sticker | Clipboard copy (if on) still provides the caption |
| Process killed while in Instagram | Share screen restored from the session snapshot with photo, edits, caption, and switches |
| System clears cache while in Instagram | Session is unaffected (D8); share files are regenerated on the next share |
| App swiped away or reopened later | Fresh launch: previous session abandoned, app starts at Capture (section 9) |
| Corrupt snapshot or missing photo on restore | Handled per the restore-failures table (section 9); never crash |
| Process killed mid-write | Previous snapshot intact (atomic writes) |
| Disk full on capture or save | Error shown, in-memory state kept, retry offered |
| Return from Instagram | Land on Share with Share again, Save, New photo; do not assume the user posted |
| Empty caption, or "Show on photo" off | Share photo only; no sticker |
| Empty caption, or "Copy to clipboard" off | No clipboard write |
| Back from Editor with edits | "Discard photo?" confirmation |
| Share files read late by Instagram | Share files are never deleted on discard, New photo, or launch; cleanup at app start removes files older than ~24 hours |
| Camera permission denied once | Stay on the Permission screen |
| Camera permission permanently denied | Permission denied screen with "Open app settings"; re-check on resume |
| Permission revoked while on Capture | Return to the Permission screen |
| Camera unavailable or in use by another app | Capture Error state with retry |
| Landscape or front-camera capture | Normalized once after capture (D10) |
| Out of memory while rendering | Retry once at half size, then show an error |
| Save tapped twice | Disabled until layers change (`exportedRevision`) |
| Facebook App ID missing (debug build) | Share shows a developer-facing error instead of launching an intent Instagram will reject |

---

## 19. Configuration

- The Facebook App ID is read at build time from, in order: `local.properties` key `facebook.appId`, Gradle property `facebook.appId`, environment variable `FACEBOOK_APP_ID`. It is exposed to the app as a `BuildConfig` field (requires `buildFeatures { buildConfig = true }`).
- Read it through the `providers` API so the build stays configuration-cache compatible.
- **Release builds fail** if the App ID is missing. Debug builds allow it to be empty (see section 18).
- The App ID is not a secret (it ships inside the APK); it is kept out of git because it is developer- and environment-specific.
- Document the property name in the README so new developers can set it up.

---

## 20. Testing strategy

Tests are written in the milestone that introduces the code under test (section 21), not deferred.

- **Unit tests (JVM):** editor reducer (add/remove layers, transforms, undo/redo, 50-entry cap), normalized coordinate mapping and brush-size conversion, snapshot serialization round-trip and version handling, `SessionStore` (serialized writes, write failure keeps state, restore-failure paths), cleanup rules, caption text normalization, sticker search/category filtering, edge color sampling, `StoryShareRequest` construction (action, package, extras, URIs, sticker/clipboard switch combinations), permission state transitions.
- **Robolectric tests:** `StoryShareRequest` → `Intent` mapping (flags, grants), photo normalization pixel output with EXIF fixtures (all eight orientations, mirrored front-camera samples; added in milestone 9; milestone 2 covers the transform logic on the JVM and the pixels on a real device).
- **Screenshot tests (Roborazzi + Robolectric native graphics):** every screen in its main states, compared visually against the handoff PNGs during review; image exporter output with fixed layers; caption sticker with short, long, max-length, emoji-only, multi-line, and RTL captions.
- **Instrumented tests:** camera flow with the CameraX test camera (milestone 9); FileProvider URIs readable from another app; gallery export on API 26–28 (legacy path) and 29+ (MediaStore).
- **Manual QA matrix:** Instagram installed vs. missing, logged in vs. out, old Instagram version, front vs. back camera (preview matches result), landscape capture, empty caption, each combination of the two caption switches, Android 12 vs. 13+ clipboard behavior, permission denied once / permanently / "Only this time", process death round-trip ("Don't keep activities"), swipe-away fresh launch, low storage, API 26, and a recent API level.

---

## 21. Milestones

Implement one milestone at a time. Each must build, include and pass its own tests, and be committed before the next begins.

| # | Milestone | Done when |
|---|---|---|
| 1 | Project skeleton and theme | Packages, DI (Hilt verified or manual fallback chosen), BuildConfig + App ID config, fixed dark theme with handoff tokens, Manrope, shared button components, type-safe navigation, and empty Permission/Capture/Editor/Caption/Share screens build and navigate |
| 2 | Permission, camera, and sessions | Permission and Permission denied screens with full result handling; Capture screen per design (grid, flash cycle, switch camera); 16:9 capture at capped resolution into a session folder; normalization verified on real front and back cameras; error states; `SessionStore` with atomic, serialized, versioned snapshots, active pointer, fresh-launch vs. restore logic, and restore-failure handling |
| 3 | Stickers | Figma stickers converted (outlined text, WebP fallbacks at 1024px), manifest with categories and tags, repository exposes them |
| 4 | Editor drawing | Editor layout and Draw tab per design; layer model, reducer, freehand drawing, swatches, brush slider, undo/redo with cap, edits persisted through `SessionStore` and surviving process death; Back → discard confirmation |
| 5 | Editor stickers | Stickers tab with non-modal sheet, search, category chips, grid; place, select, move, pinch, rotate, ✕ remove, and resize/rotate handle |
| 6 | Rendering and save | Image exporter (share and gallery targets, OOM fallback); gallery save on API 26–28 and 29+; `exportedRevision` disables duplicate saves (wired to a Save button on the Share screen) |
| 7 | Caption and Share screens | Caption screen per design with counter and switches; caption sticker renderer meeting the spec; Share screen with accurate centered preview, Save, and New photo; screenshot tests set up |
| 8 | Instagram share | Stories opens directly with background, edge colors, and caption sticker per the switches; clipboard per the switch; `sharedAt`; availability check and Get Instagram fallback; sticker size tuned on device |
| 9 | Edge cases and polish | All rows in section 18 handled; cleanup at app start; coach mark; accessibility checklist (section 17) met; Robolectric normalization pixel tests and the instrumented camera test (section 20) |
| 10 | QA pass | Manual QA matrix from section 20 completed; screens checked against the handoff PNGs; issues fixed |

---

## 22. Open questions

- **Facebook App ID:** has a Meta app been registered? Registration can take time and blocks real testing of milestone 8, so start it early.
- **Figma sticker assets:** the stickers need to be exported from the Figma file (or file access provided) before milestone 3. Their categories and tags are defined at that point.
- Analytics events, if any.
