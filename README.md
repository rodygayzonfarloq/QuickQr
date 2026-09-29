# QuickQR

A native Android QR code / barcode scanner and generator, built with Kotlin.

## Features

- **Live camera scanning** (CameraX + ML Kit) — detects QR codes plus EAN-13/8, UPC-A/E,
  Code 128/39/93, Codabar, ITF, PDF417, Aztec and Data Matrix.
- **Scan from a gallery photo** using the system Photo Picker (no storage permission needed).
- **Smart results** — links open in the browser, Wi-Fi codes show the password with a
  copy button, phone numbers can be dialed, emails/SMS open your messaging apps, etc.
- **QR code generation** from text, a URL, Wi-Fi credentials, email, or phone — with
  Save-to-gallery and Share.
- **Scan history**, stored locally on-device with Room (swipe to delete, clear all).
- Material 3 UI, dark/light follows the system theme.

## Requirements

- Android Studio (current stable — Ladybug/Koala or newer)
- JDK 17 (Android Studio bundles this)
- A physical device is strongly recommended for testing the camera — many emulators
  don't expose a working camera by default.

## Opening the project

1. Unzip this folder anywhere.
2. Android Studio → **Open** → select the `QuickQR` folder.
3. Let it sync. On first sync it may need to download compileSdk 36 / the Gradle
   distribution — accept any SDK Manager prompts.
4. If Android Studio prompts about the Gradle wrapper jar being missing, accept its
   offer to regenerate it — the wrapper *properties* (which Gradle version to use)
   are included, just not the binary jar, which isn't practical to hand-write.
5. Run on a device.

## Project structure

```
app/src/main/java/com/quickqr/app/
├── MainActivity.kt          bottom-nav host, swaps the 3 tab fragments
├── ui/scanner/               camera preview (CameraX) + ML Kit frame analysis
├── ui/result/                shows a decoded code, type-specific actions, saves history
├── ui/history/                Room-backed list of past scans
├── ui/generator/              text -> QR bitmap (ZXing), save/share
├── data/                     Room entity/DAO/database for scan history
└── util/                     ContentParser (classifies raw QR text), QRCodeGenerator
```

## Building it with no PC (GitHub Actions)

Upload this project's files to the **root** of a GitHub repo (not zipped, not inside an
extra folder). Three workflows are included in `.github/workflows/`:

| Workflow | What it does |
|---|---|
| `build.yml` | Builds a debug APK on every push / PR, and a **signed AAB** too once your signing secrets are set |
| `release.yml` | Builds a **signed AAB** (Play Store) + signed APK; tag `v1.0.0` to run, optional upload to Play |
| `generate-keystore.yml` | One-time helper that creates your upload keystore if you have no computer |

Full step-by-step (keystore, secrets, Play Console, checklist): **[docs/PLAY_STORE_RELEASE.md](docs/PLAY_STORE_RELEASE.md)**

## Changelog

**Release pipeline pass:**
- Added signed release builds (`signingConfigs` driven by env vars / `keystore.properties`), CI-controlled `versionCode`/`versionName`, R8 + resource shrinking, and AAB splits.
- New `release.yml` (signed AAB + APK + R8 mapping, GitHub Release on tags, optional Play upload), `generate-keystore.yml`, `scripts/generate-keystore.sh`, and `docs/PLAY_STORE_RELEASE.md`.
- `build.yml` no longer unzips a nested project; the repo root is the project.

**Feature upgrade pass:**
- **Scanner:** added a front/rear camera flip button (hidden automatically on single-camera
  devices), and a short vibration + beep the instant a code is recognized, on top of the
  existing flashlight, gallery-photo scanning, and fully offline on-device decoding.
- **Generator:** replaced the old single-textbox-plus-template-chips approach with dedicated
  forms per content type - Website/URL, Wi-Fi (SSID/password/security/hidden), Phone,
  Email (to/subject/body), Contact/vCard (name/phone/email/org), Text, Location
  (lat/lon/label, encoded as a `geo:` URI), Event (title/location/start/end/description via
  date & time pickers, encoded as an iCalendar `VEVENT`), and Social (a platform picker -
  Instagram, X, Facebook, LinkedIn, TikTok, YouTube, Snapchat, Threads, WhatsApp - plus a
  handle, which builds the right profile/deep link automatically).
- **Scan history:** added a search box (matches against the raw content and its parsed
  preview), a "Favorites only" filter chip, and a per-item star to favorite/unfavorite a
  scan. Each item now also has a "\u22ee" overflow menu with Copy, Share, Delete, and (for
  links) Open Link, alongside the existing swipe-to-delete and "Clear All" (which now
  intentionally spares favorited scans - the star is meant to be a safe keep).
