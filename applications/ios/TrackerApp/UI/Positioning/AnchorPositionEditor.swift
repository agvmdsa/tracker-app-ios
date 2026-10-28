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
    private var heightIsValid: Bool {
        heightText.trimmingCharacters(in: .whitespaces).isEmpty || DecimalInput.parse(heightText) != nil
    }

    private var canSave: Bool {
        DecimalInput.parse(xText) != nil && DecimalInput.parse(yText) != nil && heightIsValid
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
                        guard let x = DecimalInput.parse(xText), let y = DecimalInput.parse(yText), heightIsValid else { return }
                        anchorStore.addAnchor(id: beaconID, label: label, position: Point2D(x: x, y: y), height: DecimalInput.parse(heightText))
                        dismiss()
                    }
                    .disabled(!canSave)
                }
            }
            .onAppear {
                guard let anchor = anchorStore.anchor(id: beaconID) else { return }
                label = anchor.label
                xText = DecimalInput.text(for: anchor.position.x)
                yText = DecimalInput.text(for: anchor.position.y)
                heightText = anchor.height.map { DecimalInput.text(for: $0) } ?? ""
            }
        }
    }
}

#Preview {
    AnchorPositionEditor(beaconID: BeaconID(hex: "B7E2F910")!, anchorStore: AnchorStore())
}
