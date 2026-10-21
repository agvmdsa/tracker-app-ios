import SwiftUI

struct ObserverView: View {
    let onExit: () -> Void

    @Environment(\.beaconScanner) private var scanner: any BeaconScanning
    @Environment(\.anchorStore) private var anchorStore: AnchorStore
    @Environment(\.savedLocationStore) private var savedLocationStore: SavedLocationStore
    @State private var engine: PositioningEngine?
    @State private var pendingBeaconID: BeaconID?
    @State private var isARCaptureActive = false
    @State private var isSavingLocation = false
    @State private var newLocationLabel = ""

    var body: some View {
        List {
            Section("Posição estimada") {
                if let fix = engine?.fix {
                    Text(String(format: "x: %.2f m   y: %.2f m", fix.position.x, fix.position.y))
                        .font(.headline)
                    Text(String(format: "incerteza: %.2f m", fix.uncertaintyMeters))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                } else {
                    Text("Aguardando pelo menos 3 âncoras configuradas e visíveis…")
                        .foregroundStyle(.secondary)
                }
            }

            Section("Locais salvos") {
                if savedLocationStore.locations.isEmpty {
                    Text("Nenhum ainda — ache sua posição e salve, por exemplo, onde estacionou.")
                        .foregroundStyle(.secondary)
                }
                ForEach(savedLocationStore.locations) { location in
                    if let engine {
                        NavigationLink(destination: NavigateToLocationView(target: location, engine: engine)) {
                            VStack(alignment: .leading) {
                                Text(location.label).bold()
                                Text(String(format: "(%.1f, %.1f) m", location.position.x, location.position.y))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .onDelete { offsets in
                    for index in offsets {
                        savedLocationStore.remove(id: savedLocationStore.locations[index].id)
                    }
                }
                Button("Salvar posição atual") {
                    newLocationLabel = ""
                    isSavingLocation = true
                }
                .disabled(engine?.fix == nil)
            }

            Section("Âncoras configuradas") {
                if anchorStore.anchors.isEmpty {
                    Text("Nenhuma ainda — toque em um sinal abaixo para configurar.")
                        .foregroundStyle(.secondary)
                }
                ForEach(anchorStore.anchors) { anchor in
                    HStack {
                        Text(anchor.label).bold()
                        Text(anchor.id.value)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(String(format: "(%.1f, %.1f) m", anchor.position.x, anchor.position.y))
                            .foregroundStyle(.secondary)
                    }
                }
                .onDelete { offsets in
                    for index in offsets {
                        anchorStore.removeAnchor(id: anchorStore.anchors[index].id)
                    }
                }
            }

            Section("Sinais próximos") {
                if unconfiguredSightings.isEmpty {
                    Text(scanner.isBluetoothUnavailable
                        ? "Bluetooth indisponível — verifique se está ligado e se o app tem permissão."
                        : "Nenhum sinal novo por perto.")
                        .foregroundStyle(.secondary)
                }
                ForEach(unconfiguredSightings, id: \.beaconID) { sighting in
                    Button {
                        pendingBeaconID = sighting.beaconID
                    } label: {
                        HStack {
                            Text(sighting.beaconID.value)
                                .font(.system(.body, design: .monospaced))
                            Spacer()
                            Text("\(sighting.rssi) dBm")
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .navigationTitle("Observador")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("Voltar", action: onExit)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button("Medir com AR") { isARCaptureActive = true }
            }
        }
        .sheet(item: $pendingBeaconID) { beaconID in
            AnchorPositionEditor(beaconID: beaconID, anchorStore: anchorStore)
        }
        .fullScreenCover(isPresented: $isARCaptureActive) {
            ARAnchorCaptureView(onExit: { isARCaptureActive = false })
        }
        .alert("Salvar posição atual", isPresented: $isSavingLocation) {
            TextField("Nome (ex: Meu carro)", text: $newLocationLabel)
            Button("Salvar") {
                guard let position = engine?.fix?.position else { return }
                let label = newLocationLabel.trimmingCharacters(in: .whitespaces)
                savedLocationStore.save(label: label.isEmpty ? "Local salvo" : label, position: position)
            }
            Button("Cancelar", role: .cancel) {}
        }
        .onAppear {
            let newEngine = PositioningEngine(
                scanner: scanner,
                anchorStore: anchorStore,
                reporter: PositioningAPIClient()
            )
            engine = newEngine
            newEngine.start()
        }
        .onDisappear {
            engine?.stop()
            engine = nil
        }
    }

    private var unconfiguredSightings: [BeaconSighting] {
        let configuredIDs = Set(anchorStore.anchors.map(\.id))
        return scanner.sightings.values
            .filter { !configuredIDs.contains($0.beaconID) }
            .sorted { $0.rssi > $1.rssi }
    }
}

#Preview {
    let anchorA = BeaconID(hex: "9F3C1A02")!
    let anchorB = BeaconID(hex: "3F8A1C04")!
    let anchorC = BeaconID(hex: "1A2B3C4D")!
    let unconfigured = BeaconID(hex: "B7E2F910")!

    let anchorStore = AnchorStore()
    anchorStore.addAnchor(id: anchorA, label: "A", position: Point2D(x: 0, y: 3))
    anchorStore.addAnchor(id: anchorB, label: "B", position: Point2D(x: 1.5, y: -0.9))
    anchorStore.addAnchor(id: anchorC, label: "C", position: Point2D(x: -1.5, y: -0.9))

    let scanner = FakeBeaconScanning(sightings: [
        anchorA: BeaconSighting(beaconID: anchorA, rssi: -55, seenAt: Date()),
        anchorB: BeaconSighting(beaconID: anchorB, rssi: -62, seenAt: Date()),
        anchorC: BeaconSighting(beaconID: anchorC, rssi: -70, seenAt: Date()),
        unconfigured: BeaconSighting(beaconID: unconfigured, rssi: -58, seenAt: Date()),
    ])

    return NavigationView {
        ObserverView(onExit: {})
            .environment(\.beaconScanner, scanner)
            .environment(\.anchorStore, anchorStore)
    }
}
