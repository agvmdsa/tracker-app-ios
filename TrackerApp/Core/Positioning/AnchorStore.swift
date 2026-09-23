import Foundation
import Observation

@Observable
@MainActor
final class AnchorStore {
    private static let key = "trackerapp.positioning.anchors"
    private static let labels = Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ").map(String.init)

    private(set) var anchors: [Anchor] = []

    nonisolated init() {
        anchors = Self.load()
    }

    @discardableResult
    func addAnchor(id: BeaconID, label: String? = nil, position: Point2D) -> Anchor {
        let anchor = Anchor(id: id, label: label?.isEmpty == false ? label! : nextLabel(), position: position)
        anchors.removeAll { $0.id == id }
        anchors.append(anchor)
        persist()
        return anchor
    }

    func removeAnchor(id: BeaconID) {
        anchors.removeAll { $0.id == id }
        persist()
    }

    private func nextLabel() -> String {
        let used = Set(anchors.map(\.label))
        return Self.labels.first { !used.contains($0) } ?? "#\(anchors.count + 1)"
    }

    private func persist() {
        guard let data = try? JSONEncoder().encode(anchors) else { return }
        UserDefaults.standard.set(data, forKey: Self.key)
    }

    private nonisolated static func load() -> [Anchor] {
        guard let data = UserDefaults.standard.data(forKey: key),
              let decoded = try? JSONDecoder().decode([Anchor].self, from: data)
        else { return [] }
        return decoded
    }
}
