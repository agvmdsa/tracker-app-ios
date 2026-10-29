package br.org.cesar.blueguide.feature.ar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.org.cesar.blueguide.feature.ar.domain.ArTrackingState

/**
 * Live camera-based position tracking — the counterpart of the iOS app's ARKit-backed
 * tracker. A standalone demo of `AndroidArPositionTracker`/ARCore, not wired into the
 * indoor-positioning (BLE) fix the other tabs compute: the iOS app's equivalent visual
 * tracking lives in its own saved-location/navigate-to flow, which this does not attempt
 * to replicate — see `list.md`.
 */
@Composable
fun ArPositionScreen(
    state: ArPositionSnapshot,
    isSupported: Boolean,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!isSupported) {
            Text("ARCore is not available on this device.", textAlign = TextAlign.Center)
            return@Column
        }

        if (state.isTracking) {
            OutlinedButton(onClick = onStop) { Text("Stop") }
        } else {
            Button(onClick = onStart) { Text("Start camera tracking") }
        }

        if (state.isTracking) {
            Text(
                text = "Tracking: ${state.trackingState.name.lowercase()}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            state.currentPosition?.let { position ->
                Text(
                    text = "(%.2f, %.2f) m".format(position.x, position.y),
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
