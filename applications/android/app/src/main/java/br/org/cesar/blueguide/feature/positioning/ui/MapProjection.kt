package br.org.cesar.blueguide.feature.positioning.ui

import br.org.cesar.blueguide.core.model.Point2D
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Where metres land on a map view — the counterpart of the iOS app's `MapProjection`: every
 * given point fits inside the margin, one scale serves both axes (a metre is as long to the
 * right as upwards) and y grows upwards. Plain numbers (pixels in, pixels out), so it is
 * testable on the JVM.
 */
data class MapProjection(
    /** Pixels per metre, the same on both axes. */
    val scale: Double,
    /** The metres shown at the middle of the view. */
    val center: Point2D,
    val width: Double,
    val height: Double
) {
    /** (x, y) in pixels from the top left. */
    fun viewPoint(point: Point2D): Pair<Double, Double> = Pair(
        width / 2 + (point.x - center.x) * scale,
        height / 2 - (point.y - center.y) * scale
    )

    /** The metres the view covers, edge to edge, for drawing the grid. */
    val visibleX: ClosedFloatingPointRange<Double>
        get() = (center.x - width / (2 * scale))..(center.x + width / (2 * scale))
    val visibleY: ClosedFloatingPointRange<Double>
        get() = (center.y - height / (2 * scale))..(center.y + height / (2 * scale))

    companion object {
        /** [minimumSpan] keeps a lone point (or points on top of each other) from zooming in
         *  without limit. */
        fun fitting(
            points: List<Point2D>,
            width: Double,
            height: Double,
            margin: Double = 28.0,
            minimumSpan: Double = 4.0
        ): MapProjection {
            val minX = points.minOfOrNull { it.x } ?: 0.0
            val maxX = points.maxOfOrNull { it.x } ?: 0.0
            val minY = points.minOfOrNull { it.y } ?: 0.0
            val maxY = points.maxOfOrNull { it.y } ?: 0.0
            val spanX = max(maxX - minX, minimumSpan)
            val spanY = max(maxY - minY, minimumSpan)
            return MapProjection(
                scale = max(1e-6, min((width - 2 * margin) / spanX, (height - 2 * margin) / spanY)),
                center = Point2D((minX + maxX) / 2, (minY + maxY) / 2),
                width = width,
                height = height
            )
        }

        /** The smallest "round" grid spacing, in metres, that keeps the lines at least
         *  [minimumGap] pixels apart (closer than that the labels crowd each other). */
        fun gridStep(scale: Double, minimumGap: Double): Double {
            val steps = listOf(0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0)
            return steps.firstOrNull { it * scale >= minimumGap } ?: steps.last()
        }

        /** The multiples of [step] inside [range]. Capped so absurd coordinates cannot make a
         *  huge grid. */
        fun ticks(range: ClosedFloatingPointRange<Double>, step: Double): List<Double> {
            val first = ceil(range.start / step)
            val last = floor(range.endInclusive / step)
            if (last < first || last - first > 60 || abs(first) >= 1e9 || abs(last) >= 1e9) return emptyList()
            return (first.toLong()..last.toLong()).map { it * step }
        }
    }
}
