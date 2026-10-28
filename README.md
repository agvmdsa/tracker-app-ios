# TrackerApp

Indoor positioning with fixed ESP32 BLE beacons: an iPhone or Android phone hears the beacons,
turns each one's RSSI into a distance and places itself on a map. The iOS app also does 1:1
tracking between two nearby iPhones over UWB, and both apps record labelled RSSI datasets for
fingerprinting.

## Repository layout

Each folder is an independent project; the shared contract is the BLE protocol (service UUID
`fc979812-36dd-4722-9316-d2a7908888dd`, an 8-hex beacon ID in the local name or in manufacturer
data `0xFFFF`).

| Folder | What | Docs |
|---|---|---|
| `ios/` | TrackerApp, the iOS app (Swift, SwiftUI) | this README, from "iOS app" on |
| `android/` | BlueGuide, the Android app (Kotlin, Compose) | [`android/README.md`](android/README.md) |
| `embedded/` | ESP32 firmware: beacon and collector roles (PlatformIO, Arduino-ESP32) | [`embedded/USAGE.md`](embedded/USAGE.md) |
| `scripts/` | Helper scripts (`mac-anchor.swift`: a Mac as a test beacon) | |

## Commands

Every Makefile lists its targets when run with no target (`make`, `make -C android`,
`make -C ios`, `make -C embedded`).

### From the repository root

| Command | What it does |
|---|---|
| `make android` | Builds the Android app, installs it on the phone connected over USB and opens it |
| `make android-setup` | Checks the Android SDK and Java (once per computer) |
| `make android-build` | Builds the debug APK only, no phone needed |
| `make android-test` | Android unit tests on the JVM, no phone needed |
| `make android-devices` | Lists connected phones and whether they authorised this computer |
| `make android-logs` | Shows the log of the app running on the phone (Ctrl+C quits) |
| `make ios` | Builds the iOS app, installs it on the iPhone connected to the Mac and opens it |
| `make ios-build` | Builds the signed iOS app only |
| `make ios-test` | iOS unit tests on the simulator |
| `make ios-devices` | Lists the iPhones paired with this Mac and whether they are reachable |
| `make ios-sim` | Builds, installs and opens the iOS app on the simulator |
| `make ios-open` | Opens the iOS project in Xcode |

With more than one phone connected, pick one with `DEVICE=`: the serial from
`make android-devices` (`make android DEVICE=R58N12ABCDE`) or the iPhone's name or identifier
from `make ios-devices` (`make ios DEVICE="iPhone de Ana"`). For iOS, `TEAM=` and `BUNDLE_ID=`
are passed on too (see below).

### Android app (`cd android`)

Needs Java 17 or newer on the `PATH` (Gradle then downloads the JDK 21 the project asks for) and
the Android SDK (Android Studio's works; it is found through `ANDROID_HOME`, `ANDROID_SDK_ROOT`,
`~/Library/Android/sdk` or `~/Android/Sdk`, and `local.properties` is written for you). On the
phone, turn on Developer options → USB debugging and accept the "Allow USB debugging" prompt.

| Command | What it does |
|---|---|
| `make setup` | Checks the SDK, `adb` and Java, writes `local.properties` |
| `make devices` | Lists connected phones (`adb devices -l`) |
| `make test` | JVM unit tests (`testDebugUnitTest`) |
| `make lint` | Android lint (`lintDebug`) |
| `make build` | Builds `app/build/outputs/apk/debug/app-debug.apk` |
| `make install` | Builds and installs on the connected phone |
| `make run` | Installs and opens the app |
| `make logs` | `logcat` of the running app (Ctrl+C quits) |
| `make uninstall` | Removes the app from the phone, with the anchors, calibration and saved locations stored on it |
| `make clean` | Deletes the build output |

`DEVICE=<serial>` works here too. `install`, `run`, `logs` and `uninstall` stop with a plain
message when no phone is connected, it has not authorised the computer, or several are connected.

### ESP32 firmware (`cd embedded`)

`make setup` creates a Python venv with PlatformIO inside `embedded/` and `include/config.h` from
the example (ignored by Git). Full guide, including where to place the boards: `embedded/USAGE.md`.

