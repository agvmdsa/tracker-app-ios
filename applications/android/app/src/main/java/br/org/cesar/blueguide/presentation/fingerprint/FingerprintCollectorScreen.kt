package br.org.cesar.blueguide.presentation.fingerprint

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.org.cesar.blueguide.R
import br.org.cesar.blueguide.domain.position.FingerprintLabel
import br.org.cesar.blueguide.domain.position.FingerprintRecorderState

/**
 * Counterpart of the iOS app's `FingerprintCollectorView`: label the current spot, record
 * for a set duration, see what came of it. Driven directly by [FingerprintRecorder]'s own
 * `StateFlow` (collected by the caller and passed in as [state]) rather than a separate
 * ViewModel — the recorder already has the shape of one.
 */
@Composable
fun FingerprintCollectorScreen(
    state: FingerprintRecorderState,
    modifier: Modifier = Modifier,
    onStartRecording: (FingerprintLabel, durationSeconds: Double) -> Unit,
    onCancelRecording: () -> Unit,
    onDeleteRecording: (java.io.File) -> Unit
) {
    var session by remember { mutableStateOf("") }
    var point by remember { mutableStateOf("") }
    var xText by remember { mutableStateOf("") }
    var yText by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf(1) }

    val label = FingerprintLabel.of(session, point, xText.toDoubleOrNull() ?: Double.NaN, yText.toDoubleOrNull() ?: Double.NaN)

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = session, onValueChange = { session = it }, enabled = !state.isRecording,
                    label = { Text(stringResource(R.string.fingerprint_session)) },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = point, onValueChange = { point = it }, enabled = !state.isRecording,
                    label = { Text(stringResource(R.string.fingerprint_point)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Row(Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(
                        value = xText, onValueChange = { xText = it }, enabled = !state.isRecording,
                        label = { Text(stringResource(R.string.fingerprint_x)) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = yText, onValueChange = { yText = it }, enabled = !state.isRecording,
                        label = { Text(stringResource(R.string.fingerprint_y)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.fingerprint_duration, minutes))
                    Row {
                        OutlinedButton(onClick = { if (minutes > 1) minutes-- }, enabled = !state.isRecording) {
                            Text("−")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = { if (minutes < 60) minutes++ }, enabled = !state.isRecording) {
                            Text("+")
                        }
                    }
                }
            }
        }

        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                val active = state.activeRecording
                if (active != null) {
                    Text(stringResource(R.string.fingerprint_recording, active.label.session, active.label.point, state.recordCount))
                    // Indeterminate: the recorder knows elapsed row counts, not a 0..1
                    // fraction of the planned duration, so this is "recording", not a timer.
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    OutlinedButton(onClick = onCancelRecording) {
                        Text(stringResource(R.string.fingerprint_cancel))
                    }
                } else {
                    Button(onClick = { label?.let { onStartRecording(it, minutes * 60.0) } }, enabled = label != null) {
                        Text(stringResource(R.string.fingerprint_start, minutes))
                    }
                }
                state.errorMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        state.lastRecording?.let { last ->
            item { HorizontalDivider(Modifier.padding(vertical = 16.dp)) }
            item {
                Text(
                    stringResource(R.string.fingerprint_last_recording, last.label.session, last.label.point, last.durationSeconds.toInt()),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            if (last.recordsPerBeacon.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.fingerprint_last_recording_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                items(last.recordsPerBeacon.keys.sortedBy { it.value }) { beacon ->
                    ListItem(
                        headlineContent = { Text(beacon.value) },
                        trailingContent = {
                            Text("${last.recordsPerBeacon[beacon] ?: 0} · %.1f/s".format(last.ratePerSecond(beacon)))
                        }
                    )
                }
            }
        }

        if (state.recordings.isNotEmpty()) {
            item { HorizontalDivider(Modifier.padding(vertical = 16.dp)) }
            item {
                Text(
                    stringResource(R.string.fingerprint_recordings),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            items(state.recordings, key = { it.path }) { recording ->
                ListItem(
                    headlineContent = { Text(recording.name) },
                    trailingContent = {
                        OutlinedButton(onClick = { onDeleteRecording(recording) }) {
                            Text(stringResource(R.string.fingerprint_delete))
                        }
                    }
                )
            }
        }
    }
}
