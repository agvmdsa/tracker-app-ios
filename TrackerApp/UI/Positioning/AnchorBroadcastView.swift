import SwiftUI

struct AnchorBroadcastView: View {
    let onExit: () -> Void

    @Environment(\.beaconAdvertiser) private var advertiser: any BeaconAdvertising

    var body: some View {
        VStack(spacing: 24) {
            Image(systemName: "antenna.radiowaves.left.and.right")
                .font(.system(size: 64))
                .foregroundStyle(advertiser.isAdvertising ? .green : .secondary)
                .animation(.easeInOut, value: advertiser.isAdvertising)

            VStack(spacing: 8) {
                Text("ID desta âncora")
                    .font(.headline)
                Text(advertiser.localID.value)
                    .font(.system(.title, design: .monospaced))
                    .textSelection(.enabled)
            }

            if advertiser.isBluetoothUnavailable {
                Text("Bluetooth indisponível — verifique se está ligado e se o app tem permissão.")
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            } else {
                Text(advertiser.isAdvertising
                    ? "Transmitindo. Anote esse ID e a posição física deste celular no app do observador."
                    : "Iniciando…")
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            Button("Voltar", action: onExit)
        }
        .padding()
        .onAppear { advertiser.startAdvertising() }
        .onDisappear { advertiser.stopAdvertising() }
    }
}

#Preview {
    NavigationView {
        AnchorBroadcastView(onExit: {})
            .environment(\.beaconAdvertiser, FakeBeaconAdvertising())
    }
}
