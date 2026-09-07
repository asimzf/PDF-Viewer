# Publishing asimPDF to Google Play

Everything in the repository is already set up for a release build. What is left
is the part only you can do: the developer account, the signing key, and the
store listing.

---

## 1. Before anything else: two decisions that are permanent

| Thing | Value | Why it matters |
| --- | --- | --- |
| Package name | `com.asimzf.asimpdf` | Cannot ever be changed once published. A new one means a new app listing with zero installs. |
| Upload key | The keystore you create in step 3 | Lose it and you can ask Google to reset it, but it is a slow, manual process. Back it up in two places. |

If you want a different package name, change `applicationId` and `namespace` in
`app/build.gradle.kts` **now**, before the first upload.

## 2. Create the developer account

- <https://play.google.com/console> — one-off registration fee (USD 25).
- Choose the account type carefully:
  - A **personal** account created after 13 November 2023 must run a **closed
    test with at least 12 testers who stay opted in for 14 continuous days**
    before it can apply for production access. Plan for this; it is the single
    biggest delay between "app is ready" and "app is public".
  - An **organisation** account is exempt from that requirement but needs a
    D-U-N-S number and takes longer to verify.
- Identity verification (address, phone, ID) takes anywhere from a day to a
  couple of weeks. Start it before the app is finished.

## 3. Create the upload key

Run this once, and keep the file somewhere you will still have it in five years:

```bash
keytool -genkeypair -v \
  -keystore asimpdf-upload.jks \
  -alias asimpdf \
  -keyalg RSA -keysize 4096 -validity 10000
```

Then create `keystore.properties` in the project root — it is git-ignored, so it
never leaves your machine:

```properties
storeFile=../asimpdf-upload.jks
storePassword=…
keyAlias=asimpdf
keyPassword=…
```

Turn on **Play App Signing** when you first upload (it is the default). Google
then holds the real app signing key and your upload key is only used to prove
uploads come from you — if the upload key is ever lost, it can be replaced.

## 4. Build the release bundle

```bash
./gradlew bundleRelease
# app/build/outputs/bundle/release/app-release.aab
```

Play requires an **AAB**, not an APK. Before uploading, install the release
build on a real device and check it works with R8 shrinking enabled:

```bash
./gradlew installRelease
```

Test at minimum: open a PDF, search it, mark it up and save, add a password, and
merge two files. R8 problems always show up in the PDF engine first, and the
keep rules in `app/proguard-rules.pro` exist for exactly that reason.

Bump `versionCode` (an integer, +1 every upload) and `versionName` in
`app/build.gradle.kts` for each release.

## 5. Fill in the store listing

Draft copy is below; edit it to sound like you.

**App name (30 characters max)**

```
asimPDF — Offline PDF Editor
```

**Short description (80 characters max)**

```
Read, edit and organise PDFs. No ads, no permissions, no internet. All on device.
```

**Full description (4000 characters max)**

```
asimPDF is a complete PDF viewer and editor that works entirely on your device.

It has no advertising, no accounts, no analytics and no tracking. It requests no
Android permissions at all, and it does not hold the internet permission, so it
is not able to send your documents anywhere even if it wanted to.

READ
• Smooth continuous scrolling or one page at a time
• Pinch and double-tap zoom
• Night mode for comfortable reading in the dark
• Search the whole document, with matches highlighted on the page
• Jump around using the document's own table of contents
• Print through Android's printing service, or share to any app

MARK UP
• Draw freehand in any colour and thickness
• Highlight, underline and strike out
• Rectangles, ovals, lines and arrows
• Add text boxes and sticky notes
• Sign with your finger and place the signature anywhere
• Add images and stamps
• Black out private information, then flatten the page so it is really gone
• Undo and redo, before and after applying

ORGANISE
• Thumbnail view with multi-select
• Rotate, reorder, duplicate, extract and delete pages
• Insert blank pages, or every page of another PDF

TOOLS
• Merge several PDFs into one
• Split by page count, at a page, or pull out a range
• Crop margins
• Watermarks, page numbers, headers and footers
• Compress to make a file smaller before sending it
• Password protection with AES-128 or AES-256, and permission controls
• Edit the title, author, subject and keywords, or strip metadata entirely
• Fill in and flatten PDF forms
• Export pages as PNG or JPEG, or extract the text
• Build a PDF from photos and scans

YOUR FILES STAY YOURS
The document you open is copied into asimPDF's private storage, so editing never
touches your original until you choose to save. Everything happens on the
processor in your hand.

asimPDF is free and open source under the MIT licence.
Source code: https://github.com/asimzf/PDF-Viewer
```

**Graphics you need to produce**

| Asset | Size | Notes |
| --- | --- | --- |
| App icon | 512 × 512 PNG | The launcher icon in `app/src/main/res/drawable/ic_launcher_foreground.xml` on the red background is a fine starting point. |
| Feature graphic | 1024 × 500 PNG/JPEG | Shown at the top of the listing. |
| Phone screenshots | 2 to 8, at least 1080 px on the short side | Take them on a device: the viewer, markup in progress, the thumbnail organiser, the tools list. |
| Tablet screenshots | optional but recommended | Play ranks tablet-ready apps better on large screens. |

## 6. Answer the App content questionnaires

These are quick for asimPDF because there is genuinely nothing to declare.

- **Privacy policy** — required. Publish `docs/privacy-policy.md` with GitHub
  Pages (repository Settings → Pages → Deploy from branch → `main` → `/docs`)
  and give Play the resulting URL.
- **Data safety** — answer **"No, this app does not collect or share any user
  data."** Nothing else in the form applies. Say yes to "data is encrypted in
  transit" being not applicable, and note the app has no internet access.
- **Ads** — **No ads**.
- **Content rating** — fill in the IARC questionnaire honestly; a utility with
  no user content and no communication rates **Everyone / PEGI 3**.
- **Target audience** — 13+ or 18+ keeps you out of the Families programme and
  its extra requirements. The app is not designed for children.
- **Government apps, financial features, health** — all no.
- **Permissions declaration** — nothing to fill in, because the app declares no
  permissions. This is a real advantage: apps asking for "All files access" get
  a manual policy review that can take weeks.
- **Target API level** — Play requires new apps to target a recent API level,
  and the bar moves every August. This project targets API 36 (Android 16).
  Check the current requirement in Play Console before you upload.

## 7. Release track order

1. **Internal testing** — up to 100 testers, available in minutes. Use it to
   check the signed build from the Play Store itself.
2. **Closed testing** — required for personal accounts (12 testers, 14 days).
   Recruit from friends, a Reddit thread, or a Discord.
3. **Open testing** — optional, a good way to gather feedback publicly.
4. **Production** — start with a staged rollout (say 20%) so a bad build can be
   halted.

First review of a new app typically takes a few days; later updates are faster.

## 8. After launch

- Watch **Android vitals** in the Console for crashes and ANRs.
- Reply to reviews; on Play it measurably affects ratings.
- Keep `versionCode` climbing and never reuse one.
- Re-check the target API requirement each August.
