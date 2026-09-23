import SwiftUI

enum PositioningRole {
    case anchor
    case observer
}

struct PositioningModeView: View {
    @State private var role: PositioningRole?

    var body: some View {
        Group {
            switch role {
            case nil:
                rolePicker
            case .anchor:
                AnchorBroadcastView(onExit: { role = nil })
            case .observer:
                ObserverView(onExit: { role = nil })
            }
        }
        .navigationTitle("Posicionamento Indoor")
        .navigationBarTitleDisplayMode(.inline)
    }

    private var rolePicker: some View {
        VStack(spacing: 24) {
            Image(systemName: "location.viewfinder")
                .font(.system(size: 48))
                .foregroundStyle(.blue)
            Text("Esse celular vai ficar parado (âncora) ou vai se mover pelo espaço (observador)?")
                .multilineTextAlignment(.center)
                .padding(.horizontal)
            Button("Sou uma âncora — fico parado") { role = .anchor }
                .buttonStyle(.borderedProminent)
            Button("Sou o observador — ando pelo espaço") { role = .observer }
                .buttonStyle(.bordered)
        }
        .padding()
    }
}

#Preview {
    NavigationView {
        PositioningModeView()
    }
}
