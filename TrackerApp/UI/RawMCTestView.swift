import SwiftUI

/// Temporary debugging screen — remove once the root cause of the connection failures is found.
struct RawMCTestView: View {
    @State private var diagnostic = RawMultipeerDiagnostic()

    var body: some View {
        List(Array(diagnostic.log.enumerated()), id: \.offset) { _, line in
            Text(line)
                .font(.system(.caption, design: .monospaced))
        }
        .navigationTitle("Raw MC Test")
        .onAppear(perform: diagnostic.start)
        .onDisappear(perform: diagnostic.stop)
    }
}
