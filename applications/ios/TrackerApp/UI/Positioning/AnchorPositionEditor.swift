import SwiftUI

struct AnchorPositionEditor: View {
    let beaconID: BeaconID
    let anchorStore: AnchorStore

    @Environment(\.dismiss) private var dismiss
    @State private var label = ""
    @State private var xText = ""
    @State private var yText = ""
    @State private var heightText = ""

    private var isExisting: Bool { anchorStore.anchor(id: beaconID) != nil }

    /// Empty means "not entered"; anything else has to be a number.
    private var heightIsValid: Bool { heightText.isEmpty || Double(heightText) != nil }

    private var canSave: Bool {
        Double(xText) != nil && Double(yText) != nil && heightIsValid
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Identidade") {
                    Text(beaconID.value)
                        .font(.system(.body, design: .monospaced))
                }
                Section {
                    TextField("Rótulo (opcional, ex: A)", text: $label)
                    TextField("x", text: $xText)
                        .keyboardType(.numbersAndPunctuation)
                    TextField("y", text: $yText)
                        .keyboardType(.numbersAndPunctuation)
                    TextField("Altura do chão (opcional)", text: $heightText)
                        .keyboardType(.numbersAndPunctuation)
                } header: {
                    Text("Posição (metros, relativa à origem que você escolher)")
                } footer: {
                    Text("Use a mesma origem e os mesmos eixos dos pontos de coleta.")
                }
            }
            .navigationTitle(isExisting ? "Editar âncora" : "Nova âncora")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") {
                        guard let x = Double(xText), let y = Double(yText), heightIsValid else { return }
                        anchorStore.addAnchor(id: beaconID, label: label, position: Point2D(x: x, y: y), height: Double(heightText))
                        dismiss()
                    }
                    .disabled(!canSave)
                }
            }
            .onAppear {
                guard let anchor = anchorStore.anchor(id: beaconID) else { return }
                label = anchor.label
                xText = String(anchor.position.x)
                yText = String(anchor.position.y)
                heightText = anchor.height.map { String($0) } ?? ""
            }
        }
    }
}

#Preview {
    AnchorPositionEditor(beaconID: BeaconID(hex: "B7E2F910")!, anchorStore: AnchorStore())
}
