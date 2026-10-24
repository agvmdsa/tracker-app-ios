// T039: recording state on the host (data-model.md, RecordingState). Counters per beacon, observed
// rate = records / elapsed seconds, a summary on stop (US2 scenario 3), exactly one label per
// record, and a label change that affects only later records.
#include <stdio.h>
#include <string.h>
#include <unity.h>

#include "label.h"
#include "observation_filter.h"
#include "recording_state.h"

using domain::CollectionLabel;
using domain::Observation;
using domain::RecordingState;

void setUp() {}
void tearDown() {}

static Observation obs(const char* id, int rssi, uint32_t tMs) {
    Observation o;
    strcpy(o.beacon.value, id);
    o.rssi = static_cast<int8_t>(rssi);
    o.tMs = tMs;
    return o;
}

static CollectionLabel label(const char* session, const char* point, float x, float y) {
    CollectionLabel l;
    domain::makeLabel(session, point, x, y, l);
    return l;
}

static void test_starts_idle_without_a_label() {
    RecordingState s;
    TEST_ASSERT_FALSE(s.recording());
    TEST_ASSERT_FALSE(s.hasLabel());
    TEST_ASSERT_EQUAL_UINT(0, s.beaconCount());
}

static void test_cannot_start_without_a_label() {
    RecordingState s;
    TEST_ASSERT_EQUAL_INT(static_cast<int>(RecordingState::StartResult::NoLabel), static_cast<int>(s.start(1000)));
    TEST_ASSERT_FALSE(s.recording());
}

static void test_starts_with_a_label_and_refuses_a_second_start() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    TEST_ASSERT_EQUAL_INT(static_cast<int>(RecordingState::StartResult::Started), static_cast<int>(s.start(1000)));
    TEST_ASSERT_TRUE(s.recording());
    TEST_ASSERT_EQUAL_INT(static_cast<int>(RecordingState::StartResult::AlreadyRecording), static_cast<int>(s.start(2000)));
}

static void test_a_label_set_while_idle_is_kept_for_the_next_recording() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    TEST_ASSERT_TRUE(s.hasLabel());
    TEST_ASSERT_EQUAL_STRING("S1", s.label().session);
    TEST_ASSERT_EQUAL_STRING("P3", s.label().point);
}

static void test_nothing_is_counted_while_idle() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    TEST_ASSERT_NULL(s.record(obs("9F3C1A02", -60, 100)));
    TEST_ASSERT_EQUAL_UINT(0, s.beaconCount());
    TEST_ASSERT_EQUAL_UINT32(0, s.totalRecords());
}

static void test_counts_records_per_beacon_in_order_of_first_appearance() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    s.start(0);
    TEST_ASSERT_NOT_NULL(s.record(obs("AAAAAAAA", -60, 10)));
    TEST_ASSERT_NOT_NULL(s.record(obs("BBBBBBBB", -70, 20)));
    TEST_ASSERT_NOT_NULL(s.record(obs("AAAAAAAA", -61, 30)));
    TEST_ASSERT_NOT_NULL(s.record(obs("AAAAAAAA", -62, 40)));
    TEST_ASSERT_TRUE(s.stop(1000));

    TEST_ASSERT_EQUAL_UINT(2, s.beaconCount());
    TEST_ASSERT_EQUAL_STRING("AAAAAAAA", s.totals(0).beacon.value);
    TEST_ASSERT_EQUAL_UINT32(3, s.totals(0).records);
    TEST_ASSERT_EQUAL_STRING("BBBBBBBB", s.totals(1).beacon.value);
    TEST_ASSERT_EQUAL_UINT32(1, s.totals(1).records);
    TEST_ASSERT_EQUAL_UINT32(4, s.totalRecords());
}

static void test_rate_is_records_over_the_elapsed_seconds_of_the_recording() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    s.start(5000);
    for (int i = 0; i < 30; ++i) s.record(obs("AAAAAAAA", -60, 5000 + i * 100));
    for (int i = 0; i < 6; ++i) s.record(obs("BBBBBBBB", -70, 5000 + i * 500));
    s.stop(8000);  // 3 s elapsed

    TEST_ASSERT_FLOAT_WITHIN(0.001f, 10.0f, s.totals(0).ratePerS);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 2.0f, s.totals(1).ratePerS);
}

static void test_a_zero_length_recording_has_a_zero_rate_not_a_division_by_zero() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    s.start(5000);
    s.record(obs("AAAAAAAA", -60, 5000));
    s.stop(5000);
    TEST_ASSERT_EQUAL_FLOAT(0.0f, s.totals(0).ratePerS);
}

