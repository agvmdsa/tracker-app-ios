package br.org.cesar.blueguide.presentation.devicelist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.org.cesar.blueguide.R
import br.org.cesar.blueguide.domain.entity.DeviceId
import br.org.cesar.blueguide.domain.position.Anchor
import br.org.cesar.blueguide.domain.position.DecimalInput
import br.org.cesar.blueguide.domain.position.Point2D

/**
 * Add/edit dialog for one [Anchor], keyed by the [DeviceId] of a device tapped in the
 * list — the counterpart of the iOS app's `AnchorPositionEditor`. A simple `AlertDialog`
 * rather than a separate navigation destination, since Navigation-Compose isn't in this
 * module yet (it arrives with the top-level nav work, not anchor editing specifically).
 */
@Composable
fun AnchorEditorDialog(
    deviceId: DeviceId,
    existing: Anchor?,
    onDismiss: () -> Unit,
    onSave: (label: String?, position: Point2D, height: Double?) -> Unit,
    onRemove: () -> Unit
) {
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    // Shown as plain decimals, never with an exponent, which DecimalInput would refuse to read back.
    var xText by remember { mutableStateOf(existing?.position?.x?.let(DecimalInput::text).orEmpty()) }
    var yText by remember { mutableStateOf(existing?.position?.y?.let(DecimalInput::text).orEmpty()) }
    var heightText by remember { mutableStateOf(existing?.height?.let(DecimalInput::text).orEmpty()) }

    // A comma or a dot: a Portuguese keyboard types "2,5".
    val x = DecimalInput.parse(xText)
    val y = DecimalInput.parse(yText)
    /** Empty means "not entered"; anything else has to be a number. */
    val heightValid = heightText.isBlank() || DecimalInput.parse(heightText) != null
    val canSave = x != null && y != null && heightValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (existing == null) R.string.anchor_editor_title_new else R.string.anchor_editor_title_edit)) },
        text = {
            Column {
                Text(deviceId.value, fontFamily = FontFamily.Monospace)
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.anchor_editor_label)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = xText,
                    onValueChange = { xText = it },
                    label = { Text(stringResource(R.string.anchor_editor_x)) },
                    isError = x == null && xText.isNotEmpty(),
                    keyboardOptions = decimalKeyboard,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = yText,
                    onValueChange = { yText = it },
                    label = { Text(stringResource(R.string.anchor_editor_y)) },
                    isError = y == null && yText.isNotEmpty(),
                    keyboardOptions = decimalKeyboard,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { heightText = it },
                    label = { Text(stringResource(R.string.anchor_editor_height)) },
                    isError = !heightValid,
                    keyboardOptions = decimalKeyboard,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
                Text(
                    text = stringResource(R.string.anchor_editor_hint),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (x != null && y != null && heightValid) onSave(label.ifEmpty { null }, Point2D(x, y), DecimalInput.parse(heightText)) },
                enabled = canSave
            ) { Text(stringResource(R.string.anchor_editor_save)) }
        },
        dismissButton = {
            Column {
                if (existing != null) {
                    TextButton(onClick = onRemove) { Text(stringResource(R.string.anchor_editor_remove)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.anchor_editor_cancel)) }
            }
        }
    )
}

/** Digits, a sign and a separator. Either separator is accepted, so the keyboard's own one works. */
private val decimalKeyboard = KeyboardOptions(keyboardType = KeyboardType.Decimal)
