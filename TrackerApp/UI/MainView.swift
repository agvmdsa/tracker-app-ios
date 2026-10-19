import SwiftUI
import CoreBluetooth
import Observation
import UIKit

struct MainView: View {
    @State private var bluetoothMonitor = BluetoothPermissionMonitor()

    var body: some View {
        NavigationView {
            Group {
                if bluetoothMonitor.isDenied {
                    PermissionBlockedView()
                } else {
                    TrackingView()
                }
            }
            .navigationTitle("TrackerApp")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    NavigationLink("Indoor") {
                        PositioningModeView()
                    }
                }
            }
        }
    }
}

/// Edge Case: Permission Denial → blocking screen with a button to open iOS Settings.
private struct PermissionBlockedView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle.fill")
                .font(.largeTitle)
                .foregroundStyle(.orange)
            Text("Permission Required")
                .font(.title2).bold()
            Text("TrackerApp needs Bluetooth and Nearby Interaction access to find and track other iPhones. Please enable them in Settings.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }
}

/// Minimal permission-denial detector for the blocking screen. Bluetooth authorization is the
/// one permission iOS exposes synchronously via CoreBluetooth; Nearby Interaction denials
/// instead surface as session errors, reported separately via `ProximityTracker.errorMessage`.
@Observable
@MainActor
private final class BluetoothPermissionMonitor: NSObject, CBCentralManagerDelegate {
    var isDenied = false
    private var manager: CBCentralManager?

    override init() {
        super.init()
        manager = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionShowPowerAlertKey: false])
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        isDenied = central.state.indicatesPermissionDenied
    }
}
