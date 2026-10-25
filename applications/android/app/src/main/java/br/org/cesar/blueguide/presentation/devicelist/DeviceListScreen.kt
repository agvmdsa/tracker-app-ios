package br.org.cesar.blueguide.presentation.devicelist

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.org.cesar.blueguide.R
import br.org.cesar.blueguide.domain.ble.BleAdvertiseException
import br.org.cesar.blueguide.domain.ble.BroadcastStatus
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.entity.DeviceSighting

@Composable
fun DeviceListScreen(
    state: DeviceListState,
    modifier: Modifier = Modifier,
    /** Tapping a device with a known id opens the anchor editor for it (`AnchorEditorDialog`). */
    onDeviceClick: (DeviceId) -> Unit = {}
) {
    when (state.mode) {
        AppMode.OBSERVER ->
            if (state.scanGranted) {
                ObserverContent(state, modifier, onDeviceClick)
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

@Composable
private fun ObserverContent(
    state: DeviceListState,
    modifier: Modifier = Modifier,
    onDeviceClick: (DeviceId) -> Unit = {}
) {
    if (state.visibleDevices.isEmpty()) {
        CenteredMessage(stringResource(R.string.observer_searching), modifier)
        return
    }

    val anchorIds = state.anchors.map { it.id }.toSet()

    Column(modifier = modifier) {
        PositionMap(
            fix = state.observerFix,
            anchorsHeard = state.anchorsHeard,
            anchorsConfigured = state.anchors.size,
            modifier = Modifier.fillMaxWidth().weight(1f)
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(state.visibleDevices, key = { it.device.key }) { sighting ->
                DeviceRow(
                    sighting = sighting,
                    isAnchor = sighting.device.id in anchorIds,
                    onClick = sighting.device.id?.let { id -> { onDeviceClick(id) } }
                )
            }
        }
    }
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
