package br.org.cesar.blueguide

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import br.org.cesar.blueguide.core.ble.BroadcastStatus
import br.org.cesar.blueguide.core.ble.android.AndroidBleAdvertiser
import br.org.cesar.blueguide.core.ble.android.AndroidBleScanner
import br.org.cesar.blueguide.core.ble.android.SharedPrefsLocalDeviceIdProvider
import br.org.cesar.blueguide.core.data.AnchorStore
import br.org.cesar.blueguide.core.data.prefs.SharedPrefsAnchorRepository
import br.org.cesar.blueguide.core.data.prefs.SharedPrefsCalibrationRepository
import br.org.cesar.blueguide.core.model.Anchor
import br.org.cesar.blueguide.core.model.BleDevice
import br.org.cesar.blueguide.core.model.DeviceId
import br.org.cesar.blueguide.core.model.DeviceSighting
import br.org.cesar.blueguide.core.model.Point2D
import br.org.cesar.blueguide.core.network.http.PositioningApiClient
import br.org.cesar.blueguide.core.ui.rememberPermissionGranted
import br.org.cesar.blueguide.core.ui.theme.BlueGuideTheme
import br.org.cesar.blueguide.feature.ar.ui.ArPositionScreen
import br.org.cesar.blueguide.feature.ar.ui.ArPositionViewModel
import br.org.cesar.blueguide.feature.find.ui.FindDeviceScreen
import br.org.cesar.blueguide.feature.find.ui.FindDeviceViewModel
import br.org.cesar.blueguide.feature.fingerprint.ui.FingerprintCollectorScreen
import br.org.cesar.blueguide.feature.fingerprint.ui.FingerprintViewModel
import br.org.cesar.blueguide.feature.positioning.data.SharedPrefsSavedLocationRepository
import br.org.cesar.blueguide.feature.positioning.domain.SavedLocationStore
import br.org.cesar.blueguide.feature.positioning.ui.AnchorEditorDialog
import br.org.cesar.blueguide.feature.positioning.ui.AppMode
import br.org.cesar.blueguide.feature.positioning.ui.DeviceListIntent
import br.org.cesar.blueguide.feature.positioning.ui.DeviceListScreen
import br.org.cesar.blueguide.feature.positioning.ui.DeviceListState
import br.org.cesar.blueguide.feature.positioning.ui.DeviceListViewModel
import br.org.cesar.blueguide.feature.positioning.ui.ModeSwitcher
import dagger.hilt.android.AndroidEntryPoint

/**
 * The four things this app does, side by side rather than nested under one mode switcher —
 * counterpart of the iOS app's top-level split between `ProximityTracker` (here: Find) and
 * indoor positioning (here: Positioning + Fingerprint, which the iOS app nests under one
 * "Indoor" destination instead). AR is a standalone demo of camera-based position tracking
 * (ARCore), not wired into the other tabs' BLE fix — see `ArPositionScreen`'s doc comment.
 *
 * Backed by Navigation-Compose (`NavHost`/`NavController`) rather than a hand-rolled `when`
 * over an enum, so each destination gets its own saved/restored UI state and back-stack
 * entry the same way any other Compose navigation in this app would.
 */
private object Routes {
    const val POSITIONING = "positioning"
    const val FINGERPRINT = "fingerprint"
    const val FIND = "find"
    const val AR = "ar"

    val ALL = listOf(POSITIONING, FINGERPRINT, FIND, AR)

