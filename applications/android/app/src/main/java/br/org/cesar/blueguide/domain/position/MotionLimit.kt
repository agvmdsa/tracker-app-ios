package br.org.cesar.blueguide.domain.position

/**
 * How far the phone can have walked since the last fix — the counterpart of the iOS app's
 * `MotionLimit`. RSSI jumps by metres between refreshes while a person walks a fraction of that,
 * so the position shown moves towards each new fix by at most the speed times the time since the
 * last one.
 */
object MotionLimit {
    /** A slow walk, 0.56 m/s. The Observer screen lets it be changed, or turned off. */
    const val DEFAULT_MAX_SPEED_KMH = 2.0
    val SPEED_RANGE_KMH: ClosedFloatingPointRange<Double> = 0.5..20.0
    const val SPEED_STEP_KMH = 0.5

    /** [target] when it is within reach of [previous] (or there is no previous fix, or no limit);
     *  otherwise the point that far from [previous] on the way to [target]. */
    fun limited(previous: Point2D?, target: Point2D, maxSpeedKmh: Double?, elapsedSeconds: Double): Point2D {
        if (previous == null || maxSpeedKmh == null) return target
        val reach = maxSpeedKmh / 3.6 * maxOf(0.0, elapsedSeconds)
        val distance = previous.distanceTo(target)
        if (distance <= reach) return target
        return Point2D(
            previous.x + (target.x - previous.x) * reach / distance,
            previous.y + (target.y - previous.y) * reach / distance
        )
    }
}
