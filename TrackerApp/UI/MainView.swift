import SwiftUI
import CoreBluetooth
import UIKit

struct MainView: View {
    @EnvironmentObject private var connectionManager: ConnectionManager
    @StateObject private var bluetoothMonitor = BluetoothPermissionMonitor()
    @State private var isRenaming = false
    @State private var nameDraft = DeviceIdentity.displayName

    var body: some View {
        NavigationView {
            Group {
                if bluetoothMonitor.isDenied {
                    PermissionBlockedView()
                } else if connectionManager.discoveredDevices.isEmpty {
                    VStack(spacing: 8) {
                        ProgressView()
                        Text("Searching for nearby trackers…")
                            .foregroundStyle(.secondary)
                    }
                } else {
                    List(connectionManager.discoveredDevices) { device in
                        NavigationLink(destination: TrackingView(device: device)) {
                            DeviceRow(device: device)
                        }
                    }
                }
            }
            .navigationTitle("Nearby Trackers")
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    NavigationLink("Raw Test") {
                        RawMCTestView()
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        nameDraft = DeviceIdentity.displayName
                        isRenaming = true
                    } label: {
                        Label("Rename this device", systemImage: "pencil.circle")
                    }
                }
            }
            .alert("Rename This Device", isPresented: $isRenaming) {
                TextField("Device name", text: $nameDraft)
                Button("Save") {
                    guard !nameDraft.trimmingCharacters(in: .whitespaces).isEmpty else { return }
                    DeviceIdentity.displayName = nameDraft
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This is the name other TrackerApp users will see. Restart the app for the change to take effect.")
            }
            .alert(
                "Connection Error",
                isPresented: Binding(
                    get: { connectionManager.transferError != nil },
                    set: { if !$0 { connectionManager.transferError = nil } }
                )
            ) {
                Button("OK", role: .cancel) { connectionManager.transferError = nil }
            } message: {
                Text(connectionManager.transferError ?? "")
            }
        }
    }
}

private struct DeviceRow: View {
    let device: TrackerDevice

    var body: some View {
        HStack {
            Image(systemName: iconName)
                .foregroundStyle(.blue)
            VStack(alignment: .leading) {
                Text(device.displayName).font(.headline)
                Text(stateLabel).font(.caption).foregroundStyle(.secondary)
            }
        }
    }

    private var iconName: String {
        switch device.state {
        case .discovered: return "dot.radiowaves.left.and.right"
        case .connecting: return "arrow.triangle.2.circlepath"
        case .connected: return "checkmark.circle.fill"
        case .tracking: return "location.fill"
        case .disconnected: return "wifi.slash"
        }
    }

    private var stateLabel: String {
        switch device.state {
        case .discovered: return "Discovered"
        case .connecting: return "Connecting…"
        case .connected: return "Connected"
        case .tracking: return "Tracking"
        case .disconnected: return "Disconnected"
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
            Text("TrackerApp needs Bluetooth, Local Network, and Nearby Interaction access to discover and track other iPhones. Please enable them in Settings.")
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
/// one permission iOS exposes synchronously via CoreBluetooth; Local Network and Nearby
/// Interaction denials instead surface as delegate/session errors, reported separately via
/// `ConnectionManager.transferError`.
private final class BluetoothPermissionMonitor: NSObject, ObservableObject, CBCentralManagerDelegate {
    @Published var isDenied = false
    private var manager: CBCentralManager?

    override init() {
        super.init()
        manager = CBCentralManager(delegate: self, queue: nil, options: [CBCentralManagerOptionShowPowerAlertKey: false])
    }

    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        isDenied = central.state == .unauthorized
    }
}
