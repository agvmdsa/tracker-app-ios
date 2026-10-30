# :core:positioning

Where the phone is, from what it hears: pure Kotlin/JVM, no Android. The app runs it live
(`DeviceListViewModel`) and `PositioningReplay` runs it over recordings (`make android-replay`),
so both place the phone with the same code.

## Contract and implementation

`PositioningEngine.kt` is what the rest of the app depends on: every advertisement goes in
(`record`), once a second a `PositioningUpdate` comes out (`refresh`), configured by a
`PositioningSetup` (the anchors and the calibration on screen). `PositionEstimator.kt` is the
contract of one step inside the app's engine. Everything else here is how the app's engine,
`PathLossPositioningEngine`, works, and can be rewritten.

Trying another engine:

1. Implement `PositioningEngine`.
2. Give it a subclass of `PositioningScenarios` (three lines, like `PathLossPositioningScenariosTest`):
   what any engine has to do.
3. Add it to `tools/replay`'s `Engines` and compare it with the app's on the same recordings:
   `make android-replay ARGS="--engine path-loss --engine <yours> <recordings>"`.
4. To run it in the app, change the one line in the app's `di/PositioningModule`.

## The app's engine, in reading order

| Step | What happens | File |
|---|---|---|
| 1. Record | Each anchor's advertisements are kept for the last 3 s (at most 120); other beacons are ignored. | `PathLossPositioningEngine.record`, `RssiWindow.kt` |
| 2. Median | Once a second, each anchor heard within the window gets the median of its RSSIs. | `PathLossPositioningEngine.refresh`, `RssiWindow.median` |
| 3. Distance | The median becomes a distance by the log-distance model, `rssi = A - 10·n·log10(d)`, calibrated from "RSSI no centro" (the anchors' mean distance to their middle) and "RSSI encostado" (0.1 m). | `PathLossModel.kt`, `PositioningSetup.calibration` |
| 4. Position | The estimator places the phone from the distances. | `PositionEstimator.kt` |
| 4a. | Linear start: the first anchor's circle subtracted from the others; none when the anchors are in a line. | `Multilateration.linearSolution` |
| 4b. | Weighted Gauss-Newton: each reading weighs 1/σ², σ = max(0.1 m, 0.35·d), so near anchors count more. | `Multilateration.refined`, `weight` |
| 4c. | Held by the nearest: an anchor read within 0.5 m, with the next at more than twice that, keeps the fix within its range less the 0.1 m of touching. | `Multilateration.heldByTheNearest` |
| 4d. | Uncertainty: the RMS of how far the circles miss the fix. | `Multilateration.solve` |
| 5. Speed limit | The position shown moves towards the new one by at most the speed times the time since the last. | `MotionLimit.kt` |

`PositioningUpdate` is what a refresh returns: the ranges (step 3), the fix (after step 5) and,
per anchor, how many advertisements arrived since the last refresh and their spread.

## Couplings that don't show in the types

- The weights (4b) size the RSSI noise with the nominal exponent, 2.0
  (`Multilateration.RELATIVE_DISTANCE_ERROR`), not the calibrated one. A common scale cancels in
  weighted least squares, so it only matters around the 0.1 m floor.
- Held by the nearest (4c) uses the touching distance of the path loss model
  (`PathLossModel.TOUCH_DISTANCE_METERS`).
- The linear start (4a) depends on which anchor is listed first, and Gauss-Newton (4b) stops as
  soon as a step can't lower the weighted error, so the order of the anchors moves the fix by
  about a millimetre.
- Outside this module: the API (`POST /sightings`) gets each anchor's distance, the map draws a
  circle of it, and each recording's metadata keeps the calibration's inputs and the app engine's
  RSSI window (`schema_version` 2). The iOS app has the same pipeline in Swift, kept equal by the
  same test cases.

## Tests

- `PositioningScenarios`: what any engine has to do, through the contract only, each with how
  close is close enough. It stays when the engine is rewritten: the acceptance criteria of the new
  one.
- How the app's engine does it, exactly: `PathLossPositioningEngineTest` (the steps together) and
  one test file per step (`MultilaterationTest`, `PathLossModelTest`, `RssiWindowTest`,
  `MotionLimitTest`). They go with the engine when it is replaced. Some check a scenario's
  situation again, more strictly (the exact centre, not within a centimetre).
- `PositioningReplayTest`: the replay's timing.
