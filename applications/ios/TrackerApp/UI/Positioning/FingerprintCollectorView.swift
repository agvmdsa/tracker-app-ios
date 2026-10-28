import SwiftUI

struct FingerprintCollectorView: View {
    let onExit: () -> Void

    @Environment(\.beaconScanner) private var scanner: any BeaconScanning
    @Environment(\.deviceMotion) private var deviceMotion: any DeviceMotionReading
    @Environment(\.altimeter) private var altimeter: any AltitudeReading
    @Environment(\.motionActivity) private var motionActivity: any MotionActivityReading
    @Environment(\.anchorStore) private var anchorStore: AnchorStore
    @State private var recorder: FingerprintRecorder?
    @State private var editingBeaconID: BeaconID?
    @State private var session = ""
    @State private var point = ""
    @State private var xText = ""
    @State private var yText = ""
    @State private var minutes = 1

    private var label: FingerprintLabel? {
        guard let x = DecimalInput.parse(xText), let y = DecimalInput.parse(yText) else { return nil }
        return FingerprintLabel(session: session, point: point, x: x, y: y)
    }

    private var isRecording: Bool { recorder?.isRecording == true }

    var body: some View {
        List {
            Section {
                TextField("Sessão (ex: S1)", text: $session)
                TextField("Ponto (ex: P3)", text: $point)
                TextField("x (m)", text: $xText)
                    .keyboardType(.numbersAndPunctuation)
                TextField("y (m)", text: $yText)
                    .keyboardType(.numbersAndPunctuation)
                Stepper("Duração: \(minutes) min", value: $minutes, in: 1...60)
            } header: {
                Text("Onde você está")
            } footer: {
                Text("Sessão e ponto: até \(FingerprintLabel.maxTokenLength) letras, números, _ ou -. x e y em metros.")
            }
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .disabled(isRecording)

            if let recorder {
                Section {
                    if let recording = recorder.activeRecording {
                        ProgressView(timerInterval: recording.startedAt...recording.endsAt, countsDown: false) {
                            Text("Gravando \(recording.label.session) / \(recording.label.point) — \(recorder.recordCount) registros")
                        }
                        Button("Cancelar e descartar", role: .destructive) { recorder.cancelRecording() }
                    } else {
                        Button("Gravar por \(minutes) min") {
                            guard let label else { return }
                            recorder.startRecording(label: label, duration: TimeInterval(minutes * 60))
                        }
                        .disabled(label == nil)
                        if let warning = layoutWarning(recorder.layoutCheck) {
                            Label(warning, systemImage: "exclamationmark.triangle")
                                .font(.footnote)
                                .foregroundStyle(.orange)
                        }
                    }
                    if let errorMessage = recorder.errorMessage {
                        Text(errorMessage)
                            .foregroundStyle(.red)
                    }
                }

                if let last = recorder.lastRecording {
                    Section {
                        if last.recordsPerBeacon.isEmpty {
                            Text("Nenhum beacon ouvido.")
                                .foregroundStyle(.secondary)
                        }
                        ForEach(last.recordsPerBeacon.keys.sorted { $0.value < $1.value }) { beacon in
                            HStack {
                                Text(beacon.value)
                                    .font(.system(.body, design: .monospaced))
                                Spacer()
                                Text("\(last.recordsPerBeacon[beacon] ?? 0) · \(last.ratePerSecond(of: beacon), specifier: "%.1f")/s")
                                    .foregroundStyle(.secondary)
                            }
                        }
                    } header: {
                        Text("Última gravação: \(last.label.session) / \(last.label.point), \(Int(last.duration.rounded())) s")
                    } footer: {
                        Text("Os beacons ESP32 anunciam 10 vezes por segundo (intervalo de 100 ms): a taxa mostra quantos desses anúncios o iPhone entregou ao app.")
                    }
                }

                Section {
                    if beaconRows.isEmpty {
                        Text(scanner.isBluetoothUnavailable
                            ? "Bluetooth indisponível — verifique se está ligado e se o app tem permissão."
                            : "Nenhum beacon por perto nem cadastrado.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(beaconRows) { row in
                        Button {
                            editingBeaconID = row.id
                        } label: {
                            BeaconRowView(row: row, count: recorder.recordsPerBeacon[row.id])
                        }
                        .foregroundStyle(.primary)
                        .disabled(isRecording)
                    }
                } header: {
                    Text("Beacons")
                } footer: {
                    Text("Toque num beacon para cadastrar ou corrigir a posição, na mesma origem e nos mesmos eixos dos pontos. As posições são copiadas para o .json de cada gravação.")
                }

                Section("Este aparelho") {
                    LabeledContent("ID", value: recorder.node.value)
                    LabeledContent("Modelo", value: recorder.model)
                    LabeledContent("Sistema", value: recorder.systemVersion)
                    DeviceMotionRow(deviceMotion: deviceMotion)
                    AltitudeRow(altimeter: altimeter)
                    MotionActivityRow(motionActivity: motionActivity)
                }

                Section {
                    if recorder.recordings.isEmpty {
                        Text("Nenhuma ainda.")
                            .foregroundStyle(.secondary)
                    }
                    ForEach(recorder.recordings, id: \.self) { url in
                        let files = recorder.files(of: url)
                        ShareLink(items: files) {
                            Label {
                                Text(url.lastPathComponent)
                                // No metadata: the app quit before the recording's time was up.
                                if files.count < 2 {
                                    Text("Incompleta — descarte")
                                        .font(.caption)
                                        .foregroundStyle(.red)
                                }
                            } icon: {
                                Image(systemName: "doc.text")
                            }
                        }
                    }
                    .onDelete { offsets in
                        for url in offsets.map({ recorder.recordings[$0] }) {
                            recorder.deleteRecording(url)
                        }
                    }
                    if recorder.recordings.count > 1 {
                        ShareLink("Exportar todas", items: recorder.recordings.flatMap { recorder.files(of: $0) })
                    }
                } header: {
                    Text("Gravações")
                } footer: {
                    Text("Cada gravação é um .csv com as leituras e um .json com a janela e o contexto; compartilhe os dois.")
                }
            }
        }
        .navigationTitle("Coleta")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button("Voltar", action: onExit)
                    .disabled(isRecording)
            }
        }
        // Leaving the screen discards the recording in progress; cancelling has to be explicit.
        .navigationBarBackButtonHidden(isRecording)
        // Locking the screen would background the app and stop the scan mid-recording.
        .onChange(of: isRecording) { _, isRecording in
            UIApplication.shared.isIdleTimerDisabled = isRecording
        }
        .sheet(item: $editingBeaconID) { beaconID in
            AnchorPositionEditor(beaconID: beaconID, anchorStore: anchorStore)
        }
        .onAppear {
            let anchorStore = anchorStore
            let newRecorder = FingerprintRecorder(
                scanner: scanner,
                deviceMotion: deviceMotion,
                altimeter: altimeter,
                motionActivity: motionActivity,
                uploader: PositioningAPIClient(),
                anchors: { anchorStore.anchors }
            )
            recorder = newRecorder
            newRecorder.start()
        }
        .onDisappear {
            recorder?.stop()
            recorder = nil
            UIApplication.shared.isIdleTimerDisabled = false
        }
    }

    /// Every beacon that is heard or has a position: positioned ones first, then the rest.
    private var beaconRows: [BeaconRow] {
        let anchorsByID = Dictionary(anchorStore.anchors.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        return Set(anchorsByID.keys).union(scanner.sightings.keys)
            .map { BeaconRow(id: $0, anchor: anchorsByID[$0], sighting: scanner.sightings[$0]) }
            .sorted { ($0.anchor == nil ? 1 : 0, $0.id.value) < ($1.anchor == nil ? 1 : 0, $1.id.value) }
    }

    private func layoutWarning(_ check: BeaconLayoutCheck) -> String? {
        var parts: [String] = []
        if !check.withoutPosition.isEmpty {
            parts.append("\(check.withoutPosition.count) ouvido(s) sem posição")
        }
        if !check.notHeard.isEmpty {
            parts.append("\(check.notHeard.count) cadastrado(s) e não ouvido(s)")
        }
        return parts.isEmpty ? nil : "Beacons: " + parts.joined(separator: ", ") + " — veja abaixo."
    }
}

