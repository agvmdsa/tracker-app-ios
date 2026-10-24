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

private struct DeviceMotionReadingKey: EnvironmentKey {
    static let defaultValue: any DeviceMotionReading = DeviceMotionReader()
}

private struct AltitudeReadingKey: EnvironmentKey {
    static let defaultValue: any AltitudeReading = AltitudeReader()
}

private struct MotionActivityReadingKey: EnvironmentKey {
    static let defaultValue: any MotionActivityReading = MotionActivityReader()
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

    var deviceMotion: any DeviceMotionReading {
        get { self[DeviceMotionReadingKey.self] }
        set { self[DeviceMotionReadingKey.self] = newValue }
    }

    var altimeter: any AltitudeReading {
        get { self[AltitudeReadingKey.self] }
        set { self[AltitudeReadingKey.self] = newValue }
    }

    var motionActivity: any MotionActivityReading {
        get { self[MotionActivityReadingKey.self] }
        set { self[MotionActivityReadingKey.self] = newValue }
    }
}
