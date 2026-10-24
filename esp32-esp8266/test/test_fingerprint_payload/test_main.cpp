// JSON body for POST /fingerprints (the API repo's FingerprintRecordIn), built from a batch of
// RawRecord rows the collector already has.
#include <string.h>
#include <unity.h>

#include "fingerprint_payload.h"

using namespace domain;

void setUp() {}
void tearDown() {}

static void test_empty_batch_is_an_empty_array() {
    char buf[32];
    TEST_ASSERT_EQUAL_INT(2, formatFingerprintBatch(buf, sizeof(buf), nullptr, 0, "ESP32"));
    TEST_ASSERT_EQUAL_STRING("[]", buf);
}

static void test_one_row() {
    char buf[256];
    RawRecord r{123456, 7, "A1B2C3", "9F3C1A02", -67, "S1", "P3", 2.0f, 4.5f};
    const int n = formatFingerprintBatch(buf, sizeof(buf), &r, 1, "ESP32");
    TEST_ASSERT_GREATER_THAN_INT(0, n);
    TEST_ASSERT_EQUAL_INT((int)strlen(buf), n);
    TEST_ASSERT_EQUAL_STRING(
        "[{\"nodeID\":\"A1B2C3\",\"platform\":\"ESP32\",\"beaconID\":\"9F3C1A02\",\"rssi\":-67,"
        "\"session\":\"S1\",\"point\":\"P3\",\"x\":2.00,\"y\":4.50}]",
        buf);
}

static void test_several_rows_are_comma_separated() {
    char buf[512];
    RawRecord rows[2] = {
        {1, 7, "A1B2C3", "9F3C1A02", -67, "S1", "P3", 2.0f, 4.5f},
        {2, 7, "A1B2C3", "B7E2F910", -72, "S1", "P3", 2.0f, 4.5f},
    };
    const int n = formatFingerprintBatch(buf, sizeof(buf), rows, 2, "ESP32");
    TEST_ASSERT_GREATER_THAN_INT(0, n);
    TEST_ASSERT_EQUAL_STRING(
        "[{\"nodeID\":\"A1B2C3\",\"platform\":\"ESP32\",\"beaconID\":\"9F3C1A02\",\"rssi\":-67,"
        "\"session\":\"S1\",\"point\":\"P3\",\"x\":2.00,\"y\":4.50},"
        "{\"nodeID\":\"A1B2C3\",\"platform\":\"ESP32\",\"beaconID\":\"B7E2F910\",\"rssi\":-72,"
        "\"session\":\"S1\",\"point\":\"P3\",\"x\":2.00,\"y\":4.50}]",
        buf);
}

static void test_refuses_rather_than_truncate_when_the_buffer_is_too_small() {
    char buf[16];
    RawRecord r{1, 7, "A1B2C3", "9F3C1A02", -67, "S1", "P3", 2.0f, 4.5f};
    TEST_ASSERT_EQUAL_INT(-1, formatFingerprintBatch(buf, sizeof(buf), &r, 1, "ESP32"));
}

static void test_a_too_small_buffer_for_even_the_brackets_is_refused() {
    char buf[1];
    TEST_ASSERT_EQUAL_INT(-1, formatFingerprintBatch(buf, sizeof(buf), nullptr, 0, "ESP32"));
}

int main(int, char**) {
    UNITY_BEGIN();
    RUN_TEST(test_empty_batch_is_an_empty_array);
    RUN_TEST(test_one_row);
    RUN_TEST(test_several_rows_are_comma_separated);
    RUN_TEST(test_refuses_rather_than_truncate_when_the_buffer_is_too_small);
    RUN_TEST(test_a_too_small_buffer_for_even_the_brackets_is_refused);
    return UNITY_END();
}
