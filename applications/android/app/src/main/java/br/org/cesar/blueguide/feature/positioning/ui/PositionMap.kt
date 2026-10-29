package br.org.cesar.blueguide.feature.positioning.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.positioning.AnchorRange
import br.org.cesar.blueguide.core.positioning.ObserverFix
import java.util.Locale

/** The two hues of the project on the map, the same as the iOS app's `MapPalette`: the phone is
 *  blue and the ESP32 beacons are orange, stepped for the dark background. */
object MapPalette {
    @Composable
    fun phone(): Color = if (isSystemInDarkTheme()) Color(0xFF3987E5) else Color(0xFF2A78D6)

    @Composable
    fun beacon(): Color = if (isSystemInDarkTheme()) Color(0xFFD95926) else Color(0xFFEB6834)
}

private const val ANIMATION_MS = 800

/**
 * A top-down map — the counterpart of the iOS app's `PositionMapView`: the beacons where they
 * were placed, a ring around each one as far as its RSSI says the phone is, and the phone where
 * the multilateration puts it. It follows every refresh, and the movement is animated so it
 * glides instead of jumping.
 */
@Composable
fun PositionMap(
    anchors: List<Anchor>,
    ranges: List<AnchorRange>,
    fix: ObserverFix?,
    modifier: Modifier = Modifier
) {
    val phoneX by animateFloatAsState(fix?.position?.x?.toFloat() ?: 0f, tween(ANIMATION_MS), label = "phoneX")
    val phoneY by animateFloatAsState(fix?.position?.y?.toFloat() ?: 0f, tween(ANIMATION_MS), label = "phoneY")
    val uncertainty by animateFloatAsState(fix?.uncertaintyMeters?.toFloat() ?: 0f, tween(ANIMATION_MS), label = "uncertainty")
    val radii: Map<DeviceId, Float> = anchors.associate { anchor ->
        key(anchor.id.value) {
            val target = ranges.firstOrNull { it.anchor.id == anchor.id }?.distanceMeters?.toFloat() ?: 0f
            val radius by animateFloatAsState(target, tween(ANIMATION_MS), label = "range")
            anchor.id to radius
        }
    }

    val phoneColor = MapPalette.phone()
    val beaconColor = MapPalette.beacon()
    val background = MaterialTheme.colorScheme.surfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val outline = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    val description = "Mapa com ${anchors.size} beacons" + (
        fix?.let {
            String.format(Locale.ROOT, ", celular estimado em x %.1f, y %.1f metros.", it.position.x, it.position.y)
        } ?: ", ainda sem a posição do celular."
        )

    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val phone = fix?.let { Point2D(phoneX.toDouble(), phoneY.toDouble()) }
            val projection = MapProjection.fitting(
                points = anchors.map { it.position } + listOfNotNull(phone),
                width = size.width.toDouble(),
                height = size.height.toDouble(),
                margin = 28.dp.toPx().toDouble()
            )

            drawGrid(projection, measurer, secondary)
            anchors.forEach { anchor ->
                val radius = radii[anchor.id] ?: 0f
                if (ranges.any { it.anchor.id == anchor.id } && radius > 0f) {
                    drawCircle(
                        color = beaconColor.copy(alpha = 0.45f),
                        radius = radius * projection.scale.toFloat(),
                        center = projection.offset(anchor.position),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            }
            if (phone != null) {
                val center = projection.offset(phone)
                val radius = uncertainty * projection.scale.toFloat()
                drawCircle(phoneColor.copy(alpha = 0.12f), radius, center)
                drawCircle(phoneColor.copy(alpha = 0.5f), radius, center, style = Stroke(width = 1.dp.toPx()))
            }
            anchors.forEach { anchor ->
                drawBeacon(
                    anchor,
                    ranges.firstOrNull {
                        it.anchor.id == anchor.id
                    },
                    projection,
                    beaconColor,
                    outline,
                    ink,
                    secondary,
                    measurer
                )
            }
            if (phone != null) {
                val center = projection.offset(phone)
                drawCircle(phoneColor, 9.dp.toPx(), center)
                drawCircle(outline, 9.dp.toPx(), center, style = Stroke(width = 2.dp.toPx()))
            }
        }
        if (anchors.isEmpty()) {
            Text(
                text = "Cadastre as âncoras: toque num sinal abaixo e informe a posição.",
                modifier = Modifier.padding(24.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = secondary
            )
        }
    }
}

private fun MapProjection.offset(point: Point2D): Offset =
    viewPoint(point).let { (x, y) -> Offset(x.toFloat(), y.toFloat()) }

/** Grid lines every round number of metres, labelled on the bottom and left edges. No label in
 *  the corners, where the two axes' labels would land on top of each other. */
private fun DrawScope.drawGrid(projection: MapProjection, measurer: TextMeasurer, color: Color) {
    val step = MapProjection.gridStep(projection.scale, minimumGap = 40.dp.toPx().toDouble())
    val xs = MapProjection.ticks(projection.visibleX, step)
    val ys = MapProjection.ticks(projection.visibleY, step)
    val line = color.copy(alpha = 0.25f)
    val style = TextStyle(fontSize = 10.sp, color = color)

    for (x in xs) {
        drawLine(
            line,
            projection.offset(Point2D(x, projection.visibleY.start)),
            projection.offset(Point2D(x, projection.visibleY.endInclusive)),
            1.dp.toPx()
        )
    }
    for (y in ys) {
        drawLine(
            line,
            projection.offset(Point2D(projection.visibleX.start, y)),
            projection.offset(Point2D(projection.visibleX.endInclusive, y)),
            1.dp.toPx()
        )
    }
    for (x in xs) {
        val at = projection.offset(Point2D(x, 0.0)).x
        if (at < 30.dp.toPx() || at > size.width - 10.dp.toPx()) continue
        drawCentered(measurer, label(x, step), style, Offset(at, size.height - 9.dp.toPx()))
    }
    for (y in ys) {
        val at = projection.offset(Point2D(0.0, y)).y
        if (at < 10.dp.toPx() || at > size.height - 26.dp.toPx()) continue
        drawCentered(measurer, label(y, step), style, Offset(14.dp.toPx(), at))
    }
}

private fun label(value: Double, step: Double): String =
    String.format(Locale.ROOT, if (step < 1) "%.1f" else "%.0f", value)

/** The beacon, and under it (above it in the lower part, so it clears the axis labels) how far
 *  the RSSI says the phone is from it, with the RSSI itself: the dBm is what calibrating needs. */
private fun DrawScope.drawBeacon(
    anchor: Anchor,
    range: AnchorRange?,
    projection: MapProjection,
    fill: Color,
    outline: Color,
    ink: Color,
    secondary: Color,
    measurer: TextMeasurer
) {
    val at = projection.offset(anchor.position)
    val side = 26.dp.toPx()
    val topLeft = Offset(at.x - side / 2, at.y - side / 2)
    val corner = CornerRadius(6.dp.toPx())
    drawRoundRect(fill, topLeft, Size(side, side), corner)
    drawRoundRect(outline, topLeft, Size(side, side), corner, style = Stroke(width = 2.dp.toPx()))
    drawCentered(measurer, anchor.label, TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.Black), at)

    if (range != null) {
        val below = at.y <= size.height * 0.6f
        val distanceAt = Offset(at.x, at.y + (if (below) 1 else -1) * 31.dp.toPx() - 6.dp.toPx())
        drawCentered(
            measurer,
            String.format(Locale.ROOT, "%.1f m", range.distanceMeters),
            TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = ink),
            distanceAt
        )
        range.rssi?.let { rssi ->
            drawCentered(
                measurer,
                String.format(Locale.ROOT, "%.0f dBm", rssi),
                TextStyle(fontSize = 10.sp, color = secondary),
                Offset(
                    distanceAt.x,
                    distanceAt.y + 13.dp.toPx()
                )
            )
        }
    }
}

private fun DrawScope.drawCentered(measurer: TextMeasurer, text: String, style: TextStyle, center: Offset) {
    val measured = measurer.measure(text, style)
    drawText(measured, topLeft = Offset(center.x - measured.size.width / 2f, center.y - measured.size.height / 2f))
}

/** What each mark on the map means, under it. */
@Composable
fun MapLegend(modifier: Modifier = Modifier) {
    val beacon = MapPalette.beacon()
    val phone = MapPalette.phone()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem("Beacon") { Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(beacon)) }
        LegendItem("Celular (estimado)") { Box(Modifier.size(12.dp).clip(CircleShape).background(phone)) }
        LegendItem("Alcance pelo RSSI") {
            Canvas(Modifier.size(12.dp)) {
                drawCircle(beacon.copy(alpha = 0.6f), size.minDimension / 2 - 1.dp.toPx(), style = Stroke(width = 1.5.dp.toPx()))
            }
        }
    }
}

@Composable
private fun LegendItem(text: String, icon: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        icon()
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
