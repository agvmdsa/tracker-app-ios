import Foundation
import Observation

@Observable
@MainActor
final class SavedLocationStore {
    private static let key = "trackerapp.positioning.savedLocations"

    private(set) var locations: [SavedLocation] = []

    nonisolated init() {
        locations = Self.load()
    }

    @discardableResult
    func save(label: String, position: Point2D) -> SavedLocation {
        let location = SavedLocation(id: UUID(), label: label, position: position, savedAt: Date())
        locations.append(location)
        persist()
        return location
    }

    func remove(id: UUID) {
        locations.removeAll { $0.id == id }
        persist()
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(locations) else { return }
        UserDefaults.standard.set(data, forKey: Self.key)
    }

    private nonisolated static func load() -> [SavedLocation] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([SavedLocation].self, from: data)
        else { return [] }
        return decoded
    }
}
