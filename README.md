# TrackerApp

iOS AirTag clone with two features: 1:1 tracking between two nearby iPhones (UWB), and indoor
positioning across multiple phones — anchors + an observer — designed to interoperate with a
companion Android app (`applications/android`, package `br.org.cesar.blueguide`, app name
"BlueGuide"). It's also the data source for an indoor-positioning (fingerprinting)
machine-learning project, built on the same BLE anchors/observer infrastructure.

## Repository layout

A monorepo, following the structure the Sistemas Embarcados course's project spec requires:

```
applications/
  ios/        This README's app (Xcode project) — see "Project structure" below
  android/    BlueGuide, the Android counterpart (Gradle/Kotlin, see applications/android/README.md)
  api/        FastAPI ingestion/positioning API + Postgres + Grafana (applications/api/README.md)
  web/        Next.js live floor-plan map, reads the API (applications/web/README.md)
esp32-esp8266/  ESP32 beacon/collector firmware, PlatformIO (esp32-esp8266/USAGE.md)
docs/           Technical report (CSBC/SBC template, LaTeX + PDF) — docs/README.md
schematics/     Circuit diagrams (ESP32 node: PDF/PNG + Wokwi diagram.json)
scripts/        Dev utilities (e.g. advertising a Mac as a BLE anchor for testing)
```

