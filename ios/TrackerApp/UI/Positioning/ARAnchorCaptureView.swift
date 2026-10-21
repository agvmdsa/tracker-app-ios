import ARKit
import SwiftUI

private struct ARCameraPreview: UIViewRepresentable {
    let session: ARSession

    func makeUIView(context: Context) -> ARSCNView {
        let view = ARSCNView()
        view.session = session
        view.automaticallyUpdatesLighting = true
        return view
    }

    func updateUIView(_ uiView: ARSCNView, context: Context) {}
}

struct ARAnchorCaptureView: View {
    let onExit: () -> Void

    @Environment(\.arPositionTracker) private var tracker: any ARPositionTracking
    @Environment(\.beaconScanner) private var scanner: any BeaconScanning
    @Environment(\.anchorStore) private var anchorStore: AnchorStore
    @State private var selectedBeaconID: BeaconID?

    var body: some View {
        ZStack(alignment: .bottom) {
            ARCameraPreview(session: tracker.session)
                .ignoresSafeArea()

            VStack(spacing: 16) {
                pill(trackingStatusText)

                if unconfiguredSightings.isEmpty {
                    pill("Nenhum sinal novo por perto.")
                } else {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(unconfiguredSightings, id: \.beaconID) { sighting in
                                Button {
                                    selectedBeaconID = sighting.beaconID
                                } label: {
                                    Text(sighting.beaconID.value)
                                        .font(.system(.footnote, design: .monospaced))
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                        .background(
                                            selectedBeaconID == sighting.beaconID ? Color.accentColor : Color.black.opacity(0.6),
                                            in: Capsule()
                                        )
                                        .foregroundStyle(.white)
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                }

                Button("Marcar posição aqui", action: markSelectedBeacon)
                    .buttonStyle(.borderedProminent)
                    .disabled(selectedBeaconID == nil || tracker.currentPosition == nil)

                Button("Concluir", action: onExit)
                    .buttonStyle(.bordered)
                    .tint(.white)
            }
            .padding(.bottom, 24)
        }
        .onAppear {
            tracker.start()
            scanner.startScanning()
        }
        .onDisappear {
            tracker.stop()
            scanner.stopScanning()
        }
    }

    private func pill(_ text: String) -> some View {
        Text(text)
            .font(.footnote)
            .padding(8)
            .background(.black.opacity(0.6), in: RoundedRectangle(cornerRadius: 8))
            .foregroundStyle(.white)
    }

    private var trackingStatusText: String {
        switch tracker.trackingState {
        case .normal:
            return "Rastreamento estável"
        case .notAvailable:
            return "Iniciando rastreamento…"
        case .limited(let reason):
            switch reason {
            case .excessiveMotion: return "Mova o celular mais devagar"
            case .insufficientFeatures: return "Aponte pra uma área com mais detalhes visuais"
            case .initializing: return "Iniciando…"
            case .relocalizing: return "Recalibrando…"
            @unknown default: return "Rastreamento limitado"
            }
        }
    }

    private var unconfiguredSightings: [BeaconSighting] {
        let configuredIDs = Set(anchorStore.anchors.map(\.id))
        return scanner.sightings.values
            .filter { !configuredIDs.contains($0.beaconID) }
            .sorted { $0.rssi > $1.rssi }
    }

    private func markSelectedBeacon() {
        guard let beaconID = selectedBeaconID, let position = tracker.currentPosition else { return }
        anchorStore.addAnchor(id: beaconID, position: position)
        selectedBeaconID = nil
    }
}

#Preview {
    let unconfiguredA = BeaconID(hex: "B7E2F910")!
    let unconfiguredB = BeaconID(hex: "02D4E611")!

    return ARAnchorCaptureView(onExit: {})
        .environment(\.arPositionTracker, FakeARPositionTracking())
        .environment(\.beaconScanner, FakeBeaconScanning(sightings: [
            unconfiguredA: BeaconSighting(beaconID: unconfiguredA, rssi: -58, seenAt: Date()),
            unconfiguredB: BeaconSighting(beaconID: unconfiguredB, rssi: -71, seenAt: Date()),
        ]))
        .environment(\.anchorStore, AnchorStore())
}
