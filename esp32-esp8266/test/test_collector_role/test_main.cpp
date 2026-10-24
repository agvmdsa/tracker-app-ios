// T040: collector role on the host, with a mock radio that injects advertisements
// (contracts/serial-protocol.md, US2 acceptance scenarios 1 to 3). Idle produces `L` lines (once per
// second per beacon, unlabelled); recording produces `R` lines carrying the active label; stopping
// produces `S` lines; a device outside the protocol produces nothing.
#include <string.h>
#include <unity.h>

#include "../mocks/mocks.h"
#include "app/collector_role.h"

using domain::CollectionLabel;
using domain::DeviceConfiguration;
using domain::RecordingState;
using domain::Role;

void setUp() {}
void tearDown() {}

namespace {

DeviceConfiguration collectorConfig() {
    DeviceConfiguration c{};
    c.role = Role::Collector;
    c.scanIntervalMs = 100;
    c.scanWindowMs = 99;
    strcpy(c.nodeId, "A1B2C3");
    c.bootId = 7;
    return c;
}

CollectionLabel label(const char* session, const char* point, float x, float y) {
    CollectionLabel l;
    domain::makeLabel(session, point, x, y, l);
    return l;
}

struct Rig {
    FakeClock clock;
    MockBleRadio radio;
    BoundedObservationQueue queue;
    CapturePublisher out;
    CaptureUploader uploader;
    CaptureMqttPublisher mqtt;
    CaptureEvents events;
    CollectorRole role;

    Rig() : role(radio, queue, out, uploader, mqtt, clock, events) { clock.now = 1000; }

    void startRole() { TEST_ASSERT_TRUE(role.start(collectorConfig())); }
    void hear(const char* id, int rssi, uint32_t tMs) { radio.inject(AdPayload::beaconPacket(id), rssi, tMs); }
    int linesStartingWith(const char* prefix) const {
        int n = 0;
        for (const auto& l : out.lines) {
            if (l.rfind(prefix, 0) == 0) ++n;
        }
        return n;
    }
};

}  // namespace

static void test_start_scans_with_the_configured_interval_and_window_and_reports_it() {
    Rig rig;
    rig.startRole();
    TEST_ASSERT_EQUAL_INT(1, rig.radio.scanStartCalls);
    TEST_ASSERT_TRUE(rig.radio.isScanning());
    TEST_ASSERT_EQUAL_UINT32(100, rig.radio.lastScan.intervalMs);
    TEST_ASSERT_EQUAL_UINT32(99, rig.radio.lastScan.windowMs);
    TEST_ASSERT_TRUE(rig.events.hasCode("scanning"));
    const std::string& message = rig.events.events.back().message;
    TEST_ASSERT_NOT_EQUAL(std::string::npos, message.find("interval=100"));
    TEST_ASSERT_NOT_EQUAL(std::string::npos, message.find("window=99"));
    TEST_ASSERT_EQUAL_STRING("collector", rig.role.name());
}

static void test_the_collector_never_advertises() {
    Rig rig;
    rig.startRole();
    TEST_ASSERT_EQUAL_INT(0, rig.radio.startCalls);
    TEST_ASSERT_FALSE(rig.radio.isAdvertising());
}

static void test_a_radio_that_cannot_scan_is_reported() {
    Rig rig;
    rig.radio.scanStartResult = false;
    TEST_ASSERT_FALSE(rig.role.start(collectorConfig()));
    TEST_ASSERT_TRUE(rig.events.hasCode("radio_start_failed"));
    TEST_ASSERT_EQUAL_STRING("error", rig.events.events.back().level.c_str());
}

static void test_scan_parameters_that_cannot_work_are_refused_before_the_radio_is_touched() {
    struct Case {
        uint32_t interval;
        uint32_t window;
    };
    const Case cases[] = {{100, 101}, {0, 0}, {100, 0}, {10241, 100}};
    for (const Case& c : cases) {
        Rig rig;
        DeviceConfiguration config = collectorConfig();
        config.scanIntervalMs = c.interval;
        config.scanWindowMs = c.window;
        TEST_ASSERT_FALSE(rig.role.start(config));
        TEST_ASSERT_EQUAL_INT(0, rig.radio.scanStartCalls);
        TEST_ASSERT_TRUE(rig.events.hasCode("bad_scan_params"));
    }
}

static void test_stop_stops_scanning() {
    Rig rig;
    rig.startRole();
    rig.role.stop();
    TEST_ASSERT_EQUAL_INT(1, rig.radio.scanStopCalls);
    TEST_ASSERT_FALSE(rig.radio.isScanning());
}

static void test_the_role_has_a_recording_half() {
    Rig rig;
    TEST_ASSERT_NOT_NULL(rig.role.recording());
}

