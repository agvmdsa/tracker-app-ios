import SwiftUI

struct AnchorPositionEditor: View {
    let beaconID: BeaconID
    let anchorStore: AnchorStore

    @Environment(\.dismiss) private var dismiss
    @State private var label = ""
    @State private var xText = ""
    @State private var yText = ""

    private var canSave: Bool {
        Double(xText) != nil && Double(yText) != nil
    }

    var body: some View {
        NavigationView {
            Form {
                Section("Identidade") {
                    Text(beaconID.value)
                        .font(.system(.body, design: .monospaced))
                }
                Section("Posição (metros, relativa à origem que você escolher)") {
                    TextField("Rótulo (opcional, ex: A)", text: $label)
                    TextField("x", text: $xText)
                        .keyboardType(.numbersAndPunctuation)
                    TextField("y", text: $yText)
                        .keyboardType(.numbersAndPunctuation)
                }
            }
            .navigationTitle("Nova âncora")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Salvar") {
                        guard let x = Double(xText), let y = Double(yText) else { return }
                        anchorStore.addAnchor(id: beaconID, label: label, position: Point2D(x: x, y: y))
                        dismiss()
                    }
                    .disabled(!canSave)
                }
            }
        }
    }
}

#Preview {
    AnchorPositionEditor(beaconID: BeaconID(hex: "B7E2F910")!, anchorStore: AnchorStore())
}