- **Scan result screen:** can now recognize and act on `geo:` location codes ("Open in
  Maps") and `VEVENT` calendar codes ("Add to Calendar"), in addition to the existing
  URL/Wi-Fi/email/phone/SMS/vCard handling.
- Added a Room migration (v1 -> v2) for the new favorite flag so upgrading the app doesn't
  wipe existing history.

**Previous pass (dependency check + bug fixes):**
- Bumped AGP to 8.13.2, CameraX to 1.6.2, core-ktx to 1.18.0, appcompat to 1.7.1,
  material to 1.13.0; removed the now-empty `room-ktx` artifact (merged into
  `room-runtime` as of Room 2.6+).
- Deliberately **not** upgraded to AGP 9.x or Kotlin 2.3.x yet: AGP 9 replaces the
  Kotlin Gradle plugin with a "built-in Kotlin" model that's a substantial rewrite of
  the build files, and Kotlin 2.3.0 has documented KSP incompatibilities as of this
  writing. Both are real upgrades worth doing eventually, just not blind since this
  project can't be compiled locally to verify them.
- Fixed: the "camera permission denied" overlay is a full-screen layer that sat on
  top of the gallery FAB, so the on-screen advice to "use the gallery button below"
  pointed at a button you couldn't actually reach - the overlay now has its own
  working gallery button.
- Fixed: tapping any content-type chip on the Create tab unconditionally overwrote
  whatever you'd already typed, even re-tapping the chip you were already on. Chips
  now only pre-fill their template into an empty field.
- Fixed: re-tapping the tab you're already on (especially Scan) tore down and rebuilt
  the fragment for no reason - for the Scan tab that meant restarting the camera every
  time. Re-tapping the active tab is now a no-op.
- Fixed: a fresh ML Kit `BarcodeScanner` client was being created (and never closed)
  every time the camera use cases were bound *and* every time a gallery photo was
  scanned - a real resource leak on repeated use. Scanning now shares and properly
  closes one scanner per Scan-tab visit.
- Fixed: the Wi-Fi/email QR parser required a trailing `;` after the last field, which
  some real-world QR generators omit - it would silently show an empty Wi-Fi password
  in that case. It now also accepts end-of-string as a valid terminator.
- Added: `mailto:` links with `?subject=&body=` query parameters are now parsed and
  carried through to the "Send Email" action instead of being silently dropped.

## Before you publish this to Google Play

This is a complete, working starting point — but a few things are on you before it's
store-ready:

1. **Change the package name.** `com.quickqr.app` in `app/build.gradle`
   (`applicationId`) and the manifest's `namespace` must become something unique to
   you — you can't publish under this one alongside anyone else who used this same
   starter. Android Studio's *Refactor → Rename Package* handles this cleanly.
2. **Replace the app icon / branding** if you want something more custom than the
   included geometric mark (`ic_launcher_foreground.xml`, `ic_launcher_background` color).
3. **Set up a signing key.** Play Store requires a signed release build (`Build →
   Generate Signed Bundle/APK` in Android Studio, or Play App Signing).
4. **Add a privacy policy URL.** Play Console requires one for every app, and
   specifically for apps that request the camera permission — this isn't optional.
5. **Test on a real device end to end**: scanning, gallery import, generating +
   saving/sharing a code, and the permission-denied path.
6. Optional: flip `minifyEnabled` to `true` in `app/build.gradle` once you've verified
   the debug build works, to shrink the release APK (starter ProGuard rules are
   already in `proguard-rules.pro`).

## Notes on design choices

- **minSdk 29** (Android 10+) — this removes the need for legacy storage permissions
  entirely (scoped storage / MediaStore handles saving images), which meant one less
  hand-written, hard-to-test permission path. It also means the adaptive launcher icon
  didn't need generated PNG fallbacks. This covers the large majority of active Android
  devices; lower it in `app/build.gradle` if you specifically need Android 8/9 support,
  but note you'd then need to add the legacy `WRITE_EXTERNAL_STORAGE` + runtime
  permission handling back into `GeneratorFragment.saveToGallery()`.
- **ML Kit via Play Services** (`play-services-mlkit-barcode-scanning`) rather than the
  bundled model — smaller APK, auto-updating model, and it's what Google recommends for
  most apps. It does mean the device needs Google Play Services, which is already a
  given for anything installed from the Play Store.
- **Camera is optional at the manifest level** (`android:required="false"`), so the
  app still installs on camera-less devices and just leans on gallery scanning there.