static void test_idle_prints_one_unlabelled_live_line_per_beacon_per_second() {
    Rig rig;
    rig.startRole();
    rig.hear("AAAAAAAA", -60, 1100);
    rig.hear("BBBBBBBB", -70, 1200);
    rig.hear("AAAAAAAA", -62, 1300);

    rig.clock.now = 1500;
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(0, rig.out.lines.size());  // not a second yet

    rig.clock.now = 2000;
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(2, rig.out.lines.size());
    TEST_ASSERT_EQUAL_STRING("L,2000,7,A1B2C3,AAAAAAAA,-62,2.0", rig.out.lines[0].c_str());
    TEST_ASSERT_EQUAL_STRING("L,2000,7,A1B2C3,BBBBBBBB,-70,1.0", rig.out.lines[1].c_str());
}

static void test_idle_keeps_reporting_every_second_while_the_beacon_is_heard() {
    Rig rig;
    rig.startRole();
    rig.hear("AAAAAAAA", -60, 1100);
    rig.clock.now = 2000;
    rig.role.poll();
    rig.hear("AAAAAAAA", -55, 2100);
    rig.clock.now = 3000;
    rig.role.poll();
    TEST_ASSERT_EQUAL_INT(2, rig.linesStartingWith("L,"));
    TEST_ASSERT_EQUAL_STRING("L,3000,7,A1B2C3,AAAAAAAA,-55,1.0", rig.out.lines[1].c_str());
}

static void test_idle_never_prints_labelled_lines() {
    Rig rig;
    rig.startRole();
    rig.hear("AAAAAAAA", -60, 1100);
    rig.clock.now = 2000;
    rig.role.poll();
    TEST_ASSERT_EQUAL_INT(0, rig.linesStartingWith("R,"));
    TEST_ASSERT_EQUAL_INT(0, rig.linesStartingWith("S,"));
}

static void test_idle_queues_nothing_for_upload() {
    Rig rig;
    rig.startRole();
    rig.hear("AAAAAAAA", -60, 1100);
    rig.clock.now = 2000;
    rig.role.poll();
    TEST_ASSERT_TRUE(rig.uploader.rows.empty());
}

static void test_idle_still_publishes_the_sighting_over_mqtt() {
    // Unlike the fingerprint uploader (labelled dataset, recording-only), MQTT carries the
    // continuous live telemetry — it doesn't care whether a recording is running.
    Rig rig;
    rig.startRole();
    rig.hear("AAAAAAAA", -60, 1100);
    rig.clock.now = 2000;
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(1, rig.mqtt.sightings.size());
    TEST_ASSERT_EQUAL_STRING("A1B2C3", rig.mqtt.sightings[0].node.c_str());
    TEST_ASSERT_EQUAL_STRING("AAAAAAAA", rig.mqtt.sightings[0].beacon.c_str());
    TEST_ASSERT_EQUAL_INT(-60, rig.mqtt.sightings[0].rssi);
}

static void test_a_device_outside_the_protocol_produces_nothing() {
    Rig rig;
    rig.startRole();
    rig.radio.inject(AdPayload().flags().name("9F3C1A02"), -50, 1100);                // no service UUID
    rig.radio.inject(AdPayload::beaconPacket("ZZZZZZZZ"), -50, 1100);                 // name is not hex
    rig.radio.inject(AdPayload::beaconPacket("9F3C1A0"), -50, 1100);                  // 7 characters
    rig.radio.inject(AdPayload().flags().projectServiceUuid(), -50, 1100);            // no name
    rig.clock.now = 2000;
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(0, rig.out.lines.size());
}

static void test_recording_prints_raw_lines_with_the_label_and_the_observation_time() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    TEST_ASSERT_EQUAL_INT(static_cast<int>(RecordingState::StartResult::Started),
                          static_cast<int>(rig.role.recording()->startRecording()));
    rig.hear("AAAAAAAA", -61, 1100);
    rig.clock.now = 1600;  // the line carries the time of the advertisement, not of the poll
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(1, rig.out.lines.size());
    TEST_ASSERT_EQUAL_STRING("R,1100,7,A1B2C3,AAAAAAAA,-61,S1,P3,2.00,4.50", rig.out.lines[0].c_str());
}

static void test_recording_queues_the_same_rows_for_upload_as_it_prints() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.role.recording()->startRecording();
    rig.hear("AAAAAAAA", -61, 1100);
    rig.clock.now = 1600;
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(1, rig.uploader.rows.size());
    const domain::RawRecord& row = rig.uploader.rows[0];
    TEST_ASSERT_EQUAL_UINT32(1100, row.tMs);
    TEST_ASSERT_EQUAL_STRING("A1B2C3", row.node);
    TEST_ASSERT_EQUAL_STRING("AAAAAAAA", row.beacon);
    TEST_ASSERT_EQUAL_INT(-61, row.rssi);
    TEST_ASSERT_EQUAL_STRING("S1", row.session);
    TEST_ASSERT_EQUAL_STRING("P3", row.point);
}