| Command | What it does |
|---|---|
| `make setup` | Creates `.venv` with PlatformIO and `include/config.h` (once) |
| `make test` | Host unit tests (`native` environment, no board) |
| `make test-one T=<folder>` | One test folder, e.g. `make test-one T=test_label` |
| `make test-scripts` | Python tests of the scripts (beacon ID, and `../scripts` if it has any) |
| `make build` | Compiles the firmware (`esp32dev`) |
| `make flash` | Compiles and flashes the board; `make flash ID=9F3C1A01` also sets its beacon ID |
| `make id ID=<8 hex>` | Sets the beacon ID over serial on a board already flashed, and checks it |
| `make monitor` | Serial monitor at 115200 baud (Ctrl+C quits) |
| `make monitor-log` | Serial monitor that also writes `logs/device-monitor-<date>.log` |
| `make flash-monitor` | Flashes, then opens the monitor |
| `make ports` | Lists the serial ports |
| `make clean` | Deletes the build output (keeps the venv and PlatformIO's cache) |

`PORT=<serial port>` picks the board when several are connected, e.g.
`make flash ID=9F3C1A02 PORT=/dev/cu.usbserial-0001`.

### iOS app (`cd ios`)

Needs Xcode 15 or newer, opened once, with your Apple ID added in Xcode → Settings → Accounts:
`xcodebuild` then creates the signing profile itself (`-allowProvisioningUpdates`). On the iPhone,
turn on Developer Mode (Settings → Privacy & Security), connect the cable and tap "Trust".

| Command | What it does |
|---|---|
| `make devices` | Lists the iPhones paired with this Mac (`xcrun devicectl list devices`) |
| `make test` | Unit tests on the simulator (`SIM=iPhone 17` by default) |
| `make test-scripts` | Tests of `scripts/pick_device.py`, which picks the iPhone to install on |
| `make build` | Builds the signed app for iPhone into `.build/DerivedData` |
| `make install` | Builds and installs on the connected iPhone |
| `make run` | Installs and opens the app |
| `make sim` | Builds, installs and opens the app on the simulator |
| `make open` | Opens `TrackerApp.xcodeproj` in Xcode |
| `make clean` | Deletes the build output |

Variables:

- `TEAM=<team ID>` — the signing team. Empty uses the project's, which is someone else's team; with
  a free account use your Personal Team, whose ID shows in Xcode → Settings → Accounts, e.g.
  `make run TEAM=ABCDE12345`.
- `BUNDLE_ID=<bundle>` — if `com.airtagclone.TrackerApp` is already registered by another team,
  e.g. `BUNDLE_ID=com.yourname.TrackerApp`.
- `DEVICE=<name, identifier or UDID>` — which iPhone, when more than one is reachable.
- `SIM=<simulator>` — which simulator for `test` and `sim`.

`install` and `run` stop with a plain message when no iPhone is reachable, or several are and none
was picked. The install from a free account expires after 7 days: run `make run` again.

### Bench test in short

1. Flash each ESP32 with its own ID: `cd embedded && make setup && make flash ID=9F3C1A01`, then
   the next board with `ID=9F3C1A02`, and so on.
2. Place the boards at the corners of a square (2.5 m on a side was the bench test) and power them.
3. Install the app: `make ios TEAM=<your team>` on the iPhone, or `make android` on the Android
   phone.
4. In the Observer screen, tap each beacon under "Sinais próximos" and type its x/y in metres.
5. Under "Calibração", stand in the middle and tap "Usar a leitura de agora" next to "RSSI no
   centro"; then touch the phone to a beacon and tap the one next to "RSSI encostado". The position
   should then stay inside the square.

# iOS app (`ios/`)

The iOS app has two features: 1:1 tracking between two nearby iPhones (UWB), and indoor
positioning with fixed beacons (ESP32 boards, or phones as anchors) and an observer phone. It
interoperates with the Android app in `android/`.

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

> The Android app (`android/`) has no AR capture yet: its anchors are typed in by hand. Its
> ARCore position tracker exists (the AR tab) but is not wired to anchor placement.

**Wire protocol** (`BeaconProtocol`) — a fixed service UUID marks a BLE advertisement as this
app's, and a 4-byte `BeaconID` (8 hex characters) identifies the specific install, surviving BLE
address rotation. The ID is carried in the advertisement's **local name**, not manufacturer data:
iOS's `CBPeripheralManager` cannot set manufacturer data when advertising (Apple only exposes
`CBAdvertisementDataLocalNameKey` and `CBAdvertisementDataServiceUUIDsKey` to
`startAdvertising(_:)`), so local name is the only field both iOS and Android can use when
*broadcasting* — both can already read manufacturer data fine when *scanning*.

