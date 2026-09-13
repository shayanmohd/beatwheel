# Beatwheel: ship-ready sheet

Final QA on 2026-09-13. Everything below was checked against the built binary, not the blueprint.

## Build

| Item | Value |
|---|---|
| Package | `com.mohdshayan.beatwheel` |
| Display name on device | Beatwheel |
| Play title | Strobe Tuner: Chromatic, Drone |
| versionCode / versionName | 1 / 1.0.0 |
| minSdk / targetSdk | 26 / 36 |
| AAB | `/Users/sherry/Documents/DEVPROJECTS/beatwheel/app/build/outputs/bundle/release/app-release.aab` |
| AAB size | 4,494,663 bytes (4.3 MB) |
| Signature | `jarsigner -verify`: jar verified, signed by CN=SocialSure Private Limited, OU=Mobile, O=SocialSure Private Limited, C=IN |
| Upload key | `beatwheel-upload.jks` with `keystore.properties`, both local only and not tracked by git. Back them up; enrol in Play App Signing when the console asks. |
| Unit tests | 67 passed |

## Permissions (merged manifest of the release build)

- `android.permission.RECORD_AUDIO`: the tuner listens to the instrument to measure its pitch. Audio is analysed in memory while the Tune or Stand screen is open and is never saved or sent.
- `com.mohdshayan.beatwheel.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: added automatically by AndroidX core for runtime-registered receivers. It is private to the app, is not a user-facing permission and needs no declaration.

No INTERNET, no AD_ID, no location, no storage permission. Files are read and written only through the system file picker and share sheet.

## App content answers

**Privacy policy:** https://shayanmohd.github.io/beatwheel/privacy-policy.html (returns 200, lists exactly RECORD_AUDIO).

**Ads:** No, the app contains no ads.

**App access:** All functionality is available without special access. No login.

**Advertising ID:** No, the app does not use the advertising ID.

**Government, financial, health, news:** No to each.

**Data safety:**
- Does your app collect or share any of the required user data types? **No.**
  - Microphone audio is processed only on the device, in memory, and never leaves it, so under Play's definition it is not collected.
  - Profiles, temperaments and sessions stay in app-private storage. Cloud backup is excluded in `data_extraction_rules.xml` and `backup_rules.xml`.
  - Files leave the device only when the user exports or shares one through the system picker or share sheet.
- Encrypted in transit: not applicable, nothing is transmitted.
- Data deletion request: not applicable, nothing is collected. Uninstalling removes all app data.
- Third-party libraries: AndroidX, Compose, Room, DataStore, kotlinx-serialization and Google Play In-App Review (`review-ktx` 2.0.2). None of them sends user data from this app. The review library only calls the Play Store app on the device.

**Content rating (IARC questionnaire):** Category: All Other App Types (a music utility, not a game, social or news app). Answer No to violence, fear, sexuality, language, controlled substances, crude humour, gambling and simulated gambling. Answer No to user-to-user interaction and to sharing user content with other users. Sharing a CSV through the Android share sheet is not in-app communication. Answer No to sharing the user's location and No to digital purchases. The price of a paid app is not an in-app purchase. Expected result: Everyone, PEGI 3, IARC 3+.

**Target audience:** 13-15, 16-17 and 18+. Do not pick any under-13 band. The app is a tool for musicians and is not designed for children. Picking under-13 would bring in the Families policy for no benefit.

**Category:** Music & Audio. Tags: tuner, music tools.

## Pricing and distribution

- Paid app, one-time price. No in-app purchases, no subscription.
- USD 3.99 (base). INR 149 in India. Set other emerging markets by hand to 25 to 50 percent of USD, as in BLUEPRINT.md section 10.
- A merchant account is required before a paid price can be set.
- Paid apps cannot later be made free and then paid again. Confirm the price before you publish.

## Listing

- Listing text: `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/listing.md`. App name 30/30, short description 79/80, full description 2402/4000, release notes 240/500.
- Every claim in the full description was checked against the code, and the UI ones on the installed release APK (details below).
- App icon: `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/play-icon-512.png` (512x512)
- Feature graphic: `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/play-feature-graphic-1024x500.png` (1024x500)
- Phone screenshots, 1080x1920, in this order:
  1. `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/screenshots/01-baroque-oboe-a415-disc-still.png`
  2. `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/screenshots/02-reed-drone-beats-per-second.png`
  3. `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/screenshots/03-trumpet-drift-session.png`
  4. `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/screenshots/04-a415-werckmeister-profile.png`
  5. `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/screenshots/05-clarinet-written-sounding-dark.png`
  6. `/Users/sherry/Documents/DEVPROJECTS/beatwheel/store/screenshots/06-stand-mode-harmonium-dark.png`
- Landing page: https://shayanmohd.github.io/beatwheel/ (returns 200)

## Claims checked on the release APK (emulator, API 36)

- Needle view is one tap from Tune, and Stand has a strobe/needle toggle.
- Drone offers Sine, Reed and Saw. The octave stops at 2 and 5. The drone starts and stops with the microphone denied.
- There are fifteen built-in profiles, named as in the listing. Oboe and Harmonium show "held tone". Copy creates an editable profile.
- Transposition presets are Concert, B flat, B flat octave lower, E flat, E flat octave lower, F and G.
- The temperament menu has Equal, Just, Pythagorean, Quarter-comma meantone, Werckmeister III and Kirnberger III. There is a tonic picker, and custom temperaments take twelve cents offsets.
- Reference: 466.1 is rejected with "Enter a value from 415.0 to 466.0", 415.3 is accepted, and the value saves per profile.
- Stand mode sets FLAG_KEEP_SCREEN_ON and hides the system bars. In landscape the disc and the note sit side by side.
- Settings has a sensitivity slider. Tune has a live input meter with a gate mark, and each profile has a noise gate.
- Export backup wrote `beatwheel-backup-2026-09-13.json` (format 1) through the file picker, and Import backup read it back. An identical profile is skipped by design.
- Denying the microphone shows "The drone, profiles and sessions still work", and they do.
- Code confirmed the rest: a 60 minute cap, a 100 ms reading loop, CSV export and share through the FileProvider, drone cancellation on the speaker only, the beat meter near unison, and one-decimal cents and Hz.
- The emulator microphone is silent, so live pitch readings could not be produced on the release build. The pitch path is the same main-source code the debug build ran for the screenshots. Only `SignalSources` differs, and the release version always uses the microphone.

## Before you submit

1. Upload the AAB above. Do not rebuild without re-running `build.sh`, `verify.sh` and `smoke.sh`.
2. The public GitHub repo contains BLUEPRINT.md, IDEA.json and DESIGN-NOTES.md, which hold pricing strategy and competitor research. If you do not want competitors to see them, remove them from the repo. Pages only needs `docs/`.
3. The Play title "Strobe Tuner: Chromatic, Drone" does not contain the name Beatwheel. The icon, feature graphic and landing page all say Beatwheel. This was a deliberate blueprint choice for search, and Play allows it, but reviewers sometimes question keyword-style titles. If Play flags it, use "Beatwheel: Strobe Tuner, Drone" (30 characters).
4. The screenshot readings come from the debug build's synthetic tone fed through the real DSP. The drift chart in shot 3 is a straight line because that tone drifts at a steady rate. Shots 3 and 5 show different status-bar clocks (7:31, 7:52) from the others (9:41). This is cosmetic and not a policy issue. Replace the shots with a real instrument recording later if you want.
5. `smoke.sh` gives a false failure on a fresh install when its first tap opens the system microphone dialog. Grant RECORD_AUDIO first (`adb shell pm grant com.mohdshayan.beatwheel android.permission.RECORD_AUDIO`) and it passes.
6. The listing's Play Store link on the landing page returns 404 until the app is published.
