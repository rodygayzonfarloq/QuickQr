# Publishing QuickQR to Google Play

Everything below is one-time setup, then each release is "push a tag" or "click Run workflow".

## 0. Change the package name first

`com.quickqr.app` is a placeholder. Once an app is on Play its package name can never change.
Edit `applicationId` in `app/build.gradle` (you don't need to rename the code folders; `namespace`
can stay as is). If you change it, also set a repository variable `PLAY_PACKAGE_NAME` to the same
value (Settings > Secrets and variables > Actions > Variables).

## 1. Create your upload keystore (once)

Pick ONE:

- **Have a computer with Java / Android Studio:** run `./scripts/generate-keystore.sh`
- **No computer:** in a **private** repo, Actions tab > **Generate upload keystore** > Run workflow.
  Download the `upload-keystore-PRIVATE` artifact (deleted after 1 day).

**Back up the `.jks` file and its password somewhere safe.** Never commit it. Never run the
generator a second time later: a new keystore is a different key.

With Play App Signing (on by default) Google holds the real app-signing key and yours is only the
*upload* key, so a lost upload key can be reset via Play support, but that takes days. Back it up.

## 2. Add GitHub secrets

Settings > Secrets and variables > Actions > New repository secret:

| Secret | Value |
|---|---|
| `KEYSTORE_BASE64` | the single line in `keystore-base64.txt` (script prints it too) |
| `KEYSTORE_PASSWORD` | keystore password |
| `KEY_ALIAS` | alias you chose (e.g. `quickqr-upload`) |
| `KEY_PASSWORD` | key password (same as keystore password for PKCS12) |
| `PLAY_SERVICE_ACCOUNT_JSON` | *optional*, see step 5 |

Then delete the keystore artifact / `secrets.txt` from wherever you downloaded it.

## 3. Build the signed AAB

- **Tag:** `git tag v1.0.0 && git push origin v1.0.0`. The version name comes from the tag.
- **Manual:** Actions > **Release (signed AAB for Google Play)** > Run workflow.

`versionCode` = the workflow run number, so it always increases (Play rejects reused codes).
Outputs (Artifacts section of the run, and the GitHub Release for tags):

- `QuickQR-<version>-<code>.aab`: upload this to Play
- `QuickQR-<version>-<code>.apk`: install on a phone and test first. Release builds use R8
  shrinking, which can break things debug builds don't.
- `mapping-*.txt`: deobfuscation map so Play crash reports are readable (the Play upload step
  sends it automatically; for manual uploads, attach it on the release page in Play Console).

## 4. First upload is manual

Play requires the first release of a new app to be uploaded by hand:

1. Play Console > Create app.
2. Test and release > Testing > Internal testing > Create release > upload the `.aab`.
3. Accept **Play App Signing** when asked.

## 5. Optional: automatic upload from GitHub

1. Play Console > Setup > API access > create/link a Google Cloud project, create a **service account**,
   grant it access to the app with release permissions.
2. In Google Cloud, create a JSON key for that service account and paste the whole file into the
   `PLAY_SERVICE_ACCOUNT_JSON` secret.
3. Run the Release workflow manually with **publish_to_play** ticked. Leave status on `draft`
   until the app has been published at least once.

## 6. Store readiness checklist

- Privacy policy URL (required because the app requests the camera permission)
- Data safety form: QuickQR scans and generates codes on-device, history stays in local storage
  and nothing is sent to a server. Answer accordingly.
- Content rating questionnaire, target audience, app category, contact email
- Listing: short description (80 chars), full description (4000), 512x512 icon,
  1024x500 feature graphic, at least 2 phone screenshots
- Permissions the app declares: `CAMERA` (live scanning), `VIBRATE` (scan feedback)
- Personal developer accounts created after 13 Nov 2023 must first run a **closed test with at
  least 12 testers opted in for 14 continuous days** before applying for production access.
  Organization accounts are exempt. Check Google's current Play Console Help page, as this rule
  has changed before.

## Building signed locally

Copy `keystore.properties.example` to `keystore.properties`, fill it in, then run
`gradle bundleRelease`. Output: `app/build/outputs/bundle/release/app-release.aab`.
