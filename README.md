# TrackerApp (iOS)

iOS AirTag clone with two features: 1:1 tracking between two nearby iPhones (UWB), and indoor
positioning across multiple phones — anchors + an observer — designed to interoperate with a
companion Android app ([`btloc`](https://github.com/filipe-ms/btloc)).

## Build requirements

- **Xcode 15.0+** and an **iOS 17.0+** deployment target — the project uses the `Observation`
  framework (`@Observable`), which does not exist before Xcode 15's SDK. Building with an older
  Xcode fails with `error: no such module 'Observation'`.
- A physical device is strongly recommended for both features: UWB ranging and BLE
  advertising/scanning are not meaningfully testable in the Simulator.
- UWB tracking needs an iPhone with a U1/U2 chip (iPhone 11 or newer). Indoor positioning (BLE)
  works on any iPhone.

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
> a custom local name without renaming the whole device's Bluetooth adapter), and iOS already
> decodes manufacturer data fine when scanning. What's missing is the reverse: an iPhone *anchor*
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

## Project structure

```
TrackerApp/
  App/                     App entry point, scene lifecycle
  Core/
    ProximityTracker.swift / ProximityTracking.swift        Handshake + UWB glue, restarts on peer loss
    BLEHandshake.swift / HandshakeChannel.swift             CoreBluetooth token exchange
    UWBManager.swift / UWBManaging.swift                    NearbyInteraction (UWB)
    DeviceIdentity.swift                                    Persisted BeaconID
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
      PositioningReporting.swift / PositioningAPIClient.swift  Heatmap API client (placeholder)
      ARPositionTracker.swift / ARPositionTracking.swift       ARKit-based anchor position capture
  Models/                  HandshakeState, RangingState, TrackingLogEntry
  UI/
    MainView.swift / TrackingView.swift
    Positioning/
      PositioningModeView.swift / AnchorBroadcastView.swift
      ObserverView.swift / AnchorPositionEditor.swift
      ARAnchorCaptureView.swift
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
- `BeaconIDTests` — identity encoding/validation.

The `Core/Positioning` math (`Point2D`, `Anchor`, `Multilateration`, `PathLossModel`, `BeaconID`)
depends only on Foundation, so it can be typechecked and run outside Xcode with the `swift`
CLI if needed.

## Concurrency

The project builds with `SWIFT_STRICT_CONCURRENCY = complete`. Every `@Observable` manager
(`ProximityTracker`, `BLEHandshake`, `UWBManager`, `BeaconAdvertiser`, `BeaconScanner`,
`AnchorStore`, `PositioningEngine`, `ARPositionTracker`, `BluetoothPermissionMonitor`, and the
`PositioningPreviewFakes`) and their matching protocols (`ProximityTracking`, `HandshakeChannel`, `UWBManaging`,
`BeaconAdvertising`, `BeaconScanning`, `ARPositionTracking`) are `@MainActor` — this is true in
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
