import SwiftUI

private struct BeaconAdvertisingKey: EnvironmentKey {
    static let defaultValue: any BeaconAdvertising = BeaconAdvertiser()
}

private struct BeaconScanningKey: EnvironmentKey {
    static let defaultValue: any BeaconScanning = BeaconScanner()
}

private struct AnchorStoreKey: EnvironmentKey {
    static let defaultValue = AnchorStore()
}

private struct ARPositionTrackingKey: EnvironmentKey {
    static let defaultValue: any ARPositionTracking = ARPositionTracker()
}

private struct SavedLocationStoreKey: EnvironmentKey {
    static let defaultValue = SavedLocationStore()
}

extension EnvironmentValues {
    var beaconAdvertiser: any BeaconAdvertising {
        get { self[BeaconAdvertisingKey.self] }
        set { self[BeaconAdvertisingKey.self] = newValue }
    }

    var beaconScanner: any BeaconScanning {
        get { self[BeaconScanningKey.self] }
        set { self[BeaconScanningKey.self] = newValue }
    }

    var anchorStore: AnchorStore {
        get { self[AnchorStoreKey.self] }
        set { self[AnchorStoreKey.self] = newValue }
    }

    var arPositionTracker: any ARPositionTracking {
        get { self[ARPositionTrackingKey.self] }
        set { self[ARPositionTrackingKey.self] = newValue }
    }

    var savedLocationStore: SavedLocationStore {
        get { self[SavedLocationStoreKey.self] }
        set { self[SavedLocationStoreKey.self] = newValue }
    }
}
