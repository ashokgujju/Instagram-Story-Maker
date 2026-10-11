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
- **Instagram decides sticker placement and size.** The interactive sticker is placed by Instagram (centered by default) and shown at **75% of the Story width whatever its pixel size** (measured on a device in milestone 8: small stickers are enlarged, wide ones shrunk); the app can set neither. Stickers therefore use a fixed canvas width, and previews reflect both (section 14).
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
- Roborazzi (Gradle plugin, verified with AGP 9 and the configuration cache in milestone 7) + Robolectric 4.17 (native graphics mode, tests run as SDK 36) for screenshot tests

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
- Dialogs use the shared `StoryDialog` (`#1F1F1F` surface, 20/700 title, 16sp body lines, text buttons). Snackbars are dark (`#1F1F1F` with white text, 16dp corners) and sit above the shutter row on the Capture screen.

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
| 44px Next pill and 40px swatches | Next pill 48dp high; swatches share the row equally (48dp where the screen allows) | 48dp touch minimum |
| Slider style | Thin 4dp track with a round 20dp thumb (Material 3's default expressive style overridden) | Match the design |
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
- **Editor Back** (top-left arrow and system back):
  - On the **Stickers tab**, Back returns to the Draw tab (the design's "Back to drawing").
  - On the **Draw tab**, if the session has any layers, show a "Discard photo?" confirmation; confirming deletes the session and returns to Capture (D12). With no layers, discard without asking.
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
| `layers` | Current stroke and sticker layers (polymorphic: `stroke` / `sticker`) |
| `undoStack`, `redoStack` | `LayerChange(index, before, after)` records, most recent last, capped at 50 each (oldest dropped). Only the change is stored, so history size grows with edits, not with the number of layers |
| `editorTab` | Draw or Stickers |
| `brushColor`, `brushSizeDp` | Current brush (ARGB color, size in dp; default white, 8dp) |
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

- Top bar: Back (left), undo and redo (centre, Draw tab only, disabled when the stack is empty, disabled icon `#8A8A8A`), **Next** (right, compact white pill).
- Photo canvas with radius 20, fitted to the space between the top bar and the controls.
- Draw/Stickers segmented tab bar below the canvas.
- **Draw tab:** 7 color swatches (selected swatch has a ring) and a brush-size slider with small/large dot end markers.
- **Stickers tab:** as in the design, the top bar shows only Back ("Back to drawing") and Next, and the sticker sheet (search, category chips, 4-column grid) covers the controls area. The sheet is a non-modal overlay with two positions, snapped to on release of a handle drag: **expanded** (~400dp, covering the lower part of the canvas, as designed) and **collapsed** (covering only the Draw controls, so the whole photo shows). It is built with `draggable` plus an animated offset, not `BottomSheetScaffold`. The Draw controls keep their space underneath (hidden and excluded from accessibility), so **the photo stays the same size on both tabs**. Focusing search expands the sheet; it rises above the keyboard.

### Layer model

All positions and sizes are normalized to the normalized capture (0..1), so edits are independent of display size.

- **StrokeLayer:** flattened normalized points (`x0, y0, x1, y1, …`, rounded to 4 decimals at commit), ARGB color, stroke width as a fraction of image width.
- **StickerLayer:** sticker ID, normalized center, scale as a fraction of image width, rotation in degrees.

Editor state also holds the undo stack and redo stack (each capped at 50 entries, one history shared by strokes and sticker actions), the current tab, brush color and size, and the selected sticker ID. Edits go through pure functions on the snapshot (`editor/EditHistory.kt`: `addLayer`, `applyEdit`, `undo`, `redo`) so they can be unit-tested; the result is pushed to `SessionStore`. The stroke being drawn lives in screen-local state and is committed on finger-up.

Stroke geometry is shared with the exporter (`media/StrokeGeometry.kt`): `fitRect` letterboxes the photo, points map between normalized and screen coordinates, and `smoothStroke` produces quadratic curves through point midpoints (a single point is a dot), so the screen and the exported image draw identical strokes.

### Rendering

A single Compose canvas draws the photo fitted to the available area (letterboxed to the photo's aspect ratio), then each layer mapped from normalized coordinates into the displayed rectangle.

### Interaction

- **Drawing (Draw tab):** a custom pointer loop (`awaitEachGesture`, not `detectDragGestures`) starts the stroke on touch-down with no touch slop and follows the first finger only; a tap draws a dot. Points closer than 1.5dp to the previous one are dropped. Strokes may run past the photo edge and are clipped when drawn. The stroke is committed as a layer on release, which pushes an undo entry. Brush size slider runs 2–24dp (default 8dp), converted to a fraction of image width using the displayed photo width when the stroke starts. Drawing is disabled until the photo loads and while the Stickers tab is selected.
- **Stickers (Stickers tab):**
  - Tapping a tile places the sticker at the centre of the photo at 35% of the photo width, unrotated, and selects it. Size is limited to 8%–150% of the photo width and the centre stays on the photo.
  - Tap a sticker to select it (hit-test topmost first); tap empty canvas to deselect.
  - The selected sticker shows a dashed bounding box, a **remove (✕) button** at the top-left, and a **resize/rotate handle** at the bottom-right. Dragging the handle scales and rotates the sticker around its centre with one finger.
  - Pan with one finger; pinch to scale and twist to rotate with two fingers.
  - The ✕ and the handle are drawn at the design's sizes (28dp, 12dp) with 48dp touch areas. The ✕ is a real button (TalkBack-reachable, the non-gesture way to remove a sticker).
  - Stickers can only be selected and edited on the Stickers tab; the Draw tab only draws. Layers keep plain order: whatever was added last is drawn on top.
  - **Performance:** while a gesture runs, the in-progress sticker is read only in the draw/layout phases (no recomposition per frame), and the artwork keeps rendering at its saved size and is scaled on the GPU, so the vector isn't re-rasterized every frame; it re-renders sharp on release.
  - Each completed gesture (place, move, transform, remove) pushes one undo entry.
- **Next** goes to Caption (enabled even with no edits).

---

## 12. Sticker pipeline

The stickers in the handoff mockups are **placeholders**. The shipped set is the Figma "Project Status Stickers" file (https://www.figma.com/design/Ul8AkOtTiNQa6uHNumE0xI/Project-Status-Stickers); the sheet layout (search, chips, 4-column grid, tile style) follows the design.

### Source and extraction

- **Source:** `docs/design/stickers/status_stickers.svg`, the Figma export of the sticker sheet. It has two panels with the same 40 designs: the left panel is outlined ("die-cut", white outline plus soft shadow), the right panel is flat. **The outlined set ships:** it stays readable on any photo, and it is the panel whose stickers are grouped individually in the export.
- **Extraction:** `tools/stickers/extract_stickers.py` (Python 3, standard library only) cuts out the 40 left-panel groups using each group's clip rect as the canvas and writes `app/src/main/res/drawable/sticker_<id>.xml` VectorDrawables. Names come from each sticker's position in the 10 × 4 grid, not the (scrambled) clip IDs. Re-run it after replacing the source SVG; never edit the generated drawables by hand.
- The script supports only the SVG features this file uses (paths with fill, stroke, opacity, line caps/joins, miter limit, even-odd fill) and **fails on anything else**, so nothing is dropped silently. The set has no text, filters, gradients, masks or images, so no text outlining or WebP fallback is needed; vectors stay sharp at any export size.
- Lint's `VectorPath` (long path data) warning is suppressed for `sticker_*.xml` only (`app/lint.xml`).

### Catalog and repository (`stickers/`)

- `StickerCatalog` lists the 40 stickers: stable `id` (matches the drawable name), display name (`sticker_name_*` string, also the content description), drawable, category, and lower-case search tags. **IDs are stored in session snapshots and must never change once shipped.**
- **Categories:** Status, Reactions, Vibes. "All" is always the first chip, followed by these three (they replace the placeholder Shapes / Faces / Words chips).
- `filterStickers` matches the query (case-insensitive, contains) against the display name and tags, combined with the selected category.
- `StickerRepository` exposes the list as a Flow (`BundledStickerRepository`, bound with Hilt). The editor depends only on the repository, so remote delivery can be added later without editor changes.
- Vector stickers are rasterized at the exact target size during export so they stay sharp at any scale.

---

## 13. Export (Save) and discard

### Image rendering

- Runs off the main thread.
- Decodes the normalized capture (no rotation or mirroring needed, D10) at the size of the render target (D4): long side ≤ 1920px for share, full resolution capped at 4096px for gallery. Use `inSampleSize` decoding plus a final scale.
- Replays all layers onto the bitmap in order (`media/ImageExporter.kt`), using the same geometry as the editor so the output matches the screen exactly: strokes via `smoothStroke` + `strokePath(...).asAndroidPath()` (anti-aliased, round caps/joins, width = fraction × bitmap width, single points as dots); stickers via `stickerFrame`, drawn from their VectorDrawables with bounds set to the exact output size, so they are rasterized sharp at any resolution.
- JPEG quality: 90 for the share target, 95 for the gallery target. Re-encoding writes no EXIF.
- On `OutOfMemoryError`, retry once at half the target size; if that also fails, show an error. Never crash.

### Save to gallery (export)

- Triggered by **Save** on the Share screen (D12).
- Contains photo, drawings, and stickers. Does not contain the caption (D6). Saved as JPEG under `Pictures/StoryMaker`.
- **API 29+:** insert into MediaStore with `RELATIVE_PATH = Pictures/StoryMaker` using the pending-file pattern (`IS_PENDING` = 1, write, then 0). No storage permission.
- **API 26–28:** `RELATIVE_PATH` and `IS_PENDING` do not exist. Declare `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="28"` and request it at save time. Write the file to the public `Pictures/StoryMaker` directory, then register it with `MediaScannerConnection.scanFile` so it appears in the gallery.
- On success, set `exportedRevision = revision` in the session and show a brief "Saved to gallery" snackbar. The Save button has three states: **Save** (download icon), **Saving…** (disabled), **Saved** (check icon, disabled while `exportedRevision == revision`, i.e. until the layers change), to avoid duplicate gallery images. On failure: "Couldn't save the photo". On API 26–28, a denied storage permission shows "Allow storage access to save photos".
- Files are named `StoryMaker_yyyyMMdd_HHmmss.jpg`. A failed MediaStore write deletes its pending row.

### Discard

- Triggered by Back from the Editor (D12, section 8). Confirmation dialog ("Discard photo?", Discard / Keep editing), then delete the session folder, clear the active session pointer, and return to Capture.
- "New photo" on the Share screen does the same, confirming ("Discard this photo?") only if the photo was neither saved to the gallery nor shared (`sharedAt`).
- Share files in `cache/share/` are **not** deleted on discard or "New photo"; Instagram may still be reading them. Only the age-based cleanup removes them.

### File locations

| Location | Contents |
|---|---|
| `noBackupFilesDir/sessions/<id>/` | Normalized photo (`capture.jpg`) and session snapshot (`session.json`) |
| `noBackupFilesDir/active_session` | ID of the current session |
| `cache/share/` | Rendered background and caption sticker for sharing (exposed via FileProvider) |
| MediaStore `Pictures/StoryMaker` | User-exported images |

### Cleanup

Runs once at app start, off the main thread (`media/StartupCleanup.kt`, started from `StoryMakerApp.onCreate`):

- `cache/share/`: delete files older than ~24 hours, judged by file modification time only. Files written moments ago are never touched, so cleanup cannot race with a share in progress.
- `sessions/`: delete every session folder except the one named in `active_session` (`SessionStore.cleanUpOrphans`, under the store's lock so it cannot race with a new capture).

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
- Caption text and switch states are stored in the session as the user changes them (not a layer change, so the gallery-saved state is unaffected). The field is cut to 150 characters (Unicode code points, so an emoji counts once and is never split); the counter is announced to screen readers within 10 characters of the limit.
- The content above Continue scrolls, so the field and switches stay reachable with the keyboard open; Continue stays pinned above the keyboard.
- Caption normalization (`media/CaptionText.kt`), applied before rendering and before the clipboard copy: trim, and collapse runs of blank lines to one. A whitespace-only caption counts as empty.
- Before rendering: trim leading/trailing whitespace and collapse excessive blank lines.

### Share screen (`Share`)

- Top bar: Back, the title "Ready to share", and a **New photo** icon button on the right.
- Large 9:16 preview: the share render (photo + layers, rendered by the exporter when the screen opens, so it matches the saved and shared image) with, when "Show on photo" is on and the caption is not empty, the **actual caption sticker PNG from the renderer** drawn centered at **75% of the preview width** (Instagram's display width for interactive stickers), so the preview matches the composer, plus a small "You can move it in Instagram" hint. Never imitate the sticker with Compose text.
- **Share to Instagram Stories** (accent pill, full width), or **Get Instagram** when Instagram is unavailable (section 15).
- **Save** (secondary pill, full width since "More" is removed): gallery export (section 13).
- The share background is pre-rendered when the screen opens, so tapping Share only renders the small caption sticker.

### Caption sticker rendering spec

Implemented by `media/CaptionRenderer.kt`; metrics in `CaptionStickerSpec`, in pixels of the rendered sticker. Instagram shows every sticker at 75% of the Story width, so the sticker canvas has a **fixed width of 912px** (box up to 864px plus margins) with the box centered: Instagram then scales every caption by the same factor (0.887), and text size stays consistent for short and long captions. The text metrics are the design's caption label (scaled ×1080/290 from the 290px mockup) divided by that factor, so the label appears in Instagram at the design's size:

- Transparent bitmap (PNG when shared), separate from the background image.
- White rounded box (corner radius 42px) with `#111111` Manrope 700 text at 63px; padding 34px vertical, 59px horizontal; line spacing ×1.1.
- Box width hugs the text, capped at 864px; the rest of the 912px canvas is transparent.
- Wrapped, center-aligned text (`StaticLayout`, first-strong text direction). If it needs more than 6 lines, the font shrinks in 2px steps (down to a 28px floor that even 150 of the widest characters fit within), so the full caption always fits; short captions keep the full 63px.
- 24px transparent margin around the box so its corners are never clipped.
- Bundled Manrope font (variable font, weight axis set to 700); emoji fall back to the system emoji font.
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

1. Show "Preparing…" on the Share button immediately on tap (repeat taps are ignored).
2. Reuse the bitmaps already rendered for the preview: the share background and the caption sticker. Nothing is re-rendered, so Instagram receives exactly what the preview shows.
3. Write them to `cache/share/` (`media/ShareFiles.kt`) as `story_<time>_<random>.jpg` (JPEG quality 90) and `caption_<time>_<random>.png`, new names for every share, and obtain FileProvider URIs (authority `<applicationId>.fileprovider`, `res/xml/share_paths.xml` exposes `share/` only).
4. Sample the edge colors from the background (`media/EdgeColors.kt`: average of the top and bottom ~2% of rows).
5. Copy the caption to the clipboard (only if "Copy to clipboard" is on and the normalized caption is not empty).
6. Emit a one-shot launch event with a `StoryShareRequest`; the screen grants Instagram read access to both URIs and starts the intent (`share/InstagramStories.kt`).
7. On a successful launch, record `sharedAt` in the session; the button becomes **"Share again"**. If the launch fails, the screen switches to the "Get Instagram" fallback.

If the Facebook App ID is empty (debug builds without `facebook.appId`), Share shows a developer message instead of launching.

### Availability

- Check whether Instagram can handle the intent each time the Share screen resumes (not only when it is first shown), so installing Instagram via "Get Instagram" and coming back updates the button.
- If unavailable, the share button becomes "Get Instagram" and opens the Play Store listing for `com.instagram.android` (`market://`, falling back to the web URL).
- Also catch launch failure at tap time (Instagram uninstalled mid-flow) and show the same fallback.

---

## 16. Clipboard UX

- Copy only at share time, only when "Copy to clipboard" is on and the caption is not empty. Never copy while the user types. The normalized caption is copied as plain text (label "Caption").
- The user is told **before** sharing, via the switch subtitle on the Caption screen. No toast or snackbar is shown during launch (the handoff's toast is dropped), since the app is leaving the foreground and it would not be seen. On Android 13+ the system also shows its own clipboard confirmation.
- First share only: a one-time coach mark ("Before you share") shown on tap, before Instagram opens. It has one line per thing to explain: the caption sticker can be dragged, pinched or rotated in Instagram (when the sticker is shown), and the caption is copied and can be pasted as text (when copying is on). If neither applies, it is skipped. "Got it" stores the "seen" flag (SharedPreferences) and continues the share; dismissing it cancels the share and it shows again next time.

---

## 17. Accessibility

- Every icon-only control (close, flash, shutter, switch camera, back, undo, redo, remove sticker, New photo) has a content description. The resize/rotate handle is drawn on the canvas and is covered by the screen reader actions below.
- Stickers in the sheet use their display name as the content description.
- Sticker removal has a non-gesture alternative (✕ button on the selected sticker). Resizing and rotating do too: when a sticker is selected, the canvas announces it ("Photo, Done sticker selected") and offers screen reader custom actions: Make sticker bigger / smaller (×1.25), Rotate sticker left / right (15°), Remove sticker. Each is one undoable change.
- Touch targets are at least 48dp: the design's 34dp category chips keep their look with a 48dp touch area, the sticker search field is 48dp tall, and the swatch row reaches 4dp into the side padding so seven swatches get 48dp even on 360dp-wide phones. Color swatches announce their color name and selected state.
- Checked in tests (`accessibility/AccessibilityTest`): on every screen, each clickable, toggleable or selectable control must have a label and a 48×48dp touch area; `AccessibilitySanityTest` proves the check reports violations. Google's Accessibility Test Framework checks were tried first but report nothing under Robolectric, so the checks run on the Compose semantics tree.
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
| Landscape or front-camera capture | Normalized once after capture (D10); device rotation mapping unit-tested; front/back pixels tested for all 8 EXIF orientations; a sideways capture verified on the test phone (3840×2160, upright) |
| Out of memory while rendering | Retry once at half size, then show an error (`withOutOfMemoryFallback`, unit-tested) |
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
- **Robolectric tests:** `StoryShareRequest` → `Intent` mapping and Instagram availability; photo normalization pixel output (`NormalizationPixelTest`: JPEGs tagged with all eight EXIF orientations, back and front camera, checking where each colored corner lands and that front photos are mirrored exactly once); accessibility checks and the sticker screen reader actions.
- **Screenshot tests (Roborazzi + Robolectric native graphics):** every screen in its main states (`screenshots/ScreenScreenshotTest`), compared visually against the handoff PNGs during review; image exporter output with fixed layers and caption sticker with short, long, max-length, emoji-only, multi-line, and RTL captions (`screenshots/RenderScreenshotTest`). Reference images are committed in `app/src/test/screenshots/`. `./gradlew recordRoborazziDebug` records them, `./gradlew verifyRoborazziDebug` compares against them (and fails on any difference); a plain `test` run does not compare. Tests run as SDK 36 with a plain `Application` (no Hilt) and render the screens' `internal` `…Content` composables directly. The test JVM needs `--add-opens=java.base/jdk.internal.access=ALL-UNNAMED` (set in `app/build.gradle.kts`) for Robolectric's SDK 36 sandbox on recent JDKs.
- **Instrumented tests** (on the test phone; they uninstall the app afterwards): `CaptureFlowTest` runs the real camera end to end (the app's permission screen, the system dialog answered with UiAutomator because some OEMs block programmatic grants, a real capture, the session file, the Editor), chosen over CameraX's fake test camera to cover the real path; `FileProviderTest` checks share files open through their content URIs and files outside `share/` are refused; gallery export on API 26–28 (legacy path) and 29+ (MediaStore). `GallerySaverTest` (API 29+ path) runs on the test phone; the API 26–28 path is not yet verified on a device.
- **Manual QA matrix:** Instagram installed vs. missing, logged in vs. out, old Instagram version, front vs. back camera (preview matches result), landscape capture, empty caption, each combination of the two caption switches, Android 12 vs. 13+ clipboard behavior, permission denied once / permanently / "Only this time", process death round-trip ("Don't keep activities"), swipe-away fresh launch, low storage, API 26, and a recent API level.

---

## 21. Milestones

Implement one milestone at a time. Each must build, include and pass its own tests, and be committed before the next begins.

| # | Milestone | Done when |
|---|---|---|
| 1 | Project skeleton and theme | Packages, DI (Hilt verified or manual fallback chosen), BuildConfig + App ID config, fixed dark theme with handoff tokens, Manrope, shared button components, type-safe navigation, and empty Permission/Capture/Editor/Caption/Share screens build and navigate |
| 2 | Permission, camera, and sessions | Permission and Permission denied screens with full result handling; Capture screen per design (grid, flash cycle, switch camera); 16:9 capture at capped resolution into a session folder; normalization verified on real front and back cameras; error states; `SessionStore` with atomic, serialized, versioned snapshots, active pointer, fresh-launch vs. restore logic, and restore-failure handling |
| 3 | Stickers | Outlined Figma set extracted to 40 VectorDrawables by `tools/stickers/extract_stickers.py`; catalog with stable IDs, categories, and tags; repository and filtering |
| 4 | Editor drawing | Editor layout and Draw tab per design; layer model, reducer, freehand drawing, swatches, brush slider, undo/redo with cap, edits persisted through `SessionStore` and surviving process death; Back → discard confirmation |
| 5 | Editor stickers | Stickers tab with the two-position sheet, search, category chips, grid; place, select, move, pinch, rotate, ✕ remove, and resize/rotate handle; one undo step per gesture |
| 6 | Rendering and save | Image exporter (share and gallery targets, OOM fallback) matching the editor; gallery save on API 26–28 and 29+; `exportedRevision` disables duplicate saves; Share screen shows the exporter's preview with Save and New photo |
| 7 | Caption and Share screens | Caption screen per design with counter and switches; caption sticker renderer meeting the spec; Share screen with accurate centered caption preview and "move it in Instagram" hint; Roborazzi screenshot tests for all screens and renderers |
| 8 | Instagram share | Stories opens directly with background, edge colors, and caption sticker per the switches; clipboard per the switch; `sharedAt` and Share again; availability check and Get Instagram fallback; sticker size measured on device (75% display width, fixed canvas) |
| 9 | Edge cases and polish | All rows in section 18 handled; cleanup at app start; coach mark; accessibility checklist (section 17) met and checked in tests; Robolectric normalization pixel tests; real-camera and FileProvider instrumented tests; shared dialog and dark snackbar |
| 10 | QA pass | Manual QA matrix from section 20 completed; screens checked against the handoff PNGs; issues fixed |

---

## 22. Open questions

- Analytics events, if any.
