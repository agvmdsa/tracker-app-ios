package br.org.cesar.blueguide.feature.find.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.org.cesar.blueguide.feature.find.domain.HandshakeState
import br.org.cesar.blueguide.feature.find.domain.RangingState
import br.org.cesar.blueguide.feature.find.domain.UwbTrackerState

/**
 * "Find this phone" over UWB — counterpart of the iOS app's `TrackingView`. Android-to-Android
 * only (see `UwbHandshakeProtocol`'s doc comment), and only on UWB-chip-equipped phones; see
 * `AndroidUwbSessionController`'s caveat about this not being verified against real hardware.
 */
@Composable
fun FindDeviceScreen(
    state: UwbTrackerState,
    isSupported: Boolean,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    if (!isSupported) {
        Column(
            modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("This device does not support Ultra Wideband ranging.", textAlign = TextAlign.Center)
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(16.dp)) {
        item {
            if (state.isRunning) {
                OutlinedButton(onClick = onStop) { Text("Stop") }
            } else {
                Button(onClick = onStart) { Text("Find a nearby phone") }
            }
        }

        if (state.isRunning) {
            item {
                Column(Modifier.padding(top = 16.dp)) {
                    Text("Handshake: ${state.handshakeState.describe()}", style = MaterialTheme.typography.bodyMedium)
                    Text("Ranging: ${state.rangingState.describe()}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            state.distance?.let { distance ->
                item {
                    Column(Modifier.padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.2f m".format(distance), style = MaterialTheme.typography.displayMedium)
                        state.azimuth?.let { Text("azimuth %.0f°".format(Math.toDegrees(it.toDouble()))) }
                        state.elevation?.let { Text("elevation %.0f°".format(Math.toDegrees(it.toDouble()))) }
                    }
                }
            }

            state.errorMessage?.let { error ->
                item {
                    Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                }
            }

            if (state.logs.isNotEmpty()) {
                item { HorizontalDivider(Modifier.padding(vertical = 16.dp)) }
                items(state.logs.asReversed()) { line ->
                    Text(line, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun HandshakeState.describe(): String = when (this) {
    HandshakeState.Idle -> "idle"
    HandshakeState.Searching -> "searching"
    HandshakeState.Exchanging -> "exchanging"
    HandshakeState.Completed -> "completed"
    is HandshakeState.Failed -> "failed: $message"
}

private fun RangingState.describe(): String = when (this) {
    RangingState.Idle -> "idle"
    RangingState.WaitingForPeer -> "waiting for peer"
    RangingState.Ranging -> "ranging"
    is RangingState.Failed -> "failed: $message"
}