Android observers read the ID the same way (manufacturer data first, then a local name that is
an ID itself), so they see the ESP32 beacons and iPhone anchors; Android anchors keep advertising
manufacturer data, which iOS reads first.

**Distance/position math** (`Core/Positioning`; the Android app uses the same model, solver and
constants):

- `PathLossModel` — converts RSSI to distance with the log-distance path loss model, calibrated
  from two readings in the Observer screen: **"RSSI no centro"**, heard standing in the middle of
  the anchors and read as their mean distance to the middle (1.77 m in a 2.5 m square), and
  **"RSSI encostado"**, heard touching a beacon and read as 0.1 m. Two readings fix both the RSSI
  at 1 m and the path loss exponent. The defaults are -70 dBm (what the iPhone read on the bench)
  and -30 dBm (touching read -34 to -42), which give about -62 dBm at 1 m and an exponent of 3.2.
  A touching reading no stronger than the center's, or one giving an exponent outside 1–6, is
  ignored and the center alone calibrates with exponent 2. "Usar a leitura de agora" takes the
  mean of the anchors' medians (center) or the strongest one (touching).
- `RSSIWindow` — the median of each beacon's advertisements in the last 3 s. A stray reading does
  not move it, and a beacon heard 30 times does not count more than one heard 3 times.
- `Multilateration` — any N ≥ 3 anchors. Weighted least squares (weight 1/d², since a fixed RSSI
  error in dB is a distance error that grows with the distance), started from the linear solution
  and refined by Gauss-Newton, so the result does not depend on the order of the anchors. Returns
  `nil` for fewer than 3 anchors or anchors in a line.
- **Near a beacon** (`Multilateration`): a beacon clearly nearer than the rest (within 0.5 m by
  its RSSI, the next one more than twice as far) holds the phone, counting from touching it: at
  "RSSI encostado" or stronger the phone is shown on the beacon, a little weaker within the extra
  distance (0.3 m by the model is at most 0.2 m away). The solver also halves a step that makes the
  error worse instead of stopping there, so one far beacon reading badly cannot strand the fix.
- `MotionLimit` — "Limitar pela velocidade" (on by default, 2 km/h, 0.5–20 km/h): the position shown
  moves towards each new fix by at most the speed times the time since the last one.
- `AnchorStore` — anchor positions are entered per deployment (not a fixed layout) and persisted
  in `UserDefaults`.
- `PositioningEngine` — ties a `BeaconScanning` instance and an `AnchorStore` together, recomputes
  the ranges and the fix every second from the windows' medians, and reports each round (with the
  sample count, RSSI spread and device model) to the API.

The Observer screen draws the beacons (orange), a ring per beacon at its RSSI distance with the
distance and dBm next to it, and the phone (blue) with its uncertainty, live. Positions can be
saved and walked back to ("Locais salvos").

**Positioning API** (`PositioningReporting` / `PositioningAPIClient`) — every second the observer
posts its readings to `POST /sightings` (observer, anchor, RSSI, distance, sample count, RSSI
spread, device model) and its fix to `POST /fixes` (with the anchor count), for cross-device
aggregation (peak hours, a floor-plan heatmap). The API runs on Render
(`https://api-tracker-app.onrender.com`) behind an `X-API-Key` header. The key in the code is a
placeholder: put the real one in locally, without committing it. The Android app and the ESP32
collector post to the same API.

### 3. Fingerprinting data collection

**Indoor → "Coletar dados para fingerprinting"** (`FingerprintCollectorView`) records a labelled
RSSI dataset for training a fingerprinting model later. It is the iPhone counterpart of the ESP32
collector's `label` / `rec start` / `rec stop` (`embedded/USAGE.md`, chapter 6), except that a
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
| `roll`, `pitch`, `yaw` | the phone's attitude in radians from CoreMotion's sensor fusion: `roll`/`pitch` are tilt against gravity; `yaw` is not tied to north, so only its changes mean something (empty when there is none) |
| `relative_altitude_m`, `pressure_kpa` | barometer: metres relative to where altitude updates started, and the pressure (empty without a barometer) |
| `motion_state`, `motion_confidence` | `stationary`, `walking`, `running`, `automotive`, `cycling` or `unknown`, and 0–2 (empty before the permission is granted) |
| `session`, `point`, `x`, `y` | the label |