    fun label(route: String): String = when (route) {
        POSITIONING -> "Positioning"
        FINGERPRINT -> "Fingerprint"
        FIND -> "Find"
        AR -> "AR"
        else -> route
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlueGuideTheme {
                val vm: DeviceListViewModel = hiltViewModel()
                val state by vm.state.collectAsStateWithLifecycle()

                val scanGranted = rememberPermissionGranted(Manifest.permission.BLUETOOTH_SCAN)
                // Only worth interrupting the user once they actually ask to broadcast.
                val advertiseGranted = rememberPermissionGranted(
                    permission = Manifest.permission.BLUETOOTH_ADVERTISE,
                    requestWhenMissing = state.mode == AppMode.BROADCAST
                )

                LaunchedEffect(scanGranted, advertiseGranted) {
                    vm.dispatch(DeviceListIntent.PermissionsChanged(scanGranted, advertiseGranted))
                }

                // Which device's anchor editor is open, if any; null means no dialog shown.
                var editingAnchorId by remember { mutableStateOf<DeviceId?>(null) }
                val navController = rememberNavController()
                val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
                    ?: Routes.POSITIONING

                // Fingerprint recording only (AndroidMotionActivityReader degrades to "no
                // reading" without it, same as a hardware gap — not blocking on this).
                rememberPermissionGranted(
                    permission = Manifest.permission.ACTIVITY_RECOGNITION,
                    requestWhenMissing = currentRoute == Routes.FINGERPRINT
                )
                // Find tab only (BleUwbHandshakeChannel/AndroidUwbSessionController need
                // these to actually connect/range; missing either just means the handshake
                // or ranging reports a Failed state, not a crash).
                rememberPermissionGranted(
                    permission = Manifest.permission.BLUETOOTH_CONNECT,
                    requestWhenMissing = currentRoute == Routes.FIND
                )
                rememberPermissionGranted(
                    permission = Manifest.permission.UWB_RANGING,
                    requestWhenMissing = currentRoute == Routes.FIND
                )
                // AR tab only: AndroidArPositionTracker needs this to open the camera.
                rememberPermissionGranted(
                    permission = Manifest.permission.CAMERA,
                    requestWhenMissing = currentRoute == Routes.AR
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Surface(tonalElevation = 2.dp) {
                            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(16.dp)) {
                                Routes.ALL.forEachIndexed { index, route ->
                                    SegmentedButton(
                                        selected = route == currentRoute,
                                        onClick = {
                                            // launchSingleTop + popUpTo(startDestination):
                                            // switching tabs replaces the current one rather
                                            // than stacking (so Back from Find doesn't land
                                            // back on, say, Fingerprint if that's where the
                                            // user happened to come from) and restores each
                                            // tab's own state when returning to it.
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        shape = SegmentedButtonDefaults.itemShape(index, Routes.ALL.size)
                                    ) {
                                        Text(Routes.label(route))
                                    }
                                }
                            }
                        }
                    },
                    bottomBar = {
                        if (currentRoute == Routes.POSITIONING) {
                            ModeSwitcher(
                                mode = state.mode,
                                onModeSelected = { mode ->
                                    vm.dispatch(DeviceListIntent.ModeSelected(mode))
                                }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.POSITIONING,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Routes.POSITIONING) {
                            DeviceListScreen(
                                state = state,
                                onDeviceClick = { id -> editingAnchorId = id },
                                onCenterRssiChange = vm::setCenterRssi,
                                onTouchRssiChange = vm::setTouchRssi,
                                onSpeedLimitChange = vm::setSpeedLimit,
                                onSaveLocation = vm::saveCurrentLocation,
                                onRemoveLocation = vm::removeSavedLocation
                            )
                        }

                        composable(Routes.FINGERPRINT) {
                            val localId = state.localDeviceId
                            if (localId != null) {
                                FingerprintTab(node = localId)
                            }
                        }

                        composable(Routes.FIND) { FindDeviceTab() }

                        composable(Routes.AR) { ArPositionTab() }
                    }
                }

                editingAnchorId?.let { id ->
                    AnchorEditorDialog(
                        deviceId = id,
                        existing = state.anchors.firstOrNull { it.id == id },
                        onDismiss = { editingAnchorId = null },
                        onSave = { label, position, height ->
                            vm.addAnchor(id, label, position, height)
                            editingAnchorId = null
                        },
                        onRemove = {
                            vm.removeAnchor(id)
                            editingAnchorId = null
                        }
                    )
                }
            }
        }
    }
}

