import Foundation

/// One line of the on-screen diagnostic log shown by `TrackingView`.
struct TrackingLogEntry: Identifiable, Equatable {
    let id = UUID()
    let date = Date()
    let message: String
}
