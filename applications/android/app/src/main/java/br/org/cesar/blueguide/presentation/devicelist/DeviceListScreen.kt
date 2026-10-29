package br.org.cesar.blueguide.presentation.devicelist

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.org.cesar.blueguide.R
import br.org.cesar.blueguide.domain.ble.BleAdvertiseException
import br.org.cesar.blueguide.domain.ble.BroadcastStatus
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.entity.DeviceSighting
import br.org.cesar.blueguide.domain.position.MotionLimit
import br.org.cesar.blueguide.domain.position.ObserverFix
import br.org.cesar.blueguide.domain.position.SavedLocation
import java.util.Locale

@Composable
fun DeviceListScreen(
    state: DeviceListState,
    modifier: Modifier = Modifier,
    /** Tapping a device with a known id opens the anchor editor for it (`AnchorEditorDialog`). */
    onDeviceClick: (DeviceId) -> Unit = {},
    onCenterRssiChange: (Double) -> Unit = {},
    onTouchRssiChange: (Double) -> Unit = {},
    onSpeedLimitChange: (isLimited: Boolean, kmh: Double) -> Unit = { _, _ -> },
    onSaveLocation: (String) -> Unit = {},
    onRemoveLocation: (String) -> Unit = {}
) {
    when (state.mode) {
        AppMode.OBSERVER ->
            if (state.scanGranted) {
                ObserverContent(
                    state,
                    modifier,
                    onDeviceClick,
                    onCenterRssiChange,
                    onTouchRssiChange,
                    onSpeedLimitChange,
                    onSaveLocation,
                    onRemoveLocation
                )
            } else {
                CenteredMessage(stringResource(R.string.scan_permission_required), modifier)
            }

        AppMode.BROADCAST ->
            if (state.advertiseGranted) {
                BroadcastContent(state.broadcastStatus, state.localDeviceId, modifier)
            } else {
                CenteredMessage(stringResource(R.string.advertise_permission_required), modifier)
            }
    }
}

/**
 * The Observer screen, section by section the same as the iOS app's `ObserverView`: the map,
 * the estimated position, the saved locations, the configured anchors, the nearby signals not
 * configured yet and the "RSSI no centro" calibration.
 */