`applications/api` and `applications/web` were originally a separate repository
(`api_tracker_app`); their own `README.md`s still describe them as such in places — read them from
inside `applications/api/`/`applications/web/`, not as if they were the repository root.
`applications/android` was originally its own repository too (previously linked as
[`btloc`](https://github.com/filipe-ms/btloc)); it's folded in here the same way, without its own
git history.

## Build requirements

- **Xcode 15.0+** and an **iOS 17.0+** deployment target — the project uses the `Observation`
  framework (`@Observable`), which does not exist before Xcode 15's SDK. Building with an older
  Xcode fails with `error: no such module 'Observation'`.
- A physical device is strongly recommended for both features: UWB ranging and BLE
  advertising/scanning are not meaningfully testable in the Simulator.
- UWB tracking needs an iPhone with a U1/U2 chip (iPhone 11 or newer). Indoor positioning (BLE)
  works on any iPhone.
- **Without a paid developer account**, sign with a free *Personal Team*: in Xcode, sign in with
  an Apple ID (Settings → Accounts), then in the TrackerApp target's Signing & Capabilities pick
  that Personal Team (the project ships with someone else's `DEVELOPMENT_TEAM`). If Xcode says the
  bundle ID is unavailable, change `com.airtagclone.TrackerApp` to something unique. On the iPhone,
  turn on Developer Mode (Settings → Privacy & Security) and trust the certificate (Settings →
  General → VPN & Device Management). The app needs no entitlements, so nothing is lost. The
  install **expires after 7 days**: run it from Xcode again to renew. Recordings survive a reinstall
  over the existing app, but not deleting the app, so export them regularly.

## Architecture

The app follows **MV (Model-View)**, not MVVM or Clean Architecture: `ProximityTracker`,
`BLEHandshake`, `UWBManager`, `BeaconAdvertiser`, and `BeaconScanner` are `@Observable` classes that
double as both the data model and the business logic. Views read them directly — there is no
separate ViewModel layer.

Each manager has a matching protocol (`ProximityTracking`, `HandshakeChannel`, `UWBManaging`,
`BeaconAdvertising`, `BeaconScanning`) so views depend on the interface, not the concrete
networking implementation.
Because Swift's `@EnvironmentObject`/`@StateObject`/`@ObservedObject` cannot bind to a protocol
type (only concrete types can satisfy `ObservableObject`'s associated type), these are injected
through a custom `EnvironmentKey` per manager (`EnvironmentValues+Managers.swift`,
`EnvironmentValues+Positioning.swift`) instead — that's what makes the protocols genuinely
swappable (e.g. for a fake in tests) rather than documentation-only.

## Features

### 1. Peer-to-peer tracking (AirTag-style)

Tap **Start** in `TrackingView`. `ProximityTracker` glues two pieces together:

- `BLEHandshake` (CoreBluetooth, behind the `HandshakeChannel` protocol) finds a nearby phone
  running this app and swaps NearbyInteraction discovery tokens with it over a GATT characteristic.
  Every phone is both a peripheral (serving its token) and a central (scanning for the service);
  each advertises a random ID as its local name and only the phone with the smaller ID opens the
  connection, so the two never connect to each other at once. There is no Wi-Fi, local-network
  permission or pairing UI involved. It pairs with whichever nearby phone it finds first, so keep
  other devices running the app out of range while testing.
- `UWBManager` (NearbyInteraction) uses the peer's token to range it, giving live distance,
  azimuth and elevation.

`UWBManager` handles the quirks of newer iPhones: it starts with camera assistance right away on
hardware that can't give a direction otherwise, and on everything else falls back to it when
distance arrives but no angle does within 5 seconds. When the peer times out it retries with the
same configuration, and when the peer ends its session `ProximityTracker` starts the handshake
over, so the pairing recovers on its own.

The azimuth comes from NearbyInteraction's direction vector as `atan2(x, -z)` (NI space: +x right,
+y up, -z out of the back of the phone), so 0 is straight ahead and positive is to the right. On
camera-assisted-only models (iPhone 14 Pro and later) `direction` stays nil and the angle arrives
as `horizontalAngle` instead, which `UWBManager.azimuth` falls back to.

### 2. Indoor positioning (BLE multilateration)

Lives under `Core/Positioning` and `UI/Positioning`. Two roles, chosen manually per device in
`PositioningModeView`:

- **Anchor** (`AnchorBroadcastView`) — a phone that stays put and just advertises its identity
  over BLE.
- **Observer** (`ObserverView`) — a phone that scans for nearby anchors, is told each anchor's
  physical position, and computes its own position from their signal strength.

An anchor's position can be entered two ways from `ObserverView`: typing x/y in metres by hand
(`AnchorPositionEditor`), or walking to it and tapping "Marcar posição aqui" in the AR capture
screen (`ARAnchorCaptureView`, backed by `ARPositionTracker`). The AR path exists because manually
measuring and typing coordinates for every anchor is real, repeated friction — `ARPositionTracker`
runs an `ARWorldTrackingConfiguration` session and reads the camera's translation since the
session started as a live (x, y) in metres, so standing where an anchor physically is and tapping
a button *is* the measurement, no tape measure or hand-drawn floor plan required. One AR session
stays open while placing multiple anchors, so they all share the same origin/frame — starting a
fresh session per anchor would reset the origin each time and make the anchors' positions
inconsistent with each other.

> ⚠️ **No Android equivalent yet, and it's more than a small fix.** ARKit's Android counterpart is
> **ARCore** — conceptually the same technique (camera + motion sensors give a relative position
> as the phone moves) — but `btloc` doesn't have anywhere to put that data: its `AnchorLayout.kt`
> is a hard-coded equilateral triangle, computed, not entered. Matching this app's flexible
> N-anchor model on Android needs two new things there, not one: a manual/AR anchor-position entry
> screen (this app's `AnchorPositionEditor`/`ARAnchorCaptureView` have no counterpart at all today)
> and the ARCore integration itself. Unlike the BLE identity fix below, this isn't a small patch to
> an existing file.

**Wire protocol** (`BeaconProtocol`) — a fixed service UUID marks a BLE advertisement as this
app's, and a 4-byte `BeaconID` (8 hex characters) identifies the specific install, surviving BLE
address rotation. The ID is carried in the advertisement's **local name**, not manufacturer data:
iOS's `CBPeripheralManager` cannot set manufacturer data when advertising (Apple only exposes
`CBAdvertisementDataLocalNameKey` and `CBAdvertisementDataServiceUUIDsKey` to
`startAdvertising(_:)`), so local name is the only field both iOS and Android can use when
*broadcasting* — both can already read manufacturer data fine when *scanning*.

> ⚠️ **Cross-platform interop is not finished — one small change needed on the `btloc` side.**
> An iPhone can already be an *observer* of an Android anchor today: Android's advertiser keeps
> using manufacturer data (its only good option — Android's `AdvertiseData` API has no way to set
> a custom local name without renaming the whole device's Bluetooth adapter), and
> `BeaconProtocol.beaconID(in:)` reads the ID from manufacturer data (company ID `0xFFFF`, then the
> 4 ID bytes) before falling back to the local name. The manufacturer data has to win: Android puts
> its device name in the scan response, which iOS reports in the same local-name field. What's
> missing is the reverse: an iPhone *anchor*
> can only put its ID in local name (the one field `CBPeripheralManager` allows), so
> `AndroidBleScanner.kt` needs a fallback — when manufacturer data for `MANUFACTURER_ID` isn't
> present on a matched advertisement, try parsing `scanRecord.deviceName` as an 8-hex-character
> `DeviceId` instead. Android's advertiser itself needs no change. This is a change to make in the
> `btloc` repo, not here.

**Distance/position math** (`Core/Positioning`, ported and generalized from `btloc`'s Kotlin):

- `PathLossModel` — converts RSSI to distance via the log-distance path loss model, with
  exponential smoothing in dB. `referenceRSSI`/`pathLossExponent` are nominal defaults and need
  calibrating against real hardware/environment.
- `Multilateration` — unlike `btloc`'s fixed 3-anchor closed-form trilateration, this solves for
  **any N ≥ 3 anchors** via least squares (the two are mathematically identical when N = 3).
  Returns `nil` for fewer than 3 anchors or for anchors placed collinearly.
- `AnchorStore` — anchor positions are entered manually per deployment (not a fixed layout) and
  persisted in `UserDefaults`.
- `PositioningEngine` — ties a `BeaconScanning` instance and an `AnchorStore` together, smooths
  RSSI, recomputes the fix every second, and reports each round of readings.

**Heatmap API** (`PositioningReporting` / `PositioningAPIClient`) — reports every sighting
(observer, anchor, RSSI, distance, timestamp) to a backend for cross-device aggregation (peak
hours, a floor-plan heatmap, etc.).

> ⚠️ **Placeholder.** No real API spec exists yet. `PositioningAPIClient`'s base URL, endpoint
> path, and payload shape are all provisional guesses — it's the only file that should need to
> change once the real spec is available. Planned backend shape: Postgres for storage, an
> ingestion endpoint this client posts to, Grafana reading Postgres directly for time-based
> metrics (peak hours), and a small custom web frontend for the floor-plan heatmap itself (which
> Grafana's panels don't natively render).

### 3. Fingerprinting data collection

**Indoor → "Coletar dados para fingerprinting"** (`FingerprintCollectorView`) records a labelled
RSSI dataset for training a fingerprinting model later. It is the iPhone counterpart of the ESP32
collector's `label` / `rec start` / `rec stop` (`esp32-esp8266/USAGE.md`, chapter 6), except that a
recording lasts a set time: type the session, the point and its x/y in metres, pick the duration
(1–60 minutes), tap **Gravar por N min** and stay at the point until it stops on its own. Every
point then gets the same amount of data. **Cancelar e descartar** stops early and deletes the
file, since a shorter recording would unbalance the dataset; leaving the screen is blocked while
recording for the same reason.

When a recording ends, the screen shows how many rows each beacon produced and the rate per
second, like the ESP32's `S` lines. The app writes a row for **every advertisement CoreBluetooth
delivers**, with no sampling or deduplication. CoreBluetooth doesn't deliver every advertisement
the beacons send, though: iOS picks its own scan duty cycle (there is no API for the scan
interval/window the ESP32 collector sets), and shares the radio with Wi-Fi and other Bluetooth
traffic. The ESP32 beacons advertise 10 times a second at the default 100 ms interval, so the rate
shows what fraction reached the app.

`FingerprintRecorder` writes one CSV per recording to
`Documents/Fingerprints/<yyyyMMdd-HHmmss>_<session>_<point>.csv`, one row per advertisement
received (`FingerprintRecord`):

| Column | Content |
|---|---|
| `t_ms` | monotonic milliseconds when the advertisement arrived (`CLOCK_MONOTONIC`: never jumps, keeps counting in sleep, restarts on reboot). Order and window the rows by this one |
| `timestamp_ms` | Unix time the advertisement arrived, in milliseconds. Only for lining up recordings from different devices: the wall clock can jump |
| `node` | this phone's install ID (`DeviceIdentity.beaconID`) |
| `model` | hardware model identifier, e.g. `"iPhone15,2"` — quoted, since it contains a comma |
| `system_version` | e.g. `"iOS 26.0.1"` |
| `beacon` | the 8-hex beacon ID, from the advertisement's manufacturer data or local name |
| `rssi` | dBm, as CoreBluetooth reported it |
| `accel_x`, `accel_y`, `accel_z` | latest accelerometer reading, in g (empty when there is none) |
| `session`, `point`, `x`, `y` | the label |

When a recording runs to the end, a **`.json` with the same name** is written next to it
(`FingerprintRecordingMetadata`): the recording's start and end on both clocks
(`started_t_ms`/`ended_t_ms`, `started_timestamp_ms`/`ended_timestamp_ms`), the planned duration,
the rows per beacon, the label, the device, how the scan was done, the acceleration's units, and
**`beacons`: where each beacon was** (`id`, `label`, `x`, `y`, `height_m`, the last one `null` when
not entered).

- **The window is what makes absences usable.** A beacon that iOS never delivered leaves no row,
  and the number of advertisements per beacon per second varies on its own (scan duty cycle, radio
  shared with Wi-Fi, body in the way). Without the window, "not heard during this recording" can't
  be told apart from "not part of it", and per-window counts can't be computed for the start and
  end of a recording.
- **`beacons` is a copy of the anchors (`AnchorStore`) taken when the recording starts**, not a
  reference: moving a beacon and correcting its position later doesn't rewrite older recordings,
  so each file carries the layout it was recorded with. Fingerprinting models (kNN, random forest)
  in one fixed room don't need it; models built on distances (path loss, per-beacon or per-phone
  calibration) and checking the data (RSSI falling with distance, swapped IDs, a moved beacon) do.
- **A CSV without its JSON is incomplete**: the app was killed before the time was up. The list
  marks it "Incompleta — descarte"; leave it out of the dataset.
- Sharing a recording, or "Exportar todas", sends the CSV and the JSON together.

**Beacon positions.** The collector's **Beacons** section lists every beacon that is heard or has a
position. Tap one (outside a recording) to enter or correct its x/y and, optionally, its height
above the floor (`AnchorPositionEditor`, the same one the observer uses). Two warnings, also
summarised next to the record button, catch a layout that would go out incomplete:

- **"Sem posição cadastrada"** — heard, but with no position;
- **"Não ouvido nos últimos segundos"** — has a position, but nothing heard in the last 5 s (off,
  out of battery, or a wrong ID).

Positions have to use **the same origin and axes as the points' x/y**. A room corner works well as
(0, 0), with x and y along two walls, measured with a tape: in a 5×5 m room with one beacon at the
middle of each wall, 0.75 m in, they are (2.5, 0.75), (4.25, 2.5), (2.5, 4.25) and (0.75, 2.5). The
AR capture also works, but its origin and axes are wherever the phone was and faced when the AR
session started, so start it at that corner facing along the y wall.

**ESP32 beacons.** Give each board a fixed, readable ID of 8 hex characters (e.g. `id BEAC0001` …
`BEAC0004`, see `esp32-esp8266/USAGE.md`) and a sticker with it. The ID names the physical board, not
the spot: a replacement board gets a new ID, since boards differ slightly in antenna and power. The
phone can't read a beacon's transmit power or advertising interval from its advertisement, so they
aren't in the JSON: keep them the same on every board (defaults `0 dBm` / `100 ms`, reported by
each board at boot in its `E,…,advertising,…` line).

**Static, then moving.** Recordings are taken standing at one labelled point. Collecting while
walking needs more than this format has today — timestamped waypoints to interpolate the position
between, and the full accelerometer stream rather than one sample per row — and is a planned
extension, not something to fake by recording while moving.

- **Mergeable with the ESP32 data.** Column names match the ESP32's `R` records wherever they
  overlap, and `FingerprintLabel` applies the same rules as the ESP32's `label` command (session
  and point are 1–16 characters of `[A-Za-z0-9_-]`), so the two datasets can be concatenated.
- **Accelerometer.** `AccelerometerReader` (`CMMotionManager`, no permission prompt needed)
  samples at 50 Hz and each row gets the most recent sample. The raw reading includes gravity, so
  it tells how the phone was being held, which affects RSSI through antenna orientation and body
  shadowing.
- **Model and system version are on every row**, so files from different phones can be
  concatenated and still tell apart radios that read RSSI differently.
- **Getting the files off the phone:** share one from the list (AirDrop), use "Exportar todas",
  or open Files → On My iPhone → TrackerApp → Fingerprints (`UIFileSharingEnabled`), or Finder
  over USB.
- **Keep the app in the foreground.** The screen doesn't auto-lock while recording, because iOS
  stops BLE scanning in the background (the app has no `bluetooth-central` background mode).
- `BeaconScanner` drops advertisements with RSSI 127, which is CoreBluetooth's "unavailable" value.

#### Collecting the same data on Android

The Android app is to be regenerated after this one, and a model trained on data from both only
holds if both collect the same way. The iOS collector is the reference; an Android collector has
to produce the same CSV and JSON, with these platform differences handled:

| What | iOS (reference) | Android equivalent |
|---|---|---|
| Scan | `scanForPeripherals(withServices: [serviceUUID], options: [AllowDuplicates: true])`, foreground, screen kept on | `BluetoothLeScanner.startScan` with a `ScanFilter` on the service UUID, `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, report delay 0; foreground, screen kept on |
| One row per | advertisement delivered (no dedup, no averaging) | `onScanResult` call (same) |
| Beacon ID | manufacturer data (company `0xFFFF` + 4 bytes), else local name if it is 8 hex | the same order: `getManufacturerSpecificData(0xFFFF)`, else `scanRecord.deviceName` |
| `t_ms` | `CLOCK_MONOTONIC` ms, read in the delegate callback | `ScanResult.getTimestampNanos() / 1_000_000` (same clock as `elapsedRealtime()`, taken at reception) |
| `timestamp_ms` | Unix ms (`Date()`) | `System.currentTimeMillis()` |
| `model` / `system_version` | `"iPhone15,2"` / `"iOS 26.0.1"` | `"<Build.MANUFACTURER> <Build.MODEL>"` / `"Android <Build.VERSION.RELEASE>"` |
| Unavailable RSSI | 127, dropped | not expected; drop 127 anyway |
| `accel_x/y/z` | CoreMotion, **g**, gravity included; lying still face up reads `z = -1` | `TYPE_ACCELEROMETER` is in **m/s² with the opposite sign** (face up reads `z = +9.81`): write `-value / 9.80665`. Same device axes, 50 Hz, latest sample per row |
| JSON `platform` / `scan_api` | `"iOS"` / CoreBluetooth | `"Android"` / a description of the scan settings above |
| JSON `beacons` | copy of `AnchorStore.anchors` at the start of the recording, sorted by ID | the same structure, from the Android app's own anchor list |

The label rules (`FingerprintLabel`: 1–16 characters of `[A-Za-z0-9_-]`), the fixed-length
recordings, discarding cancelled ones, writing the JSON only on completion and the beacon-position
warnings are part of the format too, not iOS details.

## Project structure

All paths below are relative to `applications/ios/`:

```
TrackerApp/
  App/                     App entry point, scene lifecycle
  Core/
    ProximityTracker.swift / ProximityTracking.swift        Handshake + UWB glue, restarts on peer loss
    BLEHandshake.swift / HandshakeChannel.swift             CoreBluetooth token exchange
    UWBManager.swift / UWBManaging.swift                    NearbyInteraction (UWB)
    DeviceIdentity.swift                                    Persisted BeaconID, model, system version
    EnvironmentValues+Managers.swift                        Protocol-based DI for the above
    EnvironmentValues+Positioning.swift                     Protocol-based DI for Positioning
    Positioning/
      BeaconID.swift / BeaconProtocol.swift / BeaconSighting.swift
      BeaconAdvertiser.swift / BeaconAdvertising.swift       CBPeripheralManager (anchor role)
      BeaconScanner.swift / BeaconScanning.swift             CBCentralManager (observer role)
      Point2D.swift / Anchor.swift / PathLossModel.swift
      Multilateration.swift                                 N-anchor least-squares solver
      AnchorStore.swift                                      User-configured anchor positions
      PositioningEngine.swift                                Sightings + anchors -> fix
      PositioningReporting.swift / PositioningAPIClient.swift  Positioning API client (Render)
      FingerprintUploading.swift                              Uploads fingerprint rows for ML training
      ARPositionTracker.swift / ARPositionTracking.swift       ARKit-based anchor position capture
      DeviceMotionReader.swift / DeviceMotionReading.swift     CoreMotion orientation (roll/pitch/yaw)
      AltitudeReader.swift / AltitudeReading.swift             CoreMotion relative altitude
      MotionActivityReader.swift / MotionActivityReading.swift CoreMotion activity (stationary/walking/...)
      FingerprintRecord.swift / FingerprintRecorder.swift      Labelled RSSI dataset -> CSV + API upload
  Models/                  HandshakeState, RangingState, TrackingLogEntry
  UI/
    MainView.swift / TrackingView.swift
    Positioning/
      PositioningModeView.swift / AnchorBroadcastView.swift
      ObserverView.swift / AnchorPositionEditor.swift
      ARAnchorCaptureView.swift / FingerprintCollectorView.swift
  Tests/TrackerAppTests/   XCTest — see below
```

## Testing

Run via `Product > Test` in Xcode, or `xcodebuild test` from an environment with Xcode 15+.

- `ProximityTrackerTests` — the handshake → ranging flow against fake `UWBManaging` and
  `HandshakeChannel` implementations: start/stop, peer token handling, restart when the peer ends
  its session, error surfacing, background/foreground.
- `BLEHandshakeTests` — which side initiates the connection, oversized tokens. The CoreBluetooth
  exchange itself needs two real phones.
- `UWBManagerTests` — ranging state updates, signal loss handling, azimuth/elevation math.
- `PositioningTests` — `Multilateration` (exact/inconsistent/degenerate ranges, N > 3 anchors)
  and `PathLossModel`, mirroring `btloc`'s `PositioningTest.kt` plus cases specific to this app's
  N-anchor (not fixed-triangle) design.
- `BeaconIDTests` — identity encoding/validation, and reading the ID from an advertisement (local
  name, Android's manufacturer data, which one wins).
- `FingerprintRecorderTests` — label validation, the CSV row format (quoting, empty
  acceleration), and the recorder against fake `BeaconScanning` and `AccelerometerReading`
  implementations: only advertisements received while recording are written, a recording stops on
  its own when its time is up, cancelling discards the file, per-beacon counts and rates, the
  metadata JSON (window on both clocks, counts, the beacon positions copied when the recording
  started), the beacon-layout warnings, anchors saved before `height` existed still loading, how a
  CSV without its JSON is reported, file listing
  and deletion.

The `Core/Positioning` math (`Point2D`, `Anchor`, `Multilateration`, `PathLossModel`, `BeaconID`)
depends only on Foundation, so it can be typechecked and run outside Xcode with the `swift`
CLI if needed.

## Concurrency

The project builds with `SWIFT_STRICT_CONCURRENCY = complete`. Every `@Observable` manager
(`ProximityTracker`, `BLEHandshake`, `UWBManager`, `BeaconAdvertiser`, `BeaconScanner`,
`AnchorStore`, `PositioningEngine`, `ARPositionTracker`, `AccelerometerReader`,
`FingerprintRecorder`, `BluetoothPermissionMonitor`, and the `PositioningPreviewFakes`) and their
matching protocols (`ProximityTracking`, `HandshakeChannel`, `UWBManaging`, `BeaconAdvertising`,
`BeaconScanning`, `ARPositionTracking`, `AccelerometerReading`) are `@MainActor` — this is true in
practice already (every delegate is registered with `queue: nil`, i.e. the main queue, and every
`Timer` is scheduled from a main-actor context), so this just makes the compiler aware of it.
Designated initializers referenced from an `EnvironmentKey`'s `defaultValue` (a non-isolated
context) are marked `nonisolated`, along with any `private static` helper they call during `init`
(e.g. `AnchorStore.load()`). `PositioningReport`, `PositioningReportingError`, and
`PositioningReporting`/`PositioningAPIClient` are `Sendable` instead, since reporting genuinely
runs off the main actor inside `PositioningEngine.refresh()`'s `Task { }`.

This was verified for real, not just reasoned about: `swiftc -typecheck -strict-concurrency=complete`
against the iPhoneSimulator SDK comes back clean for every file that doesn't need `Observation`
(all the protocols, `PositioningReporting`/`PositioningAPIClient`, `PositioningPreviewFakes`).

> ⚠️ **Known gap: delegate methods still warn under strict concurrency.** `BLEHandshake`,
> `UWBManager`, `BeaconAdvertiser`, `BeaconScanner`, `BluetoothPermissionMonitor`, and
> `ARPositionTracker` implement delegate protocols from
> NearbyInteraction/CoreBluetooth/ARKit whose requirements aren't
> actor-isolated. Confirmed with a standalone repro against the iOS 17 SDK: a `@MainActor` type's
> delegate method satisfying one of these requirements produces
> `warning: main actor-isolated instance method ... cannot be used to satisfy nonisolated protocol
> requirement` — a **warning, not an error** (this project uses Swift 5 language mode, not Swift 6
> mode, so this doesn't fail the build). The documented fix is marking each delegate method
> `nonisolated` and wrapping its body in `MainActor.assumeIsolated { ... }` — but that API is
> Swift 5.9+, and the only toolchain available while writing this (Xcode 14.2) is Swift 5.7, so it
> could not be verified and was deliberately left alone rather than guessed at. Apply it (and
> confirm it compiles) once building on Xcode 15+.

## Known gaps / next steps

- The app builds and the unit tests pass on Xcode 26 / the iPhone 17 simulator, but UWB ranging,
  the BLE handshake and the positioning flow have not been run on real devices in this version —
  the Simulator can't exercise any of them. The BLE handshake and the camera-assistance fallback
  were carried over from a standalone prototype that did range between two iPhones; the glue
  around them (`ProximityTracker`, background/foreground handling) is new.
- The fingerprinting collector is covered by unit tests only. The real scan rate, the
  accelerometer and getting the files off the phone have not been tried on a device yet.
- Android-side BLE protocol change (local name fallback in `AndroidBleScanner.kt`) needed for
  iPhone anchors to be visible to Android observers.
- Android-side ARCore work (anchor-position entry screen + capture, neither of which exists in
  `btloc` today) needed for feature parity with this app's AR-assisted anchor placement.
- `PathLossModel` constants need calibrating with real devices in the target environment.
- Backend (Postgres + ingestion API + Grafana + heatmap web frontend) does not exist yet —
  `PositioningAPIClient` has nothing real to talk to.
- `ObserverView` shows the computed fix as plain x/y text; no visual (canvas/map) rendering yet.
- `ARPositionTracker`'s ARKit calls were verified by typechecking against a real iOS SDK
  (`iphonesimulator`, target iOS 16), unlike the rest of this app which only got as far as
  `swiftc -parse` — still not run on-device.
