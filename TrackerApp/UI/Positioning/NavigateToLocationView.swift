import SwiftUI

struct NavigateToLocationView: View {
    let target: SavedLocation
    let engine: PositioningEngine

    var body: some View {
        VStack(spacing: 28) {
            if let current = engine.fix?.position {
                Image(systemName: "location.north.fill")
                    .font(.system(size: 72))
                    .foregroundStyle(.blue)
                    .rotationEffect(.radians(bearingRadians(from: current, to: target.position)))
                    .animation(.easeInOut, value: bearingRadians(from: current, to: target.position))

                Text(String(format: "%.1f m", current.distance(to: target.position)))
                    .font(.system(size: 40, weight: .bold, design: .rounded))

                Text("A seta aponta na direção do mapa (relativa às âncoras), não necessariamente pra onde seu celular está virado — o app não tem uma bússola calibrada nesse referencial.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            } else {
                ProgressView("Aguardando pelo menos 3 âncoras visíveis…")
            }
        }
        .padding()
        .navigationTitle(target.label)
        .navigationBarTitleDisplayMode(.inline)
    }

    private func bearingRadians(from current: Point2D, to destination: Point2D) -> Double {
        atan2(destination.x - current.x, destination.y - current.y)
    }
}

#Preview {
    let anchorA = BeaconID(hex: "9F3C1A02")!
    let anchorB = BeaconID(hex: "3F8A1C04")!
    let anchorC = BeaconID(hex: "1A2B3C4D")!

    let anchorStore = AnchorStore()
    anchorStore.addAnchor(id: anchorA, label: "A", position: Point2D(x: 0, y: 3))
    anchorStore.addAnchor(id: anchorB, label: "B", position: Point2D(x: 1.5, y: -0.9))
    anchorStore.addAnchor(id: anchorC, label: "C", position: Point2D(x: -1.5, y: -0.9))

    let scanner = FakeBeaconScanning(sightings: [
        anchorA: BeaconSighting(beaconID: anchorA, rssi: -55, seenAt: Date()),
        anchorB: BeaconSighting(beaconID: anchorB, rssi: -62, seenAt: Date()),
        anchorC: BeaconSighting(beaconID: anchorC, rssi: -70, seenAt: Date()),
    ])

    let engine = PositioningEngine(scanner: scanner, anchorStore: anchorStore)
    engine.refresh()

    return NavigationView {
        NavigateToLocationView(
            target: SavedLocation(id: UUID(), label: "Meu carro", position: Point2D(x: 5, y: 8), savedAt: Date()),
            engine: engine
        )
    }
}
