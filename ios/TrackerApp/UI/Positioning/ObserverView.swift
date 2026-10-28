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
    @AppStorage("trackerapp.positioning.centerRSSI") private var centerRSSI = PathLossModel.defaultCenterRSSI
    @AppStorage("trackerapp.positioning.touchRSSI") private var touchRSSI = PathLossModel.defaultTouchRSSI
    @AppStorage("trackerapp.positioning.isSpeedLimited") private var isSpeedLimited = true
    @AppStorage("trackerapp.positioning.maxSpeedKmh") private var maxSpeedKmh = MotionLimit.defaultMaxSpeedKmh

    private var speedLimit: Double? { isSpeedLimited ? maxSpeedKmh : nil }

    var body: some View {
        List {
            Section("Mapa") {
                PositionMapView(anchors: anchorStore.anchors, ranges: engine?.anchorRanges ?? [], fix: engine?.fix)
                    .frame(height: 300)
                    .listRowInsets(EdgeInsets())
                MapLegend()
                Text("O círculo azul é a incerteza do cálculo (o resíduo das distâncias), não um intervalo de confiança.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }

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
                        Text(anchor.positionDescription)
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

            Section {
                Stepper(value: $centerRSSI, in: -100...(-30), step: 1) {
                    HStack {
                        Text("RSSI no centro")
                        Spacer()
                        Text(String(format: "%.0f dBm", centerRSSI))
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }
                Button {
                    if let current = engine?.currentCenterRSSI {
                        centerRSSI = current.rounded()
                    }
                } label: {
                    if let current = engine?.currentCenterRSSI {
                        Text(String(format: "Usar a leitura de agora (%.0f dBm)", current))
                    } else {
                        Text("Usar a leitura de agora")
                    }
                }
                .disabled(engine?.currentCenterRSSI == nil)

                Stepper(value: $touchRSSI, in: -60...(-10), step: 1) {
                    HStack {
                        Text("RSSI encostado")
                        Spacer()
                        Text(String(format: "%.0f dBm", touchRSSI))
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }
                Button {
                    if let current = engine?.currentTouchRSSI {
                        touchRSSI = current.rounded()
                    }
                } label: {
                    if let current = engine?.currentTouchRSSI {
                        Text(String(format: "Usar a leitura de agora (%.0f dBm)", current))
                    } else {
                        Text("Usar a leitura de agora")
                    }
                }
                .disabled(engine?.currentTouchRSSI == nil)

                Toggle("Limitar pela velocidade", isOn: $isSpeedLimited)
                Stepper(value: $maxSpeedKmh, in: MotionLimit.speedRangeKmh, step: MotionLimit.speedStepKmh) {
                    HStack {
                        Text("Velocidade máxima")
                        Spacer()
                        Text(String(format: "%.1f km/h", maxSpeedKmh).replacingOccurrences(of: ".", with: ","))
                            .monospacedDigit()
                            .foregroundStyle(.secondary)
                    }
                }
                .disabled(!isSpeedLimited)

                if let calibration = engine?.calibration {
                    Text(calibrationSummary(calibration))
                        .font(.caption)
                        .foregroundStyle(calibration.usesTouchRSSI ? Color.secondary : Color.orange)
                }
            } header: {
                Text("Calibração")
            } footer: {
                Text("RSSI no centro: o que o iPhone lê no meio das âncoras, tratado como a distância média delas até o meio (1,77 m num quadrado de 2,5 m). RSSI encostado: o que ele lê encostado num beacon, tratado como 10 cm. Com os dois o app calcula o RSSI a 1 m e o expoente de perda. Fique no meio (ou encoste num beacon) e toque em \"Usar a leitura de agora\". Com o limite de velocidade, a posição anda na direção da nova leitura, no máximo o que daria para andar nessa velocidade desde a anterior.")
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
            newEngine.centerRSSI = centerRSSI
            newEngine.touchRSSI = touchRSSI
            newEngine.maxSpeedKmh = speedLimit
            newEngine.start()
        }
        .onChange(of: centerRSSI) { _, newValue in
            engine?.centerRSSI = newValue
            engine?.refresh()
        }
        .onChange(of: touchRSSI) { _, newValue in
            engine?.touchRSSI = newValue
            engine?.refresh()
        }
        .onChange(of: speedLimit) { _, newValue in
            engine?.maxSpeedKmh = newValue
        }
        .onDisappear {
            engine?.stop()
            engine = nil
        }
    }

    /// The model the two readings give, or why the touching one is being ignored.
    private func calibrationSummary(_ calibration: PathLossCalibration) -> String {
        let model = String(format: "%.0f dBm a 1 m, expoente %.1f", calibration.referenceRSSI, calibration.exponent)
            .replacingOccurrences(of: ".", with: ",")
        return calibration.usesTouchRSSI
            ? "Modelo: " + model + "."
            : "O RSSI encostado precisa ser mais forte que o do centro (e coerente com ele); usando só o centro. Modelo: " + model + "."
    }

    private var unconfiguredSightings: [BeaconSighting] {
        let configuredIDs = Set(anchorStore.anchors.map(\.id))
        return scanner.sightings.values
            .filter { !configuredIDs.contains($0.beaconID) }
            .sorted { $0.rssi > $1.rssi }
    }
}

/// The two hues of the project on the map: the iPhone is blue and the ESP32 beacons are orange (the
/// data-flow animation uses the same ones), stepped for the dark background.
enum MapPalette {
    static let phone = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 0.224, green: 0.529, blue: 0.898, alpha: 1) : UIColor(red: 0.165, green: 0.471, blue: 0.839, alpha: 1)
    })
    static let beacon = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark ? UIColor(red: 0.851, green: 0.349, blue: 0.149, alpha: 1) : UIColor(red: 0.922, green: 0.408, blue: 0.204, alpha: 1)
    })
}