static void test_recording_also_publishes_the_sighting_over_mqtt() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.role.recording()->startRecording();
    rig.hear("AAAAAAAA", -61, 1100);
    rig.clock.now = 1600;
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(1, rig.mqtt.sightings.size());
    TEST_ASSERT_EQUAL_STRING("AAAAAAAA", rig.mqtt.sightings[0].beacon.c_str());
    TEST_ASSERT_EQUAL_INT(-61, rig.mqtt.sightings[0].rssi);
}

static void test_recording_prints_no_live_lines() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.role.recording()->startRecording();
    rig.hear("AAAAAAAA", -61, 1100);
    rig.clock.now = 3000;
    rig.role.poll();
    TEST_ASSERT_EQUAL_INT(0, rig.linesStartingWith("L,"));
    TEST_ASSERT_EQUAL_INT(1, rig.linesStartingWith("R,"));
}

static void test_a_label_change_mid_recording_affects_only_later_lines() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.role.recording()->startRecording();
    rig.hear("AAAAAAAA", -61, 1100);
    // Not polled yet: the label change must not reach back to the advertisement already queued.
    rig.role.recording()->setLabel(label("S1", "P4", 3.0f, 4.5f));
    rig.hear("AAAAAAAA", -62, 1200);
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(2, rig.out.lines.size());
    TEST_ASSERT_EQUAL_STRING("R,1100,7,A1B2C3,AAAAAAAA,-61,S1,P3,2.00,4.50", rig.out.lines[0].c_str());
    TEST_ASSERT_EQUAL_STRING("R,1200,7,A1B2C3,AAAAAAAA,-62,S1,P4,3.00,4.50", rig.out.lines[1].c_str());
}

static void test_advertisements_heard_before_rec_start_are_not_recorded() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.hear("AAAAAAAA", -61, 1100);  // queued, not polled when `rec start` arrives
    rig.role.recording()->startRecording();
    rig.role.poll();
    TEST_ASSERT_EQUAL_INT(0, rig.linesStartingWith("R,"));
}

static void test_rec_start_without_a_label_is_refused_and_nothing_is_recorded() {
    Rig rig;
    rig.startRole();
    TEST_ASSERT_EQUAL_INT(static_cast<int>(RecordingState::StartResult::NoLabel),
                          static_cast<int>(rig.role.recording()->startRecording()));
    rig.hear("AAAAAAAA", -61, 1100);
    rig.clock.now = 2000;
    rig.role.poll();
    TEST_ASSERT_EQUAL_INT(0, rig.linesStartingWith("R,"));
    TEST_ASSERT_EQUAL_INT(1, rig.linesStartingWith("L,"));  // it stays in the live view
}

static void test_a_second_rec_start_is_refused() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.role.recording()->startRecording();
    TEST_ASSERT_EQUAL_INT(static_cast<int>(RecordingState::StartResult::AlreadyRecording),
                          static_cast<int>(rig.role.recording()->startRecording()));
}

static void test_stop_prints_one_summary_line_per_beacon_and_reports_the_totals() {
    Rig rig;
    rig.startRole();
    rig.clock.now = 2000;
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.role.recording()->startRecording();
    rig.hear("AAAAAAAA", -60, 2100);
    rig.hear("BBBBBBBB", -70, 2200);
    rig.hear("AAAAAAAA", -61, 3100);
    rig.hear("AAAAAAAA", -62, 4100);  // still queued when `rec stop` arrives: it belongs to the recording
    rig.clock.now = 5000;             // 3 s of recording

    RecordingSummary summary;
    TEST_ASSERT_TRUE(rig.role.recording()->stopRecording(summary));
    TEST_ASSERT_EQUAL_UINT32(4, summary.records);
    TEST_ASSERT_EQUAL_UINT32(2, summary.beacons);
    TEST_ASSERT_EQUAL_INT(4, rig.linesStartingWith("R,"));
    TEST_ASSERT_EQUAL_INT(2, rig.linesStartingWith("S,"));
    TEST_ASSERT_EQUAL_STRING("S,5000,7,A1B2C3,S1,P3,AAAAAAAA,3,1.0", rig.out.lines[4].c_str());
    TEST_ASSERT_EQUAL_STRING("S,5000,7,A1B2C3,S1,P3,BBBBBBBB,1,0.3", rig.out.lines[5].c_str());
}

static void test_stop_without_a_recording_prints_nothing() {
    Rig rig;
    rig.startRole();
    RecordingSummary summary;
    TEST_ASSERT_FALSE(rig.role.recording()->stopRecording(summary));
    TEST_ASSERT_EQUAL_UINT(0, rig.out.lines.size());
}

