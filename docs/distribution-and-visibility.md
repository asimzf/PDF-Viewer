# Getting asimPDF in front of people

Google Play is the biggest shop window, but for a free, open-source, no-tracking
utility it is not the only one, and it is not the fastest. Here is what actually
moves the needle, roughly in the order worth doing it.

---

## Inside Google Play

**Get the basics right first — this is most of organic discovery.**
Play's search is keyword driven, and the two fields that matter most are the app
title and the short description. `asimPDF — Offline PDF Editor` earns the words
"offline", "PDF" and "editor"; a bare `asimPDF` would earn nothing. In Play
Console under *Grow*, two free features are worth using:

- **Custom store listings** — a different listing for people arriving from, say,
  a Reddit post than from search.
- **Store listing experiments** — real A/B tests on the icon, screenshots and
  descriptions. Cheap and surprisingly effective.

**Google Play Pass.** A curated subscription of apps with no ads and no
in-app purchases. asimPDF fits that profile precisely. Google approaches
developers, but there is also an interest form in the Play Console help pages —
worth registering interest once the app has a few months of good reviews.

**Editorial features and "Best of Play".** Play's editorial team curates the
collections you see on the Apps tab. There is no self-serve nomination for
ordinary apps; what they look at is polish, stability, tablet and large-screen
support, and recent ratings. Supporting tablets and foldables well is the single
most common thing that gets a small utility picked up.

**#WeArePlay.** Google's developer-story programme, which publishes profiles of
individual developers. It accepts submissions, and a "one person built a
privacy-first PDF editor" story is exactly its shape.

**Independent security review badge.** Through the App Defense Alliance's mobile
app security assessment, an authorised lab audits the app and Play then shows an
"Independent security review" line in your Data safety section. It costs money,
so it is a later-stage move, but for an app whose whole pitch is privacy it is
the strongest trust signal Play offers.

**Things that do not apply here:** the Indie Games programmes (games only) and
Teacher Approved (children's apps only).

## Outside Google Play — where privacy-minded users actually look

These reach a smaller but much more receptive audience, and several of them can
happen this week rather than after a 14-day closed test.

**IzzyOnDroid** (<https://apt.izzysoft.de/fdroid/>) — an F-Droid-compatible
repository that takes the APK straight from your GitHub releases. It is the
fastest real listing you can get: publish a signed APK as a GitHub Release, then
open a request. It also runs a tracker scan, and "0 trackers, 0 permissions" is
the badge this app deserves.

**F-Droid main repository** — the flagship free-software store. Higher bar: they
build from source on their own servers and every dependency must be free
software. asimPDF qualifies (PdfBox-Android and Bouncy Castle are both open
source), so this is realistic, but expect a slower review. The
`fastlane/metadata/` folder in this repository is already in the layout F-Droid
and IzzyOnDroid read descriptions and screenshots from.

**Accrescent** — a newer, security-focused Android store. Small but growing, and
a natural fit for an app like this.

**GitHub Releases + Obtainium** — publish the signed APK on every release and
users of Obtainium can install and auto-update straight from the repository. Zero
gatekeepers.

## Telling people it exists

A good launch post is worth more than months of passive store presence:

- **r/androidapps** (read its self-promotion rules), **r/fossdroid**,
  **r/privacy**, **r/degoogle** — the offline, permission-free angle is the
  story, not the feature list.
- **Hacker News** as a *Show HN*, linking the repository rather than the store.
- **XDA Forums** — Android app releases still get real traction there.
- **Product Hunt** for a launch-day spike.
- Directory listings that people search: **AlternativeTo** (list it as an
  alternative to Adobe Acrobat and Xodo), **LibHunt**, **Awesome-Android**,
  **Privacy Guides** forum.
- Add the F-Droid/IzzyOnDroid/Play badges to the README once each listing is
  live; a repository that shows where to install gets more installs.

## What earns the visibility

None of the above compensates for the two things Play's ranking and every
community actually reacts to: **crash-free sessions** and **ratings**. Watch
Android vitals, fix what it reports, and reply to reviews. A 4.5-star utility
with no crashes gets recommended by users far more reliably than any programme
will recommend it for you.