/**
 * The Fingerprint tab's content, backed by a [FingerprintViewModel] keyed on [node] — so it
 * (and the recording it may be running) survives a configuration change, the same guarantee
 * [DeviceListViewModel] already gives the Positioning tab.
 */
@Composable
private fun FingerprintTab(
    node: DeviceId,
    modifier: Modifier = Modifier
) {
    val vm = hiltViewModel<FingerprintViewModel, FingerprintViewModel.Factory>(key = node.value) { factory ->
        factory.create(node.value)
    }
    LaunchedEffect(Unit) { vm.refreshAnchors() }
    val recorderState by vm.state.collectAsStateWithLifecycle()

    FingerprintCollectorScreen(
        state = recorderState,
        modifier = modifier,
        onStartRecording = vm::startRecording,
        onCancelRecording = vm::cancelRecording,
        onDeleteRecording = vm::deleteRecording
    )
}

/**
 * The Find tab's content, backed by a [FindDeviceViewModel] — same rotation-survival
 * reasoning as [FingerprintTab]. Unlike the other two tabs this one needs no device
 * identity, so there's no analogous "wait for localDeviceId" gate.
 */
@Composable
private fun FindDeviceTab(modifier: Modifier = Modifier) {
    val vm: FindDeviceViewModel = hiltViewModel()
    val trackerState by vm.state.collectAsStateWithLifecycle()

    FindDeviceScreen(
        state = trackerState,
        isSupported = vm.isSupported,
        modifier = modifier,
        onStart = vm::start,
        onStop = vm::stop
    )
}

/** The AR tab's content, backed by an [ArPositionViewModel] — same rotation-survival
 *  reasoning as [FingerprintTab]/[FindDeviceTab]. */
@Composable
private fun ArPositionTab(modifier: Modifier = Modifier) {
    val vm: ArPositionViewModel = hiltViewModel()
    val arState by vm.state.collectAsStateWithLifecycle()

    ArPositionScreen(
        state = arState,
        isSupported = vm.isSupported,
        modifier = modifier,
        onStart = vm::start,
        onStop = vm::stop
    )
}

@Preview(showBackground = true)
@Composable
private fun ObserverModePreview() {
    // Three configured anchors (an equilateral triangle, just for this preview — AnchorStore
    // itself makes no such assumption any more) plus an id-less peer: enough for a fix, and
    // the fourth row shows the address fallback.
    val ids = listOf("1A2B3C4D" to -61, "3F8A1C04" to -64, "9B20E7D1" to -66)
    val anchors = listOf(
        Anchor(DeviceId("1A2B3C4D"), "A", Point2D(0.0, 1.73)),
        Anchor(DeviceId("3F8A1C04"), "B", Point2D(1.5, -0.87)),
        Anchor(DeviceId("9B20E7D1"), "C", Point2D(-1.5, -0.87))
    )
    BlueGuideTheme {
        PreviewScaffold(
            DeviceListState(
                mode = AppMode.OBSERVER,
                scanGranted = true,
                devicesByKey = ids.associate { (id, rssi) ->
                    id to DeviceSighting(
                        BleDevice("A0:11:22:33:44:55", "Phone $id", DeviceId(id)),
                        rssi,
                        0L
                    )
                } + (
                    "B1:66:77:88:99:AA" to DeviceSighting(
                        BleDevice("B1:66:77:88:99:AA", null),
                        -83,
                        0L
                    )
                    ),
                anchors = anchors
            )
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BroadcastModePreview() {
    BlueGuideTheme {
        PreviewScaffold(
            DeviceListState(
                mode = AppMode.BROADCAST,
                advertiseGranted = true,
                localDeviceId = DeviceId("3F8A1C04"),
                broadcastStatus = BroadcastStatus.Advertising
            )
        )
    }
}

@Composable
private fun PreviewScaffold(state: DeviceListState) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { ModeSwitcher(mode = state.mode, onModeSelected = {}) }
    ) { innerPadding ->
        DeviceListScreen(state = state, modifier = Modifier.padding(innerPadding))
    }
}
