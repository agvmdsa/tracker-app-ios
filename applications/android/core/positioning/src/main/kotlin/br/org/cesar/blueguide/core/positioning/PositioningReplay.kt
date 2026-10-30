package br.org.cesar.blueguide.core.positioning

import br.org.cesar.blueguide.core.model.DeviceId

/** One advertisement as a recording keeps it: a fingerprint row's `beacon`, `rssi` and `t_ms`. */
data class RecordedSighting(val beacon: DeviceId, val rssi: Int, val atMs: Long)

/**
 * Runs a recording through a [PositioningEngine] the way the app runs it live, so what the app
 * would have shown each second — and how far that was from where the phone really stood — can be
 * worked out offline with the code the app ships. That is the dataset a model correcting the
 * error is trained on, and the yardstick a new [PositionEstimator] is measured against.
 */
object PositioningReplay {

    /**
     * A refresh at every [PositioningEngine.REFRESH_INTERVAL_MS] after [startMs], up to [endMs],
     * with each of [sightings] recorded before the first refresh after it — as the app's
     * once-a-second loop does with what the scan has delivered by then.
     *
     * [sightings] go in as heard, which is a recording's row order: scan results arrive in
     * bursts, so `t_ms` can step back a little. A refresh happens before the first sighting heard
     * after it; one heard later that is dated earlier counts from the next refresh on, as it
     * would have live.
     *
     * [engine] is reset and given [setup] first, so the same one can replay one recording after
     * another; to compare engines, replay the same sightings with each.
     */
    fun replay(
        sightings: List<RecordedSighting>,
        setup: PositioningSetup,
        engine: PositioningEngine = PathLossPositioningEngine(),
        startMs: Long = sightings.firstOrNull()?.atMs ?: 0L,
        endMs: Long = sightings.maxOfOrNull { it.atMs } ?: startMs
    ): List<PositioningUpdate> {
        engine.reset()
        engine.setup = setup
        val updates = mutableListOf<PositioningUpdate>()
        var nextRefreshMs = startMs + PositioningEngine.REFRESH_INTERVAL_MS
        for (sighting in sightings) {
            while (sighting.atMs > nextRefreshMs && nextRefreshMs <= endMs) {
                updates += engine.refresh(nextRefreshMs)
                nextRefreshMs += PositioningEngine.REFRESH_INTERVAL_MS
            }
            engine.record(sighting.beacon, sighting.rssi, sighting.atMs)
        }
        while (nextRefreshMs <= endMs) {
            updates += engine.refresh(nextRefreshMs)
            nextRefreshMs += PositioningEngine.REFRESH_INTERVAL_MS
        }
        return updates
    }
}