/// A top-down map: the beacons where they were placed, a ring around each one as far as its RSSI says
/// the iPhone is, and the iPhone where the multilateration puts it. It reads the engine's state, so it
/// follows every refresh, and the movement is animated so it glides instead of jumping.
struct PositionMapView: View {
    let anchors: [Anchor]
    let ranges: [AnchorRange]
    let fix: ObserverFix?

    private struct Snapshot: Equatable {
        let position: Point2D?
        let uncertainty: Double
        let distances: [Double]
        let anchorIDs: [String]
    }

    private var snapshot: Snapshot {
        Snapshot(position: fix?.position, uncertainty: fix?.uncertaintyMeters ?? 0, distances: ranges.map(\.distanceMeters), anchorIDs: anchors.map(\.id.value))
    }

    var body: some View {
        GeometryReader { geo in
            let projection = MapProjection(fitting: anchors.map(\.position) + (fix.map { [$0.position] } ?? []), in: geo.size)
            ZStack {
                grid(projection, size: geo.size)
                ForEach(ranges, id: \.anchor.id) { ring($0, projection) }
                if let fix { uncertainty(fix, projection) }
                ForEach(anchors) { anchor in
                    beacon(anchor, projection, range: ranges.first { $0.anchor.id == anchor.id }, viewHeight: geo.size.height)
                }
                if let fix { phone(at: projection.viewPoint(for: fix.position)) }
                if anchors.isEmpty {
                    Text("Cadastre as âncoras: toque num sinal abaixo e informe a posição.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(24)
                }
            }
            .frame(width: geo.size.width, height: geo.size.height)
            .animation(.easeInOut(duration: 0.8), value: snapshot)
        }
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(accessibilityDescription)
    }

    private var accessibilityDescription: String {
        let beacons = "Mapa com \(anchors.count) beacons"
        guard let fix else { return beacons + ", ainda sem a posição do iPhone." }
        return beacons + String(format: ", iPhone estimado em x %.1f, y %.1f metros.", fix.position.x, fix.position.y)
    }

    private func grid(_ projection: MapProjection, size: CGSize) -> some View {
        let step = MapProjection.gridStep(forScale: projection.scale)
        let xs = ticks(projection.visibleX, step: step), ys = ticks(projection.visibleY, step: step)
        return ZStack {
            Path { path in
                for x in xs {
                    path.move(to: projection.viewPoint(for: Point2D(x: x, y: projection.visibleY.lowerBound)))
                    path.addLine(to: projection.viewPoint(for: Point2D(x: x, y: projection.visibleY.upperBound)))
                }
                for y in ys {
                    path.move(to: projection.viewPoint(for: Point2D(x: projection.visibleX.lowerBound, y: y)))
                    path.addLine(to: projection.viewPoint(for: Point2D(x: projection.visibleX.upperBound, y: y)))
                }
            }
            .stroke(Color.secondary.opacity(0.25), lineWidth: 1)
            // No label in the corners, where the two axes' labels would land on top of each other.
            ForEach(xs.filter { (30...(size.width - 10)).contains(projection.viewPoint(for: Point2D(x: $0, y: 0)).x) }, id: \.self) { x in
                Text(label(x, step: step)).position(x: projection.viewPoint(for: Point2D(x: x, y: 0)).x, y: size.height - 9)
            }
            ForEach(ys.filter { (10...(size.height - 26)).contains(projection.viewPoint(for: Point2D(x: 0, y: $0)).y) }, id: \.self) { y in
                Text(label(y, step: step)).position(x: 14, y: projection.viewPoint(for: Point2D(x: 0, y: y)).y)
            }
        }
        .font(.system(size: 10))
        .foregroundStyle(.secondary)
    }

    /// The multiples of `step` inside `range`. Capped so absurd coordinates cannot make a huge grid.
    private func ticks(_ range: ClosedRange<Double>, step: Double) -> [Double] {
        let first = (range.lowerBound / step).rounded(.up), last = (range.upperBound / step).rounded(.down)
        guard last >= first, last - first <= 60, abs(first) < 1e9, abs(last) < 1e9 else { return [] }
        return (Int(first)...Int(last)).map { Double($0) * step }
    }

    private func label(_ value: Double, step: Double) -> String {
        step < 1 ? String(format: "%.1f", value) : String(format: "%.0f", value)
    }

    private func ring(_ range: AnchorRange, _ projection: MapProjection) -> some View {
        let radius = range.distanceMeters * projection.scale
        return Circle()
            .stroke(MapPalette.beacon.opacity(0.45), lineWidth: 1.5)
            .frame(width: 2 * radius, height: 2 * radius)
            .position(projection.viewPoint(for: range.anchor.position))
    }

    private func uncertainty(_ fix: ObserverFix, _ projection: MapProjection) -> some View {
        let radius = fix.uncertaintyMeters * projection.scale
        return Circle()
            .fill(MapPalette.phone.opacity(0.12))
            .overlay(Circle().stroke(MapPalette.phone.opacity(0.5), lineWidth: 1))
            .frame(width: 2 * radius, height: 2 * radius)
            .position(projection.viewPoint(for: fix.position))
    }

    /// The beacon, and under it (above it in the lower half, so it clears the axis labels) how far the
    /// RSSI says the iPhone is from it, with the RSSI itself: the dBm is what calibrating needs.
    private func beacon(_ anchor: Anchor, _ projection: MapProjection, range: AnchorRange?, viewHeight: CGFloat) -> some View {
        let point = projection.viewPoint(for: anchor.position)
        return ZStack {
            RoundedRectangle(cornerRadius: 6)
                .fill(MapPalette.beacon)
                .frame(width: 26, height: 26)
                .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color(.systemBackground), lineWidth: 2))
                .overlay(Text(anchor.label).font(.system(size: 13, weight: .bold)).foregroundStyle(.black).minimumScaleFactor(0.6).lineLimit(1))
                .position(point)
            if let range {
                VStack(spacing: 0) {
                    Text(String(format: "%.1f m", range.distanceMeters)).font(.system(size: 11, weight: .semibold))
                    if let rssi = range.rssi {
                        Text(String(format: "%.0f dBm", rssi)).font(.system(size: 10)).foregroundStyle(.secondary)
                    }
                }
                .foregroundStyle(.primary)
                .position(x: point.x, y: point.y + (point.y > viewHeight * 0.6 ? -31 : 31))
            }
        }
    }

    private func phone(at point: CGPoint) -> some View {
        Circle()
            .fill(MapPalette.phone)
            .frame(width: 18, height: 18)
            .overlay(Circle().stroke(Color(.systemBackground), lineWidth: 2))
            .position(point)
    }
}

struct MapLegend: View {
    var body: some View {
        HStack(spacing: 14) {
            item(RoundedRectangle(cornerRadius: 3).fill(MapPalette.beacon).frame(width: 12, height: 12), "Beacon")
            item(Circle().fill(MapPalette.phone).frame(width: 12, height: 12), "iPhone (estimado)")
            item(Circle().stroke(MapPalette.beacon.opacity(0.6), lineWidth: 1.5).frame(width: 12, height: 12), "Alcance pelo RSSI")
        }
        .font(.caption)
        .foregroundStyle(.secondary)
    }

    private func item(_ icon: some View, _ text: String) -> some View {
        HStack(spacing: 5) { icon; Text(text) }
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
