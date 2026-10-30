# BlueGuide (Android)

The Android counterpart of `../ios`'s TrackerApp: same indoor-positioning system (BLE anchors +
an observer computing a 2D fix), the same fingerprinting dataset for the ML project, UWB
peer-to-peer ranging, and a camera-based position tracker — brought here from a previously
separate repository and built up to track the iOS app feature-for-feature where the underlying
platform APIs allow it.

Package `br.org.cesar.blueguide`, app name "BlueGuide".

## Stack

- **Kotlin + Jetpack Compose**, Material 3.
- **Navigation-Compose** — one route per tab (Positioning/Fingerprint/Find/AR), switched via
  `NavController.navigate` rather than a hand-rolled `when`.
- **Ktor client** (OkHttp engine) + `kotlinx.serialization` — talks to the same positioning API
  as the iOS app and the ESP32 firmware (`../api`), same `X-API-Key` header.
- **androidx.core.uwb** (Jetpack UWB) — peer-to-peer ranging ("Find" tab).
- **ARCore** — camera-based position tracking ("AR" tab).
- **Google Play Services** (`play-services-location`'s `ActivityRecognitionClient`) — motion
  activity for fingerprint recordings.

## Build requirements

- An Android SDK with `compileSdk` 37.2 and `platform-tools` installed (`sdkmanager
  "platforms;android-37.2" "build-tools;37.0.0"` — no Android Studio required, just the
  command-line tools). Point `local.properties`' `sdk.dir` at it.
- `minSdk` 32. Most of the app works in the emulator; BLE (all four tabs depend on it one way or
  another), UWB ranging and ARCore all need a physical device — the UWB chip and ARCore support
  in particular are far from universal (see "Known gaps" below).

```bash
make setup    # checks the SDK, adb and Java, writes local.properties
make test     # JVM unit tests, no device needed
make run      # builds, installs and opens the app on the phone connected over USB
make          # lists every target (lint, build, install, logs, uninstall, clean, devices)
```

From the repository root the same is `make android` (and `make android-setup`, `-build`, `-test`,
`-devices`, `-logs`); see the root README's "Commands".

## Structure

Pragmatic Clean Architecture, organised by feature: plain Kotlin wherever it can be, Android
only at the edges. The positioning and the recordings' format are pure Kotlin/JVM modules, so
the build itself keeps the framework out of them, and the same code runs on a computer:

```
core/model/        Point2D, DeviceId, Anchor, BleDevice, DeviceSighting, the sensor samples
core/positioning/  PathLossModel, RssiWindow, Multilateration, PositionEstimator,
                   PositioningEngine, PositioningReplay
core/dataset/      FingerprintRecord, FingerprintCsv, FingerprintRecordingMetadata
tools/replay/      command-line replay of fingerprint recordings (below)
app/               the Android app:

app/src/main/java/br/org/cesar/blueguide/
  core/
    ble/           BlueGuideProtocol, BleScanner/BleAdvertiser, SharedBleScanner;
                   android/ the BluetoothLeScanner/Advertiser implementations
    sensors/       DeviceMotionReading, AltitudeReading, MotionActivityReading;
                   android/ the SensorManager and Play Services readers
    data/          AnchorStore, CalibrationStore, PersistedValue; prefs/ SharedPreferences
    network/       PositioningReporting, FingerprintUploading; http/ PositioningApiClient (Ktor)
    system/        MonotonicClock, DeviceInfo
    ui/            theme, DecimalInput, BlePermission
  feature/         one package per tab, each with ui/, domain/ and data/ as it needs:
    positioning/   Observer/Broadcast, the map, the anchor editor, saved locations
    fingerprint/   FingerprintRecorder and the collector screen
    find/          UWB "find this phone"
    ar/            ARCore position tracking
  di/              Hilt modules (the composition root); PositioningModule picks the estimator
  MainActivity.kt  the NavHost with the four tabs
```

`core` never imports `feature`, and no feature imports another. The refactoring that led here,
and what is still to do, is in the repository root's `list.md`.

## Replaying recordings

`make replay ARGS="<recording.csv | folder>..."` runs fingerprint recordings (the `.csv` with its
`.json`, as exported from the Fingerprint tab) through the app's positioning engine and writes a
CSV with one row per second: where the app would have placed the phone (`x`, `y`), where it
stood (`true_x`, `true_y`), the error, and each anchor's median RSSI and distance. The summary of
each recording's error (mean, p95, maximum) goes to the standard error.

It uses the calibration kept in each recording (metadata `schema_version` 2). Recordings made
before that kept none and are replayed with the app's defaults, with a warning; the options set
another calibration to try on any recording:

```bash
make replay ARGS="--out replay.csv ~/Downloads/recordings"
make replay ARGS="--center-rssi -68 --touch-rssi -36 --no-speed-limit ~/Downloads/recordings"
make replay ARGS="--help"
```

`--engine`, repeated, runs each positioning engine on the same recordings and adds an `engine`
column and a summary per engine: how a new engine is compared with the app's
(`core/positioning/README.md`).

## Features and parity with the iOS app

| Feature | iOS | Android | Notes |
|---|---|---|---|
| BLE anchors/observer, 2D fix | `Multilateration`, `AnchorStore` | `Multilateration`, `AnchorStore` | Same weighted solver, 3 s RSSI median and constants; reads the ESP32 beacons' ID from the local name |
| "RSSI no centro" calibration, live map | `ObserverView`, `PositionMapView` | `DeviceListScreen`, `PositionMap` | Same sections, texts and colours (beacons orange, phone blue) |
| Saved locations | `SavedLocationStore`, `NavigateToLocationView` | `SavedLocationStore`, `NavigateToLocationScreen` | Arrow on the map's axes and distance |
| Backend upload (sightings/fixes) | `PositioningAPIClient` | `PositioningApiClient` | Same API, same `X-API-Key` |
| Fingerprint recording for ML | `FingerprintRecorder` | `FingerprintRecorder` | Same CSV/metadata schema, `platform` field distinguishes them |
| UWB "find this phone" | NearbyInteraction | `androidx.core.uwb` | **Android-to-Android only** — the two platforms' UWB stacks don't interoperate, so this can't range an iPhone and vice versa |
| Camera-based position tracking | ARKit (`ARPositionTracker`) | ARCore (`AndroidArPositionTracker`) | Same camera-space convention, so the same `x, -z` mapping applies unchanged |
| AR anchor capture (`ARAnchorCaptureView`) | ✅ | ❌ | Not ported yet: anchors are typed in by hand |

## Known gaps

- **UWB and ARCore are unverified on real hardware.** Both compile and have their pure logic
  (role/id comparison, OOB payload encoding, orchestration) covered by JVM unit tests, but
  actually ranging two phones or tracking a camera pose needs hardware (a UWB chip; a
  camera + ARCore-supported device) this development environment doesn't have.
- **No AR anchor capture** — the iOS app's "Medir com AR" (walking to each anchor and marking it
  with ARKit) isn't replicated; only the position tracker itself (`ArPositionTracking`) is.
- **Only the Observer screen is in Portuguese**, like the iOS app; the tab names and the other
  tabs are still in English.
- Sensor conventions that don't match the iOS app byte-for-byte (device orientation axes,
  motion-activity confidence scale) are documented in each fingerprint recording's metadata
  JSON rather than converted — see `AndroidOrientationReader`/`MotionActivityReading`'s doc
  comments for exactly what differs and why.

The iOS app is the reference for behaviour. See the repository root's `list.md` for the fuller,
living TODO list this summarizes.