When a recording runs to the end, a **`.json` with the same name** is written next to it
(`FingerprintRecordingMetadata`): the recording's start and end on both clocks
(`started_t_ms`/`ended_t_ms`, `started_timestamp_ms`/`ended_timestamp_ms`), the planned duration,
the rows per beacon, the label, the device, how the scan was done, the sensors' units, and
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
`BEAC0004`, see `embedded/USAGE.md`) and a sticker with it. The ID names the physical board, not
the spot: a replacement board gets a new ID, since boards differ slightly in antenna and power. The
phone can't read a beacon's transmit power or advertising interval from its advertisement, so they
aren't in the JSON: keep them the same on every board (defaults `0 dBm` / `100 ms`, reported by
each board at boot in its `E,…,advertising,…` line).

**Static, then moving.** Recordings are taken standing at one labelled point. Collecting while
walking needs more than this format has today — timestamped waypoints to interpolate the position
between, and the full motion stream rather than one sample per row — and is a planned
extension, not something to fake by recording while moving.

- **Mergeable with the ESP32 data.** Column names match the ESP32's `R` records wherever they
  overlap, and `FingerprintLabel` applies the same rules as the ESP32's `label` command (session
  and point are 1–16 characters of `[A-Za-z0-9_-]`), so the two datasets can be concatenated.
- **Motion sensors.** Each row gets the latest attitude (`DeviceMotionReader`, 50 Hz), barometer
  reading (`AltitudeReader`) and motion activity (`MotionActivityReader`, which asks for the
  Motion & Fitness permission). Attitude tells how the phone was being held, which affects RSSI
  through antenna orientation and body shadowing; the barometer separates floors; the activity
  marks rows where the phone was not actually still.
- **Uploaded to the API.** When a recording ends, its rows are also sent to `POST /fingerprints`
  (the file is written either way).
- **Model and system version are on every row**, so files from different phones can be
  concatenated and still tell apart radios that read RSSI differently.
- **Getting the files off the phone:** share one from the list (AirDrop), use "Exportar todas",
  or open Files → On My iPhone → TrackerApp → Fingerprints (`UIFileSharingEnabled`), or Finder
  over USB.
- **Keep the app in the foreground.** The screen doesn't auto-lock while recording, because iOS
  stops BLE scanning in the background (the app has no `bluetooth-central` background mode).
- `BeaconScanner` drops advertisements with RSSI 127, which is CoreBluetooth's "unavailable" value.

#### Collecting the same data on Android

A model trained on data from both apps only holds if both collect the same way. The iOS collector
is the reference; the Android collector (`android/`, Fingerprint tab) writes the same CSV and JSON,
with these platform differences handled:

| What | iOS (reference) | Android equivalent |
|---|---|---|
| Scan | `scanForPeripherals(withServices: [serviceUUID], options: [AllowDuplicates: true])`, foreground, screen kept on | `BluetoothLeScanner.startScan` with a `ScanFilter` on the service UUID, `SCAN_MODE_LOW_LATENCY`, `CALLBACK_TYPE_ALL_MATCHES`, report delay 0; foreground, screen kept on |
| One row per | advertisement delivered (no dedup, no averaging) | `onScanResult` call (same) |
| Beacon ID | manufacturer data (company `0xFFFF` + 4 bytes), else local name if it is 8 hex | the same order: `getManufacturerSpecificData(0xFFFF)`, else `scanRecord.deviceName` |
| `t_ms` | `CLOCK_MONOTONIC` ms, read in the delegate callback | `ScanResult.getTimestampNanos() / 1_000_000` (same clock as `elapsedRealtime()`, taken at reception) |
| `timestamp_ms` | Unix ms (`Date()`) | `System.currentTimeMillis()` |
| `model` / `system_version` | `"iPhone15,2"` / `"iOS 26.0.1"` | `"<Build.MANUFACTURER> <Build.MODEL>"` / `"Android <Build.VERSION.RELEASE>"` |
| Unavailable RSSI | 127, dropped | not expected; drop 127 anyway |
| `roll/pitch/yaw` | CoreMotion attitude, radians | rotation-vector sensor; axis conventions differ and are described in each recording's JSON (`AndroidOrientationReader`) |
| `motion_state/confidence` | `CMMotionActivityManager`, confidence 0–2 | `ActivityRecognitionClient`; its confidence scale differs and is described in the JSON |
| JSON `platform` / `scan_api` | `"iOS"` / CoreBluetooth | `"Android"` / a description of the scan settings above |
| JSON `beacons` | copy of `AnchorStore.anchors` at the start of the recording, sorted by ID | the same structure, from the Android app's own anchor list |

