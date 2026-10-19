import SwiftUI

private struct ConnectionManagingKey: EnvironmentKey {
    static let defaultValue: any ConnectionManaging = ConnectionManager()
}

private struct UWBManagingKey: EnvironmentKey {
    static let defaultValue: any UWBManaging = UWBManager()
}

extension EnvironmentValues {
    var connectionManager: any ConnectionManaging {
        get { self[ConnectionManagingKey.self] }
        set { self[ConnectionManagingKey.self] = newValue }
    }

    var uwbManager: any UWBManaging {
        get { self[UWBManagingKey.self] }
        set { self[UWBManagingKey.self] = newValue }
    }
}