static void test_stop_ends_the_recording_and_a_second_stop_reports_nothing_to_stop() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    s.start(0);
    TEST_ASSERT_TRUE(s.stop(1000));
    TEST_ASSERT_FALSE(s.recording());
    TEST_ASSERT_FALSE(s.stop(2000));
}

static void test_stop_without_a_recording_is_refused() {
    RecordingState s;
    TEST_ASSERT_FALSE(s.stop(1000));
}

static void test_each_record_carries_exactly_the_active_label() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    s.start(0);
    const CollectionLabel* l = s.record(obs("AAAAAAAA", -60, 10));
    TEST_ASSERT_NOT_NULL(l);
    TEST_ASSERT_EQUAL_STRING("S1", l->session);
    TEST_ASSERT_EQUAL_STRING("P3", l->point);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 2.0f, l->x);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 4.5f, l->y);
}

static void test_a_label_change_affects_only_later_records() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    s.start(0);
    const CollectionLabel before = *s.record(obs("AAAAAAAA", -60, 10));
    s.setLabel(label("S1", "P4", 3.0f, 4.5f));
    const CollectionLabel after = *s.record(obs("AAAAAAAA", -60, 20));

    TEST_ASSERT_EQUAL_STRING("P3", before.point);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 2.0f, before.x);
    TEST_ASSERT_EQUAL_STRING("P4", after.point);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 3.0f, after.x);
    TEST_ASSERT_EQUAL_UINT32(2, s.totalRecords());  // the counters keep running across the change
}

static void test_a_new_recording_starts_from_zero() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    s.start(0);
    s.record(obs("AAAAAAAA", -60, 10));
    s.stop(1000);

    s.start(2000);
    TEST_ASSERT_EQUAL_UINT(0, s.beaconCount());
    TEST_ASSERT_EQUAL_UINT32(0, s.totalRecords());
    s.record(obs("BBBBBBBB", -60, 2010));
    s.stop(3000);
    TEST_ASSERT_EQUAL_UINT(1, s.beaconCount());
    TEST_ASSERT_EQUAL_STRING("BBBBBBBB", s.totals(0).beacon.value);
}

static void test_a_beacon_beyond_the_table_is_refused_and_counted_without_touching_the_others() {
    RecordingState s;
    s.setLabel(label("S1", "P3", 2.0f, 4.5f));
    s.start(0);
    for (unsigned i = 0; i < RecordingState::kMaxBeacons; ++i) {
        char id[9];
        snprintf(id, sizeof(id), "%08X", 0x1000u + i);
        TEST_ASSERT_NOT_NULL(s.record(obs(id, -60, 10)));
    }
    TEST_ASSERT_NULL(s.record(obs("FFFFFFFF", -60, 20)));
    TEST_ASSERT_EQUAL_UINT32(1, s.untracked());
    // A beacon already in the table is still counted.
    TEST_ASSERT_NOT_NULL(s.record(obs("00001000", -60, 30)));
    s.stop(1000);
    TEST_ASSERT_EQUAL_UINT(RecordingState::kMaxBeacons, s.beaconCount());
    TEST_ASSERT_EQUAL_UINT32(2, s.totals(0).records);
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_starts_idle_without_a_label);
    RUN_TEST(test_cannot_start_without_a_label);
    RUN_TEST(test_starts_with_a_label_and_refuses_a_second_start);
    RUN_TEST(test_a_label_set_while_idle_is_kept_for_the_next_recording);
    RUN_TEST(test_nothing_is_counted_while_idle);
    RUN_TEST(test_counts_records_per_beacon_in_order_of_first_appearance);
    RUN_TEST(test_rate_is_records_over_the_elapsed_seconds_of_the_recording);
    RUN_TEST(test_a_zero_length_recording_has_a_zero_rate_not_a_division_by_zero);
    RUN_TEST(test_stop_ends_the_recording_and_a_second_stop_reports_nothing_to_stop);
    RUN_TEST(test_stop_without_a_recording_is_refused);
    RUN_TEST(test_each_record_carries_exactly_the_active_label);
    RUN_TEST(test_a_label_change_affects_only_later_records);
    RUN_TEST(test_a_new_recording_starts_from_zero);
    RUN_TEST(test_a_beacon_beyond_the_table_is_refused_and_counted_without_touching_the_others);
    return UNITY_END();
}