private struct BeaconRow: Identifiable {
    let id: BeaconID
    let anchor: Anchor?
    let sighting: BeaconSighting?
}

private struct BeaconRowView: View {
    let row: BeaconRow
    /// Rows written for it by the recording in progress.
    let count: Int?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack {
                Text(row.id.value)
                    .font(.system(.body, design: .monospaced))
                if let anchor = row.anchor {
                    Text(anchor.label).bold()
                }
                Spacer()
                if let sighting = row.sighting {
                    Text("\(sighting.rssi) dBm")
                        .foregroundStyle(.secondary)
                }
                if let count {
                    Text("· \(count)")
                        .foregroundStyle(.secondary)
                }
            }
            Group {
                if let anchor = row.anchor {
                    Text(anchor.positionDescription)
                        .foregroundStyle(.secondary)
                } else {
                    Text("Sem posição cadastrada")
                        .foregroundStyle(.orange)
                }
                if row.sighting == nil {
                    Text("Não ouvido nos últimos segundos — está ligado?")
                        .foregroundStyle(.red)
                }
            }
            .font(.caption)
        }
    }
}

/// Its own view so the 50 readings a second only redraw this row, not the whole list.
private struct DeviceMotionRow: View {
    let deviceMotion: any DeviceMotionReading

    var body: some View {
        LabeledContent("Orientação (rad)") {
            if let sample = deviceMotion.latest {
                Text(String(format: "%+.2f  %+.2f  %+.2f", sample.roll, sample.pitch, sample.yaw))
                    .monospacedDigit()
            } else {
                Text("indisponível")
            }
        }
    }
}

