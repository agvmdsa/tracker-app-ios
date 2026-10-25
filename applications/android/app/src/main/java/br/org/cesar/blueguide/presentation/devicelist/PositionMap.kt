package br.org.cesar.blueguide.presentation.devicelist

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.org.cesar.blueguide.R
import br.org.cesar.blueguide.domain.position.ObserverFix
import br.org.cesar.blueguide.domain.position.Point2D
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Margin added past the furthest configured anchor, in metres. */
private const val EXTENT_MARGIN_M = 0.5

/** Past this the area is too small to read, so the dot gets pinned to the edge. */
private const val MAX_HALF_EXTENT_M = 6.0f

/**
 * Top-down view of the configured anchors' area with the observer's own position in it.
 * Any anchor count works — there is no fixed triangle any more (see `AnchorStore`, which
 * replaced the old `AnchorLayout`'s auto-assigned equilateral triangle).
 *
 * The area is filled, so "am I inside it" reads at a glance — and a dot outside it is a
 * hint that the fix has drifted, given the observer is generally expected to be walking
 * within the anchors' span.
 *
 * The halo around the observer is [ObserverFix.uncertaintyMeters]. Over BLE RSSI expect it
 * to be on the order of a metre or so, depending on the anchors' spacing and how noisy the
 * signal is.
 */
@Composable
fun PositionMap(
    fix: ObserverFix?,
    anchorsHeard: Int,
    anchorsConfigured: Int,
    modifier: Modifier = Modifier
) {
    if (fix == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = if (anchorsConfigured == 0) {
                    stringResource(R.string.position_no_anchors_configured)
                } else {
                    stringResource(R.string.position_needs_anchors, anchorsHeard, anchorsConfigured)
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val anchorColor = MaterialTheme.colorScheme.primary
    val observerColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val measurer = rememberTextMeasurer()

    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                // Lower bound from the anchors' own spread (not a fixed triangle size any
                // more), so the view never crops a configured anchor even if the observer's
                // own fix lands close to the centre. Quantised to whole metres so the view
                // does not resize on every packet.
                val minHalfExtent = (fix.readings.maxOf { it.anchor.position.magnitude } + EXTENT_MARGIN_M).toFloat()
                // Widely spread anchors can exceed MAX_HALF_EXTENT_M; never let that invert
                // the clamp (coerceIn throws if min > max).
                val halfExtent = ceil(fix.position.magnitude + 0.4)
                    .toFloat()
                    .coerceIn(minHalfExtent, max(minHalfExtent, MAX_HALF_EXTENT_M))
                val pxPerMeter = min(size.width, size.height) / (2f * halfExtent)
                val origin = Offset(size.width / 2f, size.height / 2f)

                drawRangeRings(halfExtent, pxPerMeter, origin, gridColor)
                drawArea(fix, pxPerMeter, origin, gridColor, anchorColor)
                drawObserver(fix, pxPerMeter, origin, observerColor)
                drawAnchors(fix, pxPerMeter, origin, anchorColor, measurer, labelStyle)
            }
        }

        Text(
            text = fix.readings.joinToString("   ") { reading ->
                "${reading.anchor.label} %.1f m".format(reading.distanceMeters)
            } + "   ±%.1f m".format(fix.uncertaintyMeters),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun DrawScope.toScreen(point: Point2D, pxPerMeter: Float, origin: Offset) = Offset(
    x = origin.x + point.x.toFloat() * pxPerMeter,
    // Screen y grows downwards, world y grows away from the viewer.
    y = origin.y - point.y.toFloat() * pxPerMeter
)

/** One ring per metre out from the centre of the triangle, as a scale reference. */
private fun DrawScope.drawRangeRings(
    halfExtent: Float,
    pxPerMeter: Float,
    origin: Offset,
    color: Color
) {
    for (metre in 1..halfExtent.toInt()) {
        drawCircle(
            color = color.copy(alpha = 0.4f),
            radius = metre * pxPerMeter,
            center = origin,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

/**
 * The walkable area: filled so that being inside it is obvious, edged with a dashed line
 * so the corners still read as anchors rather than as a solid object.
 */
private fun DrawScope.drawArea(
    fix: ObserverFix,
    pxPerMeter: Float,
    origin: Offset,
    edgeColor: Color,
    fillColor: Color
) {
    val corners = fix.readings.map { toScreen(it.anchor.position, pxPerMeter, origin) }

    drawPath(
        path = Path().apply {
            moveTo(corners.first().x, corners.first().y)
            corners.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        },
        color = fillColor.copy(alpha = 0.10f)
    )

    val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
    corners.indices.forEach { index ->
        drawLine(
            color = edgeColor,
            start = corners[index],
            end = corners[(index + 1) % corners.size],
            strokeWidth = 1.dp.toPx(),
            pathEffect = dash
        )
    }
}

private fun DrawScope.drawAnchors(
    fix: ObserverFix,
    pxPerMeter: Float,
    origin: Offset,
    color: Color,
    measurer: TextMeasurer,
    labelStyle: TextStyle
) {
    fix.readings.forEach { reading ->
        val at = toScreen(reading.anchor.position, pxPerMeter, origin)
        drawCircle(color = color, radius = 6.dp.toPx(), center = at)

        // Last four hex digits are enough to match a corner to a broadcaster's screen.
        val label = "${reading.anchor.label} ${reading.anchor.id.value.takeLast(4)}"
        val measured = measurer.measure(label, labelStyle)
        drawText(
            textLayoutResult = measured,
            topLeft = Offset(
                x = at.x - measured.size.width / 2f,
                y = at.y + 8.dp.toPx()
            )
        )
    }
}

private fun DrawScope.drawObserver(
    fix: ObserverFix,
    pxPerMeter: Float,
    origin: Offset,
    color: Color
) {
    val exact = toScreen(fix.position, pxPerMeter, origin)
    val margin = 8.dp.toPx()
    val pinned = Offset(
        x = exact.x.coerceIn(margin, size.width - margin),
        y = exact.y.coerceIn(margin, size.height - margin)
    )
    val offScale = pinned != exact

    drawCircle(
        color = color.copy(alpha = 0.15f),
        radius = (fix.uncertaintyMeters * pxPerMeter).toFloat(),
        center = pinned
    )

    // Hollow when the fix landed outside the view, so a pinned dot is not read as a
    // position. The metre readings under the map still show where it actually went.
    if (offScale) {
        drawCircle(
            color = color,
            radius = 7.dp.toPx(),
            center = pinned,
            style = Stroke(width = 2.dp.toPx())
        )
    } else {
        drawCircle(color = color, radius = 7.dp.toPx(), center = pinned)
    }
}
