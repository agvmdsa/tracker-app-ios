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
  as the iOS app and the ESP32 firmware (deployed on Render), same `X-API-Key` header.
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

Same `domain`/`data`/`presentation` split throughout: `domain` is plain Kotlin (no Android or
third-party-library imports), testable on the host JVM; `data` holds the real Android/library
implementations of `domain` interfaces; `presentation` is Compose UI plus the `ViewModel`s that
keep each tab's state alive across configuration changes.

```
app/src/main/java/br/org/cesar/blueguide/
  domain/
    ble/          BLE advertising/scanning protocol (BlueGuideProtocol), shared with the iOS app
    entity/       DeviceId, BleDevice, DeviceSighting
    position/     Multilateration, PathLossModel, AnchorStore, FingerprintRecorder, sensor interfaces
    network/      PositioningReporting / FingerprintUploading interfaces
    uwb/          HandshakeChannel, UwbSessionController, UwbProximityTracker, UwbHandshakePayload
    ar/           ArPositionTracking
  data/
    ble/          AndroidBleAdvertiser, AndroidBleScanner, SharedPrefsLocalDeviceIdProvider,
                  BleUwbHandshakeChannel (GATT handshake for the UWB pairing)
    network/      PositioningApiClient (Ktor)
    positioning/  SharedPrefsAnchorRepository
    sensors/      AndroidOrientationReader, AndroidAltitudeReader, AndroidMotionActivityReader
    uwb/          AndroidUwbSessionController (androidx.core.uwb)
    ar/           AndroidArPositionTracker (ARCore)
  presentation/
    devicelist/   Positioning tab (Observer/Broadcast modes, the 2D map, anchor editor)
    fingerprint/  Fingerprint tab (FingerprintViewModel + FingerprintCollectorScreen)
    find/         Find tab (FindDeviceViewModel + FindDeviceScreen) — UWB "find this phone"
    ar/           AR tab (ArPositionViewModel + ArPositionScreen)
  MainActivity.kt NavHost wiring all four tabs together
```

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
- **The Fingerprint tab's `AnchorStore` doesn't see anchors added on the Positioning tab** until
  the app is relaunched or the tab is revisited (each tab keeps its own `AnchorStore` instance,
  refreshed from disk on entry — see `FingerprintViewModel.refreshAnchors`).
- **No AR anchor capture** — the iOS app's "Medir com AR" (walking to each anchor and marking it
  with ARKit) isn't replicated; only the position tracker itself (`ArPositionTracking`) is.
- **Only the Observer screen is in Portuguese**, like the iOS app; the tab names and the other
  tabs are still in English.
- Sensor conventions that don't match the iOS app byte-for-byte (device orientation axes,
  motion-activity confidence scale) are documented in each fingerprint recording's metadata
  JSON rather than converted — see `AndroidOrientationReader`/`MotionActivityReading`'s doc
  comments for exactly what differs and why.

The iOS app is the reference for behaviour; see the root README.
