// Live view on the host (contracts/serial-protocol.md, `L` lines): while the collector is idle it
// reports each nearby beacon once per second, with the last RSSI and the rate over that second.
// Part of T040's idle behaviour, kept as its own pure piece so it is tested without a radio.
#include <stdio.h>
#include <string.h>
#include <unity.h>

#include "live_view.h"
#include "observation_filter.h"

using domain::LiveView;
using domain::Observation;

void setUp() {}
void tearDown() {}

static Observation obs(const char* id, int rssi, uint32_t tMs) {
    Observation o;
    strcpy(o.beacon.value, id);
    o.rssi = static_cast<int8_t>(rssi);
    o.tMs = tMs;
    return o;
}

static void test_reports_nothing_before_a_second_has_passed() {
    LiveView v;
    v.reset(0);
    v.observe(obs("AAAAAAAA", -60, 100));
    LiveView::Entry out[LiveView::kMaxBeacons];
    TEST_ASSERT_EQUAL_UINT(0, v.report(999, out, LiveView::kMaxBeacons));
}

static void test_reports_each_beacon_once_after_a_second_with_its_last_rssi_and_rate() {
    LiveView v;
    v.reset(0);
    for (int i = 0; i < 9; ++i) v.observe(obs("AAAAAAAA", -60 - i, 100 * i));
    v.observe(obs("BBBBBBBB", -75, 500));
    LiveView::Entry out[LiveView::kMaxBeacons];
    const std::size_t n = v.report(1000, out, LiveView::kMaxBeacons);

    TEST_ASSERT_EQUAL_UINT(2, n);
    TEST_ASSERT_EQUAL_STRING("AAAAAAAA", out[0].beacon.value);
    TEST_ASSERT_EQUAL_INT(-68, out[0].rssiLast);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 9.0f, out[0].ratePerS);
    TEST_ASSERT_EQUAL_STRING("BBBBBBBB", out[1].beacon.value);
    TEST_ASSERT_EQUAL_INT(-75, out[1].rssiLast);
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 1.0f, out[1].ratePerS);
}

static void test_the_rate_uses_the_real_time_since_the_previous_report() {
    LiveView v;
    v.reset(0);
    for (int i = 0; i < 20; ++i) v.observe(obs("AAAAAAAA", -60, i));
    LiveView::Entry out[LiveView::kMaxBeacons];
    TEST_ASSERT_EQUAL_UINT(1, v.report(2000, out, LiveView::kMaxBeacons));  // reported late
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 10.0f, out[0].ratePerS);
}

static void test_counters_restart_after_each_report() {
    LiveView v;
    v.reset(0);
    LiveView::Entry out[LiveView::kMaxBeacons];
    v.observe(obs("AAAAAAAA", -60, 10));
    v.observe(obs("AAAAAAAA", -60, 20));
    TEST_ASSERT_EQUAL_UINT(1, v.report(1000, out, LiveView::kMaxBeacons));
    v.observe(obs("AAAAAAAA", -50, 1100));
    TEST_ASSERT_EQUAL_UINT(1, v.report(2000, out, LiveView::kMaxBeacons));
    TEST_ASSERT_FLOAT_WITHIN(0.001f, 1.0f, out[0].ratePerS);
    TEST_ASSERT_EQUAL_INT(-50, out[0].rssiLast);
}

static void test_a_silent_beacon_produces_no_line_and_frees_its_slot() {
    LiveView v;
    v.reset(0);
    LiveView::Entry out[LiveView::kMaxBeacons];
    v.observe(obs("AAAAAAAA", -60, 10));
    TEST_ASSERT_EQUAL_UINT(1, v.report(1000, out, LiveView::kMaxBeacons));
    TEST_ASSERT_EQUAL_UINT(0, v.report(2000, out, LiveView::kMaxBeacons));  // silent for a second
    TEST_ASSERT_EQUAL_UINT(0, v.tracked());
}

static void test_more_beacons_than_slots_are_dropped_without_disturbing_the_others() {
    LiveView v;
    v.reset(0);
    for (unsigned i = 0; i < LiveView::kMaxBeacons + 3; ++i) {
        char id[9];
        snprintf(id, sizeof(id), "%08X", 0x2000u + i);
        v.observe(obs(id, -60, 10));
    }
    LiveView::Entry out[LiveView::kMaxBeacons];
    TEST_ASSERT_EQUAL_UINT(LiveView::kMaxBeacons, v.report(1000, out, LiveView::kMaxBeacons));
}

static void test_reset_forgets_everything_and_restarts_the_second() {
    LiveView v;
    v.reset(0);
    v.observe(obs("AAAAAAAA", -60, 10));
    v.reset(5000);
    LiveView::Entry out[LiveView::kMaxBeacons];
    TEST_ASSERT_EQUAL_UINT(0, v.report(5999, out, LiveView::kMaxBeacons));
    TEST_ASSERT_EQUAL_UINT(0, v.report(6000, out, LiveView::kMaxBeacons));  // nothing was heard since
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_reports_nothing_before_a_second_has_passed);
    RUN_TEST(test_reports_each_beacon_once_after_a_second_with_its_last_rssi_and_rate);
    RUN_TEST(test_the_rate_uses_the_real_time_since_the_previous_report);
    RUN_TEST(test_counters_restart_after_each_report);
    RUN_TEST(test_a_silent_beacon_produces_no_line_and_frees_its_slot);
    RUN_TEST(test_more_beacons_than_slots_are_dropped_without_disturbing_the_others);
    RUN_TEST(test_reset_forgets_everything_and_restarts_the_second);
    return UNITY_END();
}