private struct AltitudeRow: View {
    let altimeter: any AltitudeReading

    var body: some View {
        LabeledContent("Altitude relativa (m)") {
            if let sample = altimeter.latest {
                Text(String(format: "%+.2f", sample.relativeAltitudeM))
                    .monospacedDigit()
            } else {
                Text("indisponível")
            }
        }
    }
}

private struct MotionActivityRow: View {
    let motionActivity: any MotionActivityReading

    var body: some View {
        LabeledContent("Atividade") {
            if let sample = motionActivity.latest {
                Text("\(sample.state.rawValue) (confiança \(sample.confidence))")
            } else {
                Text("indisponível")
            }
        }
    }
}

#Preview {
    let beaconA = BeaconID(hex: "BEAC0001")!
    let beaconB = BeaconID(hex: "BEAC0002")!
    let beaconC = BeaconID(hex: "BEAC0003")!

    let scanner = FakeBeaconScanning(sightings: [
        beaconA: BeaconSighting(beaconID: beaconA, rssi: -58, seenAt: Date()),
        beaconB: BeaconSighting(beaconID: beaconB, rssi: -71, seenAt: Date()),
    ])
    let anchorStore = AnchorStore()
    anchorStore.addAnchor(id: beaconA, label: "baixo", position: Point2D(x: 2.5, y: 0.75), height: 2.1)
    anchorStore.addAnchor(id: beaconC, label: "cima", position: Point2D(x: 2.5, y: 4.25))

    return NavigationView {
        FingerprintCollectorView(onExit: {})
            .environment(\.beaconScanner, scanner)
            .environment(\.deviceMotion, FakeDeviceMotionReading())
            .environment(\.altimeter, FakeAltitudeReading())
            .environment(\.motionActivity, FakeMotionActivityReading())
            .environment(\.anchorStore, anchorStore)
    }
}
