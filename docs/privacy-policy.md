# asimPDF privacy policy

_Last updated: 7 September 2026_

**asimPDF does not collect, store, transmit or share any personal data.**

## The short version

asimPDF is a PDF viewer and editor that runs entirely on your device. It has no
servers, no accounts, no analytics, no advertising and no tracking of any kind.
Nothing you open in asimPDF ever leaves your phone or tablet unless you
deliberately share it with another app yourself.

## No network access at all

asimPDF does not request the `INTERNET` permission. Android therefore prevents
the app from making any network connection whatsoever. This is not a promise
about how the app behaves — it is a restriction the operating system enforces on
it, and you can verify it in the app's manifest, which is published in full at
<https://github.com/asimzf/PDF-Viewer>.

## No permissions

asimPDF requests no Android permissions. It has no access to your storage,
camera, microphone, contacts, location or any other protected data.

## How your files are handled

- You choose a document through Android's own file picker, or by sharing a PDF
  to asimPDF from another app. asimPDF never browses your storage on its own.
- The document you choose is copied into the app's private storage so that
  editing never damages your original. That working copy stays on the device,
  is readable only by asimPDF, and is deleted automatically once it is a day
  old, or when you clear the app's storage.
- Exported files (split parts, images, extracted text) are written to the app's
  private storage until you save or share them somewhere yourself.
- The app remembers a short list of recently opened documents and your viewer
  preferences. This is stored on the device only.
- Signatures you draw are saved as image files in the app's private storage so
  you can reuse them. Clearing the app's storage removes them.

## Children

asimPDF collects no data from anyone, including children.

## Changes

If this policy ever changes, the updated version will be published at this same
address and the date above will be updated.

## Contact

Questions about this policy can be raised as an issue at
<https://github.com/asimzf/PDF-Viewer/issues>.