@Composable
private fun ObserverContent(
    state: DeviceListState,
    modifier: Modifier,
    onDeviceClick: (DeviceId) -> Unit,
    onCenterRssiChange: (Double) -> Unit,
    onTouchRssiChange: (Double) -> Unit,
    onSpeedLimitChange: (Boolean, Double) -> Unit,
    onSaveLocation: (String) -> Unit,
    onRemoveLocation: (String) -> Unit
) {
    var navigatingTo by remember { mutableStateOf<SavedLocation?>(null) }
    var isSavingLocation by remember { mutableStateOf(false) }

    navigatingTo?.let { target ->
        // The list may have dropped it meanwhile; the screen still shows the one picked.
        BackHandler { navigatingTo = null }
        NavigateToLocationScreen(target, state.observerFix, onBack = { navigatingTo = null }, modifier = modifier)
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { SectionHeader("Mapa") }
        item {
            PositionMap(
                anchors = state.anchors,
                ranges = state.anchorRanges,
                fix = state.observerFix,
                modifier = Modifier.fillMaxWidth().height(300.dp).padding(horizontal = 16.dp)
            )
        }
        item { MapLegend(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
        item {
            Caption(
                "O círculo azul é a incerteza do cálculo (o resíduo das distâncias), não um intervalo de confiança.",
                Modifier.padding(horizontal = 16.dp)
            )
        }

        item { SectionHeader("Posição estimada") }
        item {
            val fix = state.observerFix
            Column(Modifier.padding(horizontal = 16.dp)) {
                if (fix != null) {
                    Text(
                        String.format(Locale.ROOT, "x: %.2f m   y: %.2f m", fix.position.x, fix.position.y),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        String.format(Locale.ROOT, "incerteza: %.2f m", fix.uncertaintyMeters),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Secondary("Aguardando pelo menos 3 âncoras configuradas e visíveis…")
                }
            }
        }

        item { SectionHeader("Locais salvos") }
        if (state.savedLocations.isEmpty()) {
            item {
                Secondary("Nenhum ainda — ache sua posição e salve, por exemplo, onde estacionou.", Modifier.padding(horizontal = 16.dp))
            }
        }
        items(state.savedLocations, key = { "location-" + it.id }) { location ->
            ListItem(
                modifier = Modifier.clickable { navigatingTo = location },
                headlineContent = { Text(location.label, fontWeight = FontWeight.Bold) },
                supportingContent = { Text(String.format(Locale.ROOT, "(%.1f, %.1f) m", location.position.x, location.position.y)) },
                trailingContent = { TextButton(onClick = { onRemoveLocation(location.id) }) { Text("Apagar") } }
            )
        }
        item {
            TextButton(
                onClick = { isSavingLocation = true },
                enabled = state.observerFix != null,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) { Text("Salvar posição atual") }
        }

        item { SectionHeader("Âncoras configuradas") }
        if (state.anchors.isEmpty()) {
            item { Secondary("Nenhuma ainda — toque em um sinal abaixo para configurar.", Modifier.padding(horizontal = 16.dp)) }
        }
        items(state.anchors, key = { "anchor-" + it.id.value }) { anchor ->
            ListItem(
                modifier = Modifier.clickable { onDeviceClick(anchor.id) },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(anchor.label, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            anchor.id.value,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                trailingContent = { Text(anchor.positionDescription, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }

        item { SectionHeader("Sinais próximos") }
        val nearby = state.unconfiguredSightings
        if (nearby.isEmpty()) {
            item { Secondary("Nenhum sinal novo por perto.", Modifier.padding(horizontal = 16.dp)) }
        }
        items(nearby, key = { "nearby-" + it.device.key }) { sighting ->
            val id = sighting.device.id ?: return@items
            ListItem(
                modifier = Modifier.clickable { onDeviceClick(id) },
                headlineContent = { Text(id.value, fontFamily = FontFamily.Monospace) },
                trailingContent = { Text("${sighting.rssi} dBm", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            )
        }

        item { SectionHeader("Calibração") }
        item { CalibrationSection(state, onCenterRssiChange, onTouchRssiChange, onSpeedLimitChange) }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (isSavingLocation) {
        SaveLocationDialog(
            onSave = { label ->
                onSaveLocation(label)
                isSavingLocation = false
            },
            onDismiss = { isSavingLocation = false }
        )
    }
}

/** "RSSI no centro" and "RSSI encostado": a stepper of 1 dBm each, and taking what the phone
 *  hears now; under them the model the two give, as in the iOS app. */
@Composable
private fun CalibrationSection(
    state: DeviceListState,
    onCenterRssiChange: (Double) -> Unit,
    onTouchRssiChange: (Double) -> Unit,
    onSpeedLimitChange: (Boolean, Double) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        RssiStepper("RSSI no centro", state.centerRssi, -100.0..-30.0, state.currentCenterRssi, onCenterRssiChange)
        RssiStepper("RSSI encostado", state.touchRssi, -60.0..-10.0, state.currentTouchRssi, onTouchRssiChange)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Limitar pela velocidade", Modifier.weight(1f))
            Switch(checked = state.isSpeedLimited, onCheckedChange = { onSpeedLimitChange(it, state.maxSpeedKmh) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Velocidade máxima", Modifier.weight(1f))
            val range = MotionLimit.SPEED_RANGE_KMH
            val step = MotionLimit.SPEED_STEP_KMH
            OutlinedButton(
                onClick = { onSpeedLimitChange(true, (state.maxSpeedKmh - step).coerceIn(range)) },
                enabled = state.isSpeedLimited && state.maxSpeedKmh > range.start
            ) { Text("−") }
            Text(
                String.format(Locale.ROOT, "%.1f km/h", state.maxSpeedKmh).replace('.', ','),
                modifier = Modifier.width(76.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = { onSpeedLimitChange(true, (state.maxSpeedKmh + step).coerceIn(range)) },
                enabled = state.isSpeedLimited && state.maxSpeedKmh < range.endInclusive
            ) { Text("+") }
        }
        val calibration = state.calibration
        val model = String.format(Locale.ROOT, "%.0f dBm a 1 m, expoente %.1f", calibration.referenceRssi, calibration.exponent)
            .replace('.', ',')
        Text(
            if (calibration.usesTouchRssi) {
                "Modelo: $model."
            } else {
                "O RSSI encostado precisa ser mais forte que o do centro (e coerente com ele); usando só o centro. Modelo: $model."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (calibration.usesTouchRssi) MaterialTheme.colorScheme.onSurfaceVariant else MapPalette.beacon()
        )
        Caption(
            "RSSI no centro: o que o celular lê no meio das âncoras, tratado como a distância média delas até o meio " +
                "(1,77 m num quadrado de 2,5 m). RSSI encostado: o que ele lê encostado num beacon, tratado como 10 cm. " +
                "Com os dois o app calcula o RSSI a 1 m e o expoente de perda. Fique no meio (ou encoste num beacon) e " +
                "toque em \"Usar a leitura de agora\". Com o limite de velocidade, a posição anda na direção da nova " +
                "leitura, no máximo o que daria para andar nessa velocidade desde a anterior.",
            Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun RssiStepper(
    title: String,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    current: Double?,
    onChange: (Double) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.start) { Text("−") }
        Text(
            String.format(Locale.ROOT, "%.0f dBm", value),
            modifier = Modifier.width(76.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.endInclusive) { Text("+") }
    }
    TextButton(onClick = { current?.let { onChange(Math.round(it).toDouble()) } }, enabled = current != null) {
        Text(
            current?.let { String.format(Locale.ROOT, "Usar a leitura de agora (%.0f dBm)", it) }
                ?: "Usar a leitura de agora"
        )
    }
}

@Composable
private fun SaveLocationDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var label by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Salvar posição atual") },
        text = {
            OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Nome (ex: Meu carro)") }, singleLine = true)
        },
        confirmButton = { Button(onClick = { onSave(label) }) { Text("Salvar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

/**
 * Walking back to a saved location — the counterpart of the iOS app's `NavigateToLocationView`:
 * an arrow towards it on the map's axes and how far it is.
 */
@Composable
fun NavigateToLocationScreen(target: SavedLocation, fix: ObserverFix?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Voltar") }
            Text(target.label, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(72.dp))
        }
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val current = fix?.position
            if (current != null) {
                val bearing = SavedLocation.bearingRadians(current, target.position)
                val degrees by animateFloatAsState(Math.toDegrees(bearing).toFloat(), label = "bearing")
                val arrowColor = MapPalette.phone()
                Canvas(Modifier.size(72.dp).rotate(degrees)) {
                    val path = Path().apply {
                        moveTo(size.width / 2, 0f)
                        lineTo(size.width * 0.9f, size.height)
                        lineTo(size.width / 2, size.height * 0.72f)
                        lineTo(size.width * 0.1f, size.height)
                        close()
                    }
                    drawPath(path, arrowColor)
                }
                Text(
                    String.format(Locale.ROOT, "%.1f m", current.distanceTo(target.position)),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold
                )
                Caption(
                    "A seta aponta na direção do mapa (relativa às âncoras), não necessariamente pra onde seu celular está " +
                        "virado — o app não tem uma bússola calibrada nesse referencial.",
                    Modifier.padding(horizontal = 16.dp),
                    TextAlign.Center
                )
            } else {
                CircularProgressIndicator()
                Text("Aguardando pelo menos 3 âncoras visíveis…")
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun Secondary(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Caption(text: String, modifier: Modifier = Modifier, align: TextAlign? = null) {
    Text(text, modifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = align)
}

/**
 * Broadcast mode is a name badge: the id in the middle of the screen is what an observer
 * labels this phone's corner with, so the two can be matched up by eye.
 */
@Composable
private fun BroadcastContent(
    status: BroadcastStatus?,
    localDeviceId: DeviceId?,
    modifier: Modifier = Modifier
) {
    val message = when (status) {
        null, BroadcastStatus.Starting -> stringResource(R.string.broadcast_starting)
        BroadcastStatus.Advertising -> stringResource(R.string.broadcast_advertising)
        is BroadcastStatus.Failed -> stringResource(status.cause.messageRes())
    }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.broadcast_your_id),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            // Placeholder only while the id is being read off disk, which is one frame.
            text = localDeviceId?.value ?: "………",
            style = MaterialTheme.typography.displaySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = message,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** Broadcast failures the user can actually do something about get their own wording. */
@StringRes
private fun Throwable.messageRes(): Int = when (this) {
    is BleAdvertiseException.PermissionDenied -> R.string.advertise_permission_required
    is BleAdvertiseException.BluetoothUnavailable -> R.string.broadcast_failed_bluetooth_off
    is BleAdvertiseException.AdvertisingUnsupported -> R.string.broadcast_failed_unsupported
    else -> R.string.broadcast_failed
}

@Composable
private fun CenteredMessage(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

/** The bottom-bar control that swaps the radio between listening and being heard. */
@Composable
fun ModeSwitcher(
    mode: AppMode,
    onModeSelected: (AppMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Scaffold hands its bottomBar the raw slot, insets included, so the bar has to
    // keep itself clear of the navigation bar under edge-to-edge.
    Surface(modifier = modifier.fillMaxWidth(), tonalElevation = 3.dp) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.navigationBars)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            AppMode.entries.forEachIndexed { index, entry ->
                SegmentedButton(
                    selected = entry == mode,
                    onClick = { onModeSelected(entry) },
                    shape = SegmentedButtonDefaults.itemShape(index, AppMode.entries.size)
                ) {
                    Text(stringResource(entry.labelRes()))
                }
            }
        }
    }
}

@StringRes
private fun AppMode.labelRes(): Int = when (this) {
    AppMode.OBSERVER -> R.string.mode_observer
    AppMode.BROADCAST -> R.string.mode_broadcast
}

@Composable
fun DeviceRow(
    sighting: DeviceSighting,
    modifier: Modifier = Modifier,
    isAnchor: Boolean = false,
    /** Null for a device with no id: there is nothing to key an anchor on, so it isn't clickable. */
    onClick: (() -> Unit)? = null
) {
    val device = sighting.device
    ListItem(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        headlineContent = { Text((if (isAnchor) "[Anchor] " else "") + (device.name ?: "Unknown device")) },
        // The id identifies the row; the address is shown beside it because seeing that
        // change while the row stays put is how you tell rotation is being absorbed.
        supportingContent = {
            Text(device.id?.let { "${it.value} · ${device.address}" } ?: device.address)
        },
        trailingContent = { Text("${sighting.rssi} dBm") }
    )
}
