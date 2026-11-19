# Sound Tuner

A system-wide equalizer for Android, tuned for the earphones I actually use.
Pick a profile, fine-tune it with three sliders, and every app on the phone sounds better.

## Features

- **Works across apps**: hooks into the phone's global audio output, and into any music app that opens an equalizer session (Spotify's *Equalizer* button opens Sound Tuner directly)
- **Measured profiles**, built from published frequency-response measurements:
  - **Apple EarPods**: removes the shouty 2 kHz peak, adds bass and air
  - **Galaxy Buds Live · Hi-Fi**: deep sub-bass, clean mids, vocals up front, airy top
  - **Galaxy Buds Live · Pro**: Harman in-ear reference tuning with punchy bass
  - **Flat**: no correction, just your sliders
- **Bass / Clarity / Treble** sliders (0.5 dB steps) on top of any profile
- **Live response curve** that redraws as you tweak
- **Auto-switch**: picks the EarPods profile when USB-C EarPods are plugged in, and a Buds profile when Galaxy Buds connect
- **Clipping protection**: automatic headroom plus a limiter, so boosts never crackle
- Tiny and dependency-free: plain Android framework, no AndroidX, no ads, no network access

## How it works

Each profile is a list of parametric filters (peaking, low-shelf, high-shelf), the same format Equalizer APO and AutoEq use.
The app calculates the combined response with standard RBJ biquad formulas, samples it into 32 log-spaced bands, and loads them into
Android's built-in [`DynamicsProcessing`](https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing) effect
(pre-EQ stage, input gain for headroom, limiter). A foreground service keeps the effect alive while other apps play audio.

| File | What it does |
|---|---|
| `EqMath.kt` | Biquad filter maths and response curve |
| `Profiles.kt` | Built-in earphone profiles |
| `EqEngine.kt` | Converts filters to `DynamicsProcessing` bands and attaches to audio sessions |
| `EqService.kt` | Foreground service, session broadcasts, headphone auto-switch |
| `MainActivity.kt` / `CurveView.kt` | UI and curve graph |

## Build and install

Requirements: Android Studio (or JDK 17+ and the Android SDK, platform 35). The phone needs Android 9 or newer.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or open the project in Android Studio and press **Run**.

## Credits

Earphone measurement data and starting corrections come from [AutoEq](https://github.com/jaakkopasanen/AutoEq) by Jaakko Pasanen (MIT License),
using measurements by Super Review (EarPods) and HypetheSonics (Galaxy Buds Live). The profiles here are adjusted from those corrections for real-world fit and a less harsh top end.

## Author

Rajveer
