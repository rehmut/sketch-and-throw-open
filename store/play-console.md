# Sketch & Throw: Play Console answers

Drafts for the Play Console forms. Check each against the form as it appears when you fill it in;
you are responsible for the final answers.

## Store listing

- **App name** (30 max): `Sketch & Throw – pottery` (24)
- **Short description** (80 max):
  `Trace a pot from a photo, measure it, see it in 3D and weigh the clay to throw.` (79)
- **Full description** (4000 max):

  > Photograph a pot, and Sketch & Throw turns it into a technical drawing you can throw from.
  >
  > • Traces the outline of a vessel from one side-on photo, on your phone
  > • Finds a mug's handle and models it with the body (switch it on when you need it)
  > • Enter one height and get every dimension: rim, foot, widest and narrowest diameter
  > • A full-width technical drawing with the wall and floor in section
  > • A rotating 3D model you can export as OBJ
  > • The ball of clay to centre: body, handle and trimming allowance, in grams
  > • A sketchbook of your designs, sorted into bowls, mugs, cups, vases, jars and plates
  > • Take the picture straight from the live camera
  >
  > Photos never leave your phone. No accounts, no ads.
  >
  > Best results: one pot, photographed straight-on at mid-height, on a plain background.

- **Category**: Tools (alternative: Art & Design)
- **Contact email**: FerdinandKirchner@gmx.de
- **Privacy policy**: https://rehmut.github.io/sketch-and-throw/privacy.html
- **Graphics**: `icon-512.png`, `feature-graphic.png`, and phone screenshots from `screenshots/`

## Data safety

**Does your app collect or share any of the required user data types?** Yes, through Google ML
Kit (see the policy). Photos are *not* collected: Play counts data as collected only when it
leaves the device, and photos never do.

| Data type | Collected | Shared | Purpose | Notes |
| --- | --- | --- | --- | --- |
| App info and performance → Diagnostics | Yes | No | Analytics | ML Kit performance metrics, error codes, API configuration |
| Device or other IDs | Yes | No | Analytics | ML Kit per-installation identifier (Firebase installations) |

- **Is all user data encrypted in transit?** Yes (ML Kit uses HTTPS).
- **Is collection required or optional?** Required: ML Kit sends it whenever object detection runs.
- **Processed ephemerally?** No.
- **Do you provide a way for users to request deletion?** The app has no server-side data of its
  own. Answer according to the form's current wording; Google handles ML Kit diagnostics.

Google's [SDK Index entry for ML Kit](https://play.google.com/sdks) and the
[ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure) are the
references to check these rows against.

## App content

- **Ads**: No ads.
- **App access**: All functionality is available without an account.
- **Target audience**: 13 and over (not designed for children, so the Families policy does not apply).
- **Content rating** questionnaire: category *Utility, Productivity, Communication, or Other*;
  no violence, sexual content, profanity, drugs, gambling, user-generated content sharing or
  location sharing.
- **Camera permission**: used for the live camera preview, object detection and taking a picture.

## Release

1. Create an upload keystore (Android Studio: *Build → Generate Signed App Bundle → Create new*),
   save it as `upload-keystore.jks` in the project root, and copy `keystore.properties.example`
   to `keystore.properties` with your passwords. Back up both somewhere safe; both stay out of git.
2. `./gradlew bundleRelease` → `app/build/outputs/bundle/release/app-release.aab`.
3. Enrol in **Play App Signing** when you create the app (the default).
4. Upload to a **closed testing** track. Personal accounts created after 13 November 2023 need at
   least 12 testers opted in for 14 days in a row before applying for production.
