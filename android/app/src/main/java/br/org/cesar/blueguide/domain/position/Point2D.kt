package br.org.cesar.blueguide.domain.position

import kotlin.math.hypot

/** A point on the floor, in metres, in the frame [AnchorLayout] defines. */
data class Point2D(val x: Double, val y: Double) {
    fun distanceTo(other: Point2D): Double = hypot(x - other.x, y - other.y)

    val magnitude: Double get() = hypot(x, y)
}