The label rules (`FingerprintLabel`: 1–16 characters of `[A-Za-z0-9_-]`), the fixed-length
recordings, discarding cancelled ones, writing the JSON only on completion and the beacon-position
warnings are part of the format too, not iOS details.

## Project structure

```
ios/TrackerApp/
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
      Point2D.swift                                          Point2D, MapProjection, DecimalInput
      Anchor.swift / PathLossModel.swift                     Path loss model, RSSIWindow, calibration
      Multilateration.swift                                 N-anchor weighted least-squares solver
      AnchorStore.swift                                      User-configured anchor positions
      PositioningEngine.swift                                Sightings + anchors -> fix
      PositioningReporting.swift / PositioningAPIClient.swift  Positioning API client (Render)
      ARPositionTracker.swift / ARPositionTracking.swift       ARKit-based anchor position capture
      DeviceMotionReader / AltitudeReader / MotionActivityReader  CoreMotion sensors per row
      FingerprintRecord.swift / FingerprintRecorder.swift      Labelled RSSI dataset -> CSV
  Models/                  HandshakeState, RangingState, TrackingLogEntry
  UI/
    MainView.swift / TrackingView.swift
    Positioning/
      PositioningModeView.swift / AnchorBroadcastView.swift
      ObserverView.swift / AnchorPositionEditor.swift        Observer screen, live map, calibration
      NavigateToLocationView.swift                           Walking back to a saved location
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
- `PositioningTests` — `Multilateration` (exact/inconsistent/degenerate ranges, N > 3 anchors,
  weighting, order independence, equal RSSI from a square's corners giving its center, a beacon
  read as touching holding the phone on it, the bench square end to end, the speed limit),
  `PathLossModel` and the center-RSSI calibration, `RSSIWindow`, `PositioningEngine` (medians,
  stale readings, beacons heard at different rates), `MapProjection`, `DecimalInput` (comma or
  dot) and rendering the map. The Android app's `CalibratedPositioningTest` and
  `DeviceListReducerTest` run the same cases.
- `BeaconIDTests` — identity encoding/validation, and reading the ID from an advertisement (local
  name, Android's manufacturer data, which one wins).
- `FingerprintRecorderTests` — label validation, the CSV row format (quoting, empty
  sensor fields), and the recorder against fake `BeaconScanning` and sensor implementations: only advertisements received while recording are written, a recording stops on
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
`AnchorStore`, `PositioningEngine`, `ARPositionTracker`, the motion sensor readers,
`FingerprintRecorder`, `BluetoothPermissionMonitor`, and the `PositioningPreviewFakes`) and their
matching protocols (`ProximityTracking`, `HandshakeChannel`, `UWBManaging`, `BeaconAdvertising`,
`BeaconScanning`, `ARPositionTracking` and the sensor reading protocols) are `@MainActor` — this is true in
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
  motion sensors and getting the files off the phone have not been tried on a device yet.
- The Android app has no AR anchor capture yet (anchors are typed in by hand there).
- The path loss model is calibrated with one number for all beacons ("RSSI no centro"); boards
  that read a few dB apart still pull the fix towards the weaker one. Per-beacon calibration is a
  possible next step.
- The API key in `PositioningAPIClient` is a placeholder; uploads fail until the real one is put in
  locally.
- `ARPositionTracker`'s ARKit calls were verified by typechecking against a real iOS SDK
  (`iphonesimulator`, target iOS 16), unlike the rest of this app which only got as far as
  `swiftc -parse` — still not run on-device.
