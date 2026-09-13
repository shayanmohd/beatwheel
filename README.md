# Beatwheel

Beatwheel is a strobe tuner for ensemble players: wind and string players, band directors and
early-music players who tune against a drone and want to see how far a held note drifts over a
rehearsal. A four-ring strobe disc turns when the note is off and stands still when it is in tune; a
sine, reed or saw drone keeps sounding while the tuner listens, with its own sound subtracted from the
microphone on the speaker and beats per second shown near unison. It covers A from 415 to 466 Hz,
six historical temperaments plus custom offsets on any tonic, B flat, E flat, F and G transposition,
fifteen instrument profiles, a full-screen Stand mode, and drift sessions of up to an hour exported as
CSV. Profiles, temperaments and sessions export to one JSON backup and import back. Paid once, no ads,
no account.

Everything runs on the device. The app declares no network permission and sends nothing anywhere.

## Build

Requires JDK 17 and the Android SDK with platform 36.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew bundleRelease       # needs keystore.properties, see below
```

`keystore.properties` and the `.jks` are not committed. Without them the release build stays
unsigned instead of failing:

```properties
storeFile=<slug>-upload.jks
storePassword=...
keyAlias=<slug>
keyPassword=...
```

## Layout

```
app/src/main/kotlin/com/mohdshayan/beatwheel/
  App.kt, MainActivity.kt   process and activity entry points
  di/                       ServiceLocator, the manual dependency container
  core/pitch/               YinDetector, PhaseDemodulator, PitchEstimator, NoiseGate, Decimator (no Android imports)
  core/music/               TuningMath, Temperaments, Transposition, NoteNames, BuiltInProfiles
  core/drone/               Wavetables, DroneCanceller, BeatMeter
  core/drift/               DriftSummary, SessionCsv, BackupModels, WeekGrouping, ReviewPolicy
  audio/                    MicSource (AudioRecord), DroneSynth (AudioTrack), AudioEngine (analysis thread)
  data/prefs/               DataStore settings (AppPrefs)
  data/db/                  Room database: profiles, custom temperaments, drift sessions and readings
  data/repo/                profiles, tuner controller, drift recorder, backup import and export
  ui/theme/                 colour, type, shape and motion tokens, AppTheme
  ui/nav/                   AppNav and the type-safe routes
  ui/tuner, ui/stand        Tune screen with the strobe disc and drone strip, full-screen Stand mode
  ui/sessions, ui/session   session list grouped by week, session detail with the drift chart
  ui/profiles               profile list, profile editor, temperament editor
  ui/settings               display, theme, sensitivity, backup, local counts, licences
  ui/components/            StrobeDisc, NeedleGauge, DriftChart, LevelMeter and shared controls
  review/                   Play in-app review prompt after the third successful use
app/src/debug/              SyntheticSignalSource for emulator runs (adb shell setprop debug.beatwheel.synth 415.0)
app/src/test/               JVM unit tests for the DSP, music and drift logic
store/                      Play listing copy, icon, feature graphic, screenshots
docs/                       landing page (index.html, fonts/, shots/), privacy policy and font licences
```

The tests run on the JVM with synthetic signals: `./gradlew testDebugUnitTest`.

## Bundled assets and licences

- Barlow Semi Condensed (Medium, SemiBold) by The Barlow Project Authors, SIL Open Font License 1.1, `docs/OFL-BarlowSemiCondensed.txt`.
- Atkinson Hyperlegible Next (variable) by The Atkinson Hyperlegible Next Project Authors, SIL Open Font License 1.1, `docs/OFL-AtkinsonHyperlegibleNext.txt`.
- AndroidX, Jetpack Compose, Material Components for Compose, Room, DataStore, kotlinx.coroutines,
  kotlinx.serialization and Play In-App Review: Apache License 2.0.
- No audio samples or models are bundled; drone wavetables are generated in code.

## Licence

Copyright SocialSure Private Limited.
