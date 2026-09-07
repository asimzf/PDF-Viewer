# asimPDF

An Android PDF **viewer and editor** that does everything on the device. There is
no account, no cloud step and no upload: the app does not declare the `INTERNET`
permission at all, so it *cannot* talk to a network even if it wanted to.

Built with Kotlin and Jetpack Compose. Pages are rasterised with the platform
`android.graphics.pdf.PdfRenderer`, and every edit is performed locally by a
bundled [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) engine.

---

## What it does

### Reading
- Open PDFs from the file picker, from the recent list, or from any app that
  shares one (asimPDF registers as a PDF viewer for `VIEW` and `SEND` intents).
- Continuous scrolling or one page at a time, with pinch to zoom, double-tap
  zoom and horizontal panning.
- Night mode that inverts the page without touching the file.
- Full text search across the document, with the matches highlighted on the page
  and next/previous navigation.
- Table of contents (PDF outline), page slider, and the last page you were on is
  remembered per document.
- Print through the Android print service, and share the document with any other
  app on the phone.

### Marking up
A markup mode with a toolbar of tools that write straight into the page content,
so the result renders identically in every other PDF reader:

- freehand ink, in any colour and thickness
- highlight (multiply blended, so the text underneath stays readable),
  underline and strikeout
- rectangles, ovals, lines and arrows, outlined or filled
- text boxes, using a font embedded from the device
- sticky notes, written as real PDF text annotations with the comment attached
- a signature you draw with a finger, saved as a transparent PNG and stamped
  where you tap
- images placed from your gallery
- black-box redaction, and a "flatten pages" tool that turns the pages into
  pictures so the covered content really is gone
- per-stroke undo/redo before you commit, and document-level undo/redo after

### Page management
Thumbnail grid with multi-select, then: rotate left/right, move up/down,
duplicate, extract to a new file, delete. Plus insert a blank page of any paper
size, or insert every page of another PDF at a chosen position.

### Tools
| Tool | What it does |
| --- | --- |
| Organise pages | Reorder, rotate, delete, duplicate, extract |
| Insert pages | Blank page of any size, or another PDF |
| Merge | Join several documents in the order you choose |
| Split | Every N pages, at a page, or pull out a range |
| Rotate | Any page range by 90/180/270° |
| Crop | Trim each side by a percentage, and undo the crop |
| Watermark | Text, colour, size, opacity, angle, tiled, in front or behind |
| Page numbers | `{page}`/`{total}` formats, six positions, start number |
| Compress | Re-encodes images at three quality levels and reports the saving |
| Password & permissions | AES-128/256, open and owner passwords, permission flags |
| Document properties | Title, author, subject, keywords, creator — or strip it all |
| Fill forms | Read, fill and flatten AcroForm fields |
| Export as images | PNG or JPEG at a chosen DPI |
| Extract text | Save the document text as `.txt` |
| Flatten pages | Rasterise pages so nothing can be edited or copied |
| Images to PDF | Build a PDF from photos or scans, with page size and fit options |
| Document info | Pages, page size, PDF version, dates, security, form status |

### Safety of your files
- The document you pick is **copied into private app storage** first. Edits
  happen on that copy, so the original is untouched until you choose *Save* (back
  to where it came from) or *Save as*.
- Every edit takes an undo snapshot; undo and redo work across the whole session.
- Encrypted documents are unlocked into the working copy after you enter the
  password, which is what lets the platform renderer show them. The security tool
  can re-apply protection before you share the file.

---

## Building

Requirements: Android Studio (or the Android SDK plus JDK 17) and an internet
connection **for the build only** — the app itself never uses one.

```bash
./gradlew assembleDebug          # APK in app/build/outputs/apk/debug/
./gradlew installDebug           # build and install on a connected device
./gradlew testDebugUnitTest      # unit tests
```

- `minSdk` 26, `targetSdk`/`compileSdk` 35
- Kotlin 2.0, Compose (Material 3), AGP 8.7

## How it is put together

```
app/src/main/java/com/asimzf/asimpdf/
├── pdf/            PDF engine: rendering, page ops, stamping, text, security,
│                   forms, compression, conversion, coordinates
├── model/          Annotations and the page-range parser
├── data/           Recent documents and viewer preferences
├── ui/             Compose screens: home, viewer, markup, organise, tools
├── print/          Print adapter for the Android print service
└── util/           Working files, images, sharing
```

Two details worth knowing if you go reading:

- **`PdfCoords`** bridges "what the user sees" (top-left origin, y down, page
  rotation applied) and PDF user space (bottom-left origin, y up, `/Rotate`
  honoured). Stamping code pushes one matrix and then draws in screen-like
  coordinates, which is why markup lands where you put it on rotated pages.
- **`PdfIo.edit`** loads, applies a change, writes to a temporary file and then
  swaps it in, so an operation that fails half way through can never leave a
  broken document behind.

## Licence

The app code is MIT licensed. PdfBox-Android is Apache 2.0.
