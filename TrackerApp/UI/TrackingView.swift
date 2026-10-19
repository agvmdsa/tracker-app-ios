import SwiftUI

/// Pairs with a nearby phone running this app over BLE and shows live distance and direction
/// to it (UWB) — see `ProximityTracker`.
struct TrackingView: View {
    @Environment(\.proximityTracker) private var tracker: any ProximityTracking

    var body: some View {
        VStack(spacing: 20) {
            directionIndicator

            VStack(spacing: 8) {
                distanceLabel
                angleLabel
            }

            statusBox

            if let error = tracker.errorMessage {
                Text(error)
                    .font(.footnote)
                    .foregroundStyle(.red)
                    .multilineTextAlignment(.center)
            }

            LogView(entries: tracker.logs)

            Button(tracker.isRunning ? "Stop" : "Start") {
                if tracker.isRunning {
                    tracker.stop()
                } else {
                    tracker.start()
                }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(!tracker.isSupported)

            if !tracker.isSupported {
                Text("This iPhone doesn't support Ultra Wideband (needs an iPhone 11 or newer).")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
        }
        .padding()
    }

    // MARK: - Direction

    private var directionIndicator: some View {
        ZStack {
            Circle()
                .strokeBorder(.tertiary, lineWidth: 2)
            if !tracker.supportsDirectionMeasurement {
                VStack(spacing: 8) {
                    Image(systemName: "location.viewfinder")
                        .font(.system(size: 64))
                        .foregroundStyle(.secondary)
                    Text("This iPhone can only measure distance, not direction")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                .padding(24)
            } else if tracker.isSignalLost {
                VStack(spacing: 8) {
                    Image(systemName: "questionmark.circle")
                        .font(.system(size: 64))
                        .foregroundStyle(.secondary)
                    Text("Searching for signal…")
                        .foregroundStyle(.secondary)
                }
            } else {
                Image(systemName: "location.north.fill")
                    .resizable()
                    .scaledToFit()
                    .padding(56)
                    .foregroundStyle(tracker.azimuth == nil ? AnyShapeStyle(.tertiary) : AnyShapeStyle(.tint))
                    .rotationEffect(.radians(Double(tracker.azimuth ?? 0)))
                    .animation(.easeOut(duration: 0.2), value: tracker.azimuth)
            }
        }
        .frame(width: 220, height: 220)
    }

    private var distanceLabel: some View {
        Text(distanceText)
            .font(.system(size: 56, weight: .bold, design: .rounded))
            .monospacedDigit()
    }

    private var angleLabel: some View {
        Text(angleText)
            .font(.title3)
            .monospacedDigit()
            .foregroundStyle(.secondary)
    }

    private var distanceText: String {
        guard let distance = tracker.distance else { return "-- m" }
        return String(format: "%.2f m", distance)
    }

    private var angleText: String {
        guard let azimuth = tracker.azimuth else { return "Angle: --" }
        var text = String(format: "Azimuth: %+.0f°", Self.degrees(fromRadians: azimuth))
        if let elevation = tracker.elevation {
            text += String(format: "  Elevation: %+.0f°", Self.degrees(fromRadians: elevation))
        }
        return text
    }

    private static func degrees(fromRadians radians: Float) -> Double {
        Measurement(value: Double(radians), unit: UnitAngle.radians).converted(to: .degrees).value
    }

    // MARK: - Status

    private var statusBox: some View {
        VStack(alignment: .leading, spacing: 4) {
            LabeledContent("Handshake (Bluetooth LE)", value: handshakeText)
            LabeledContent("Ranging", value: rangingText)
        }
        .font(.callout)
        .padding()
        .background(.quaternary, in: .rect(cornerRadius: 12))
    }

    private var handshakeText: String {
        switch tracker.handshakeState {
        case .idle: "Idle"
        case .searching: "Searching for peer…"
        case .exchanging: "Exchanging tokens…"
        case .completed: "Completed"
        case .failed: "Failed"
        }
    }

    private var rangingText: String {
        switch tracker.rangingState {
        case .idle: "Idle"
        case .waitingForPeer: "Waiting for peer token"
        case .ranging: tracker.isCameraAssisted ? "Ranging (camera assisted)" : "Ranging"
        case .failed: "Failed"
        }
    }
}

/// Small auto-scrolling diagnostic log.
private struct LogView: View {
    let entries: [TrackingLogEntry]

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 2) {
                    ForEach(entries) { entry in
                        Text("\(entry.date.formatted(date: .omitted, time: .standard))  \(entry.message)")
                            .id(entry.id)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .font(.caption2.monospaced())
            .foregroundStyle(.secondary)
            .padding(8)
            .frame(maxHeight: .infinity)
            .background(.quaternary.opacity(0.5), in: .rect(cornerRadius: 8))
            .onChange(of: entries.last?.id) { _, id in
                if let id { proxy.scrollTo(id, anchor: .bottom) }
            }
        }
    }
}
