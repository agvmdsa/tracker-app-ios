package br.org.cesar.blueguide.core.model

/**
 * One broadcaster, placed by hand at a known point on the floor — the counterpart of the
 * iOS app's `Anchor` (`AnchorStore`/`AnchorPositionEditor`). There is no assumed count or
 * shape: any three or more anchors with a position are enough for `Multilateration` to
 * produce a fix.
 */
data class Anchor(
    val id: DeviceId,
    /** "A", "B", "C" — drawn on the map so the phones can be arranged to match. */
    val label: String,
    val position: Point2D,
    /** Metres above the floor, when entered. Optional, same as the iOS counterpart. */
    val height: Double? = null
) {
    /** e.g. "(2.50, 0.75) m · h 2.10 m". */
    val positionDescription: String
        get() {
            val base = "(%.2f, %.2f) m".format(position.x, position.y)
            return height?.let { base + " · h %.2f m".format(it) } ?: base
        }
}
