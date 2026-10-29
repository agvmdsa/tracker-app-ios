package br.org.cesar.blueguide.core.ui

import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Tracks whether [permission] is granted, asking the user for it the first time
 * [requestWhenMissing] is true. Broadcast mode only needs BLUETOOTH_ADVERTISE once it is
 * actually selected, so callers gate the prompt instead of asking for everything upfront.
 */
@Composable
fun rememberPermissionGranted(
    permission: String,
    requestWhenMissing: Boolean = true
): Boolean {
    val context = LocalContext.current

    var granted by remember(permission) {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Android only shows the system dialog once per permission; asking again after a
    // denial silently returns "denied", so we stop after the first attempt.
    var alreadyAsked by remember(permission) { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted = isGranted }

    LaunchedEffect(requestWhenMissing, granted) {
        if (requestWhenMissing && !granted && !alreadyAsked) {
            alreadyAsked = true
            launcher.launch(permission)
        }
    }

    return granted
}