static void test_after_stop_the_live_view_resumes() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.role.recording()->startRecording();
    rig.hear("AAAAAAAA", -60, 1100);
    rig.clock.now = 2000;
    RecordingSummary summary;
    rig.role.recording()->stopRecording(summary);
    rig.out.lines.clear();

    rig.hear("AAAAAAAA", -58, 2100);
    rig.clock.now = 3000;
    rig.role.poll();
    TEST_ASSERT_EQUAL_UINT(1, rig.out.lines.size());
    TEST_ASSERT_EQUAL_STRING("L,3000,7,A1B2C3,AAAAAAAA,-58,1.0", rig.out.lines[0].c_str());
}

static void test_a_recording_can_follow_another_without_restarting_the_role() {
    // Acceptance for SC-004: ten labelled points in a row without reflashing.
    Rig rig;
    rig.startRole();
    for (int point = 1; point <= 10; ++point) {
        char name[8];
        snprintf(name, sizeof(name), "P%d", point);
        rig.role.recording()->setLabel(label("S1", name, static_cast<float>(point), 0.0f));
        TEST_ASSERT_EQUAL_INT(static_cast<int>(RecordingState::StartResult::Started),
                              static_cast<int>(rig.role.recording()->startRecording()));
        rig.hear("AAAAAAAA", -60, rig.clock.now + 10);
        rig.clock.now += 1000;
        RecordingSummary summary;
        TEST_ASSERT_TRUE(rig.role.recording()->stopRecording(summary));
        TEST_ASSERT_EQUAL_UINT32(1, summary.records);
    }
    TEST_ASSERT_EQUAL_INT(10, rig.linesStartingWith("R,"));
    TEST_ASSERT_EQUAL_INT(10, rig.linesStartingWith("S,"));
}

static void test_a_full_beacon_table_is_reported_once_and_does_not_stop_the_recording() {
    Rig rig;
    rig.startRole();
    rig.role.recording()->setLabel(label("S1", "P3", 2.0f, 4.5f));
    rig.role.recording()->startRecording();
    for (unsigned i = 0; i < RecordingState::kMaxBeacons + 3; ++i) {
        char id[9];
        snprintf(id, sizeof(id), "%08X", 0x3000u + i);
        rig.hear(id, -60, 1100 + i);
    }
    rig.role.poll();
    int warnings = 0;
    for (const auto& e : rig.events.events) {
        if (e.code == "beacon_table_full") ++warnings;
    }
    TEST_ASSERT_EQUAL_INT(1, warnings);
    TEST_ASSERT_EQUAL_INT(static_cast<int>(RecordingState::kMaxBeacons), rig.linesStartingWith("R,"));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_start_scans_with_the_configured_interval_and_window_and_reports_it);
    RUN_TEST(test_the_collector_never_advertises);
    RUN_TEST(test_a_radio_that_cannot_scan_is_reported);
    RUN_TEST(test_scan_parameters_that_cannot_work_are_refused_before_the_radio_is_touched);
    RUN_TEST(test_stop_stops_scanning);
    RUN_TEST(test_the_role_has_a_recording_half);
    RUN_TEST(test_idle_prints_one_unlabelled_live_line_per_beacon_per_second);
    RUN_TEST(test_idle_keeps_reporting_every_second_while_the_beacon_is_heard);
    RUN_TEST(test_idle_never_prints_labelled_lines);
    RUN_TEST(test_idle_queues_nothing_for_upload);
    RUN_TEST(test_idle_still_publishes_the_sighting_over_mqtt);
    RUN_TEST(test_a_device_outside_the_protocol_produces_nothing);
    RUN_TEST(test_recording_prints_raw_lines_with_the_label_and_the_observation_time);
    RUN_TEST(test_recording_queues_the_same_rows_for_upload_as_it_prints);
    RUN_TEST(test_recording_also_publishes_the_sighting_over_mqtt);
    RUN_TEST(test_recording_prints_no_live_lines);
    RUN_TEST(test_a_label_change_mid_recording_affects_only_later_lines);
    RUN_TEST(test_advertisements_heard_before_rec_start_are_not_recorded);
    RUN_TEST(test_rec_start_without_a_label_is_refused_and_nothing_is_recorded);
    RUN_TEST(test_a_second_rec_start_is_refused);
    RUN_TEST(test_stop_prints_one_summary_line_per_beacon_and_reports_the_totals);
    RUN_TEST(test_stop_without_a_recording_prints_nothing);
    RUN_TEST(test_after_stop_the_live_view_resumes);
    RUN_TEST(test_a_recording_can_follow_another_without_restarting_the_role);
    RUN_TEST(test_a_full_beacon_table_is_reported_once_and_does_not_stop_the_recording);
    return UNITY_END();
}
