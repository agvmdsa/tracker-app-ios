import Foundation

/// Where the phone is while recording: the ground truth a fingerprinting model learns from.
/// Same fields and rules as the ESP32 collector's `label <session> <point> <x> <y>` command, so
/// datasets from both can be merged.
struct FingerprintLabel: Equatable {
    static let maxTokenLength = 16

    let session: String
    let point: String
    /// Metres in the session's local frame.
    let x: Double
    let y: Double

    init?(session: String, point: String, x: Double, y: Double) {
        guard Self.isToken(session), Self.isToken(point), x.isFinite, y.isFinite else { return nil }
        self.session = session
        self.point = point
        self.x = x
        self.y = y
    }

    /// 1 to 16 characters of [A-Za-z0-9_-], so a token can never break a CSV row or a file name.
    static func isToken(_ text: String) -> Bool {
        (1...maxTokenLength).contains(text.count)
            && text.allSatisfy { $0.isASCII && ($0.isLetter || $0.isNumber || $0 == "_" || $0 == "-") }
    }
}

/// One row of a fingerprinting dataset: a single advertisement as this phone received it, how the
/// phone was being held at the time, and where it was.
struct FingerprintRecord {
    /// Column names follow the ESP32 collector's `R` records (`t_ms`, `node`, `beacon`, `rssi`,
    /// `session`, `point`, `x`, `y`) wherever the two overlap.
    static let csvHeader = "t_ms,timestamp_ms,node,model,system_version,beacon,rssi,accel_x,accel_y,accel_z,session,point,x,y"

    /// `t_ms`: monotonic milliseconds (`MonotonicClock`), for ordering and windowing the rows.
    let monotonicMs: Int64
    /// `timestamp_ms`: Unix time, for lining recordings up across devices.
    let seenAt: Date
    /// The recording phone's own install ID (`DeviceIdentity.beaconID`).
    let node: BeaconID
    let model: String
    let systemVersion: String
    let beacon: BeaconID
    let rssi: Int
    let acceleration: AccelerometerSample?
    let label: FingerprintLabel

    var csvLine: String {
        let timestampMs = Int64((seenAt.timeIntervalSince1970 * 1000).rounded())
        let accelerationFields = acceleration.map {
            [String(format: "%.4f", $0.x), String(format: "%.4f", $0.y), String(format: "%.4f", $0.z)]
        } ?? ["", "", ""]
        let fields = [String(monotonicMs), String(timestampMs), node.value, Self.quoted(model), Self.quoted(systemVersion), beacon.value, String(rssi)]
            + accelerationFields
            + [label.session, label.point, String(format: "%.2f", label.x), String(format: "%.2f", label.y)]
        return fields.joined(separator: ",")
    }

    /// Model identifiers contain a comma ("iPhone15,2"), so free-text fields are always quoted.
    private static func quoted(_ field: String) -> String {
        "\"" + field.replacingOccurrences(of: "\"", with: "\"\"") + "\""
    }
}

/// Written next to each finished recording's CSV, as `<same name>.json`, with what the rows can't
/// say. A beacon that was never heard leaves no row at all, so only the recording's window tells "not
/// heard for the whole recording" apart from "not part of it". And since this file is written only
/// when a recording ends normally, a CSV without it was cut short (the app was killed) and should be
/// discarded.
struct FingerprintRecordingMetadata: Encodable, Equatable {
    static let currentSchemaVersion = 1

    let schemaVersion: Int
    let platform: String
    let node: String
    let model: String
    let systemVersion: String
    let session: String
    let point: String
    let x: Double
    let y: Double
    let csvFile: String
    /// Same clock as the rows' `t_ms`.
    let startedTMs: Int64
    let endedTMs: Int64
    /// Same clock as the rows' `timestamp_ms`.
    let startedTimestampMs: Int64
    let endedTimestampMs: Int64
    let plannedDurationS: Double
    let recordsPerBeacon: [String: Int]
    /// How advertisements were received, since it changes how many arrive.
    let scanAPI: String
    /// Units and sign of `accel_x/y/z`, which other platforms have to convert to.
    let accelerationConvention: String
    let accelerationRateHz: Double
    /// Where each beacon was when the recording started, in the same frame as `x`/`y`. A copy, not a
    /// reference: moving a beacon later doesn't rewrite the recordings made before.
    let beacons: [Beacon]

    struct Beacon: Encodable, Equatable {
        let id: String
        let label: String
        let x: Double
        let y: Double
        /// Metres above the floor; `null` when it wasn't entered.
        let heightM: Double?

        init(_ anchor: Anchor) {
            id = anchor.id.value
            label = anchor.label
            x = anchor.position.x
            y = anchor.position.y
            heightM = anchor.height
        }

        enum CodingKeys: String, CodingKey {
            case id, label, x, y
            case heightM = "height_m"
        }

        /// Written as `null` rather than left out, so every beacon has the same keys.
        func encode(to encoder: Encoder) throws {
            var container = encoder.container(keyedBy: CodingKeys.self)
            try container.encode(id, forKey: .id)
            try container.encode(label, forKey: .label)
            try container.encode(x, forKey: .x)
            try container.encode(y, forKey: .y)
            try container.encode(heightM, forKey: .heightM)
        }
    }

    enum CodingKeys: String, CodingKey {
        case schemaVersion = "schema_version"
        case platform, node, model
        case systemVersion = "system_version"
        case session, point, x, y
        case csvFile = "csv_file"
        case startedTMs = "started_t_ms"
        case endedTMs = "ended_t_ms"
        case startedTimestampMs = "started_timestamp_ms"
        case endedTimestampMs = "ended_timestamp_ms"
        case plannedDurationS = "planned_duration_s"
        case recordsPerBeacon = "records_per_beacon"
        case scanAPI = "scan_api"
        case accelerationConvention = "acceleration_convention"
        case accelerationRateHz = "acceleration_rate_hz"
        case beacons
    }

    func jsonData() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(self)
    }
}

/// Compares the beacons with a position against the ones being heard. Either list being non-empty
/// means a recording would go out with an incomplete layout: a beacon without a position can't be
/// used by models that need distances, and a positioned beacon that isn't heard is probably off.
struct BeaconLayoutCheck: Equatable {
    let withoutPosition: [BeaconID]
    let notHeard: [BeaconID]

    var isComplete: Bool { withoutPosition.isEmpty && notHeard.isEmpty }

    init(anchors: [Anchor], heard: some Sequence<BeaconID>) {
        let positioned = Set(anchors.map(\.id))
        let heard = Set(heard)
        withoutPosition = heard.subtracting(positioned).sorted { $0.value < $1.value }
        notHeard = positioned.subtracting(heard).sorted { $0.value < $1.value }
    }
}
