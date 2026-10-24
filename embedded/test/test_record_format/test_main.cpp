// T015: serial line formatting. Exact layouts of contracts/serial-protocol.md. (The JSON payloads
// of contracts/mqtt-topics.md are tested in T055, in the MQTT story.)
#include <string.h>
#include <unity.h>

#include "record_format.h"

using namespace domain;

void setUp() {}
void tearDown() {}

static void test_header_line() {
    char buf[160];
    int n = formatHeader(buf, sizeof(buf), "0.1.0", "A1B2C3", 7, "beacon", "9F3C1A02", 115200);
    TEST_ASSERT_GREATER_THAN_INT(0, n);
    TEST_ASSERT_EQUAL_STRING("# fw=0.1.0 node=A1B2C3 boot=7 role=beacon id=9F3C1A02 baud=115200", buf);
    TEST_ASSERT_EQUAL_INT((int)strlen(buf), n);
}

static void test_live_line() {
    char buf[96];
    LiveLine l{123456, 7, "A1B2C3", "9F3C1A02", -67, 9.8f};
    TEST_ASSERT_GREATER_THAN_INT(0, formatLive(buf, sizeof(buf), l));
    TEST_ASSERT_EQUAL_STRING("L,123456,7,A1B2C3,9F3C1A02,-67,9.8", buf);
}

static void test_raw_line_has_two_decimals_for_position() {
    char buf[128];
    RawRecord r{123456, 7, "A1B2C3", "9F3C1A02", -67, "S1", "P3", 2.0f, 4.5f};
    TEST_ASSERT_GREATER_THAN_INT(0, formatRaw(buf, sizeof(buf), r));
    TEST_ASSERT_EQUAL_STRING("R,123456,7,A1B2C3,9F3C1A02,-67,S1,P3,2.00,4.50", buf);
}

static void test_window_line_has_one_decimal_for_statistics() {
    char buf[160];
    WindowRecord w{120000, 2000, 7, "A1B2C3", "9F3C1A02", 9, -66.8f, -67.0f, 1.9f, -71, -63, false, "S1", "P3", 2.0f, 4.5f};
    TEST_ASSERT_GREATER_THAN_INT(0, formatWindow(buf, sizeof(buf), w));
    TEST_ASSERT_EQUAL_STRING("W,120000,2000,7,A1B2C3,9F3C1A02,9,-66.8,-67.0,1.9,-71,-63,0,S1,P3,2.00,4.50", buf);
}

static void test_missing_window_has_empty_statistics() {
    char buf[160];
    WindowRecord w{120000, 2000, 7, "A1B2C3", "9F3C1A02", 0, 0, 0, 0, 0, 0, true, "S1", "P3", 2.0f, 4.5f};
    TEST_ASSERT_GREATER_THAN_INT(0, formatWindow(buf, sizeof(buf), w));
    TEST_ASSERT_EQUAL_STRING("W,120000,2000,7,A1B2C3,9F3C1A02,0,,,,,,1,S1,P3,2.00,4.50", buf);
}

static void test_summary_line() {
    char buf[128];
    SummaryLine s{130000, 7, "A1B2C3", "S1", "P3", "9F3C1A02", 540, 9.0f};
    TEST_ASSERT_GREATER_THAN_INT(0, formatSummary(buf, sizeof(buf), s));
    TEST_ASSERT_EQUAL_STRING("S,130000,7,A1B2C3,S1,P3,9F3C1A02,540,9.0", buf);
}

static void test_beacon_health_line() {
    char buf[96];
    BeaconHealthLine b{130000, 7, "9F3C1A02", 9.8f, 2};
    TEST_ASSERT_GREATER_THAN_INT(0, formatBeaconHealth(buf, sizeof(buf), b));
    TEST_ASSERT_EQUAL_STRING("B,130000,7,9F3C1A02,9.8,2", buf);
}

static void test_device_health_line() {
    char buf[192];
    HealthLine h{130000, 7, "A1B2C3", 7, 3600, 0, 1, 2, 1, 0, 152000, 131000, -58, 0, 100};
    TEST_ASSERT_GREATER_THAN_INT(0, formatHealth(buf, sizeof(buf), h));
    TEST_ASSERT_EQUAL_STRING("H,130000,7,A1B2C3,7,3600,0,1,2,1,0,152000,131000,-58,0,100", buf);
}

static void test_event_line_replaces_commas_in_the_message() {
    char buf[128];
    TEST_ASSERT_GREATER_THAN_INT(0, formatEvent(buf, sizeof(buf), 130000, 7, "error", "bad_id", "Invalid id, rejected"));
    TEST_ASSERT_EQUAL_STRING("E,130000,7,error,bad_id,Invalid id; rejected", buf);
}

static void test_no_commas_inside_label_fields_means_fixed_field_count() {
    char buf[128];
    RawRecord r{1, 0, "A1B2C3", "9F3C1A02", -90, "S1", "P3", 0.0f, 0.0f};
    TEST_ASSERT_GREATER_THAN_INT(0, formatRaw(buf, sizeof(buf), r));
    int commas = 0;
    for (const char* p = buf; *p; ++p) commas += (*p == ',');
    TEST_ASSERT_EQUAL_INT(9, commas);  // 10 fields: R + 9 values
}

static void test_returns_minus_one_when_the_buffer_is_too_small() {
    char buf[10];
    RawRecord r{123456, 7, "A1B2C3", "9F3C1A02", -67, "S1", "P3", 2.0f, 4.5f};
    TEST_ASSERT_EQUAL_INT(-1, formatRaw(buf, sizeof(buf), r));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_header_line);
    RUN_TEST(test_live_line);
    RUN_TEST(test_raw_line_has_two_decimals_for_position);
    RUN_TEST(test_window_line_has_one_decimal_for_statistics);
    RUN_TEST(test_missing_window_has_empty_statistics);
    RUN_TEST(test_summary_line);
    RUN_TEST(test_beacon_health_line);
    RUN_TEST(test_device_health_line);
    RUN_TEST(test_event_line_replaces_commas_in_the_message);
    RUN_TEST(test_no_commas_inside_label_fields_means_fixed_field_count);
    RUN_TEST(test_returns_minus_one_when_the_buffer_is_too_small);
    return UNITY_END();
}
