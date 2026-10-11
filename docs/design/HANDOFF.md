# Photo to Story — design handoff (Android)

A minimal Android flow: the user grants camera access, takes a photo, draws on it, adds stickers, writes a caption, and shares the result to Instagram Stories. There is **no gallery import**. The camera is the only way in.

## What's in this folder

| Path | What it is |
|---|---|
| `HANDOFF.md` | This spec. Read it first. |
| `screens/png/*.png` | A rendered image of each screen at 390×844, the visual target. |
| `screens/html/*.html` | A static HTML version of each screen. Use it for exact sizes, colors and copy. |
| `screens/source/*.dc.html` | The original design files, which use a template syntax. `{{accent}}` = `#FF5A36`. |

Treat the HTML as a **layout and styling reference only**. Build natively in **Jetpack Compose + Material 3**. 1 CSS px in the mockups ≈ 1 dp.

## Flow

```
1  Camera access ──Continue──▶ 1a System permission dialog
                                ├─ While using the app ─▶ 2 Capture
                                ├─ Only this time ──────▶ 2 Capture
                                └─ Don't allow ─────────▶ 1b Access denied ──Open app settings──▶ Android Settings
2  Capture ──shutter──▶ 3 Draw ◀──tabs──▶ 4 Stickers ──Next──▶ 5 Caption ──Continue──▶ 6 Share
```

| # | Screen | File | Purpose |
|---|---|---|---|
| 1 | Camera access | `Permission` | In-app explanation shown **before** the system prompt. Buttons: Continue (primary) and Not now. |
| 1a | System dialog | `SystemPrompt` | Android's own runtime permission dialog. **Do not build this.** It's here only to show the flow. |
| 1b | Access denied | `PermissionDenied` | Shown when the permission is permanently denied. Button: "Open app settings". |
| 2 | Capture | `Main` | Full-screen camera preview with a rule-of-thirds grid, close, flash toggle, shutter and switch camera. No gallery button. |
| 3 | Draw | `Draw` | Photo canvas, undo/redo, Next, Draw/Stickers tabs, 7 colour swatches, brush-size slider. |
| 4 | Stickers | `Stickers` | Same canvas plus a bottom sheet: search, category chips (All / Shapes / Faces / Words), 4-column sticker grid. A placed sticker shows a dashed bounding box, a remove button (top-left) and a resize/rotate handle (bottom-right). |
| 5 | Caption | `Caption` | Thumbnail plus a caption field (max 150 chars, live counter). Two switches: "Show on photo" (on by default) and "Copy to clipboard" (off). Continue button. |
| 6 | Share | `Share` | Final preview, primary "Share to Instagram Stories" button, secondary Save and More buttons. |

## Design tokens

**Colours**

| Token | Hex | Use |
|---|---|---|
| background | `#0E0E0E` | All screens |
| surface | `#1A1A1A` | Tab bar, settings card, bottom sheet, caption field |
| surfaceHigh | `#1F1F1F` | Round icon buttons, secondary buttons |
| surfaceHigher | `#262626` / `#2A2A2A` | Sticker tiles, search field, chips, dividers |
| onBackground | `#FFFFFF` | Primary text and icons |
| onSurfaceVariant | `#B5B5B5` | Body copy, labels |
| muted | `#9A9A9A` | Hints, counters, placeholders |
| primaryButton | `#FFFFFF` bg / `#111111` text | Next, Continue, Allow |
| accent | `#FF5A36` bg / `#111111` text | Share to Instagram Stories only |

**Brush swatches:** `#FFFFFF` (default), `#111111`, `#FF5A36`, `#FFD23F`, `#4ADE80`, `#3B82F6`, `#8C7BFF`.

**Type:** Manrope (Google Fonts), weights 400, 600, 700 and 800.

| Use | Size and weight |
|---|---|
| Screen title (permission screens) | 28 / 800 |
| Top app bar title | 20 / 700 |
| Body | 16 / 400, line height 1.5 |
| Buttons | 15–16 / 600–700 |
| Secondary text | 13 / 400–600 |

**Shape:** buttons are fully rounded (pills). Corner radius is 20 for the photo canvas, 24 for the bottom sheet's top corners, 16 for sticker tiles and cards, 12 for inputs, and 28 for dialogs.

**Spacing:** screen side padding is 16 (24 on the permission screens), with gaps of 16 between sections. The minimum touch target is **48dp** on Android (the mockups use 44px, so round up).

**Icons:** 24px stroke icons, 2px stroke, round caps. Use Material Symbols *Outlined* equivalents: close, flash_off, cameraswitch, arrow_back, undo, redo, edit, sticky-note/sticker, search, download, share, photo_camera, no_photography.

## Android implementation notes

**Permission (screens 1, 1b)**
- Manifest: `android.permission.CAMERA`.
- Show screen 1 first, then on Continue launch `ActivityResultContracts.RequestPermission()`.
- Denied once: if `shouldShowRequestPermissionRationale` returns true, return to screen 1.
- Permanently denied: show screen 1b. "Open app settings" fires `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` with `package:<applicationId>`.
- Re-check the permission in `onResume` so returning from Settings drops the user straight into Capture.

**Capture (screen 2)**
- CameraX: `Preview` + `ImageCapture`. A flash toggle (off/auto/on) and a front/back switch.
- Fill the screen; the preview has a 28dp bottom radius, and the controls sit below it.
- After capture, apply EXIF rotation, then go to Draw.

**Editor (screens 3–4)**
- Store layers as state: a base bitmap, a list of strokes `(path, color, widthPx)`, and a list of stickers `(id, offset, scale, rotation)`.
- Draw with the Compose `Canvas` and `pointerInput` drag gestures. Brush width runs 2–24, default 8.
- Stickers: tap a tile to place it at the centre; `detectTransformGestures` handles pan/pinch/rotate; tapping a sticker selects it.
- One undo/redo stack covers both strokes and sticker actions.
- The sticker library is a `ModalBottomSheet` with search filtering by name/tag. Ship the stickers as vector drawables.
- Export: render all layers to a **1080×1920** bitmap (9:16 for Stories), centre-cropping the photo.

**Caption (screen 5)**
- Max 150 characters.
- "Show on photo" draws the caption as a white rounded label (bottom-centre) into the export.
- "Copy to clipboard" puts the caption on the clipboard via `ClipboardManager` and shows a short toast or snackbar. Instagram's share intent can't pass caption text, which is why both options exist.

**Share (screen 6)**
- Save the exported PNG to cache and expose it through a `FileProvider`.
- Instagram Stories:
  ```kotlin
  val intent = Intent("com.instagram.share.ADD_TO_STORY").apply {
      setDataAndType(uri, "image/png")
      putExtra("source_application", FACEBOOK_APP_ID) // required by Instagram
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
  grantUriPermission("com.instagram.android", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  if (intent.resolveActivity(packageManager) != null) startActivity(intent) else /* show "Instagram isn't installed" */
  ```
  Check Meta's current Sharing to Stories docs for the latest requirements.
- Save: write to `MediaStore.Images` (Pictures/<AppName>).
- More: `Intent.createChooser(ACTION_SEND, …)`.

## Placeholders to replace
- `[App name]` in the permission dialog copy is your app label (Android shows it automatically).
- The landscape illustration in the mockups stands in for the captured photo.
- `[Your caption]` on the Share preview is the user's caption.
- `FACEBOOK_APP_ID` is required for the Instagram Stories intent.

## Out of scope
- Gallery / photo picker import.
- Text tool (beyond the caption), filters, cropping.
